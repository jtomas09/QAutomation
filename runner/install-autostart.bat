@echo off
chcp 65001 > nul

echo.
echo  ╔══════════════════════════════════════════════════════════╗
echo  ║   Cinepolis QA — Instalar Runner como tarea de inicio   ║
echo  ╚══════════════════════════════════════════════════════════╝
echo.

set RUNNER_DIR=%~dp0
set STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set VBS_SRC=%RUNNER_DIR%runner-launcher.vbs
set VBS_DST=%STARTUP_DIR%\CinepolisQARunner.vbs

REM ── 1. Verificar Java ────────────────────────────────────────
echo  [1/3] Verificando Java...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ERROR: Java no encontrado. Instala JDK 17+ y agregalo al PATH.
    pause & exit /b 1
)
echo  OK Java encontrado

REM ── 2. Compilar JAR si no existe ────────────────────────────
echo  [2/3] Compilando runner...
cd /d "%RUNNER_DIR%"
if not exist "target\cinepolis-runner.jar" (
    echo  Compilando con Maven (solo la primera vez)...
    call mvn package -q -DskipTests
    if !ERRORLEVEL! neq 0 (
        echo  ERROR al compilar. Ejecuta manualmente: mvn package -DskipTests
        pause & exit /b 1
    )
)
echo  OK JAR listo en target\cinepolis-runner.jar

REM ── 3. Copiar launcher a carpeta Inicio ─────────────────────
echo  [3/3] Registrando en carpeta de Inicio de Windows...

copy /Y "%VBS_SRC%" "%VBS_DST%" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ERROR al copiar a la carpeta Inicio.
    echo  Ruta destino: %VBS_DST%
    pause & exit /b 1
)
echo  OK Copiado a: %VBS_DST%

echo.
echo  ══════════════════════════════════════════════════════════
echo   Instalacion completada SIN necesitar permisos de admin.
echo   El runner arrancara automaticamente en cada inicio de sesion.
echo   Logs en: %RUNNER_DIR%logs\runner.log
echo  ══════════════════════════════════════════════════════════
echo.
echo  Iniciar el runner AHORA sin reiniciar? (S/N)
set /p START_NOW=  Respuesta:

if /i "%START_NOW%"=="S" (
    echo.
    echo  Iniciando runner en background...
    start "" wscript.exe "%VBS_SRC%"
    echo  OK Runner corriendo. Revisa la UI en ~10 segundos.
)

echo.
pause
