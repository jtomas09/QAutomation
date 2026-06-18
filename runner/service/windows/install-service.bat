@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ============================================================
REM  Automation QA — Windows Service Installer
REM  Registra el Universal Runner como Tarea Programada del
REM  sistema: arranca automaticamente al iniciar sesion,
REM  se reinicia si falla, sin consola visible.
REM ============================================================

REM ── Rutas base con comillas para soportar espacios en el path ──────────────────
set "SCRIPT_DIR=%~dp0"

REM Normalizar RUNNER_DIR a ruta absoluta sin ".." (evita problemas con for/f)
for %%D in ("%SCRIPT_DIR%..\..") do set "RUNNER_DIR=%%~fD\"

REM Validar LOCALAPPDATA — puede estar vacio en entornos restringidos
if not defined LOCALAPPDATA (
    echo  [ERROR] La variable de entorno LOCALAPPDATA no esta definida.
    echo          Este instalador requiere una sesion de usuario normal de Windows.
    pause & exit /b 1
)

set "SERVICE_NAME=AutomationQARunner"
set "TASK_NAME=Automation QA Runner"
set "INSTALL_DIR=%LOCALAPPDATA%\AutomationQA\runner"
set "LOG_DIR=%INSTALL_DIR%\logs"
set "JAR_NAME=cinepolis-runner.jar"
set "JAR_SRC=%RUNNER_DIR%target\%JAR_NAME%"
set "JAR_DST=%INSTALL_DIR%\%JAR_NAME%"
set "LOG_FILE=%LOG_DIR%\runner.log"
set "WRAPPER_SCRIPT=%INSTALL_DIR%\run-runner.bat"
set "VBS_PATH=%INSTALL_DIR%\launcher.vbs"

REM Leer configuracion de runner.properties si existe
REM usebackq + comillas permiten rutas con espacios
set "BACKEND_URL=https://qautomation-production.up.railway.app"
set "RUNNER_TOKEN=runner-local-token"
set "POLL_INTERVAL_MS=30000"

if exist "%RUNNER_DIR%runner.properties" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%RUNNER_DIR%runner.properties") do (
        if /i "%%a"=="BACKEND_URL"      set "BACKEND_URL=%%b"
        if /i "%%a"=="RUNNER_TOKEN"     set "RUNNER_TOKEN=%%b"
        if /i "%%a"=="POLL_INTERVAL_MS" set "POLL_INTERVAL_MS=%%b"
    )
)

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║        Automation QA — Instalador de Servicio               ║
echo  ║        Universal Runner (Auto-Start Enterprise)             ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

REM ── PASO 1: Verificar Java ──────────────────────────────────────────────────
echo  [1/5] Verificando Java...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [ERROR] Java no encontrado en PATH.
    echo          Instala JDK 17+ desde: https://adoptium.net
    echo          y agrega JAVA_HOME al PATH del sistema.
    echo.
    pause & exit /b 1
)
echo  [OK]    Java disponible.
echo.

REM ── PASO 2: Compilar o verificar JAR ───────────────────────────────────────
echo  [2/5] Preparando cinepolis-runner.jar...
cd /d "%RUNNER_DIR%"

if exist "%JAR_SRC%" (
    echo  [OK]    JAR encontrado: %JAR_SRC%
) else (
    echo  [Build] JAR no encontrado. Compilando con Maven...
    where mvn >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo.
        echo  [ERROR] Maven no encontrado en PATH. Opciones:
        echo          1. Instala Maven: https://maven.apache.org/download.cgi
        echo          2. Copia manualmente cinepolis-runner.jar a:
        echo             %RUNNER_DIR%target\
        echo.
        pause & exit /b 1
    )
    call mvn package -q -DskipTests
    if !ERRORLEVEL! neq 0 (
        echo  [ERROR] Fallo la compilacion Maven.
        pause & exit /b 1
    )
    echo  [OK]    Compilacion exitosa.
)
echo.

REM ── PASO 3: Instalar en directorio estable ─────────────────────────────────
echo  [3/5] Instalando en %INSTALL_DIR%...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%LOG_DIR%"     mkdir "%LOG_DIR%"

copy /Y "%JAR_SRC%" "%JAR_DST%" >nul
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] No se pudo copiar el JAR.
    echo          Origen:  %JAR_SRC%
    echo          Destino: %JAR_DST%
    pause & exit /b 1
)

REM Detectar RUNNER_ID desde hostname
for /f "usebackq tokens=*" %%h in (`hostname`) do set "HOST_NAME=%%h"
set "RUNNER_ID=win-!HOST_NAME!"

