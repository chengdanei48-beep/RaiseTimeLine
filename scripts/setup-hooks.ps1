<#
.SYNOPSIS
  このリポジトリの Git フック(scripts/git-hooks/)を有効にする。クローン直後に一度だけ実行すればよい。

.DESCRIPTION
  core.hooksPath を scripts/git-hooks に向ける。main への直接 push を、GitHub 側のブランチ保護ルールより先に
  ローカルで拒否できるようになる(CLAUDE.md「mainへの直接pushは禁止。必ずPR経由でマージする」)。
#>
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingWriteHost', '')]
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$HooksDir = Join-Path $Root 'scripts/git-hooks'

if (-not (Test-Path (Join-Path $HooksDir 'pre-push'))) {
  throw "フックが見つかりません: $HooksDir/pre-push"
}

Push-Location $Root
try {
  & git config core.hooksPath 'scripts/git-hooks'
  if ($LASTEXITCODE -ne 0) { throw 'git config core.hooksPath の設定に失敗しました' }
} finally {
  Pop-Location
}

Write-Host 'Git フックを有効にしました(core.hooksPath = scripts/git-hooks)。' -ForegroundColor Green
Write-Host 'main への直接 push は、このクローンでもローカルで拒否されます。'
