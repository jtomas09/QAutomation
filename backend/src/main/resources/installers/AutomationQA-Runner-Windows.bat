@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ════════════════════════════════════════════════════════════════════════════
REM  Automation QA Runner — Instalador Windows v2.3.0
REM  Instalacion automatica del servicio de automatizacion.
REM  Todas las rutas son dinamicas — sin rutas hardcodeadas.
REM ════════════════════════════════════════════════════════════════════════════

set "BACKEND_URL=https://qautomation-production.up.railway.app"
set "RUNNER_TOKEN=runner-local-token"

echo.
echo  ╔═══════════════════════════════════════════════════════════════╗
echo  ║   Automation QA Runner — Instalacion v2.3.0                 ║
echo  ║   Configurando el servicio automaticamente...               ║
echo  ╚═══════════════════════════════════════════════════════════════╝
echo.

REM ── Validar sesion de usuario (LOCALAPPDATA requerido) ───────────────────────
if not defined LOCALAPPDATA (
    echo  [ERROR] La instalacion requiere una sesion de usuario normal de Windows.
    echo  Ejecuta este instalador iniciando sesion con tu cuenta de usuario.
    pause & exit /b 1
)
if "%LOCALAPPDATA%"=="\" (
    echo  [ERROR] La variable de entorno LOCALAPPDATA esta vacia.
    echo  Ejecuta este instalador iniciando sesion con tu cuenta de usuario.
    pause & exit /b 1
)

set "INSTALL_DIR=%LOCALAPPDATA%\AutomationQA\runner"
set "LOG_DIR=%INSTALL_DIR%\logs"
set "JAR_DST=%INSTALL_DIR%\automationqa-runner.jar"

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%" >nul 2>&1
if not exist "%LOG_DIR%"     mkdir "%LOG_DIR%"     >nul 2>&1

REM ── Paso 1: Verificar entorno de ejecucion ────────────────────────────────────
echo  [1/4] Verificando entorno de ejecucion...
java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  ╔═══════════════════════════════════════════════════════════════╗
    echo  ║   Se requiere instalar un componente del sistema            ║
    echo  ╚═══════════════════════════════════════════════════════════════╝
    echo.
    echo  El Runner de Automation QA necesita el entorno de ejecucion
    echo  Java para funcionar. Este componente es gratuito y seguro.
    echo.
    echo  1. Descarga el instalador desde: https://adoptium.net
    echo  2. Selecciona la version LTS y completa la instalacion
    echo  3. Reinicia tu sesion de Windows
    echo  4. Vuelve a ejecutar este instalador
    echo.
    start "" https://adoptium.net
    pause & exit /b 1
)
echo  [OK] Entorno de ejecucion verificado.

REM ── Paso 2: Obtener componente principal del Runner ───────────────────────────
echo  [2/4] Descargando componentes del Runner...
set "RUNNER_JAR="

REM Si ya esta instalada una version anterior, reutilizarla
if exist "%JAR_DST%" (
    set "RUNNER_JAR=%JAR_DST%"
    echo  [OK] Componentes ya presentes — actualizando configuracion.
    goto :JAR_READY
)

REM Buscar JAR junto a este instalador (paquete completo)
for %%P in (
    "%~dp0cinepolis-runner.jar"
    "%~dp0automationqa-runner.jar"
    "%~dp0runner\cinepolis-runner.jar"
) do (
    if exist %%P (
        set "RUNNER_JAR=%%~fP"
        goto :JAR_FOUND
    )
)

REM Descargar automaticamente desde el servidor de Automation QA
echo  Conectando al servidor para descargar componentes...
set "JAR_TMP=%TEMP%\automationqa-runner-setup.jar"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { Invoke-WebRequest -Uri '%BACKEND_URL%/api/runner/download/jar' -OutFile '%JAR_TMP%' -UseBasicParsing -TimeoutSec 120; exit 0 } catch { exit 1 }" >nul 2>&1
if !ERRORLEVEL! equ 0 if exist "%JAR_TMP%" (
    set "RUNNER_JAR=%JAR_TMP%"
    goto :JAR_FOUND
)

REM No se pudieron obtener los componentes
echo.
echo  ╔═══════════════════════════════════════════════════════════════╗
echo  ║   El paquete de instalacion esta incompleto                 ║
echo  ╚═══════════════════════════════════════════════════════════════╝
echo.
echo  No se pudieron obtener los componentes necesarios.
echo.
echo  Por favor:
echo    1. Verifica tu conexion a internet
echo    2. Descarga nuevamente el instalador desde el Dashboard
echo    3. Ejecuta el nuevo instalador descargado
echo.
pause & exit /b 1

:JAR_FOUND
echo  Instalando componentes en %INSTALL_DIR%...
copy /Y "!RUNNER_JAR!" "%JAR_DST%" >nul 2>&1
if errorlevel 1 (
    echo.
    echo  [ERROR] No se pudieron copiar los componentes.
    echo  Verifica que tienes permisos de escritura en: %INSTALL_DIR%
    echo.
    pause & exit /b 1
)

:JAR_READY
echo  [OK] Componentes del Runner listos.

REM ── Paso 3: Configurar servicio de inicio automatico ─────────────────────────
echo  [3/4] Configurando inicio automatico...

REM Identificador unico del equipo
for /f "usebackq tokens=*" %%h in (`hostname`) do set "HOST_NAME=%%h"
set "RUNNER_ID=win-!HOST_NAME!"

