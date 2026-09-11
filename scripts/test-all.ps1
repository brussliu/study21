# Study 2.1 — test-all.ps1
# 前端 lint + typecheck + unit test，后端 mvn test。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$cmd = Get-Command mvn -ErrorAction SilentlyContinue
$mvn = if ($cmd) { $cmd.Source } elseif (Test-Path 'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd') { 'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd' } else { throw 'Maven (mvn) not found. See docs/DEVELOPMENT_SETUP.md' }

Write-Host '== Testing frontend =='
Push-Location (Join-Path $Root 'frontend')
try {
    npm run lint
    if ($LASTEXITCODE -ne 0) { throw 'frontend lint failed' }
    npm run typecheck
    if ($LASTEXITCODE -ne 0) { throw 'frontend typecheck failed' }
    npm run test
    if ($LASTEXITCODE -ne 0) { throw 'frontend test failed' }
} finally {
    Pop-Location
}

Write-Host '== Testing backend =='
Push-Location (Join-Path $Root 'backend')
try {
    & $mvn -B test
    if ($LASTEXITCODE -ne 0) { throw 'backend test failed' }
} finally {
    Pop-Location
}

Write-Host 'test-all complete.'
