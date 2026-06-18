@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ==============================================================================
REM  Automation QA Runner - Instalador Auto-Start v4.1 (Windows 10 / 11)
REM
REM  Compatible con Windows 10 build 1703+ y Windows 11.
REM  No requiere privilegios de Administrador.
REM  No asume rutas fijas ni que la tarea se crea correctamente.
REM  No lee ni ejecuta contenido de runner.properties, .env ni config.json.
REM
REM  REQUISITO: Ejecutar desde la carpeta runner\ del proyecto.
REM  Todas las rutas se construyen a partir de %~dp0 - sin rutas hardcodeadas.
REM ==============================================================================

REM -- Rutas - todas relativas a la carpeta de este script (%~dp0) ---------------
set "SCRIPT_DIR=%~dp0"
set "RUNNER_VBS=%SCRIPT_DIR%runner-launcher.vbs"
set "START_BAT=%SCRIPT_DIR%start-runner-auto.bat"
set "JAR_FILE=%SCRIPT_DIR%target\cinepolis-runner.jar"
set "LOG_DIR=%SCRIPT_DIR%logs"
set "INSTALL_LOG=%LOG_DIR%\install.log"

REM Nombre de tarea sin espacios para evitar problemas de parseo en schtasks
set "TASK_NAME=AutomationQARunner"
set "TASK_DISPLAY=Automation QA Runner"

REM -- Variables de estado -------------------------------------------------------
set "INSTALADO="
set "SCHED_OK=1"
set "PREFLIGHT_OK=1"

REM -- Crear directorio de logs antes de escribir --------------------------------
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

REM -- Cabecera del log ----------------------------------------------------------
>> "%INSTALL_LOG%" echo.
>> "%INSTALL_LOG%" echo ================================================================
>> "%INSTALL_LOG%" echo  Automation QA Runner - Instalacion Auto-Start v4.1
>> "%INSTALL_LOG%" echo  Fecha: %DATE%   Hora: %TIME%
>> "%INSTALL_LOG%" echo ================================================================
>> "%INSTALL_LOG%" echo [%TIME%] --- Variables de entorno ---
>> "%INSTALL_LOG%" echo [%TIME%] USERNAME=%USERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] COMPUTERNAME=%COMPUTERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] LOCALAPPDATA=%LOCALAPPDATA%
>> "%INSTALL_LOG%" echo [%TIME%] APPDATA=%APPDATA%
>> "%INSTALL_LOG%" echo.
>> "%INSTALL_LOG%" echo [%TIME%] --- Rutas calculadas ---
>> "%INSTALL_LOG%" echo [%TIME%] SCRIPT_DIR=%SCRIPT_DIR%
>> "%INSTALL_LOG%" echo [%TIME%] RUNNER_VBS=%RUNNER_VBS%
>> "%INSTALL_LOG%" echo [%TIME%] START_BAT=%START_BAT%
>> "%INSTALL_LOG%" echo [%TIME%] JAR_FILE=%JAR_FILE%
>> "%INSTALL_LOG%" echo [%TIME%] TASK_NAME=%TASK_NAME%
>> "%INSTALL_LOG%" echo.

echo.
echo  ================================================================
echo   Automation QA Runner - Instalador Auto-Start v4.1
echo   Windows 10 / 11 - Sin privilegios de Administrador
echo  ================================================================
echo.
echo    SCRIPT_DIR : %SCRIPT_DIR%
echo    RUNNER_VBS : %RUNNER_VBS%
echo    START_BAT  : %START_BAT%
echo    LOG_DIR    : %LOG_DIR%
echo    TASK_NAME  : %TASK_NAME%
echo.

REM ==============================================================================
REM  PASO 1: Validar archivos requeridos
REM  IMPORTANTE: No se leen ni ejecutan runner.properties, .env ni config.json.
REM  Las variables se definen directamente en start-runner-auto.bat.
REM ==============================================================================
echo  [1/4] Validando archivos del Runner...
>> "%INSTALL_LOG%" echo [%TIME%] --- PASO 1: Validacion de archivos ---

if not exist "%RUNNER_VBS%" (
    echo.
    echo  [ERROR] Archivo no encontrado: runner-launcher.vbs
    echo          Ruta buscada: %RUNNER_VBS%
    echo.
    echo          Asegurate de ejecutar install-autostart.bat
    echo          desde la carpeta runner\ del proyecto.
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: runner-launcher.vbs no encontrado: %RUNNER_VBS%
    pause & exit /b 1
)
echo          runner-launcher.vbs    [OK]
>> "%INSTALL_LOG%" echo [%TIME%] OK: runner-launcher.vbs: %RUNNER_VBS%

