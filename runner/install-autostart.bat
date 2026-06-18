@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ══════════════════════════════════════════════════════════════════════════════
REM  Automation QA Runner — Instalador Auto-Start v3.1 (Windows 10 / 11)
REM  Registra el Runner como Tarea Programada sin privilegios de Admin.
REM
REM  REQUISITO: Ejecutar desde la carpeta runner\ del proyecto.
REM  Todas las rutas se construyen a partir de %~dp0 — sin rutas hardcodeadas.
REM ══════════════════════════════════════════════════════════════════════════════

REM ── Rutas — todas relativas a la carpeta de este script (%~dp0) ───────────────
set "SCRIPT_DIR=%~dp0"
set "RUNNER_VBS=%SCRIPT_DIR%runner-launcher.vbs"
set "START_BAT=%SCRIPT_DIR%start-runner-auto.bat"
set "JAR_FILE=%SCRIPT_DIR%target\cinepolis-runner.jar"
set "LOG_DIR=%SCRIPT_DIR%logs"
set "INSTALL_LOG=%LOG_DIR%\install.log"
set "TASK_NAME=Automation QA Runner"

REM ── Crear directorio de logs antes de escribir ────────────────────────────────
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

REM ── Abrir install.log ─────────────────────────────────────────────────────────
>> "%INSTALL_LOG%" (
    echo.
    echo ================================================================
    echo  Automation QA Runner — Instalacion Auto-Start
    echo  Fecha: %DATE%   Hora: %TIME%
    echo ================================================================
)
>> "%INSTALL_LOG%" echo.

REM ── Registrar variables criticas en el log ────────────────────────────────────
>> "%INSTALL_LOG%" echo [%TIME%] --- Variables de entorno ---
>> "%INSTALL_LOG%" echo [%TIME%] LOCALAPPDATA=%LOCALAPPDATA%
>> "%INSTALL_LOG%" echo [%TIME%] APPDATA=%APPDATA%
>> "%INSTALL_LOG%" echo [%TIME%] USERNAME=%USERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] COMPUTERNAME=%COMPUTERNAME%
>> "%INSTALL_LOG%" echo.
>> "%INSTALL_LOG%" echo [%TIME%] --- Rutas calculadas ---
>> "%INSTALL_LOG%" echo [%TIME%] SCRIPT_DIR=%SCRIPT_DIR%
>> "%INSTALL_LOG%" echo [%TIME%] RUNNER_VBS=%RUNNER_VBS%
>> "%INSTALL_LOG%" echo [%TIME%] START_BAT=%START_BAT%
>> "%INSTALL_LOG%" echo [%TIME%] JAR_FILE=%JAR_FILE%
>> "%INSTALL_LOG%" echo [%TIME%] LOG_DIR=%LOG_DIR%
>> "%INSTALL_LOG%" echo [%TIME%] INSTALL_LOG=%INSTALL_LOG%
>> "%INSTALL_LOG%" echo.

echo.
echo  ╔═══════════════════════════════════════════════════════════════════╗
echo  ║   Automation QA Runner — Instalador Auto-Start v3.1            ║
echo  ║   Windows 10 / 11  —  Sin privilegios de Administrador         ║
echo  ╚═══════════════════════════════════════════════════════════════════╝
echo.

REM ── DIAGNOSTICO: Rutas calculadas al inicio ───────────────────────────────────
echo  ┌─ DIAGNOSTICO ─────────────────────────────────────────────────────────┐
echo  ^|  LOCALAPPDATA  = %LOCALAPPDATA%
echo  ^|  SCRIPT_DIR    = %SCRIPT_DIR%
echo  ^|  RUNNER_VBS    = %RUNNER_VBS%
echo  ^|  START_BAT     = %START_BAT%
echo  ^|  LOG_DIR       = %LOG_DIR%
echo  ^|  install.log   = %INSTALL_LOG%
echo  └───────────────────────────────────────────────────────────────────────┘
echo.

REM ═══════════════════════════════════════════════════════════════════════════════
REM  PASO 1: Validar archivos requeridos
REM ═══════════════════════════════════════════════════════════════════════════════
echo  [1/4] Validando archivos del Runner...
>> "%INSTALL_LOG%" echo [%TIME%] --- PASO 1: Validacion de archivos ---

