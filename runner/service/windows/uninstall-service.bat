@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║      Automation QA Runner — Desinstalador de Servicio       ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

set TASK_NAME=Automation QA Runner
set INSTALL_DIR=%LOCALAPPDATA%\AutomationQA\runner
set STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup

REM ── Detener proceso Java del runner ─────────────────────────────
echo  [1/3] Deteniendo runner (si esta corriendo)...
taskkill /f /im java.exe >nul 2>&1
echo  [OK]

REM ── Eliminar Tarea Programada ────────────────────────────────────
echo  [2/3] Eliminando Tarea Programada...
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    echo  [OK]  Tarea "%TASK_NAME%" eliminada.
) else (
    echo  [INFO] Tarea no encontrada (ya fue eliminada).
)

REM Limpiar entrada de carpeta Startup (si existe)
if exist "%STARTUP_DIR%\AutomationQARunner.vbs" (
    del /f /q "%STARTUP_DIR%\AutomationQARunner.vbs" >nul 2>&1
    echo  [OK]  Entrada de Startup eliminada.
)
if exist "%STARTUP_DIR%\CinepolisQARunner.vbs" (
    del /f /q "%STARTUP_DIR%\CinepolisQARunner.vbs" >nul 2>&1
)

REM ── Eliminar directorio de instalacion ──────────────────────────
echo  [3/3] Limpiando archivos de instalacion...
set /p REMOVE_DIR=  Eliminar archivos instalados en %INSTALL_DIR%? (S/N):
if /i "!REMOVE_DIR!"=="S" (
    if exist "%INSTALL_DIR%" (
        rd /s /q "%INSTALL_DIR%" >nul 2>&1
        echo  [OK]  Directorio eliminado.
    )
) else (
    echo  [INFO] Archivos conservados en %INSTALL_DIR%
)

echo.
echo  ════════════════════════════════════════════════════════════
echo   Desinstalacion completada.
echo   El Runner ya no arrancara automaticamente al iniciar sesion.
echo  ════════════════════════════════════════════════════════════
echo.
pause