REM Generar wrapper script de arranque (java en una sola linea, sin continuaciones ^)
REM NOTA: %%VAR%% dentro del bloque echo -> %VAR% en el archivo generado
(
    echo @echo off
    echo chcp 65001 ^>nul
    echo setlocal
    echo.
    echo set "RUNNER_ID=%RUNNER_ID%"
    echo set "BACKEND_URL=%BACKEND_URL%"
    echo set "RUNNER_TOKEN=%RUNNER_TOKEN%"
    echo set "POLL_INTERVAL_MS=%POLL_INTERVAL_MS%"
    echo set "LOG_FILE=%LOG_FILE%"
    echo.
    echo if exist "%%LOCALAPPDATA%%\Android\Sdk\platform-tools\adb.exe" set "PATH=%%LOCALAPPDATA%%\Android\Sdk\platform-tools;%%PATH%%"
    echo if exist "%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "PATH=%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools;%%PATH%%"
    echo if defined ANDROID_HOME set "PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%"
    echo.
    echo if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
    echo echo [%%DATE%% %%TIME%%] Runner iniciado ^>^>"%%LOG_FILE%%"
    echo.
    echo :loop
    echo java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -DBACKEND_URL=%%BACKEND_URL%% -DRUNNER_TOKEN=%%RUNNER_TOKEN%% -DRUNNER_ID=%%RUNNER_ID%% -DPOLL_INTERVAL_MS=%%POLL_INTERVAL_MS%% -DWORK_DIR="%RUNNER_DIR%" -DAPPIUM_HUB=http://127.0.0.1:4723 -jar "%JAR_DST%" ^>^>"%%LOG_FILE%%" 2^>^&1
    echo echo [%%DATE%% %%TIME%%] Runner detenido (exit %%ERRORLEVEL%%) -- reiniciando en 10s ^>^>"%%LOG_FILE%%"
    echo timeout /t 10 /nobreak ^>nul
    echo goto loop
) > "%WRAPPER_SCRIPT%"

if %ERRORLEVEL% neq 0 (
    echo  [ERROR] No se pudo crear el wrapper script en:
    echo          %WRAPPER_SCRIPT%
    pause & exit /b 1
)
echo  [OK]    Wrapper instalado: %WRAPPER_SCRIPT%
echo  [OK]    JAR instalado:     %JAR_DST%
echo.

REM ── PASO 4: Generar VBS launcher y registrar Tarea Programada ─────────────
echo  [4/5] Registrando Tarea Programada (inicio de sesion)...

REM Generar VBS launcher usando Chr(34) para quotes sin problemas de escape
REM Esto ejecuta run-runner.bat sin mostrar ventana de consola
(
    echo Dim sh, q
    echo Set sh = CreateObject("WScript.Shell"^)
    echo q = Chr(34^)
    echo sh.Run "cmd /c " ^& q ^& "%WRAPPER_SCRIPT%" ^& q, 0, False
    echo Set sh = Nothing
) > "%VBS_PATH%"

if not exist "%VBS_PATH%" (
    echo  [ERROR] No se pudo crear el archivo VBS en:
    echo          %VBS_PATH%
    pause & exit /b 1
)

REM Obtener ruta 8.3 del VBS (sin espacios) para schtasks
for %%F in ("%VBS_PATH%") do set "VBS_SHORT=%%~sF"

REM Eliminar tarea anterior si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    echo  [INFO]  Tarea anterior eliminada.
)

REM Registrar tarea con ruta 8.3 para evitar problemas de espacios en schtasks
schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /rl LIMITED /delay 0000:30 >nul 2>&1
if !ERRORLEVEL! neq 0 (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /delay 0000:30 >nul 2>&1
)
if !ERRORLEVEL! neq 0 (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f >nul 2>&1
)

schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo  [OK]    Tarea "%TASK_NAME%" registrada.
) else (
    echo  [WARN]  No se pudo registrar la tarea programada.
    echo          Instalando en carpeta Startup como alternativa...
    set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    copy /Y "%VBS_PATH%" "!STARTUP_DIR!\AutomationQARunner.vbs" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo  [OK]    Instalado en carpeta Startup.
    ) else (
        echo  [ERROR] No se pudo instalar en carpeta Startup.
        echo          Ruta: !STARTUP_DIR!
    )
)
echo.

REM ── PASO 5: Herramientas de control ────────────────────────────────────────
echo  [5/5] Creando herramientas de control...

(
    echo Dim sh
    echo Set sh = CreateObject("WScript.Shell"^)
    echo sh.Run "cmd /c type ""%LOG_FILE%"" | more", 1, False
    echo Set sh = Nothing
) > "%INSTALL_DIR%\ver-logs.vbs"

echo  [OK]    Logs: %INSTALL_DIR%\ver-logs.vbs
echo.

echo  ════════════════════════════════════════════════════════════════
echo   Instalacion completada.
echo.
echo   El Runner arrancara AUTOMATICAMENTE al iniciar sesion.
echo   No necesitas ejecutar ningun script manualmente.
echo.
echo   Runner ID:  %RUNNER_ID%
echo   Backend:    %BACKEND_URL%
echo   JAR:        %JAR_DST%
echo   Logs:       %LOG_FILE%
echo.
echo   Para desinstalar: uninstall-service.bat
echo  ════════════════════════════════════════════════════════════════
echo.

if not exist "%VBS_PATH%" (
    echo  [ERROR] El archivo launcher no existe: %VBS_PATH%
    echo          La instalacion puede estar incompleta.
    pause & exit /b 1
)

set /p LAUNCH_NOW=  Iniciar el Runner ahora? (S/N):
if /i "!LAUNCH_NOW!"=="S" (
    echo.
    echo  Iniciando Runner en segundo plano...
    start "" wscript.exe "%VBS_PATH%"
    echo  [OK]  Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
)

echo.
pause
endlocal
