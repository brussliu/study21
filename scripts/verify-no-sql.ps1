# Study 2.1 — verify-no-sql.ps1
# ハードコードされた DB パスワードがリポジトリ内に存在しないことを検証する。
#
# 補足（アサーション削除の経緯）:
#   かつては足場固めとして次の3つも検証していたが、いずれも現在の正規構成と
#   矛盾するため削除した。
#     - リポジトリ内に *.sql を置かない        → database/ 配下の DDL・移行スクリプトが正規
#     - コード/設定に jdbc:・datasource を書かない → backend の datasource 設定が正規
#     - database/ は .gitkeep のみ             → 機能別サブフォルダ運用が正規
#   残るのは「DB パスワードの直書き禁止」のみ。
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failed = $false

function Is-Excluded([string]$Path) {
    return $Path -match '\\node_modules\\' -or $Path -match '\\target\\' -or $Path -match '\\dist\\' -or $Path -match '\\\.git\\'
}

Write-Host "== Verify no hard-coded DB password under $Root =="

# コード/設定ファイルのみを走査（ドキュメント .md・ロックファイル・依存ディレクトリ・ビルド成果物は除外）
$targetFiles = Get-ChildItem -Path $Root -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object {
        (-not (Is-Excluded $_.FullName)) -and
        ($_.Extension -in '.java', '.kt', '.ts', '.js', '.vue', '.yml', '.yaml', '.properties', '.xml', '.json', '.gradle', '.env', '.example') -and
        ($_.Name -ne 'package-lock.json')
    }

# データベースパスワードの直書きがないこと
$passwordTokens = @('DB_PASSWORD', 'DATABASE_PASSWORD', 'spring.datasource.password', 'db.password', 'database.password')
$pwdMatched = @()
foreach ($file in $targetFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    foreach ($token in $passwordTokens) {
        if ($content -match [regex]::Escape($token)) {
            $pwdMatched += "$($file.FullName)  (token: $token)"
        }
    }
}
if ($pwdMatched.Count -gt 0) {
    Write-Host 'FAIL: found database password tokens:'
    $pwdMatched | Sort-Object -Unique | ForEach-Object { Write-Host "  $_" }
    $failed = $true
} else {
    Write-Host 'OK: no database password configuration'
}

if ($failed) {
    Write-Host ''
    Write-Host 'verify-no-sql: FAILED'
    exit 1
}

Write-Host ''
Write-Host 'verify-no-sql: PASSED (no hard-coded DB password found)'
