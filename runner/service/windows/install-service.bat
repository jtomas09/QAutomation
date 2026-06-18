@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ==============================================================================
REM  Automation QA - Windows Service Installer v3.1
REM  Copia el JAR a LOCALAPPDATA, genera wrapper de arranque y registra
REM  Tarea Programada. Sin privilegios de Admin. Compatible con rutas con espacios.
REM
REM  Todas las rutas se construyen con %~dp0, %LOCALAPPDATA% o %APPDATA%.
REM  Ningun path esta hardcodeado.
REM ==============================================================================

REM -- 1. Rutas de origen (relativas a este script) ------------------------------
set "SCRIPT_DIR=%~dp0"

REM Normalizar RUNNER_DIR: resolver "..\..\" a ruta absoluta sin ".."
REM %%~fD convierte "C:\runner\service\windows\..\.." -> "C:\runner" (path absoluto limpio)
for %%D in ("%SCRIPT_DIR%..\..") do set "RUNNER_DIR=%%~fD\"

REM -- 2. Validar LOCALAPPDATA antes de usarlo -----------------------------------
REM LOCALAPPDATA puede estar vacio o indefinido en entornos restringidos (RDP, GPO, etc.)
if not defined LOCALAPPDATA (
    echo.
    echo  [ERROR] La variable LOCALAPPDATA no esta definida en esta sesion.
    echo          Este instalador requiere una sesion de usuario interactiva normal.
    echo.
    echo          Si usas RDP o una cuenta de servicio, inicia sesion directamente
    echo          con la cuenta de usuario que ejecutara el Runner.
    echo.
    pause & exit /b 1
)

REM Validar que LOCALAPPDATA no sea solo un slash o ruta invalida
if "%LOCALAPPDATA%"=="\" (
    echo  [ERROR] LOCALAPPDATA tiene un valor invalido: %LOCALAPPDATA%
    pause & exit /b 1
)

REM -- 3. Rutas de destino (construidas con LOCALAPPDATA dinamicamente) ----------
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
set "INSTALL_LOG=%LOG_DIR%\install.log"

REM -- 4. Crear directorio de logs ANTES de escribir en el log ------------------
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%" >nul 2>&1
if not exist "%LOG_DIR%"     mkdir "%LOG_DIR%"     >nul 2>&1

REM Si no se pudo crear el log dir, continuar sin log en archivo
set "LOG_AVAILABLE=0"
if exist "%LOG_DIR%" set "LOG_AVAILABLE=1"

REM -- 5. Abrir install.log ------------------------------------------------------
if "!LOG_AVAILABLE!"=="1" (
    >> "%INSTALL_LOG%" (
        echo.
        echo ================================================================
        echo  Automation QA Runner - Instalacion Servicio v3.1
        echo  Fecha: %DATE%   Hora: %TIME%
        echo ================================================================
    )
    >> "%INSTALL_LOG%" echo.
    >> "%INSTALL_LOG%" echo [%TIME%] --- Variables de entorno ---
    >> "%INSTALL_LOG%" echo [%TIME%] LOCALAPPDATA=%LOCALAPPDATA%
    >> "%INSTALL_LOG%" echo [%TIME%] APPDATA=%APPDATA%
    >> "%INSTALL_LOG%" echo [%TIME%] USERNAME=%USERNAME%
    >> "%INSTALL_LOG%" echo [%TIME%] COMPUTERNAME=%COMPUTERNAME%
    >> "%INSTALL_LOG%" echo.
    >> "%INSTALL_LOG%" echo [%TIME%] --- Rutas calculadas ---
    >> "%INSTALL_LOG%" echo [%TIME%] SCRIPT_DIR=%SCRIPT_DIR%
    >> "%INSTALL_LOG%" echo [%TIME%] RUNNER_DIR=%RUNNER_DIR%
    >> "%INSTALL_LOG%" echo [%TIME%] INSTALL_DIR=%INSTALL_DIR%
    >> "%INSTALL_LOG%" echo [%TIME%] JAR_SRC=%JAR_SRC%
    >> "%INSTALL_LOG%" echo [%TIME%] JAR_DST=%JAR_DST%
    >> "%INSTALL_LOG%" echo [%TIME%] WRAPPER_SCRIPT=%WRAPPER_SCRIPT%
    >> "%INSTALL_LOG%" echo [%TIME%] VBS_PATH=%VBS_PATH%
    >> "%INSTALL_LOG%" echo [%TIME%] LOG_FILE=%LOG_FILE%
    >> "%INSTALL_LOG%" echo [%TIME%] INSTALL_LOG=%INSTALL_LOG%
    >> "%INSTALL_LOG%" echo.
)