REM ── runner-launcher.vbs — REQUERIDO ──────────────────────────────────────────
if not exist "%RUNNER_VBS%" (
    echo.
    echo  [ERROR] Archivo no encontrado:
    echo          runner-launcher.vbs
    echo.
    echo          Ruta buscada: %RUNNER_VBS%
    echo.
    echo          Asegurate de ejecutar install-autostart.bat
    echo          desde la carpeta runner\ del proyecto.
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: runner-launcher.vbs no encontrado: %RUNNER_VBS%
    pause & exit /b 1
)
echo          runner-launcher.vbs    [OK]
>> "%INSTALL_LOG%" echo [%TIME%] OK: runner-launcher.vbs

REM ── start-runner-auto.bat — REQUERIDO ────────────────────────────────────────
if not exist "%START_BAT%" (
    echo.
    echo  [ERROR] Archivo no encontrado:
    echo          start-runner-auto.bat
    echo.
    echo          Ruta buscada: %START_BAT%
    echo.
    echo          Este archivo es necesario para iniciar el Runner.
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: start-runner-auto.bat no encontrado: %START_BAT%
    pause & exit /b 1
)
echo          start-runner-auto.bat  [OK]
>> "%INSTALL_LOG%" echo [%TIME%] OK: start-runner-auto.bat

REM ── cinepolis-runner.jar — OPCIONAL ──────────────────────────────────────────
if exist "%JAR_FILE%" (
    echo          cinepolis-runner.jar   [OK]
    >> "%INSTALL_LOG%" echo [%TIME%] OK: cinepolis-runner.jar encontrado
) else (
    where mvn >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo          cinepolis-runner.jar   [INFO] Maven disponible — se compilara al primer inicio
        >> "%INSTALL_LOG%" echo [%TIME%] INFO: JAR no encontrado, Maven disponible
    ) else (
        echo.
        echo  [AVISO] cinepolis-runner.jar no encontrado y Maven no esta en PATH.
        echo.
        echo          El Runner no podra iniciarse hasta tener el JAR disponible.
        echo          Opciones:
        echo            A^) mvn package -DskipTests  (desde la carpeta runner\^)
        echo            B^) Copia cinepolis-runner.jar a: %SCRIPT_DIR%target\
        echo.
        >> "%INSTALL_LOG%" echo [%TIME%] AVISO: JAR no encontrado y Maven no disponible
        set /p CONT=  Continuar la instalacion de todas formas? (S/n):
        if "!CONT!"=="" set CONT=S
        if /i not "!CONT!"=="S" (
            >> "%INSTALL_LOG%" echo [%TIME%] Instalacion cancelada por el usuario.
            pause & exit /b 0
        )
    )
)

REM ═══════════════════════════════════════════════════════════════════════════════
REM  PASO 2: Verificar Java
REM ═══════════════════════════════════════════════════════════════════════════════
echo.
echo  [2/4] Verificando Java...
>> "%INSTALL_LOG%" echo [%TIME%] --- PASO 2: Java ---

java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
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

REM ═══════════════════════════════════════════════════════════════════════════════
REM  PASO 3: Registrar Tarea Programada
REM ═══════════════════════════════════════════════════════════════════════════════
echo.
echo  [3/4] Registrando tarea programada (al iniciar sesion)...
>> "%INSTALL_LOG%" echo [%TIME%] --- PASO 3: Tarea Programada ---

REM Obtener ruta 8.3 (sin espacios) del VBS para pasar a schtasks sin problemas
REM %%~sF convierte "C:\Mi Carpeta\..." en "C:\MICARP~1\..." (sin espacios)
for %%F in ("%RUNNER_VBS%") do set "VBS_SHORT=%%~sF"
>> "%INSTALL_LOG%" echo [%TIME%] RUNNER_VBS=%RUNNER_VBS%
>> "%INSTALL_LOG%" echo [%TIME%] VBS_SHORT=%VBS_SHORT%

REM Eliminar tarea anterior si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    echo          Tarea anterior eliminada.
    >> "%INSTALL_LOG%" echo [%TIME%] INFO: Tarea anterior eliminada
)

set "SCHED_OK=1"

REM Intento 1: /rl LIMITED + /delay (preferido en Windows 10 build 1703+)
schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /rl LIMITED /delay 0000:30 >nul 2>&1
if !ERRORLEVEL! equ 0 (
    set "SCHED_OK=0"
    >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 1 ^(/rl LIMITED /delay^) exitoso
)

REM Intento 2: sin /rl
if "!SCHED_OK!"=="1" (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f /delay 0000:30 >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        set "SCHED_OK=0"
        >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 2 ^(sin /rl^) exitoso
    )
)

REM Intento 3: parametros minimos (maxima compatibilidad)
if "!SCHED_OK!"=="1" (
    schtasks /create /tn "%TASK_NAME%" /tr "wscript.exe %VBS_SHORT%" /sc ONLOGON /ru "%USERNAME%" /f >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        set "SCHED_OK=0"
        >> "%INSTALL_LOG%" echo [%TIME%] OK: schtasks intento 3 ^(parametros minimos^) exitoso
    )
)

