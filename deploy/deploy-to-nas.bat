@echo off
setlocal EnableExtensions

rem Study 2.1 one-step deploy: copy source to NAS, then rebuild/restart a service.
rem Usage:
rem   deploy\deploy-to-nas.bat              (default: web)
rem   deploy\deploy-to-nas.bat web
rem   deploy\deploy-to-nas.bat admin-api
rem   deploy\deploy-to-nas.bat user-api
rem   deploy\deploy-to-nas.bat --dry-run    (copy dry-run only, docker step skipped)
rem
rem First time on a new PC: run deploy\setup-nas-ssh.bat ONCE to install the
rem SSH key (after that no password is needed).

rem ======== Fill in your NAS info here (only these two lines) ========
set "SSH_TARGET=brussliu@192.168.0.100"
set "NAS_PATH=/vol5/1000/DATA0/tomcat/study2.1"
rem ===================================================================

set "SCRIPT_DIR=%~dp0"
set "SERVICE=%~1"
set "DRY_RUN="

if /I "%~1"=="--dry-run" (
    set "DRY_RUN=1"
    set "SERVICE=web"
)

if not defined SERVICE set "SERVICE=web"

if not "%SERVICE%"=="web" if not "%SERVICE%"=="admin-api" if not "%SERVICE%"=="user-api" (
    echo [ERROR] Unknown service: "%SERVICE%"
    echo Usage: %~nx0 [web^|admin-api^|user-api^|--dry-run]
    exit /b 2
)

echo ================================================================
echo  Step 1/2 : Copy source to NAS  (Y:\tomcat\study2.1)
echo ================================================================
set "COPY_ARG="
if defined DRY_RUN set "COPY_ARG=--dry-run"
call "%SCRIPT_DIR%copy-to-tomcat.bat" %COPY_ARG%
if errorlevel 1 (
    echo [ERROR] Copy step failed. Deploy aborted.
    exit /b 1
)

if defined DRY_RUN (
    echo [OK] Dry run completed. Docker step skipped.
    exit /b 0
)

echo.
echo ================================================================
echo  Step 2/2 : Rebuild and restart "%SERVICE%" on the NAS
echo  ssh %SSH_TARGET%  -^>  %NAS_PATH%
echo  (passwordless SSH - run deploy\setup-nas-ssh.bat once if asked for a password)
echo ================================================================
where ssh >nul 2>nul
if errorlevel 1 (
    echo [ERROR] ssh command not found. Install Windows OpenSSH Client
    echo          ^(Settings - Apps - Optional Features - Add a feature - OpenSSH Client^).
    exit /b 1
)
rem accept-new: auto-trust the host key on FIRST connection only (still
rem protects against the host key changing later).
ssh -o StrictHostKeyChecking=accept-new "%SSH_TARGET%" "cd '%NAS_PATH%' && docker compose up -d --build %SERVICE%"
if errorlevel 1 (
    echo [ERROR] docker compose failed on the NAS. Deploy aborted.
    exit /b 1
)

echo.
echo [OK] Deploy finished: %SERVICE% rebuilt and restarted.
echo      PC:      http://192.168.0.100:8090
echo      Mobile:  http://192.168.0.100:8091
exit /b 0
