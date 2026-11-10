<#
.SYNOPSIS
  E2E テスト(Playwright)を手動で実行する。詳細は docs/11-e2e-testing.md。

.DESCRIPTION
  専用 DB(raisetimeline_e2e)と専用 S3 バケット(raisetimeline-e2e)を用意し、
  e2e プロファイルでバックエンドを起動し、フロントエンドは本番ビルド(vite preview)で配信して実行する。
  終了時(成功・失敗・Ctrl+C のいずれでも)に、起動したプロセスの停止と専用 DB・バケットの削除を行う。

.PARAMETER Tag
  perf を指定すると、通常のシナリオではなくブラウザ性能計測(@perf)だけを実行する。

.PARAMETER Grep
  Playwright の --grep にそのまま渡す(個別のテストだけ実行したいとき)。

.PARAMETER Headed
  ブラウザを表示して実行する(デバッグ用)。

.PARAMETER KeepData
  終了時に専用 DB・S3 バケットを削除せず残す(結果の調査用)。

.PARAMETER Cleanup
  実行せず、専用 DB・バケット・一時ファイルだけ削除する。
#>
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingWriteHost', '')]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseShouldProcessForStateChangingFunctions', '')]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPositionalParameters', '')]
[CmdletBinding()]
param(
  [ValidateSet('all', 'perf')][string]$Tag = 'all',
  [string]$Grep,
  [switch]$Headed,
  [switch]$KeepData,
  [switch]$Cleanup
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$E2eDb = 'raisetimeline_e2e'
$E2eBucket = 'raisetimeline-e2e'
$PgContainer = 'raisetimeline-postgres'
$BackendPort = 8080
$FrontendPort = 5173
$S3Port = 9000
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$ResultDir = Join-Path $Root "perf/results/e2e-$Stamp"
$Started = New-Object System.Collections.Generic.List[object]
$Stopped = New-Object System.Collections.Generic.List[object]
$Failures = New-Object System.Collections.Generic.List[string]

function Read-DotEnv {
  $map = @{}
  $path = Join-Path $Root '.env'
  if (-not (Test-Path $path)) { throw '.env がありません。.env.example をコピーして設定してください' }
  foreach ($line in Get-Content $path) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $map[$Matches[1]] = $Matches[2].Trim() }
  }
  return $map
}

# 開発 DB の誤削除を防ぐ。専用 DB 名以外は処理しない
function Assert-E2eDb([string]$name) {
  if ($name -notmatch '_e2e$') { throw "安全装置: '$name' は *_e2e ではないため中止します" }
}

# CLAUDE.md「動作確認でのサーバー起動」: 使用中ならそのプロセスを停止して既定ポートを空ける(別ポートへは逃げない)
function Stop-PortOwner([int]$port) {
  $pids = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($p in $pids) {
    $info = Get-CimInstance Win32_Process -Filter "ProcessId=$p" | Select-Object ProcessId, Name, CommandLine
    $Stopped.Add($info)
    & taskkill /T /F /PID $p | Out-Null
  }
  for ($i = 0; $i -lt 20; $i++) {
    if (-not (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "ポート $port を解放できませんでした"
}

function Invoke-Psql([string]$db, [string[]]$psqlArgs, [string]$user) {
  $out = & docker exec $PgContainer psql -U $user -d $db -v ON_ERROR_STOP=1 @psqlArgs
  if ($LASTEXITCODE -ne 0) { throw "psql が失敗しました (db=$db)" }
  return $out
}

function Wait-Http([string]$url, [int]$timeoutSec) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
      if ($r.StatusCode -eq 200) { return }
    } catch { Start-Sleep -Milliseconds 700 }
  }
  throw "$url が $timeoutSec 秒以内に応答しませんでした"
}

