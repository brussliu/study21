# Study 2.1 — start-dev.ps1
# 顺序启动全部开发服务。
$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'start-admin-api.ps1')
& (Join-Path $PSScriptRoot 'start-user-api.ps1')
& (Join-Path $PSScriptRoot 'start-pc.ps1')
& (Join-Path $PSScriptRoot 'start-mobile.ps1')

Write-Host ''
Write-Host 'All Study 2.1 dev services have been started.'
Write-Host '  PC     : http://localhost:5173'
Write-Host '  Mobile : http://localhost:5174'
Write-Host '  admin-api : http://localhost:8081/api/admin/health'
Write-Host '  user-api  : http://localhost:8082/api/user/health'
