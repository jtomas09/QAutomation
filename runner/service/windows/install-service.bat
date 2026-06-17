@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ============================================================
REM  Automation QA — Windows Service Installer
REM  Registra el Universal Runner como Tarea Programada del
REM  sistema: arranca automaticamente al iniciar sesion,
REM  se reinicia si falla, sin consola visible.
REM ============================================================

set SCRIPT_DIR=%~dp0
set RUNNER_DIR=%SCRIPT_DIR%..\..\
set SERVICE_NAME=AutomationQARunner
set TASK_NAME=Automation QA Runner
set TASK_DESCRIPTION=Universal Runner para la plataforma Automation QA. Descubre dispositivos Android e iOS automaticamente.
set INSTALL_DIR=%LOCALAPPDATA%\AutomationQA\runner
set LOG_DIR=%INSTALL_DIR%\logs
set JAR_NAME=cinepolis-runner.jar
set JAR_SRC=%RUNNER_DIR%target\%JAR_NAME%
set JAR_DST=%INSTALL_DIR%\%JAR_NAME%
set LOG_FILE=%LOG_DIR%\runner.log
set WRAPPER_SCRIPT=%INSTALL_DIR%\run-runner.bat

REM Leer configuracion de runner.properties si existe
set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token
set POLL_INTERVAL_MS=30000

if exist "%RUNNER_DIR%runner.properties" (
    for /f "tokens=1,* delims==" %%a in (%RUNNER_DIR%runner.properties) do (
        if /i "%%a"=="BACKEND_URL"     set BACKEND_URL=%%b
        if /i "%%a"=="RUNNER_TOKEN"    set RUNNER_TOKEN=%%b
        if /i "%%a"=="POLL_INTERVAL_MS" set POLL_INTERVAL_MS=%%b
    )
)

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║        Automation QA — Instalador de Servicio               ║
echo  ║        Universal Runner (Auto-Start Enterprise)             ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

REM ── PASO 1: Verificar Java ───────────────────────────────────────
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
for /f "tokens=*" %%v in ('java -version 2^>^&1') do (
    echo  [OK]    %%v
    goto :java_ok
)
:java_ok
echo.

REM ── PASO 2: Compilar JAR ────────────────────────────────────────
echo  [2/5] Preparando cinepolis-runner.jar...
cd /d "%RUNNER_DIR%"

if exist "%JAR_SRC%" (
    echo  [OK]    JAR existente: %JAR_SRC%
) else (
    echo  [Build] JAR no encontrado. Compilando con Maven...
    where mvn >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo.
        echo  [ERROR] Maven no encontrado. Opciones:
        echo          1. Instala Maven: https://maven.apache.org/download.cgi
        echo          2. O copia manualmente cinepolis-runner.jar a:
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

REM ── PASO 3: Instalar en directorio estable ──────────────────────
echo  [3/5] Instalando en %INSTALL_DIR%...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%LOG_DIR%"     mkdir "%LOG_DIR%"

copy /Y "%JAR_SRC%" "%JAR_DST%" >nul
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] No se pudo copiar el JAR a %JAR_DST%
    pause & exit /b 1
)

REM Detectar RUNNER_ID desde hostname
for /f "tokens=*" %%h in ('hostname') do set HOST_NAME=%%h
set RUNNER_ID=win-%HOST_NAME%
REM Convertir a minusculas (solo ASCII)
for %%a in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do (
    set RUNNER_ID=!RUNNER_ID:%%a=%%a!
)
REM WinSW/Task Scheduler approach: usar script wrapper para heredar PATH usuario
(
    echo @echo off
    echo chcp 65001 ^> nul
    echo setlocal
    echo.
    echo set RUNNER_ID=%RUNNER_ID%
    echo set BACKEND_URL=%BACKEND_URL%
    echo set RUNNER_TOKEN=%RUNNER_TOKEN%
    echo set POLL_INTERVAL_MS=%POLL_INTERVAL_MS%
    echo set LOG_FILE=%LOG_FILE%
    echo.
    echo REM Agregar rutas comunes de ADB al PATH
    echo if exist "%%LOCALAPPDATA%%\Android\Sdk\platform-tools" ^(
    echo     set PATH=%%LOCALAPPDATA%%\Android\Sdk\platform-tools;%%PATH%%
    echo ^)
    echo if exist "%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools" ^(
    echo     set PATH=%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools;%%PATH%%
    echo ^)
    echo if defined ANDROID_HOME ^(
    echo     set PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%
    echo ^)
    echo.
    echo if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
    echo echo [%%DATE%% %%TIME%%] Runner iniciado >> "%%LOG_FILE%%"
    echo.
    echo :loop
    echo java ^
    echo   -Dfile.encoding=UTF-8 ^
    echo   -DBACKEND_URL=%%BACKEND_URL%% ^
    echo   -DRUNNER_TOKEN=%%RUNNER_TOKEN%% ^
    echo   -DRUNNER_ID=%%RUNNER_ID%% ^
    echo   -DPOLL_INTERVAL_MS=%%POLL_INTERVAL_MS%% ^
    echo   -DWORK_DIR="%RUNNER_DIR%" ^
    echo   -DAPPIUM_HUB=http://127.0.0.1:4723 ^
    echo   -jar "%JAR_DST%" ^>^> "%%LOG_FILE%%" 2^>^&1
    echo echo [%%DATE%% %%TIME%%] Runner detenido (exit %%ERRORLEVEL%%) -- reiniciando en 10s >> "%%LOG_FILE%%"
    echo timeout /t 10 /nobreak ^> nul
    echo goto loop
) > "%WRAPPER_SCRIPT%"

