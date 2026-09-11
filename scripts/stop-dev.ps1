# Study 2.1 — stop-dev.ps1
# 只停止由 Study 2.1 启动脚本创建的进程（按 PID 文件 + 进程树），不杀无关进程。
$ErrorActionPreference = 'Continue'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tmp = Join-Path $Root 'tmp'

$names = @('pc-web', 'mobile-web', 'admin-api', 'user-api')

foreach ($name in $names) {
    $PidFile = Join-Path $Tmp "$name.pid"
    if (Test-Path $PidFile) {
        $pidValue = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1).Trim()
        if ($pidValue -and (Get-Process -Id $pidValue -ErrorAction SilentlyContinue)) {
            & taskkill /PID $pidValue /T /F 2>$null | Out-Null
            Write-Host "$name stopped (PID $pidValue)"
        } else {
            Write-Host "$name was not running (stale PID $pidValue)"
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "$name is not running"
    }
}

Write-Host 'Study 2.1 dev services stopped.'
