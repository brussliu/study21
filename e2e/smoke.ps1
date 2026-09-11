# Study 2.1 — e2e/smoke.ps1
# 验证两个后端 health 端点返回 200 且为统一响应结构。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adminBase = if ($env:ADMIN_API_BASE_URL) { $env:ADMIN_API_BASE_URL } else { 'http://localhost:8081' }
$userBase = if ($env:USER_API_BASE_URL) { $env:USER_API_BASE_URL } else { 'http://localhost:8082' }

$failed = $false

function Test-Health([string]$Name, [string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -TimeoutSec 10 -UseBasicParsing
        if ($response.StatusCode -ne 200) {
            Write-Host "FAIL: $Name returned $($response.StatusCode)"
            $script:failed = $true
            return
        }
        $body = $response.Content | ConvertFrom-Json
        if (-not $body.success -or $body.code -ne 'OK') {
            Write-Host "FAIL: $Name returned unexpected body: $($response.Content)"
            $script:failed = $true
            return
        }
        Write-Host "OK: $Name -> $Url (status $($body.data.status), service $($body.data.service))"
    } catch {
        Write-Host "FAIL: $Name ($Url) — $($_.Exception.Message)"
        $script:failed = $true
    }
}

Write-Host '== Study 2.1 smoke test =='
Test-Health 'admin-api' "$adminBase/api/admin/health"
Test-Health 'user-api' "$userBase/api/user/health"

if ($failed) {
    Write-Host ''
    Write-Host 'smoke test: FAILED'
    exit 1
}
Write-Host ''
Write-Host 'smoke test: PASSED'