REM -- 6. Leer runner.properties si existe (con usebackq + comillas) --------------
REM usebackq + "ruta entre comillas" permite rutas con espacios en for /f
set "BACKEND_URL=https://qautomation-production.up.railway.app"
set "RUNNER_TOKEN=runner-local-token"
set "POLL_INTERVAL_MS=30000"

if exist "%RUNNER_DIR%runner.properties" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%RUNNER_DIR%runner.properties") do (
        if /i "%%a"=="BACKEND_URL"      set "BACKEND_URL=%%b"
        if /i "%%a"=="RUNNER_TOKEN"     set "RUNNER_TOKEN=%%b"
        if /i "%%a"=="POLL_INTERVAL_MS" set "POLL_INTERVAL_MS=%%b"
    )
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] INFO: runner.properties leido desde %RUNNER_DIR%runner.properties
)

echo.
echo  +==============================================================+
echo  |   Automation QA - Instalador de Servicio v3.1              |
echo  |   Auto-Start Enterprise  -  Windows 10 / 11               |
echo  +==============================================================+
echo.

REM -- DIAGNOSTICO: Rutas calculadas --------------------------------------------
echo  +- DIAGNOSTICO ----------------------------------------------------------+
echo  ^|  LOCALAPPDATA   = %LOCALAPPDATA%
echo  ^|  RUNNER_DIR     = %RUNNER_DIR%
echo  ^|  INSTALL_DIR    = %INSTALL_DIR%
echo  ^|  WRAPPER_SCRIPT = %WRAPPER_SCRIPT%
echo  ^|  VBS_PATH       = %VBS_PATH%
echo  ^|  install.log    = %INSTALL_LOG%
echo  +------------------------------------------------------------------------+
echo.

REM ==============================================================================
REM  PASO 1: Verificar Java
REM ==============================================================================
echo  [1/5] Verificando Java...
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] --- PASO 1: Java ---

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [ERROR] Java no encontrado en PATH.
    echo          Instala JDK 17+ desde: https://adoptium.net
    echo          Agrega JAVA_HOME al PATH del sistema y reinicia sesion.
    echo.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: java no encontrado en PATH
    pause & exit /b 1
)
echo  [OK]    Java disponible.
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: Java disponible
echo.

REM ==============================================================================
REM  PASO 2: Verificar o compilar JAR
REM ==============================================================================
echo  [2/5] Preparando cinepolis-runner.jar...
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] --- PASO 2: JAR ---
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] JAR_SRC=%JAR_SRC%

cd /d "%RUNNER_DIR%"

if exist "%JAR_SRC%" (
    echo  [OK]    JAR encontrado.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: JAR encontrado en %JAR_SRC%
) else (
    echo  [Build] JAR no encontrado. Compilando con Maven...
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] INFO: JAR no encontrado - intentando compilar

    where mvn >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo.
        echo  [ERROR] Maven no encontrado en PATH.
        echo          Opciones:
        echo            1. Instala Maven: https://maven.apache.org/download.cgi
        echo            2. Copia manualmente cinepolis-runner.jar a:
        echo               %RUNNER_DIR%target\
        echo.
        if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: Maven no encontrado
        pause & exit /b 1
    )
    call mvn package -q -DskipTests
    if !ERRORLEVEL! neq 0 (
        echo  [ERROR] Fallo la compilacion Maven.
        if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: Fallo mvn package
        pause & exit /b 1
    )
    echo  [OK]    Compilacion exitosa.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: mvn package exitoso
)
echo.

REM ==============================================================================
REM  PASO 3: Instalar JAR en directorio estable y generar scripts
REM ==============================================================================
echo  [3/5] Instalando en LOCALAPPDATA...
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] --- PASO 3: Instalacion en %INSTALL_DIR% ---

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%" >nul 2>&1
if not exist "%LOG_DIR%"     mkdir "%LOG_DIR%"     >nul 2>&1

copy /Y "%JAR_SRC%" "%JAR_DST%" >nul
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [ERROR] No se pudo copiar el JAR.
    echo          Origen:  %JAR_SRC%
    echo          Destino: %JAR_DST%
    echo.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: copy /Y fallo. Origen=%JAR_SRC% Destino=%JAR_DST%
    pause & exit /b 1
)
echo  [OK]    JAR instalado: %JAR_DST%
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: JAR copiado a %JAR_DST%

