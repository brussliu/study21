@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Study 2.1 incremental source copy script.
rem Usage:
rem   deploy\copy-to-tomcat.bat
rem   deploy\copy-to-tomcat.bat --dry-run

rem This script lives under deploy; copy from the repository root.
for %%I in ("%~dp0..") do set "SOURCE=%%~fI"
set "TARGET=Y:\tomcat\study2.1"
set "ROBOCOPY_MODE="

if /I "%~1"=="--dry-run" (
    set "ROBOCOPY_MODE=/L"
    echo [DRY RUN] No files will be copied.
) else if not "%~1"=="" (
    echo [ERROR] Unknown option: %~1
    echo Usage: %~nx0 [--dry-run]
    exit /b 2
)

if not exist "%SOURCE%\README.md" (
    echo [ERROR] Study 2.1 source folder was not detected: "%SOURCE%"
    exit /b 2
)

if not exist "Y:\" (
    echo [ERROR] Drive Y: is not available.
    exit /b 3
)

if not exist "%TARGET%\" (
    if defined ROBOCOPY_MODE (
        echo [INFO] Target folder does not exist yet: "%TARGET%"
    ) else (
        mkdir "%TARGET%"
        if errorlevel 1 (
            echo [ERROR] Could not create target folder: "%TARGET%"
            exit /b 4
        )
    )
)

echo [INFO] Source: "%SOURCE%"
echo [INFO] Target: "%TARGET%"
echo [INFO] Copying new and changed files. Existing target-only files will not be deleted.

rem The previous UI import published raw HTML files. Move that one obsolete
rem directory out of public before copying the Vue-based implementation.
set "OBSOLETE_UI_DEMO=%TARGET%\frontend\pc-web\public\ui-demo"
if exist "%OBSOLETE_UI_DEMO%\" (
    if defined ROBOCOPY_MODE (
        echo [DRY RUN] Would move obsolete static UI folder: "%OBSOLETE_UI_DEMO%"
    ) else (
        if not exist "%TARGET%\tmp\" mkdir "%TARGET%\tmp"
        set "OBSOLETE_UI_BACKUP=%TARGET%\tmp\obsolete-ui-demo-static-%RANDOM%"
        move "%OBSOLETE_UI_DEMO%" "!OBSOLETE_UI_BACKUP!" >nul
        if errorlevel 1 (
            echo [ERROR] Could not move obsolete static UI folder: "%OBSOLETE_UI_DEMO%"
            exit /b 5
        )
        echo [INFO] Obsolete static UI folder moved to: "!OBSOLETE_UI_BACKUP!"
    )
)

robocopy "%SOURCE%" "%TARGET%" *.* ^
    /E /Z /FFT /COPY:DAT /DCOPY:DAT /R:3 /W:2 /XJ ^
    /XD ".git" "node_modules" "dist" "target" "coverage" "tmp" ".idea" ".vscode" ".run" ^
    /XF ".env" ".env.local" ".env.*.local" "*.log" "*.pid" "*.tsbuildinfo" ".eslintcache" "Thumbs.db" "Desktop.ini" ^
    %ROBOCOPY_MODE%

set "ROBOCOPY_EXIT=%ERRORLEVEL%"

rem Robocopy exit codes 0-7 are successful results; 8 or greater is a failure.
if %ROBOCOPY_EXIT% GEQ 8 (
    echo [ERROR] Copy failed. Robocopy exit code: %ROBOCOPY_EXIT%
    exit /b %ROBOCOPY_EXIT%
)

if defined ROBOCOPY_MODE (
    echo [OK] Dry run completed. No files were changed.
) else (
    echo [OK] Study 2.1 was copied successfully.
)

exit /b 0