REM Generar script de arranque (java en una sola linea — sin continuaciones ^)
(
    echo @echo off
    echo chcp 65001 ^>nul
    echo setlocal
    echo set "RUNNER_ID=%RUNNER_ID%"
    echo set "BACKEND_URL=%BACKEND_URL%"
    echo set "RUNNER_TOKEN=%RUNNER_TOKEN%"
    echo set "LOG_FILE=%LOG_DIR%\runner.log"
    echo if exist "%%LOCALAPPDATA%%\Android\Sdk\platform-tools\adb.exe" set "PATH=%%LOCALAPPDATA%%\Android\Sdk\platform-tools;%%PATH%%"
    echo if exist "%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "PATH=%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools;%%PATH%%"
    echo if defined ANDROID_HOME set "PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%"
    echo :loop
    echo java -Dfile.encoding=UTF-8 -DBACKEND_URL=%%BACKEND_URL%% -DRUNNER_TOKEN=%%RUNNER_TOKEN%% -DRUNNER_ID=%%RUNNER_ID%% -DPOLL_INTERVAL_MS=30000 -jar "%JAR_DST%" ^>^>"%%LOG_FILE%%" 2^>^&1
    echo timeout /t 15 /nobreak ^>nul
    echo goto loop
) > "%INSTALL_DIR%\run-runner.bat"

REM Generar VBS para lanzar sin ventana (Chr(34) evita problemas de comillas en VBScript)
(
    echo Dim sh, q
    echo Set sh = CreateObject("WScript.Shell"^)
    echo q = Chr(34^)
    echo sh.Run "cmd /c " ^& q ^& "%INSTALL_DIR%\run-runner.bat" ^& q, 0, False
    echo Set sh = Nothing
) > "%INSTALL_DIR%\launcher.vbs"

if not exist "%INSTALL_DIR%\launcher.vbs" (
    echo.
    echo  [ERROR] No se pudo crear el servicio de inicio.
    echo  Verifica permisos en: %INSTALL_DIR%
    echo.
    pause & exit /b 1
)

REM Obtener ruta 8.3 para schtasks (evita problemas con espacios en rutas)
for %%F in ("%INSTALL_DIR%\launcher.vbs") do set "VBS_SHORT=%%~sF"

REM Registrar en Task Scheduler (3 intentos, de mas especifico a minimo)
schtasks /delete /tn "AutomationQA Runner" /f >nul 2>&1

schtasks /create /tn "AutomationQA Runner" /sc ONLOGON /ru "%USERNAME%" /tr "wscript.exe %VBS_SHORT%" /f /rl LIMITED /delay 0000:30 >nul 2>&1
if !ERRORLEVEL! equ 0 goto :SCHED_OK

schtasks /create /tn "AutomationQA Runner" /sc ONLOGON /ru "%USERNAME%" /tr "wscript.exe %VBS_SHORT%" /f /delay 0000:30 >nul 2>&1
if !ERRORLEVEL! equ 0 goto :SCHED_OK

schtasks /create /tn "AutomationQA Runner" /sc ONLOGON /ru "%USERNAME%" /tr "wscript.exe %VBS_SHORT%" /f >nul 2>&1

:SCHED_OK
REM Verificar que la tarea quedo registrada
schtasks /query /tn "AutomationQA Runner" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo  [OK] Servicio de inicio automatico configurado.
) else (
    REM Fallback: carpeta Startup del usuario (genera wrapper que apunta al VBS original)
    echo  Configurando metodo alternativo de inicio...
    set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    (
        echo Dim sh
        echo Set sh = CreateObject("WScript.Shell"^)
        echo sh.Run "wscript.exe ""%INSTALL_DIR%\launcher.vbs""", 0, False
        echo Set sh = Nothing
    ) > "!STARTUP_DIR!\AutomationQARunner.vbs"
    if exist "!STARTUP_DIR!\AutomationQARunner.vbs" (
        echo  [OK] Inicio automatico configurado (metodo alternativo).
    ) else (
        echo.
        echo  [ERROR] No se pudo configurar el inicio automatico.
        echo  El Runner se instalo pero deberas iniciarlo manualmente.
        echo  Ruta: %INSTALL_DIR%\launcher.vbs
        echo.
    )
)

REM ── Paso 4: Finalizar ─────────────────────────────────────────────────────────
echo.
echo  [4/4] Instalacion completada.
echo.
echo  ╔═══════════════════════════════════════════════════════════════╗
echo  ║   Automation QA Runner instalado correctamente              ║
echo  ║                                                               ║
echo  ║   El Runner se conectara automaticamente al iniciar sesion. ║
echo  ║   Aparecera en el Dashboard en aproximadamente 15 segundos. ║
echo  ╚═══════════════════════════════════════════════════════════════╝
echo.

set /p START_NOW=  Iniciar el Runner ahora mismo? (S/n):
if "!START_NOW!"=="" set START_NOW=S
if /i "!START_NOW!"=="n" goto :DONE

if not exist "%INSTALL_DIR%\launcher.vbs" (
    echo  [ERROR] No se encontro el servicio de inicio.
) else (
    echo  Iniciando el Runner en segundo plano...
    wscript.exe "%INSTALL_DIR%\launcher.vbs"
    echo  [OK] Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
)

:DONE
echo.
pause
endlocal
