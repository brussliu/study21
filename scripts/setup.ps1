# Study 2.1 — setup.ps1
# 检查前置工具并安装前端依赖。不修改系统级环境变量。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tmp = Join-Path $Root 'tmp'
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

function Test-Command([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Resolve-MavenPath {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd',
        'C:\Program Files\Apache\maven\bin\mvn.cmd'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    throw 'Maven (mvn) not found. See docs/DEVELOPMENT_SETUP.md'
}

Write-Host '== Study 2.1 setup =='
Write-Host "Root: $Root"

$missing = @()
if (-not (Test-Command 'node')) { $missing += 'node' }
if (-not (Test-Command 'npm')) { $missing += 'npm' }
if (-not (Test-Command 'java')) { $missing += 'java' }
if (-not (Test-Command 'git')) { $missing += 'git' }
try { $null = Resolve-MavenPath } catch { $missing += 'mvn' }

if ($missing.Count -gt 0) {
    throw "Missing prerequisites: $($missing -join ', '). See docs/DEVELOPMENT_SETUP.md"
}

Write-Host 'Prerequisites OK (node / npm / java / mvn / git)'

Write-Host 'Installing frontend dependencies (npm install)...'
Push-Location (Join-Path $Root 'frontend')
try {
    npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host 'Setup complete.'
Write-Host 'Next steps:'
Write-Host '  start all : powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1'
Write-Host '  stop all  : powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1'