if not exist "%START_BAT%" (
    echo.
    echo  [ERROR] Archivo no encontrado: start-runner-auto.bat
    echo          Ruta buscada: %START_BAT%
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: start-runner-auto.bat no encontrado: %START_BAT%
    pause & exit /b 1
)
echo          start-runner-auto.bat  [OK]
>> "%INSTALL_LOG%" echo [%TIME%] OK: start-runner-auto.bat: %START_BAT%

if exist "%JAR_FILE%" (
    echo          cinepolis-runner.jar   [OK]
    >> "%INSTALL_LOG%" echo [%TIME%] OK: cinepolis-runner.jar: %JAR_FILE%
) else (
    where mvn >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo          cinepolis-runner.jar   [INFO] Maven disponible - compilara al primer inicio
        >> "%INSTALL_LOG%" echo [%TIME%] INFO: JAR no encontrado - Maven disponible
    ) else (
        echo.
        echo  [AVISO] cinepolis-runner.jar no encontrado y Maven no esta en PATH.
        echo.
        echo          El Runner no podra iniciarse hasta tener el JAR disponible.
        echo          Opciones:
        echo            A) mvn package -DskipTests  (desde la carpeta runner\)
        echo            B) Copia cinepolis-runner.jar a: %SCRIPT_DIR%target\
        echo.
        >> "%INSTALL_LOG%" echo [%TIME%] AVISO: JAR no encontrado y Maven no disponible
        set /p "CONT=  Continuar la instalacion de todas formas? (S/n): "
        if "!CONT!"=="" set "CONT=S"
        if /i not "!CONT!"=="S" (
            >> "%INSTALL_LOG%" echo [%TIME%] Instalacion cancelada por el usuario
            exit /b 0
        )
    )
)

REM ==============================================================================
REM  PASO 2: Verificar Java
REM ==============================================================================
echo.
echo  [2/4] Verificando Java...
>> "%INSTALL_LOG%" echo [%TIME%] --- PASO 2: Java ---

java -version >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo.
    echo  [ERROR] Java 17+ no encontrado en el PATH del sistema.
    echo.
    echo          Descarga e instala Java desde: https://adoptium.net
    echo          Reinicia sesion y vuelve a ejecutar este instalador.
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: java no encontrado en PATH
    pause & exit /b 1
)
echo          Java disponible  [OK]
>> "%INSTALL_LOG%" echo [%TIME%] OK: Java disponible

REM ==============================================================================
REM  PASO 3: Registrar Tarea Programada
REM  Se verifica con schtasks /query despues de cada intento de creacion.
REM  Si los 3 intentos fallan, se usa la carpeta Startup como alternativa.
REM ==============================================================================
echo.
echo  [3/4] Registrando tarea programada (al iniciar sesion)...
>> "%INSTALL_LOG%" echo [%TIME%] --- PASO 3: Tarea Programada ---

REM Obtener ruta 8.3 del VBS para evitar problemas con espacios en schtasks /tr
for %%F in ("%RUNNER_VBS%") do set "VBS_SHORT=%%~sF"
>> "%INSTALL_LOG%" echo [%TIME%] VBS_SHORT=%VBS_SHORT%

REM Eliminar tarea anterior si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    >> "%INSTALL_LOG%" echo [%TIME%] INFO: Tarea anterior eliminada
)

REM -- Intento 1: /rl LIMITED + /delay (Windows 10 build 1703+) -----------------
schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /rl LIMITED /delay 0000:30 >nul 2>&1
if !ERRORLEVEL! equ 0 (
    schtasks /query /tn "%TASK_NAME%" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        set "SCHED_OK=0"
        >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 1 (/rl LIMITED /delay) - tarea verificada
    ) else (
        >> "%INSTALL_LOG%" echo [%TIME%] FALLO: intento 1 - /create OK pero /query fallo
    )
)