function Remove-E2eDatabase([string]$user) {
  Assert-E2eDb $E2eDb
  & docker exec $PgContainer psql -U $user -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $E2eDb WITH (FORCE)" | Out-Null
  if ($LASTEXITCODE -ne 0) { Write-Warning "専用 DB の削除に失敗しました: $E2eDb" }
}

function New-E2eBucket {
  if ($E2eBucket -notmatch '-e2e$') { throw "安全装置: '$E2eBucket' は *-e2e ではないため中止します" }
  Invoke-WebRequest -Uri "http://localhost:$S3Port/$E2eBucket" -Method Put -UseBasicParsing | Out-Null
}

function Remove-E2eBucket {
  if ($E2eBucket -notmatch '-e2e$') { throw "安全装置: '$E2eBucket' は *-e2e ではないため中止します" }
  try {
    [xml]$listing = (Invoke-WebRequest -Uri "http://localhost:$S3Port/$E2eBucket" -UseBasicParsing).Content
    $keys = $listing.ListBucketResult.Contents | ForEach-Object { $_.Key }
    foreach ($key in $keys) {
      Invoke-WebRequest -Uri "http://localhost:$S3Port/$E2eBucket/$key" -Method Delete -UseBasicParsing | Out-Null
    }
    Invoke-WebRequest -Uri "http://localhost:$S3Port/$E2eBucket" -Method Delete -UseBasicParsing | Out-Null
  } catch {
    Write-Warning "専用バケットの削除に失敗しました: $E2eBucket ($($_.Exception.Message))"
  }
}

$envMap = Read-DotEnv
$dbUser = if ($envMap.ContainsKey('DB_USER')) { $envMap['DB_USER'] } else { 'raisetimeline' }

# 単独クリーンアップ
if ($Cleanup) {
  Remove-E2eDatabase $dbUser
  Remove-E2eBucket
  Write-Host "クリーンアップ完了: DB $E2eDb とバケット $E2eBucket を削除しました"
  return
}

$pgHealth = (& docker inspect -f '{{.State.Health.Status}}' $PgContainer) 2>$null
if ($pgHealth -ne 'healthy') { throw "$PgContainer が healthy ではありません。docker compose up -d で起動してください" }
try { Invoke-WebRequest -Uri "http://localhost:$S3Port/" -UseBasicParsing -TimeoutSec 3 | Out-Null } catch {
  throw "S3Mock (localhost:$S3Port) に接続できません。docker compose up -d で起動してください"
}

New-Item -ItemType Directory -Force $ResultDir | Out-Null
$exit = 0

