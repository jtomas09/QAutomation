@echo off
echo ================================================
echo  Cinepolis QA Runner Agent v2.0
echo ================================================

REM ── Backend ────────────────────────────────────────
set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token
set POLL_INTERVAL_MS=5000

REM ── Directorio raiz del proyecto Maven de pruebas ──
REM  Cambia esta ruta al directorio donde esta tu pom.xml de Appium
set WORK_DIR=..

REM ── Appium ─────────────────────────────────────────
set APPIUM_HUB=http://127.0.0.1:4723

REM ── Allure (opcional) ──────────────────────────────
REM set ALLURE_BASE_URL=https://mi-servidor/allure

echo Backend:   %BACKEND_URL%
echo WorkDir:   %WORK_DIR%
echo Appium:    %APPIUM_HUB%
echo.
echo Requisitos:
echo   - Appium corriendo en puerto 4723
echo   - adb devices debe mostrar un dispositivo
echo   - mvn en PATH
echo.

cd /d "%~dp0"
call mvn package -q -DskipTests
if %ERRORLEVEL% neq 0 (
    echo ERROR: Fallo al compilar el runner
    pause
    exit /b 1
)

java -jar target\cinepolis-runner.jar
pause
