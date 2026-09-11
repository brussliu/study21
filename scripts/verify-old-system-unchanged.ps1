# Study 2.1 — verify-old-system-unchanged.ps1
# 检查旧系统 Git 状态是否与 Study 2.1 脚手架开始前的快照一致。
# 旧系统只读：不执行 commit / stash / reset / checkout / clean。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Old = 'C:\work\Source\study2\study2'
$Snapshot = Join-Path $Root 'tmp\old-system-status-before.txt'

if (-not (Test-Path (Join-Path $Old '.git'))) {
    Write-Host "WARN: $Old is not a git repository (no .git directory). Nothing to compare."
    exit 0
}

$head = (git -C $Old rev-parse HEAD).Trim()
$porcelain = git -C $Old status --porcelain

if (-not (Test-Path $Snapshot)) {
    Write-Host "WARN: snapshot not found: $Snapshot"
    Write-Host "Current HEAD: $head"
    Write-Host 'Cannot compare; skipping. (Run scripts before modifying anything.)'
    exit 0
}

$nowLines = @("HEAD: $head") + $porcelain
$nowText = $nowLines -join "`r`n"
$beforeText = (Get-Content -LiteralPath $Snapshot -Raw).TrimEnd()

if ($nowText.TrimEnd() -eq $beforeText) {
    Write-Host "OK: old system unchanged (HEAD $head, git status identical to snapshot)"
    exit 0
}

Write-Host 'CHANGED: old system git status differs from the snapshot captured before Study 2.1 scaffold.'
Write-Host "  snapshot : $Snapshot"
Write-Host "  current HEAD: $head"
Write-Host '  This may be pre-existing user changes — DO NOT clean/commit/revert/reset.'
exit 1