try {
  # 1. 既定ポートを空ける(最新コードを反映するため、動いていても停止して起動し直す)
  Stop-PortOwner $BackendPort
  Stop-PortOwner $FrontendPort

  # 2. 専用 DB・バケットを作り直す
  Remove-E2eDatabase $dbUser
  Invoke-Psql 'postgres' @('-c', "CREATE DATABASE $E2eDb") $dbUser | Out-Null
  Remove-E2eBucket
  New-E2eBucket

  # 3. バックエンドを build して e2e プロファイルで起動(Flyway がスキーマを作る)
  Push-Location (Join-Path $Root 'backend')
  try { & .\mvnw.cmd -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'バックエンドの build に失敗' } } finally { Pop-Location }
  $jar = Get-ChildItem (Join-Path $Root 'backend/target') -Filter 'raisetimeline-*.jar' |
    Where-Object { $_.Name -notmatch 'plain' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  $env:SPRING_PROFILES_ACTIVE = 'e2e'
  $env:DB_NAME = $E2eDb
  $env:S3_BUCKET = $E2eBucket
  $env:APP_ENV = 'e2e'
  $app = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar.FullName) -WorkingDirectory (Join-Path $Root 'backend') `
    -PassThru -NoNewWindow -RedirectStandardOutput (Join-Path $ResultDir 'app.log') -RedirectStandardError (Join-Path $ResultDir 'app.err.log')
  $Started.Add([pscustomobject]@{ ProcessId = $app.Id; Name = 'java (backend)'; CommandLine = "java -jar $($jar.Name) [e2e]" })
  Remove-Item Env:SPRING_PROFILES_ACTIVE, Env:DB_NAME, Env:S3_BUCKET, Env:APP_ENV -ErrorAction SilentlyContinue
  Wait-Http "http://localhost:$BackendPort/api/health" 90

  # 4. フロントエンド(本番ビルドを vite preview で配信)
  Push-Location (Join-Path $Root 'frontend')
  try {
    if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci に失敗' } }
    & npm run build; if ($LASTEXITCODE -ne 0) { throw 'フロントエンドの build に失敗' }
    $fe = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'npm run preview' -PassThru -NoNewWindow `
      -RedirectStandardOutput (Join-Path $ResultDir 'preview.log') -RedirectStandardError (Join-Path $ResultDir 'preview.err.log')
    $Started.Add([pscustomobject]@{ ProcessId = $fe.Id; Name = 'node (vite preview)'; CommandLine = 'npm run preview' })
  } finally { Pop-Location }
  Wait-Http "http://localhost:$FrontendPort/" 60

  # 5. Playwright の依存
  Push-Location (Join-Path $Root 'e2e')
  try {
    if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'e2e の npm ci に失敗' } }
    & npx playwright install chromium; if ($LASTEXITCODE -ne 0) { throw 'Chromium の取得に失敗' }

    # 6. 実行
    $playwrightArgs = @('playwright', 'test')
    if ($Headed) { $playwrightArgs += '--headed' }
    if ($Grep) { $playwrightArgs += @('--grep', $Grep) }
    if ($Tag -eq 'perf') {
      $env:E2E_INCLUDE_PERF = '1'
      $playwrightArgs += @('--grep', '@perf')
    }
    $env:E2E_PERF_OUT_DIR = $ResultDir
    Write-Host "== 実行開始: Tag=$Tag =="
    & npx @playwrightArgs
    if ($LASTEXITCODE -ne 0) { $Failures.Add("Playwright が失敗しました (exit $LASTEXITCODE)") }
  } finally {
    Remove-Item Env:E2E_INCLUDE_PERF, Env:E2E_PERF_OUT_DIR -ErrorAction SilentlyContinue
    Pop-Location
  }
} catch {
  $Failures.Add($_.Exception.Message)
} finally {
  # 後始末(成功・失敗・Ctrl+C のいずれでも実行)
  Write-Host '== 後始末 =='
  foreach ($p in $Started) {
    if (Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue) { & taskkill /T /F /PID $p.ProcessId | Out-Null }
  }
  foreach ($port in @($BackendPort, $FrontendPort)) {
    try { Stop-PortOwner $port } catch { Write-Warning $_.Exception.Message }
  }
  if (-not $KeepData) {
    Remove-E2eDatabase $dbUser
    Remove-E2eBucket
  } else {
    Write-Host "専用 DB $E2eDb とバケット $E2eBucket を残しました(-KeepData)。不要になったら -Cleanup で削除してください"
  }

  Write-Host '起動したプロセス:'
  $Started | ForEach-Object { Write-Host ("  PID {0} {1}: {2}" -f $_.ProcessId, $_.Name, $_.CommandLine) }
  Write-Host '停止した既存プロセス(ポート解放のため):'
  if ($Stopped.Count -eq 0) { Write-Host '  なし' }
  $Stopped | ForEach-Object { Write-Host ("  PID {0} {1}: {2}" -f $_.ProcessId, $_.Name, $_.CommandLine) }
}

Write-Host "結果・ログ: $ResultDir"
Write-Host "Playwright レポート: e2e/playwright-report/index.html"
if ($Failures.Count -gt 0) {
  Write-Host '== 失敗 ==' -ForegroundColor Red
  $Failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
  $exit = 1
} else {
  Write-Host '== すべて成功しました ==' -ForegroundColor Green
}
exit $exit
