# Study 2.1 — build-all.ps1
# 前端 lint + typecheck + test + build，后端 clean package。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$cmd = Get-Command mvn -ErrorAction SilentlyContinue
$mvn = if ($cmd) { $cmd.Source } elseif (Test-Path 'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd') { 'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd' } else { throw 'Maven (mvn) not found. See docs/DEVELOPMENT_SETUP.md' }

Write-Host '== Building frontend =='
Push-Location (Join-Path $Root 'frontend')
try {
    npm run lint
    if ($LASTEXITCODE -ne 0) { throw 'frontend lint failed' }
    npm run typecheck
    if ($LASTEXITCODE -ne 0) { throw 'frontend typecheck failed' }
    npm run test
    if ($LASTEXITCODE -ne 0) { throw 'frontend test failed' }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
} finally {
    Pop-Location
}

Write-Host '== Building backend =='
Push-Location (Join-Path $Root 'backend')
try {
    & $mvn -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }
} finally {
    Pop-Location
}

Write-Host 'build-all complete.'
