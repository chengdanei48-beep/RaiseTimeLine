<#
.SYNOPSIS
  性能テストを手動で実行する(CI には組み込まない)。詳細は docs/09-performance-testing.md。

.DESCRIPTION
  専用 DB(raisetimeline_perf)を作ってシードを投入し、perf プロファイルでバックエンドを起動して計測する。
  終了時(成功・失敗・Ctrl+C のいずれでも)に、起動したプロセスの停止と専用 DB の削除を行う。開発 DB には触れない。

.PARAMETER Target
  api(k6 の API 負荷)/ browser(Playwright + Lighthouse)/ all(api の後に browser)/ combined(k6 の背景負荷をかけながら browser)

.PARAMETER Scenario
  api / combined で使う k6 シナリオ: smoke / read-mix / write-mix / login(combined は read-mix 固定)

.PARAMETER LoadProfile
  負荷の形: load(既定)/ stress / soak

.PARAMETER Rows
  投稿数。既定 5000(要件規模)、10 倍の 50000 でも実行する。

.PARAMETER KeepData
  終了時に専用 DB を削除せず残す(結果の調査用)。

.PARAMETER Cleanup
  計測せず、専用 DB と一時ファイルだけ削除する。

.PARAMETER Prune
  perf/results に残す結果の世代数。古いものを削除する。
