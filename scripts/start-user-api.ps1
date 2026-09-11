# Study 2.1 — start-user-api.ps1
# 启动 user-api（Maven spring-boot:run）。PID 写入 Study 2.1 自己的 tmp 目录。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tmp = Join-Path $Root 'tmp'
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

$PidFile = Join-Path $Tmp 'user-api.pid'
$LogFile = Join-Path $Tmp 'user-api.log'
$ErrFile = Join-Path $Tmp 'user-api.err.log'

$env:USER_API_PORT = if ($env:USER_API_PORT) { $env:USER_API_PORT } else { '8082' }

$cmd = Get-Command mvn -ErrorAction SilentlyContinue
$mvn = if ($cmd) { $cmd.Source } elseif (Test-Path 'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd') { 'C:\work\Tools\apache-maven-3.9.15\bin\mvn.cmd' } else { throw 'Maven (mvn) not found. See docs/DEVELOPMENT_SETUP.md' }

if (Test-Path $PidFile) {
    $existing = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1).Trim()
    if ($existing -and (Get-Process -Id $existing -ErrorAction SilentlyContinue)) {
        Write-Host "user-api is already running (PID $existing)"
        exit 0
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

$proc = Start-Process -FilePath $mvn -ArgumentList @('-B', '-pl', 'user-api', 'spring-boot:run') `
    -WorkingDirectory (Join-Path $Root 'backend') `
    -RedirectStandardOutput $LogFile -RedirectStandardError $ErrFile `
    -PassThru -WindowStyle Hidden

$proc.Id | Out-File -FilePath $PidFile -Encoding ascii
Write-Host "user-api starting (PID $($proc.Id), port $env:USER_API_PORT)"
Write-Host "log: $LogFile"