REM -- Intento 2: sin /rl -------------------------------------------------------
if "!SCHED_OK!"=="1" (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /delay 0000:30 >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        schtasks /query /tn "%TASK_NAME%" >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            set "SCHED_OK=0"
            >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 2 (sin /rl) - tarea verificada
        ) else (
            >> "%INSTALL_LOG%" echo [%TIME%] FALLO: intento 2 - /create OK pero /query fallo
        )
    )
)

REM -- Intento 3: parametros minimos (maxima compatibilidad) --------------------
if "!SCHED_OK!"=="1" (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        schtasks /query /tn "%TASK_NAME%" >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            set "SCHED_OK=0"
            >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 3 (minimo) - tarea verificada
        ) else (
            >> "%INSTALL_LOG%" echo [%TIME%] FALLO: intento 3 - /create OK pero /query fallo
        )
    )
)

REM -- Resultado de schtasks ----------------------------------------------------
if "!SCHED_OK!"=="0" (
    echo          Tarea "%TASK_DISPLAY%"  [OK]
    >> "%INSTALL_LOG%" echo [%TIME%] OK: Tarea programada registrada y verificada: %TASK_NAME%
    set "INSTALADO=tarea"
    goto :PASO4
)

REM -- Fallback: carpeta Startup del usuario ------------------------------------
echo  [AVISO] No se pudo registrar la tarea programada en schtasks.
echo          Motivo probable: politica del equipo o Windows sin actualizar.
echo          Instalando en carpeta Startup del usuario como alternativa...
echo.
>> "%INSTALL_LOG%" echo [%TIME%] AVISO: schtasks fallo en 3 intentos - usando Startup

set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "STARTUP_VBS=!STARTUP_DIR!\AutomationQARunner.vbs"
>> "%INSTALL_LOG%" echo [%TIME%] STARTUP_DIR=!STARTUP_DIR!
>> "%INSTALL_LOG%" echo [%TIME%] STARTUP_VBS=!STARTUP_VBS!

if not exist "!STARTUP_DIR!" (
    echo  [ERROR] No se encontro la carpeta Startup del usuario.
    echo          Ruta esperada: !STARTUP_DIR!
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: Carpeta Startup no existe: !STARTUP_DIR!
    pause & exit /b 1
)

REM Generar wrapper VBS en Startup que invoca al runner-launcher.vbs original.
REM El VBS original usa WScript.ScriptFullName para localizar start-runner-auto.bat,
REM por lo que NO se debe copiar - debe llamarse desde su ubicacion original.
(
    echo ' AutomationQARunner.vbs - generado por install-autostart.bat
    echo ' Invoca runner-launcher.vbs desde su ubicacion original en runner\
    echo Dim sh
    echo Set sh = CreateObject^("WScript.Shell"^)
    echo sh.Run "wscript.exe ""%RUNNER_VBS%""", 0, False
    echo Set sh = Nothing
) > "!STARTUP_VBS!"

if exist "!STARTUP_VBS!" (
    echo          Instalado en carpeta Startup  [OK]
    >> "%INSTALL_LOG%" echo [%TIME%] OK: Wrapper VBS creado: !STARTUP_VBS!
    set "INSTALADO=startup"
) else (
    echo.
    echo  [ERROR] No se pudo crear el acceso directo en la carpeta Startup.
    echo          Ruta intentada: !STARTUP_VBS!
    echo.
    echo          Causas posibles:
    echo            - Ruta de Startup no accesible
    echo            - Permisos insuficientes sobre la carpeta
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: No se pudo crear wrapper en Startup: !STARTUP_VBS!
    pause & exit /b 1
)

:PASO4
REM ==============================================================================
REM  PASO 4: Resumen de instalacion
REM ==============================================================================
echo.
echo  [4/4] Instalacion completada.
>> "%INSTALL_LOG%" echo [%TIME%] --- INSTALACION COMPLETADA ---
>> "%INSTALL_LOG%" echo [%TIME%] Metodo: !INSTALADO!
>> "%INSTALL_LOG%" echo [%TIME%] Launcher: %RUNNER_VBS%
>> "%INSTALL_LOG%" echo [%TIME%] JAR: %JAR_FILE%
>> "%INSTALL_LOG%" echo [%TIME%] Log de runner: %LOG_DIR%\runner.log

