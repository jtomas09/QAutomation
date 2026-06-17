@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ============================================================
echo   Cinepolis QA Universal Runner v2.2.0
echo   Auto-detects: OS, ADB, Android devices
echo  ============================================================
echo.

REM ============================================================
REM  CONFIGURACION — solo editar si necesario
REM ============================================================
set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token

REM Runner ID unico por maquina (win-<hostname>)
for /f "delims=" %%H in ('hostname') do set _HOST=%%H
set RUNNER_ID=win-%_HOST%

REM Capacidades: el Runner detecta automaticamente Android/iOS segun el OS.
REM En Windows → solo Android (ADB)
REM En Mac     → Android + iOS (ADB + Xcode)
REM
REM Para sobreescribir la deteccion automatica, descomenta:
REM set RUNNER_PLATFORM=android

REM Directorio raiz del proyecto de pruebas (donde esta gradlew.bat o pom.xml)
set WORK_DIR=%~dp0..

set POLL_INTERVAL_MS=5000
set APPIUM_HUB=http://127.0.0.1:4723

echo  Config:
echo    Runner ID:  %RUNNER_ID%
echo    OS:         Windows (auto-detectado)
echo    Backend:    %BACKEND_URL%
echo    Capacidades: Android AUTO | iOS NO (solo Mac)
echo.

REM ============================================================
REM  DETECTAR ADB
REM ============================================================
echo  [Check] Buscando ADB...

where adb >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo  [OK] ADB en PATH
    goto :ADB_READY
)

if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
        echo  [OK] ADB via ANDROID_HOME
        goto :ADB_READY
    )
)

for %%P in (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    "C:\Android\platform-tools\adb.exe"
    "C:\Program Files\Android\platform-tools\adb.exe"
) do (
    if exist %%P (
        for %%D in (%%~dpP.) do set "PATH=%%~fD;%PATH%"
        echo  [OK] ADB encontrado en %%~dpP
        goto :ADB_READY
    )
)

echo  [WARN] ADB no encontrado. Android no sera descubierto.
echo         Instala Android SDK y agrega platform-tools al PATH.
goto :BUILD_JAR

:ADB_READY
echo.
echo  [ADB] Dispositivos conectados ahora:
adb devices -l
echo.

:BUILD_JAR
REM ============================================================
REM  COMPILAR si no existe el JAR
REM ============================================================
cd /d "%~dp0"

if exist "target\cinepolis-runner.jar" (
    echo  [OK] JAR existente: target\cinepolis-runner.jar
    goto :RUN
)

echo  [Build] Compilando con Maven...
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Maven no en PATH.
    echo.
    echo  Opciones:
    echo    1. Instala Maven 3.8+ y re-ejecuta este script
    echo    2. Compila en otro equipo: mvn package -DskipTests
    echo       y copia target\cinepolis-runner.jar aqui
    pause & exit /b 1
)
call mvn package -DskipTests -q
if !ERRORLEVEL! neq 0 (
    echo  [ERROR] Fallo la compilacion Maven.
    pause & exit /b 1
)
echo  [OK] Compilacion exitosa.

:RUN
echo.
echo  ------------------------------------------------------------
echo   Universal Runner iniciando...
echo   El dispositivo aparecera en Device Farm en ~5 segundos.
echo   Ctrl+C para detener.
echo  ------------------------------------------------------------
echo.

java ^
  -Dfile.encoding=UTF-8 ^
  -Dstdout.encoding=UTF-8 ^
  -DBACKEND_URL="%BACKEND_URL%" ^
  -DRUNNER_TOKEN="%RUNNER_TOKEN%" ^
  -DRUNNER_ID="%RUNNER_ID%" ^
  -DPOLL_INTERVAL_MS="%POLL_INTERVAL_MS%" ^
  -DWORK_DIR="%WORK_DIR%" ^
  -DAPPIUM_HUB="%APPIUM_HUB%" ^
  -jar target\cinepolis-runner.jar

echo.
echo  [INFO] Runner detenido (exit %ERRORLEVEL%).
pause
