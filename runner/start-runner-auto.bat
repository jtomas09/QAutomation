@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ══════════════════════════════════════════════════════════════
REM  Cinepolis QA Runner — Auto-start (sin ventana de consola)
REM  Este script es invocado por el Programador de Tareas.
REM  Logs en: runner\logs\runner.log
REM ══════════════════════════════════════════════════════════════

set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token
set POLL_INTERVAL_MS=5000
set WORK_DIR=%~dp0..
set APPIUM_HUB=http://127.0.0.1:4723
set LOG_FILE=%~dp0logs\runner.log

REM Crear carpeta de logs si no existe
if not exist "%~dp0logs" mkdir "%~dp0logs"

cd /d "%~dp0"

REM ── Compilar solo si el JAR no existe ────────────────────────
if not exist "target\cinepolis-runner.jar" (
    echo [%DATE% %TIME%] Compilando runner por primera vez... >> "%LOG_FILE%"
    call mvn package -q -DskipTests >> "%LOG_FILE%" 2>&1
    if !ERRORLEVEL! neq 0 (
        echo [%DATE% %TIME%] ERROR: Fallo la compilacion Maven. >> "%LOG_FILE%"
        exit /b 1
    )
    echo [%DATE% %TIME%] Compilacion exitosa. >> "%LOG_FILE%"
)

REM ── Iniciar runner (logs a archivo, sin bloquear) ─────────────
echo [%DATE% %TIME%] Runner iniciado. Backend: %BACKEND_URL% >> "%LOG_FILE%"

java ^
  -DBACKEND_URL=%BACKEND_URL% ^
  -DRUNNER_TOKEN=%RUNNER_TOKEN% ^
  -DPOLL_INTERVAL_MS=%POLL_INTERVAL_MS% ^
  -DWORK_DIR="%WORK_DIR%" ^
  -DAPPIUM_HUB=%APPIUM_HUB% ^
  -jar target\cinepolis-runner.jar >> "%LOG_FILE%" 2>&1

echo [%DATE% %TIME%] Runner detenido (exit %ERRORLEVEL%). >> "%LOG_FILE%"
