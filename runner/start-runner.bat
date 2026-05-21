@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ============================================================
echo   Cinepolis QA  -  Runner Agent v2.1.0
echo   Conectando con Railway Backend...
echo  ============================================================
echo.

REM ============================================================
REM  CONFIGURACION
REM ============================================================

set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token
set POLL_INTERVAL_MS=5000
set WORK_DIR=%~dp0..
set APPIUM_HUB=http://127.0.0.1:4723

echo  Configuracion:
echo    Backend:    %BACKEND_URL%
echo    WorkDir:    %WORK_DIR%
echo    Appium:     %APPIUM_HUB%
echo    Polling:    cada %POLL_INTERVAL_MS% ms
echo.

REM ============================================================
REM  VALIDACIONES
REM ============================================================

echo  [Check] Verificando requisitos...

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Java no encontrado en PATH. Instala JDK 17+.
    pause & exit /b 1
)
echo  [OK] Java encontrado

where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Maven no encontrado en PATH. Instala Apache Maven 3.8+.
    pause & exit /b 1
)
echo  [OK] Maven encontrado

if not exist "%WORK_DIR%\gradlew.bat" (
    echo  [WARN] gradlew.bat no encontrado en %WORK_DIR%
    echo         WORK_DIR debe apuntar al directorio raiz del proyecto de pruebas.
)

where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [WARN] adb no encontrado en PATH. Las pruebas Android requieren ADB.
) else (
    echo  [OK] ADB encontrado
    echo.
    echo  [ADB] Dispositivos conectados:
    adb devices
)

echo.

REM ============================================================
REM  COMPILAR RUNNER (solo si el JAR no existe)
REM ============================================================

cd /d "%~dp0"

if not exist "target\cinepolis-runner.jar" (
    echo  [Build] Compilando Runner Agent...
    echo.
    call mvn package -DskipTests
    if !ERRORLEVEL! neq 0 (
        echo.
        echo  [ERROR] Fallo al compilar el runner. Revisa los errores de Maven arriba.
        pause & exit /b 1
    )
    echo.
    echo  [OK] Runner compilado correctamente.
    echo.
) else (
    echo  [OK] JAR encontrado, omitiendo compilacion.
)

REM ============================================================
REM  INICIO DEL AGENTE
REM ============================================================

echo.
echo  ------------------------------------------------------------
echo   Runner iniciado. Consultando Railway cada 5 segundos...
echo   Ctrl+C para detener el runner.
echo  ------------------------------------------------------------
echo.

java ^
  -Dfile.encoding=UTF-8 ^
  -Dstdout.encoding=UTF-8 ^
  -DBACKEND_URL=%BACKEND_URL% ^
  -DRUNNER_TOKEN=%RUNNER_TOKEN% ^
  -DPOLL_INTERVAL_MS=%POLL_INTERVAL_MS% ^
  -DWORK_DIR="%WORK_DIR%" ^
  -DAPPIUM_HUB=%APPIUM_HUB% ^
  -jar target\cinepolis-runner.jar

echo.
echo  [INFO] Runner detenido (exit %ERRORLEVEL%).
pause
