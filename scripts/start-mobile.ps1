# Study 2.1 — start-mobile.ps1
# 启动 Mobile 前端开发服务器。PID 写入 Study 2.1 自己的 tmp 目录。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tmp = Join-Path $Root 'tmp'
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

$PidFile = Join-Path $Tmp 'mobile-web.pid'
$LogFile = Join-Path $Tmp 'mobile-web.log'
$ErrFile = Join-Path $Tmp 'mobile-web.err.log'

$env:MOBILE_WEB_PORT = if ($env:MOBILE_WEB_PORT) { $env:MOBILE_WEB_PORT } else { '5174' }

if (Test-Path $PidFile) {
    $existing = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1).Trim()
    if ($existing -and (Get-Process -Id $existing -ErrorAction SilentlyContinue)) {
        Write-Host "mobile-web is already running (PID $existing)"
        exit 0
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$proc = Start-Process -FilePath $npm -ArgumentList @('run', 'dev') `
    -WorkingDirectory (Join-Path $Root 'frontend\mobile-web') `
    -RedirectStandardOutput $LogFile -RedirectStandardError $ErrFile `
    -PassThru -WindowStyle Hidden

$proc.Id | Out-File -FilePath $PidFile -Encoding ascii
Write-Host "mobile-web started (PID $($proc.Id), port $env:MOBILE_WEB_PORT)"
Write-Host "log: $LogFile"