echo  [OK]    Wrapper instalado: %WRAPPER_SCRIPT%
echo  [OK]    JAR instalado:     %JAR_DST%
echo.

REM ── PASO 4: Registrar en Tarea Programada ───────────────────────
echo  [4/5] Registrando Tarea Programada (inicio de sesion)...

REM Eliminar tarea previa si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    echo  [INFO]  Tarea anterior eliminada.
)

REM Crear VBS oculto para ejecutar sin ventana de consola
set VBS_PATH=%INSTALL_DIR%\launcher.vbs
(
    echo Dim shell
    echo Set shell = CreateObject^("WScript.Shell"^)
    echo shell.Run "cmd /c """ ^& "%WRAPPER_SCRIPT%" ^& """", 0, False
    echo Set shell = Nothing
) > "%VBS_PATH%"

REM Registrar tarea: al iniciar sesion del usuario actual, reinicio infinito
schtasks /create ^
    /tn "%TASK_NAME%" ^
    /tr "wscript.exe ""%VBS_PATH%""" ^
    /sc ONLOGON ^
    /ru "%USERNAME%" ^
    /f ^
    /rl LIMITED ^
    /delay 0000:15 >nul 2>&1

if %ERRORLEVEL% neq 0 (
    echo  [WARN]  Tarea programada no pudo crearse con ONLOGON.
    echo          Intentando con ONSTART...
    schtasks /create ^
        /tn "%TASK_NAME%" ^
        /tr "wscript.exe ""%VBS_PATH%""" ^
        /sc ONLOGON ^
        /f >nul 2>&1
)

schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo  [OK]    Tarea "%TASK_NAME%" registrada correctamente.
) else (
    echo  [WARN]  No se pudo registrar la tarea. Usando carpeta Startup...
    set STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
    copy /Y "%VBS_PATH%" "%STARTUP_DIR%\AutomationQARunner.vbs" >nul
    echo  [OK]    Instalado en carpeta Startup: %STARTUP_DIR%
)
echo.

REM ── PASO 5: Crear accesos directos de control ───────────────────
echo  [5/5] Creando accesos directos de control...

set DESKTOP=%USERPROFILE%\Desktop
set CONTROL_VBS=%INSTALL_DIR%\control.vbs

REM Control script (start/stop/status)
(
    echo ' Automation QA Runner - Control
    echo Dim shell, arg
    echo Set shell = CreateObject^("WScript.Shell"^)
    echo arg = InputBox^("Comando ^(status / stop^):", "Automation QA Runner"^)
    echo If LCase^(arg^) = "status" Then
    echo     shell.Run "cmd /k tasklist | findstr java", 1, False
    echo ElseIf LCase^(arg^) = "stop" Then
    echo     shell.Run "cmd /c taskkill /f /im java.exe", 0, True
    echo     MsgBox "Runner detenido."
    echo End If
) > "%CONTROL_VBS%"

REM Acceso directo al log
(
    echo Dim shell
    echo Set shell = CreateObject^("WScript.Shell"^)
    echo shell.Run "cmd /c type ""%LOG_FILE%"" | more", 1, False
) > "%INSTALL_DIR%\ver-logs.vbs"

echo  [OK]    Acceso directo de logs: %INSTALL_DIR%\ver-logs.vbs
echo.

echo  ════════════════════════════════════════════════════════════
echo   Instalacion completada con exito.
echo.
echo   El Runner arrancara AUTOMATICAMENTE al iniciar sesion.
echo   No necesitas ejecutar ningun script manualmente.
echo.
echo   Runner ID:  %RUNNER_ID%
echo   Backend:    %BACKEND_URL%
echo   JAR:        %JAR_DST%
echo   Logs:       %LOG_FILE%
echo.
echo   Para ver logs: %INSTALL_DIR%\ver-logs.vbs
echo   Para desinstalar: uninstall-service.bat
echo  ════════════════════════════════════════════════════════════
echo.
set /p LAUNCH_NOW=  Iniciar runner AHORA? (S/N):
if /i "!LAUNCH_NOW!"=="S" (
    echo.
    echo  Iniciando runner en segundo plano...
    start "" wscript.exe "%VBS_PATH%"
    echo  [OK] Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
)
echo.
pause