REM Verificar registro de la tarea
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo          Tarea "%TASK_NAME%"  [OK]
    >> "%INSTALL_LOG%" echo [%TIME%] OK: Tarea programada verificada con schtasks /query
    set "INSTALADO=tarea"
) else (
    REM ── Fallback: carpeta Startup ─────────────────────────────────────────────
    echo  [AVISO] schtasks no disponible en este equipo.
    echo          Instalando en carpeta Startup del usuario...
    >> "%INSTALL_LOG%" echo [%TIME%] AVISO: schtasks fallo en los 3 intentos — fallback a Startup

    set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    >> "%INSTALL_LOG%" echo [%TIME%] STARTUP_DIR=!STARTUP_DIR!

    REM Generar wrapper VBS en Startup apuntando a runner-launcher.vbs en su ubicacion original.
    REM NO copiar runner-launcher.vbs directamente: el VBS resuelve su bat por WScript.ScriptFullName
    REM y si se copia a Startup, no encontraria start-runner-auto.bat.
    (
        echo Dim sh
        echo Set sh = CreateObject("WScript.Shell"^)
        echo sh.Run "wscript.exe ""%RUNNER_VBS%""", 0, False
        echo Set sh = Nothing
    ) > "!STARTUP_DIR!\AutomationQARunner.vbs"

    if !ERRORLEVEL! equ 0 (
        if exist "!STARTUP_DIR!\AutomationQARunner.vbs" (
            echo          Instalado en carpeta Startup  [OK]
            >> "%INSTALL_LOG%" echo [%TIME%] OK: Wrapper VBS creado: !STARTUP_DIR!\AutomationQARunner.vbs
            set "INSTALADO=startup"
        ) else (
            echo.
            echo  [ERROR] No se pudo crear el acceso directo en Startup.
            echo          Ruta intentada: !STARTUP_DIR!\AutomationQARunner.vbs
            echo.
            >> "%INSTALL_LOG%" echo [%TIME%] ERROR: VBS no encontrado despues de crearlo: !STARTUP_DIR!\AutomationQARunner.vbs
            pause & exit /b 1
        )
    ) else (
        echo.
        echo  [ERROR] No se pudo escribir en la carpeta Startup.
        echo          Ruta: !STARTUP_DIR!
        echo.
        echo          Verifica permisos de escritura en esa carpeta.
        echo.
        >> "%INSTALL_LOG%" echo [%TIME%] ERROR: No se pudo escribir en Startup: !STARTUP_DIR!
        pause & exit /b 1
    )
)

REM ═══════════════════════════════════════════════════════════════════════════════
REM  PASO 4: Resumen
REM ═══════════════════════════════════════════════════════════════════════════════
echo.
echo  [4/4] Instalacion completada.
>> "%INSTALL_LOG%" echo [%TIME%] --- INSTALACION COMPLETADA ---
>> "%INSTALL_LOG%" echo [%TIME%] Metodo: !INSTALADO!
>> "%INSTALL_LOG%" echo [%TIME%] Launcher: %RUNNER_VBS%

echo.
echo  ════════════════════════════════════════════════════════════════════
echo   Automation QA Runner — Configurado correctamente.
echo.
echo   El Runner arrancara AUTOMATICAMENTE al iniciar sesion en Windows.
echo   No necesitas ejecutar ningun script manualmente.
echo.
echo   Launcher:    %RUNNER_VBS%
echo   Logs:        %LOG_DIR%\runner.log
echo   Instalacion: %INSTALL_LOG%
echo  ════════════════════════════════════════════════════════════════════
echo.

set /p RUN_NOW=  Iniciar el Runner ahora mismo? (S/n):
if "!RUN_NOW!"=="" set RUN_NOW=S
if /i "!RUN_NOW!"=="S" (
    echo.
    if not exist "%RUNNER_VBS%" (
        echo  [ERROR] Archivo no encontrado al intentar iniciar:
        echo          %RUNNER_VBS%
        >> "%INSTALL_LOG%" echo [%TIME%] ERROR: VBS no encontrado al intentar iniciar
    ) else (
        echo  Iniciando Runner en segundo plano...
        wscript.exe "%RUNNER_VBS%"
        echo  [OK]  Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
        >> "%INSTALL_LOG%" echo [%TIME%] OK: Runner iniciado manualmente
    )
)

echo.
echo  Log de instalacion: %INSTALL_LOG%
echo.
pause
endlocal