REM Detectar RUNNER_ID desde hostname
for /f "usebackq tokens=*" %%h in (`hostname`) do set "HOST_NAME=%%h"
set "RUNNER_ID=win-!HOST_NAME!"
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] RUNNER_ID=!RUNNER_ID!

REM -- Generar run-runner.bat ----------------------------------------------------
REM REGLA: java en una SOLA linea - NO usar ^ de continuacion dentro del bloque echo.
REM        El ^ al final de echo java ^ une los echo siguientes en un solo comando,
REM        corrompiendo el archivo generado y causando errores "okens", "[OK]", etc.
REM
REM %%VAR%% -> %VAR% en el archivo generado (se expande en tiempo de ejecucion del wrapper)
REM %VAR%   -> valor literal bakeado al generar (RUNNER_ID, BACKEND_URL, rutas, etc.)
(
    echo @echo off
    echo chcp 65001 ^>nul
    echo setlocal EnableDelayedExpansion
    echo.
    echo REM Generado por install-service.bat - NO editar manualmente
    echo.
    echo set "RUNNER_ID=%RUNNER_ID%"
    echo set "BACKEND_URL=%BACKEND_URL%"
    echo set "RUNNER_TOKEN=%RUNNER_TOKEN%"
    echo set "POLL_INTERVAL_MS=%POLL_INTERVAL_MS%"
    echo set "LOG_FILE=%LOG_FILE%"
    echo set "INSTALL_DIR=%INSTALL_DIR%"
    echo.
    echo REM Agregar ADB al PATH si existe Android SDK
    echo if exist "%%LOCALAPPDATA%%\Android\Sdk\platform-tools\adb.exe" set "PATH=%%LOCALAPPDATA%%\Android\Sdk\platform-tools;%%PATH%%"
    echo if exist "%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "PATH=%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools;%%PATH%%"
    echo if defined ANDROID_HOME set "PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%"
    echo.
    echo if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
    echo echo [%%DATE%% %%TIME%%] Runner iniciado ^>^>"%%LOG_FILE%%"
    echo.
    echo :loop
    echo java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -DBACKEND_URL=%%BACKEND_URL%% -DRUNNER_TOKEN=%%RUNNER_TOKEN%% -DRUNNER_ID=%%RUNNER_ID%% -DPOLL_INTERVAL_MS=%%POLL_INTERVAL_MS%% "-DWORK_DIR=%RUNNER_DIR%" -DAPPIUM_HUB=http://127.0.0.1:4723 -jar "%JAR_DST%" ^>^>"%%LOG_FILE%%" 2^>^&1
    echo echo [%%DATE%% %%TIME%%] Runner detenido ^(exit %%ERRORLEVEL%%^) - reiniciando en 10s ^>^>"%%LOG_FILE%%"
    echo timeout /t 10 /nobreak ^>nul
    echo goto loop
) > "%WRAPPER_SCRIPT%"

if not exist "%WRAPPER_SCRIPT%" (
    echo.
    echo  [ERROR] No se pudo crear el wrapper en: %WRAPPER_SCRIPT%
    echo.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: WRAPPER_SCRIPT no fue creado: %WRAPPER_SCRIPT%
    pause & exit /b 1
)
echo  [OK]    Wrapper generado: %WRAPPER_SCRIPT%
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: WRAPPER_SCRIPT creado: %WRAPPER_SCRIPT%
echo.

REM ==============================================================================
REM  PASO 4: Generar VBS launcher y registrar Tarea Programada
REM ==============================================================================
echo  [4/5] Registrando Tarea Programada (inicio de sesion)...
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] --- PASO 4: VBS + schtasks ---

REM Generar VBS launcher con Chr(34) para quotes - evita ambiguedad de escape de CMD.
REM q = Chr(34) = " - la ruta del wrapper queda bien protegida con comillas en el Run.
(
    echo Dim sh, q
    echo Set sh = CreateObject("WScript.Shell"^)
    echo q = Chr(34^)
    echo sh.Run "cmd /c " ^& q ^& "%WRAPPER_SCRIPT%" ^& q, 0, False
    echo Set sh = Nothing
) > "%VBS_PATH%"

if not exist "%VBS_PATH%" (
    echo.
    echo  [ERROR] No se pudo crear el VBS en: %VBS_PATH%
    echo.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: VBS_PATH no fue creado: %VBS_PATH%
    pause & exit /b 1
)
echo  [OK]    VBS launcher generado: %VBS_PATH%
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: VBS_PATH creado: %VBS_PATH%