#>
# 対話実行用の進捗表示(Write-Host)と、内部関数(Stop-PortOwner / Remove-PerfDatabase)は意図した設計のため抑制する
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingWriteHost', '')]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseShouldProcessForStateChangingFunctions', '')]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPositionalParameters', '')]
[CmdletBinding()]
param(
  [ValidateSet('api', 'browser', 'all', 'combined')][string]$Target = 'api',
  [ValidateSet('smoke', 'read-mix', 'write-mix', 'login')][string]$Scenario = 'read-mix',
  [ValidateSet('load', 'stress', 'soak')][string]$LoadProfile = 'load',
  [int]$Rows = 5000,
  [int]$Users = 50,
  [switch]$KeepData,
  [switch]$Cleanup,
  [int]$Prune = 10
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$PerfDb = 'raisetimeline_perf'
$PgContainer = 'raisetimeline-postgres'
$BackendPort = 8080
$FrontendPort = 5173
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$ResultDir = Join-Path $Root "perf/results/$Stamp"
$TmpDir = Join-Path $Root 'perf/browser/.tmp'
$Started = New-Object System.Collections.Generic.List[object]   # 起動したプロセス(停止・報告用)
$Stopped = New-Object System.Collections.Generic.List[object]   # 停止したプロセス(報告用)
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
function Assert-PerfDb([string]$name) {
  if ($name -notmatch '_perf$') { throw "安全装置: '$name' は *_perf ではないため中止します" }
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

function Write-K6Summary([string]$file) {
  if (-not (Test-Path $file)) { return }
  $m = (Get-Content $file -Raw | ConvertFrom-Json).metrics
  $d = $m.http_req_duration
  Write-Host ("  リクエスト {0} 件 / 失敗率 {1:P2} / avg {2:N0}ms p95 {3:N0}ms max {4:N0}ms" -f
    $m.http_reqs.count, $m.http_req_failed.value, $d.avg, $d.'p(95)', $d.max)
}

function Invoke-K6([string]$scenario, [string]$name, [switch]$Background) {
  # Windows の Docker から ホストの 8080 へは host.docker.internal で届く
  $dockerArgs = @(
    'run', '--rm', '-v', "$($Root)/perf/k6:/scripts:ro", '-v', "$($ResultDir):/out",
    '-e', 'BASE_URL=http://host.docker.internal:8080', '-e', "K6_PROFILE=$LoadProfile", '-e', "PERF_USERS=$Users",
    'grafana/k6:latest', 'run', "--summary-export=/out/k6-$name.json", "/scripts/scenarios/$scenario.js"
  )
  if ($Background) {
    return Start-Process -FilePath 'docker' -ArgumentList $dockerArgs -PassThru -NoNewWindow `
      -RedirectStandardOutput (Join-Path $ResultDir "k6-$name.log") -RedirectStandardError (Join-Path $ResultDir "k6-$name.err.log")
  }
  & docker @dockerArgs
  $code = $LASTEXITCODE
  Write-K6Summary (Join-Path $ResultDir "k6-$name.json")
  if ($code -ne 0) { $Failures.Add("k6 $name が閾値違反または失敗 (exit $code)") }
}

function Invoke-Browser([string]$script, [hashtable]$extraEnv = @{}) {
  $env:BASE_URL = "http://localhost:$FrontendPort"
  $env:OUT_DIR = $ResultDir
  $env:TMP_DIR = $TmpDir
  foreach ($k in $extraEnv.Keys) { Set-Item -Path "env:$k" -Value $extraEnv[$k] }
  Push-Location (Join-Path $Root 'perf/browser')
  try {
    & node $script
    if ($LASTEXITCODE -ne 0) { $Failures.Add("browser $script が閾値違反または失敗 (exit $LASTEXITCODE)") }
  } finally {
    Pop-Location
    foreach ($k in $extraEnv.Keys) { Remove-Item -Path "env:$k" -ErrorAction SilentlyContinue }
  }
}

function Remove-PerfDatabase([string]$user) {
  Assert-PerfDb $PerfDb
  & docker exec $PgContainer psql -U $user -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $PerfDb WITH (FORCE)" | Out-Null
  if ($LASTEXITCODE -ne 0) { Write-Warning "専用 DB の削除に失敗しました: $PerfDb" }
}

$envMap = Read-DotEnv
$dbUser = if ($envMap.ContainsKey('DB_USER')) { $envMap['DB_USER'] } else { 'raisetimeline' }

# 単独クリーンアップ
if ($Cleanup) {
  Remove-PerfDatabase $dbUser
  if (Test-Path $TmpDir) { Remove-Item $TmpDir -Recurse -Force }
  Write-Host "クリーンアップ完了: DB $PerfDb と一時ファイルを削除しました"
  return
}

$health = (& docker inspect -f '{{.State.Health.Status}}' $PgContainer) 2>$null
if ($health -ne 'healthy') { throw "$PgContainer が healthy ではありません。docker compose up -d で起動してください" }

New-Item -ItemType Directory -Force $ResultDir | Out-Null
$needBrowser = $Target -in 'browser', 'all', 'combined'
$exit = 0

try {
  # 1. 既定ポートを空ける(最新コードを反映するため、動いていても停止して起動し直す)
  Stop-PortOwner $BackendPort
  if ($needBrowser) { Stop-PortOwner $FrontendPort }

  # 2. 専用 DB を作り直す
  Remove-PerfDatabase $dbUser
  Invoke-Psql 'postgres' @('-c', "CREATE DATABASE $PerfDb") $dbUser | Out-Null

  # 3. バックエンドを build して perf プロファイルで起動(Flyway がスキーマを作る)
  Push-Location (Join-Path $Root 'backend')
  try { & .\mvnw.cmd -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'バックエンドの build に失敗' } } finally { Pop-Location }
  $jar = Get-ChildItem (Join-Path $Root 'backend/target') -Filter 'raisetimeline-*.jar' |
    Where-Object { $_.Name -notmatch 'plain' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  $env:SPRING_PROFILES_ACTIVE = 'perf'
  $env:DB_NAME = $PerfDb
  $env:LOG_FORMAT = 'ecs'
  $env:APP_ENV = 'perf'
  $app = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar.FullName) -WorkingDirectory (Join-Path $Root 'backend') `
    -PassThru -NoNewWindow -RedirectStandardOutput (Join-Path $ResultDir 'app.log') -RedirectStandardError (Join-Path $ResultDir 'app.err.log')
  $Started.Add([pscustomobject]@{ ProcessId = $app.Id; Name = 'java (backend)'; CommandLine = "java -jar $($jar.Name) [perf]" })
  Remove-Item Env:SPRING_PROFILES_ACTIVE, Env:DB_NAME, Env:LOG_FORMAT, Env:APP_ENV -ErrorAction SilentlyContinue
  Wait-Http "http://localhost:$BackendPort/api/health" 90

  # 4. シード投入
  & docker cp (Join-Path $Root 'perf/seed/seed.sql') "${PgContainer}:/tmp/perf-seed.sql"
  Invoke-Psql $PerfDb @('-v', "users=$Users", '-v', "posts=$Rows", '-f', '/tmp/perf-seed.sql') $dbUser

  # 5. フロントエンド(本番ビルドを vite preview で配信)
  if ($needBrowser) {
    Push-Location (Join-Path $Root 'frontend')
    try {
      if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci に失敗' } }
      & npm run build; if ($LASTEXITCODE -ne 0) { throw 'フロントエンドの build に失敗' }
      $fe = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'npm run preview' -PassThru -NoNewWindow `
        -RedirectStandardOutput (Join-Path $ResultDir 'preview.log') -RedirectStandardError (Join-Path $ResultDir 'preview.err.log')
      $Started.Add([pscustomobject]@{ ProcessId = $fe.Id; Name = 'node (vite preview)'; CommandLine = 'npm run preview' })
    } finally { Pop-Location }
    Wait-Http "http://localhost:$FrontendPort/" 60
    Push-Location (Join-Path $Root 'perf/browser')
    try {
      if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'perf/browser の npm ci に失敗' } }
      & npx playwright install chromium; if ($LASTEXITCODE -ne 0) { throw 'Chromium の取得に失敗' }
    } finally { Pop-Location }
  }

  # 6. 計測
  Write-Host "== 計測開始: Target=$Target Scenario=$Scenario LoadProfile=$LoadProfile Rows=$Rows =="
  switch ($Target) {
    'api' { Invoke-K6 $Scenario $Scenario }
    'browser' {
      Invoke-Browser 'measure.mjs'
      Invoke-Browser 'measure.mjs' @{ THROTTLE = '1' }
      Invoke-Browser 'lighthouse.mjs'
    }
    'all' {
      Invoke-K6 $Scenario $Scenario
      Invoke-Browser 'measure.mjs'
      Invoke-Browser 'measure.mjs' @{ THROTTLE = '1' }
      Invoke-Browser 'lighthouse.mjs'
    }
    'combined' {
      # k6 で背景負荷をかけ、ウォームアップ後に画面の体感を測る
      $bg = Invoke-K6 'read-mix' 'combined-background' -Background
      $null = $bg.Handle   # 先にハンドルを掴まないと、終了後に ExitCode が取れない(Windows PowerShell 5.1)
      $Started.Add([pscustomobject]@{ ProcessId = $bg.Id; Name = 'docker (k6 background)'; CommandLine = 'docker run grafana/k6 read-mix' })
      Start-Sleep -Seconds 20
      Invoke-Browser 'measure.mjs' @{ PERF_USERNAME = 'perf_user_050' }
      $bg.WaitForExit()
      if ($bg.ExitCode -ne 0) { $Failures.Add("k6 背景負荷が閾値違反または失敗 (exit $($bg.ExitCode))") }
      Write-K6Summary (Join-Path $ResultDir 'k6-combined-background.json')
    }
  }
} catch {
  $Failures.Add($_.Exception.Message)
} finally {
  # 後始末(成功・失敗・Ctrl+C のいずれでも実行)
  Write-Host '== 後始末 =='
  foreach ($p in $Started) {
    if (Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue) { & taskkill /T /F /PID $p.ProcessId | Out-Null }
  }
  # 取りこぼしの確認。今回使ったポートだけ(使っていない 5173 の開発サーバーは止めない)
  $usedPorts = if ($needBrowser) { @($BackendPort, $FrontendPort) } else { @($BackendPort) }
  foreach ($port in $usedPorts) {
    try { Stop-PortOwner $port } catch { Write-Warning $_.Exception.Message }
  }
  & docker exec $PgContainer rm -f /tmp/perf-seed.sql 2>$null | Out-Null
  if (-not $KeepData) { Remove-PerfDatabase $dbUser } else { Write-Host "専用 DB $PerfDb を残しました(-KeepData)。不要になったら -Cleanup で削除してください" }
  if (Test-Path $TmpDir) { Remove-Item $TmpDir -Recurse -Force -ErrorAction SilentlyContinue }
  # 結果は世代管理し、新しい $Prune 件だけ残す
  Get-ChildItem (Join-Path $Root 'perf/results') -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -Skip $Prune | Remove-Item -Recurse -Force

  Write-Host '起動したプロセス:'
  $Started | ForEach-Object { Write-Host ("  PID {0} {1}: {2}" -f $_.ProcessId, $_.Name, $_.CommandLine) }
  Write-Host '停止した既存プロセス(ポート解放のため):'
  if ($Stopped.Count -eq 0) { Write-Host '  なし' }
  $Stopped | ForEach-Object { Write-Host ("  PID {0} {1}: {2}" -f $_.ProcessId, $_.Name, $_.CommandLine) }
}

Write-Host "結果: $ResultDir"
if ($Failures.Count -gt 0) {
  Write-Host '== 失敗 ==' -ForegroundColor Red
  $Failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
  $exit = 1
} else {
  Write-Host '== すべての閾値を満たしました ==' -ForegroundColor Green
}
exit $exit
