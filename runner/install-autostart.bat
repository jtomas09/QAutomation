@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ══════════════════════════════════════════════════════════════════════════════
REM  Automation QA Runner — Instalador Auto-Start (Windows 10/11)
REM  Registra el runner como Tarea Programada usando runner-launcher.vbs
REM  que ya existe en esta misma carpeta.
REM
REM  IMPORTANTE: Ejecutar desde la carpeta runner\ del proyecto.
REM  NO requiere privilegios de Administrador.
REM ══════════════════════════════════════════════════════════════════════════════

REM ── Rutas base — siempre relativas a este script ──────────────────────────────
set "SCRIPT_DIR=%~dp0"
set "RUNNER_VBS=%SCRIPT_DIR%runner-launcher.vbs"
set "START_BAT=%SCRIPT_DIR%start-runner-auto.bat"
set "PROPS_FILE=%SCRIPT_DIR%runner.properties"
set "LOG_DIR=%SCRIPT_DIR%logs"
set "TASK_NAME=Automation QA Runner"

echo.
echo  ╔═══════════════════════════════════════════════════════════════════╗
echo  ║   Automation QA Runner — Instalador Auto-Start                  ║
echo  ║   Configura inicio automatico sin intervencion manual           ║
echo  ╚═══════════════════════════════════════════════════════════════════╝
echo.

REM ── PASO 1: Validar archivos del Runner ───────────────────────────────────────
echo  [1/4] Validando archivos del Runner...
echo.

if not exist "%RUNNER_VBS%" (
    echo  [ERROR] No se encontro el archivo:
    echo.
    echo         %RUNNER_VBS%
    echo.
    echo  Asegurate de ejecutar este script desde la carpeta runner\ del proyecto.
    echo  El archivo runner-launcher.vbs debe estar en la misma carpeta.
    echo.
    pause & exit /b 1
)
echo         runner-launcher.vbs  [OK]

if not exist "%START_BAT%" (
    echo  [WARN] No se encontro start-runner-auto.bat
    echo         El runner podria no iniciar correctamente.
)

if exist "%SCRIPT_DIR%target\cinepolis-runner.jar" (
    echo         cinepolis-runner.jar  [OK]
) else (
    where mvn >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo         cinepolis-runner.jar  [INFO] No encontrado — se compilara al primer inicio
    ) else (
        echo.
        echo  [AVISO] cinepolis-runner.jar no encontrado y Maven no esta en PATH.
        echo          El Runner no podra iniciarse hasta que el JAR este disponible.
        echo.
        echo          Opciones:
        echo            A^) Compila primero desde esta carpeta: mvn package -DskipTests
        echo            B^) Copia cinepolis-runner.jar a: %SCRIPT_DIR%target\
        echo.
        echo  Puedes continuar la instalacion y agregar el JAR despues.
        echo.
        set /p CONTINUAR=  Continuar de todas formas? (S/n):
        if "!CONTINUAR!"=="" set CONTINUAR=S
        if /i not "!CONTINUAR!"=="S" (
            echo  Instalacion cancelada.
            pause & exit /b 0
        )
    )
)

REM ── PASO 2: Verificar Java ────────────────────────────────────────────────────
echo.
echo  [2/4] Verificando Java...

java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [ERROR] Java 17+ no encontrado en el PATH del sistema.
    echo.
    echo          Descarga e instala Java desde: https://adoptium.net
    echo          Despues de instalar, vuelve a ejecutar este instalador.
    echo.
    pause & exit /b 1
)
echo         Java disponible  [OK]

REM ── PASO 3: Registrar Tarea Programada ───────────────────────────────────────
echo.
echo  [3/4] Registrando tarea programada (inicio de sesion)...

REM Obtener ruta 8.3 sin espacios para pasar a schtasks de forma segura
for %%F in ("%RUNNER_VBS%") do set "VBS_SHORT=%%~sF"

REM Eliminar tarea anterior si existe
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
    echo         Tarea anterior eliminada.
)

REM Crear carpeta de logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

REM Intento 1: con /rl LIMITED y /delay (Windows 10 build 1703+)
schtasks /create ^
    /tn "%TASK_NAME%" ^
    /tr "wscript.exe %VBS_SHORT%" ^
    /sc ONLOGON ^
    /ru "%USERNAME%" ^
    /f ^
    /rl LIMITED ^
    /delay 0000:30 >nul 2>&1
set "SCHED_OK=!ERRORLEVEL!"

REM Intento 2: sin /rl (compatibilidad ampliada)
if !SCHED_OK! neq 0 (
    schtasks /create ^
        /tn "%TASK_NAME%" ^
        /tr "wscript.exe %VBS_SHORT%" ^
        /sc ONLOGON ^
        /ru "%USERNAME%" ^
        /f ^
        /delay 0000:30 >nul 2>&1
    set "SCHED_OK=!ERRORLEVEL!"
)

REM Intento 3: sin delay (compatibilidad maxima)
if !SCHED_OK! neq 0 (
    schtasks /create ^
        /tn "%TASK_NAME%" ^
        /tr "wscript.exe %VBS_SHORT%" ^
        /sc ONLOGON ^
        /ru "%USERNAME%" ^
        /f >nul 2>&1
    set "SCHED_OK=!ERRORLEVEL!"
)

REM Verificar si la tarea quedo registrada
schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo         Tarea programada registrada  [OK]
    set "INSTALADO=tarea"
) else (
    echo  [AVISO] No se pudo registrar la tarea programada.
    echo          Instalando en carpeta Startup como alternativa...

    set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"

    REM Crear un VBS en Startup que lance runner-launcher.vbs desde su ubicacion original
    REM (NO copiar runner-launcher.vbs: perderia su referencia relativa a start-runner-auto.bat)
    (
        echo Dim sh
        echo Set sh = CreateObject("WScript.Shell"^)
        echo sh.Run "wscript.exe ""%RUNNER_VBS%""", 0, False
        echo Set sh = Nothing
    ) > "!STARTUP_DIR!\AutomationQARunner.vbs"

    if !ERRORLEVEL! equ 0 (
        echo         Instalado en carpeta Startup  [OK]
        set "INSTALADO=startup"
    ) else (
        echo.
        echo  [ERROR] No se pudo instalar el inicio automatico.
        echo          Verifica que tienes permisos de escritura en:
        echo          !STARTUP_DIR!
        echo.
        pause & exit /b 1
    )
)

REM ── PASO 4: Resultado ─────────────────────────────────────────────────────────
echo.
echo  [4/4] Instalacion completada.
echo.
echo  ════════════════════════════════════════════════════════════════════
echo   Automation QA Runner configurado correctamente.
echo.
echo   El Runner arrancara AUTOMATICAMENTE al iniciar sesion en Windows.
echo   No necesitas ejecutar ningun script manualmente.
echo.
echo   Archivos:
echo     Launcher: %RUNNER_VBS%
echo     Logs:     %LOG_DIR%\runner.log
echo  ════════════════════════════════════════════════════════════════════
echo.

set /p RUN_NOW=  Iniciar el Runner ahora mismo? (S/n):
if "!RUN_NOW!"=="" set RUN_NOW=S
if /i "!RUN_NOW!"=="S" (
    echo.
    if not exist "%RUNNER_VBS%" (
        echo  [ERROR] No se encuentra: %RUNNER_VBS%
    ) else (
        echo  Iniciando Runner en segundo plano...
        wscript.exe "%RUNNER_VBS%"
        echo  [OK]  Runner iniciado. Aparecera en el Dashboard en ~15 segundos.
    )
)

echo.
pause
endlocal