echo.
echo  ================================================================
if "!INSTALADO!"=="tarea" (
    echo   Metodo  : Tarea Programada ^(schtasks^)
    echo   Tarea   : %TASK_NAME%
) else (
    echo   Metodo  : Carpeta Startup del usuario
    echo   Archivo : AutomationQARunner.vbs en Startup
)
echo.
echo   El Runner arrancara automaticamente al iniciar sesion en Windows.
echo   No necesitas ejecutar ningun script manualmente.
echo.
echo   Launcher    : %RUNNER_VBS%
echo   Log runner  : %LOG_DIR%\runner.log
echo   Instalacion : %INSTALL_LOG%
echo  ================================================================
echo.

REM ==============================================================================
REM  ARRANQUE OPCIONAL - con pre-flight completo antes de iniciar
REM ==============================================================================
set /p "RUN_NOW=  Iniciar el Runner ahora mismo? (S/n): "
if "!RUN_NOW!"=="" set "RUN_NOW=S"
if /i not "!RUN_NOW!"=="S" goto :FIN

echo.
echo  Ejecutando verificaciones previas al arranque...
>> "%INSTALL_LOG%" echo [%TIME%] --- PRE-FLIGHT: Verificaciones antes de arrancar ---

set "PREFLIGHT_OK=1"

REM -- 1. Verificar launcher VBS ------------------------------------------------
if not exist "%RUNNER_VBS%" (
    echo  [ERROR] Launcher no encontrado:
    echo          %RUNNER_VBS%
    >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT ERROR: launcher VBS no existe: %RUNNER_VBS%
    set "PREFLIGHT_OK=0"
) else (
    >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT OK: launcher VBS existe
)

REM -- 2. Verificar start-runner-auto.bat ---------------------------------------
if not exist "%START_BAT%" (
    echo  [ERROR] Archivo de inicio no encontrado:
    echo          %START_BAT%
    >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT ERROR: start-runner-auto.bat no existe: %START_BAT%
    set "PREFLIGHT_OK=0"
) else (
    >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT OK: start-runner-auto.bat existe
)

REM -- 3. Verificar JAR (advertencia, no bloquea) --------------------------------
if not exist "%JAR_FILE%" (
    echo  [AVISO] JAR no encontrado: %JAR_FILE%
    echo          El Runner intentara compilar con Maven al iniciar.
    >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT AVISO: JAR no existe - Maven compilara al iniciar
) else (
    >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT OK: JAR existe: %JAR_FILE%
)

REM -- 4. Verificar metodo de inicio segun lo instalado --------------------------
if "!INSTALADO!"=="tarea" (
    schtasks /query /tn "%TASK_NAME%" >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo  [ERROR] La tarea programada "%TASK_NAME%" no existe en el sistema.
        echo          No es posible iniciar el Runner mediante la tarea.
        >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT ERROR: tarea programada no existe: %TASK_NAME%
        set "PREFLIGHT_OK=0"
    ) else (
        >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT OK: tarea programada existe: %TASK_NAME%
    )
)

if "!INSTALADO!"=="startup" (
    set "_SVBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\AutomationQARunner.vbs"
    if not exist "!_SVBS!" (
        echo  [ERROR] Wrapper de Startup no encontrado:
        echo          !_SVBS!
        >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT ERROR: wrapper Startup no existe: !_SVBS!
        set "PREFLIGHT_OK=0"
    ) else (
        >> "%INSTALL_LOG%" echo [%TIME%] PRE-FLIGHT OK: wrapper Startup existe: !_SVBS!
    )
)

REM -- Resultado del pre-flight -------------------------------------------------
if "!PREFLIGHT_OK!"=="0" (
    echo.
    echo  [ERROR] Una o mas verificaciones fallaron.
    echo          El Runner NO sera iniciado para evitar errores.
    echo.
    echo          Revisa el log de instalacion:
    echo          %INSTALL_LOG%
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: Pre-flight fallido - Runner NO iniciado
    goto :FIN
)

echo          Todas las verificaciones pasaron  [OK]
echo.
echo  Iniciando Runner en segundo plano...
>> "%INSTALL_LOG%" echo [%TIME%] OK: Pre-flight OK - iniciando Runner
>> "%INSTALL_LOG%" echo [%TIME%] Comando: wscript.exe "%RUNNER_VBS%"

wscript.exe "%RUNNER_VBS%"

echo  [OK]  Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
>> "%INSTALL_LOG%" echo [%TIME%] OK: Runner iniciado manualmente via wscript.exe

:FIN
echo.
echo  Log de instalacion: %INSTALL_LOG%
echo.
pause
endlocal
