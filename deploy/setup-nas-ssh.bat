@echo off
setlocal EnableExtensions

rem One-time SSH key setup for deploying to the fnOS NAS.
rem Usage: deploy\setup-nas-ssh.bat
rem After running this ONCE, deploy\deploy-to-nas.bat logs in without any password.
rem
rem You will type the fnOS admin password at most twice:
rem   1) the SSH login password for brussliu@192.168.0.100
rem   2) the sudo password on the NAS (same password)
rem Safe to re-run any time (duplicate key entries are harmless).

set "NAS=192.168.0.100"
set "SSH_USER=brussliu"
set "SSH_TARGET=%SSH_USER%@%NAS%"
set "PUBKEY_FILE=%USERPROFILE%\.ssh\id_ed25519.pub"

if not exist "%PUBKEY_FILE%" (
    echo [ERROR] Public key not found: %PUBKEY_FILE%
    echo          Run ssh-keygen first, or ask your assistant.
    exit /b 1
)

set "PUBKEY="
set /p PUBKEY=<"%PUBKEY_FILE%"
if not defined PUBKEY (
    echo [ERROR] Could not read the public key file.
    exit /b 1
)

echo ================================================================
echo  One-time SSH key setup for %SSH_TARGET%
echo  Enter the fnOS admin password when prompted
echo  (sudo may ask once more for the same password)
echo ================================================================
ssh -o StrictHostKeyChecking=accept-new -t %SSH_TARGET% "sudo mkdir -p /home/%SSH_USER% && sudo chown %SSH_USER% /home/%SSH_USER% && mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo %PUBKEY% >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && sudo usermod -aG docker %SSH_USER% && echo [NAS] home created + key installed + docker access granted"
if errorlevel 1 (
    echo [ERROR] Setup failed on the NAS. Check the user name / password.
    exit /b 1
)

echo.
echo ================================================================
echo  Testing passwordless login ...
echo ================================================================
ssh -o BatchMode=yes %SSH_TARGET% "docker ps >/dev/null 2>&1 && echo [OK] passwordless login + docker access confirmed"
if errorlevel 1 (
    echo [ERROR] Passwordless login test failed.
    echo          Ask your assistant for the fallback setup.
    exit /b 1
)

echo.
echo [OK] Setup complete. deploy\deploy-to-nas.bat will not ask for a password.
exit /b 0