REM Obtener ruta 8.3 (sin espacios) del VBS para schtasks
for %%F in ("%VBS_PATH%") do set "VBS_SHORT=%%~sF"
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] VBS_SHORT=!VBS_SHORT!

REM Eliminar tarea anterior si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    echo  [INFO]  Tarea anterior eliminada.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] INFO: Tarea anterior eliminada
)

set "SCHED_OK=1"

REM Intento 1: /rl LIMITED + /delay (preferido en Windows 10 build 1703+)
schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /rl LIMITED /delay 0000:30 >nul 2>&1
if !ERRORLEVEL! equ 0 (
    set "SCHED_OK=0"
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 1 ^(/rl LIMITED /delay^)
)

REM Intento 2: sin /rl
if "!SCHED_OK!"=="1" (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /delay 0000:30 >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        set "SCHED_OK=0"
        if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 2 ^(sin /rl^)
    )
)

REM Intento 3: parametros minimos
if "!SCHED_OK!"=="1" (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        set "SCHED_OK=0"
        if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 3 ^(parametros minimos^)
    )
)

REM Verificar registro
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo  [OK]    Tarea "%TASK_NAME%" registrada.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: Tarea verificada con schtasks /query
    set "INSTALADO=tarea-programada"
) else (
    REM Fallback: carpeta Startup
    echo  [AVISO] schtasks no disponible. Instalando en carpeta Startup...
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] AVISO: schtasks fallo en 3 intentos - fallback Startup

    set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] STARTUP_DIR=!STARTUP_DIR!

    copy /Y "%VBS_PATH%" "!STARTUP_DIR!\AutomationQARunner.vbs" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        if exist "!STARTUP_DIR!\AutomationQARunner.vbs" (
            echo  [OK]    Instalado en carpeta Startup.
            if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: VBS copiado a Startup: !STARTUP_DIR!\AutomationQARunner.vbs
            set "INSTALADO=startup"
        ) else (
            echo  [ERROR] VBS no encontrado despues de copiarlo a Startup.
            if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: VBS no encontrado en Startup tras copy
        )
    ) else (
        echo  [ERROR] No se pudo copiar a Startup: !STARTUP_DIR!
        if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR: copy fallo. Startup=%STARTUP_DIR%
    )
)
echo.

REM ==============================================================================
REM  PASO 5: Herramientas de control
REM ==============================================================================
echo  [5/5] Creando herramientas de control...
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] --- PASO 5: Herramientas ---

(
    echo Dim sh
    echo Set sh = CreateObject("WScript.Shell"^)
    echo sh.Run "cmd /c type ""%LOG_FILE%"" ^| more", 1, False
    echo Set sh = Nothing
) > "%INSTALL_DIR%\ver-logs.vbs"

echo  [OK]    Logs: %INSTALL_DIR%\ver-logs.vbs
echo.

REM -- Validacion final: verificar que VBS_PATH existe antes de lanzar ----------
if not exist "%VBS_PATH%" (
    echo.
    echo  [ERROR] El archivo VBS launcher no existe: %VBS_PATH%
    echo          La instalacion puede estar incompleta.
    echo.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] ERROR CRITICO: VBS_PATH no existe al final: %VBS_PATH%
    pause & exit /b 1
)

if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] --- INSTALACION COMPLETADA ---
if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] Metodo: !INSTALADO!

echo  ================================================================
echo   Instalacion completada correctamente.
echo.
echo   El Runner arrancara AUTOMATICAMENTE al iniciar sesion.
echo.
echo   Runner ID:     %RUNNER_ID%
echo   Backend:       %BACKEND_URL%
echo   JAR:           %JAR_DST%
echo   Logs runtime:  %LOG_FILE%
echo   Instalacion:   %INSTALL_LOG%
echo.
echo   Para desinstalar: service\windows\uninstall-service.bat
echo  ================================================================
echo.

set /p LAUNCH_NOW=  Iniciar el Runner ahora mismo? (S/n):
if "!LAUNCH_NOW!"=="" set LAUNCH_NOW=S
if /i "!LAUNCH_NOW!"=="S" (
    echo.
    echo  Iniciando Runner en segundo plano...
    start "" wscript.exe "%VBS_PATH%"
    echo  [OK]  Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
    if "!LOG_AVAILABLE!"=="1" >> "%INSTALL_LOG%" echo [%TIME%] OK: Runner iniciado manualmente con wscript.exe
)

echo.
echo  Log de instalacion: %INSTALL_LOG%
echo.
pause
endlocal
