@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ============================================================
echo   Cinepolis QA  -  Runner Agent v2.2.0
echo   Device Farm Enterprise - Auto Discovery
echo  ============================================================
echo.

REM ============================================================
REM  CONFIGURACION — editar solo estas lineas si necesario
REM ============================================================
set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token
set RUNNER_PLATFORM=android
set POLL_INTERVAL_MS=5000
set WORK_DIR=%~dp0..
set APPIUM_HUB=http://127.0.0.1:4723

REM Runner ID unico por maquina (win-<hostname>)
for /f "delims=" %%H in ('hostname') do set _HOST=%%H
set RUNNER_ID=win-%_HOST%

echo  Configuracion:
echo    Runner ID:  %RUNNER_ID%
echo    Platform:   %RUNNER_PLATFORM%
echo    Backend:    %BACKEND_URL%
echo    WorkDir:    %WORK_DIR%
echo    Appium:     %APPIUM_HUB%
echo.

REM ============================================================
REM  DETECTAR ADB  (Android Debug Bridge)
REM ============================================================
echo  [Check] Buscando ADB...

set ADB_FOUND=0

where adb >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo  [OK] ADB encontrado en PATH del sistema
    set ADB_FOUND=1
    goto :ADB_VERIFY
)

REM Buscar en ANDROID_HOME
if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        echo  [OK] ADB encontrado via ANDROID_HOME
        set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
        set ADB_FOUND=1
        goto :ADB_VERIFY
    )
)

REM Buscar en rutas comunes del Android SDK en Windows
for %%P in (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    "C:\Android\platform-tools\adb.exe"
    "C:\Program Files\Android\platform-tools\adb.exe"
) do (
    if exist %%P (
        echo  [OK] ADB encontrado en %%~dpP
        set "PATH=%%~dpP;%PATH%"
        set ADB_FOUND=1
        goto :ADB_VERIFY
    )
)

echo  [WARN] ADB no encontrado. Dispositivos Android no seran descubiertos.
echo         Instala Android SDK y agrega platform-tools al PATH.
goto :BUILD_RUNNER

:ADB_VERIFY
echo.
echo  [ADB] Dispositivos detectados:
adb devices -l
echo.
REM Exportar ADB en PATH para que el runner lo use
set ANDROID_ADB_PATH=%PATH%

:BUILD_RUNNER
REM ============================================================
REM  COMPILAR RUNNER si no existe el JAR
REM ============================================================
cd /d "%~dp0"

if exist "target\cinepolis-runner.jar" (
    echo  [OK] JAR existente: target\cinepolis-runner.jar
    echo       Para recompilar borra target\cinepolis-runner.jar y reinicia.
    goto :START_RUNNER
)

echo  [Build] JAR no encontrado. Compilando con Maven...
echo.

where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Maven no encontrado en PATH.
    echo.
    echo  Opciones para obtener el JAR:
    echo    1. Instala Apache Maven 3.8+ y ejecuta este script de nuevo.
    echo    2. En otro equipo con Maven: cd runner ^&^& mvn package -DskipTests
    echo       Luego copia runner\target\cinepolis-runner.jar a esta carpeta.
    pause & exit /b 1
)

call mvn package -DskipTests -q
if !ERRORLEVEL! neq 0 (
    echo.
    echo  [ERROR] Fallo la compilacion. Revisa los errores de Maven.
    pause & exit /b 1
)
echo  [OK] Compilacion exitosa.

:START_RUNNER
echo.
echo  ------------------------------------------------------------
echo   Runner iniciando. Se conectara a Railway en segundos...
echo   El dispositivo aparecera en el Dashboard tras el primer
echo   heartbeat (aprox. 5-10 segundos).
echo   Ctrl+C para detener.
echo  ------------------------------------------------------------
echo.

java ^
  -Dfile.encoding=UTF-8 ^
  -Dstdout.encoding=UTF-8 ^
  -DBACKEND_URL="%BACKEND_URL%" ^
  -DRUNNER_TOKEN="%RUNNER_TOKEN%" ^
  -DRUNNER_ID="%RUNNER_ID%" ^
  -DRUNNER_PLATFORM="%RUNNER_PLATFORM%" ^
  -DPOLL_INTERVAL_MS="%POLL_INTERVAL_MS%" ^
  -DWORK_DIR="%WORK_DIR%" ^
  -DAPPIUM_HUB="%APPIUM_HUB%" ^
  -jar target\cinepolis-runner.jar

echo.
echo  [INFO] Runner detenido (exit %ERRORLEVEL%).
pause
