@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ============================================================================
REM  Automation QA Runner - Instalador Windows v5.0
REM
REM  CAMBIOS v5.0:
REM  - launcher.vbs y run-runner.bat generados via PowerShell (sin ECHO blocks)
REM    para evitar corrupcion por codepage UTF-8.
REM  - VBS usa sh.Run Chr(34) & "<ruta>" & Chr(34) sin cmd /c.
REM  - Tarea programada via Register-ScheduledTask (sin rutas 8.3 ni %%~sF).
REM  - Validacion completa al final: no muestra exito si falla algo.
REM  - Logs detallados en cada paso.
REM ============================================================================

set "BACKEND_URL=https://qautomation-production.up.railway.app"
set "RUNNER_TOKEN=runner-local-token"
set "TASK_NAME=AutomationQA Runner"

echo.
echo  +===============================================================+
echo  ^|   Automation QA Runner - Instalacion v5.0                   ^|
echo  +===============================================================+
echo.

REM -- Validar sesion de usuario ----------------------------------------------
if not defined LOCALAPPDATA (
    echo  [ERROR] LOCALAPPDATA no definido.
    echo  Ejecuta el instalador con una cuenta de usuario normal.
    pause & exit /b 1
)

set "INSTALL_DIR=%LOCALAPPDATA%\AutomationQA\runner"
set "LOG_DIR=%INSTALL_DIR%\logs"
set "JAR_DST=%INSTALL_DIR%\automationqa-runner.jar"
set "RUNNER_BAT=%INSTALL_DIR%\run-runner.bat"
set "LAUNCHER_VBS=%INSTALL_DIR%\launcher.vbs"
set "INSTALL_LOG=%INSTALL_DIR%\install.log"

REM -- Crear carpetas ---------------------------------------------------------
if not exist "%INSTALL_DIR%" (
    mkdir "%INSTALL_DIR%" >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo  [ERROR] No se pudo crear la carpeta de instalacion:
        echo  %INSTALL_DIR%
        pause & exit /b 1
    )
)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

REM -- Log inicial (overwrite) ------------------------------------------------
> "%INSTALL_LOG%" echo [%DATE% %TIME%] === AutomationQA Runner v5.0 - Inicio instalacion ===
>> "%INSTALL_LOG%" echo [%TIME%] INSTALL_DIR=%INSTALL_DIR%
>> "%INSTALL_LOG%" echo [%TIME%] USERNAME=%USERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] COMPUTERNAME=%COMPUTERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] LOCALAPPDATA=%LOCALAPPDATA%

REM ===========================================================================
REM  [1/5] JAVA
REM ===========================================================================
echo  [1/5] Verificando entorno Java...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 1: verificando Java...

java -version >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo.
    echo  [ERROR] Java no esta instalado.
    echo  Descarga la version gratuita desde: https://adoptium.net
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: Java no encontrado
    start "" https://adoptium.net
    pause & exit /b 1
)

for /f "tokens=* usebackq" %%v in (`java -version 2^>^&1`) do (
    set "JAVA_VER=%%v"
    goto :java_ver_done
)
:java_ver_done
echo  [OK] Java: !JAVA_VER!
>> "%INSTALL_LOG%" echo [%TIME%] OK: Java - !JAVA_VER!

REM ===========================================================================
REM  [2/5] JAR
REM ===========================================================================
echo  [2/5] Verificando componentes del Runner...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 2: verificando JAR...

if exist "%JAR_DST%" (
    echo  [OK] Runner ya instalado - actualizando configuracion.
    >> "%INSTALL_LOG%" echo [%TIME%] OK: JAR existente en %JAR_DST%
    goto :JAR_READY
)

for %%P in (
    "%~dp0automationqa-runner.jar"
    "%~dp0cinepolis-runner.jar"
    "%~dp0runner\automationqa-runner.jar"
) do (
    if exist %%P (
        echo  Copiando runner desde paquete de instalacion...
        copy /Y %%P "%JAR_DST%" >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            >> "%INSTALL_LOG%" echo [%TIME%] OK: JAR copiado desde %%~fP
            goto :JAR_READY
        )
    )
)

echo  Descargando runner desde servidor...
>> "%INSTALL_LOG%" echo [%TIME%] Descargando JAR: %BACKEND_URL%/api/runner/download/jar
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { Invoke-WebRequest -Uri '%BACKEND_URL%/api/runner/download/jar' -OutFile '%JAR_DST%' -UseBasicParsing -TimeoutSec 120; Write-Host 'Descarga OK' } catch { Write-Host ('Error: ' + $_.Exception.Message); exit 1 }" >>%INSTALL_LOG% 2>&1

if !ERRORLEVEL! neq 0 (
    echo.
    echo  [ERROR] No se pudo descargar el runner.
    echo  Verifica la conexion a internet y vuelve a intentarlo.
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: descarga del JAR fallo
    pause & exit /b 1
)

:JAR_READY
if not exist "%JAR_DST%" (
    echo.
    echo  [ERROR] No se descargo correctamente el runner.
    echo  Ruta esperada: %JAR_DST%
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: JAR no existe tras todos los intentos
    pause & exit /b 1
)
echo  [OK] automationqa-runner.jar verificado.
>> "%INSTALL_LOG%" echo [%TIME%] OK: JAR verificado en %JAR_DST%

REM ===========================================================================
REM  [3/5] SCRIPTS DE ARRANQUE
REM ===========================================================================
echo  [3/5] Creando scripts de arranque...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 3: creando scripts...

REM -- RUNNER_ID --------------------------------------------------------------
for /f "tokens=* usebackq" %%h in (`hostname`) do set "HOST_NAME=%%h"
set "RUNNER_ID=win-!HOST_NAME!"
>> "%INSTALL_LOG%" echo [%TIME%] RUNNER_ID=!RUNNER_ID!

REM ---------------------------------------------------------------------------
REM  run-runner.bat via PowerShell (garantiza codificacion ASCII sin BOM)
REM ---------------------------------------------------------------------------
>> "%INSTALL_LOG%" echo [%TIME%] Creando run-runner.bat via PowerShell...

set "PS1=%TEMP%\qa_bat_%RANDOM%.ps1"
> "%PS1%" echo $dst = '!RUNNER_BAT!'
>> "%PS1%" echo $jar = '!JAR_DST!'
>> "%PS1%" echo $log = '!LOG_DIR!\runner.log'
>> "%PS1%" echo $lines = @(
>> "%PS1%" echo     '@echo off',
>> "%PS1%" echo     'setlocal',
>> "%PS1%" echo     'if exist "%%LOCALAPPDATA%%\Android\Sdk\platform-tools\adb.exe" set "PATH=%%LOCALAPPDATA%%\Android\Sdk\platform-tools;%%PATH%%"',
>> "%PS1%" echo     'if exist "%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "PATH=%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools;%%PATH%%"',
>> "%PS1%" echo     'if defined ANDROID_HOME set "PATH=%%ANDROID_HOME%%\platform-tools;%%PATH%%"',
>> "%PS1%" echo     ':loop',
>> "%PS1%" echo     ('java -Dfile.encoding=UTF-8 -DBACKEND_URL=!BACKEND_URL! -DRUNNER_TOKEN=!RUNNER_TOKEN! -DRUNNER_ID=!RUNNER_ID! -DPOLL_INTERVAL_MS=30000 -jar "' + $jar + '" ^>^>"' + $log + '" 2^>^&1'),
>> "%PS1%" echo     'timeout /t 15 /nobreak ^>nul',
>> "%PS1%" echo     'goto loop'
>> "%PS1%" echo )
>> "%PS1%" echo [IO.File]::WriteAllLines($dst, $lines, [Text.Encoding]::ASCII)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" >>"%INSTALL_LOG%" 2>&1
del "%PS1%" >nul 2>&1

if not exist "%RUNNER_BAT%" (
    echo  [ERROR] No se pudo crear run-runner.bat
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: run-runner.bat no creado en %RUNNER_BAT%
    pause & exit /b 1
)
>> "%INSTALL_LOG%" echo [%TIME%] OK: run-runner.bat creado en %RUNNER_BAT%

REM ---------------------------------------------------------------------------
REM  launcher.vbs via PowerShell - plantilla de 2 lineas sin ECHO blocks
REM  Contenido resultante:
REM    Set sh = CreateObject("WScript.Shell")
REM    sh.Run Chr(34) & "<RUNNER_BAT>" & Chr(34), 0, False
REM ---------------------------------------------------------------------------
>> "%INSTALL_LOG%" echo [%TIME%] Creando launcher.vbs via PowerShell...

set "PS1=%TEMP%\qa_vbs_%RANDOM%.ps1"
> "%PS1%" echo $dst = '!LAUNCHER_VBS!'
>> "%PS1%" echo $bat = '!RUNNER_BAT!'
>> "%PS1%" echo $line1 = 'Set sh = CreateObject("WScript.Shell")'
>> "%PS1%" echo $line2 = 'sh.Run Chr(34) ^& "' + $bat + '" ^& Chr(34), 0, False'
>> "%PS1%" echo [IO.File]::WriteAllLines($dst, @($line1, $line2), [Text.Encoding]::ASCII)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" >>"%INSTALL_LOG%" 2>&1
del "%PS1%" >nul 2>&1

if not exist "%LAUNCHER_VBS%" (
    echo  [ERROR] No se pudo crear launcher.vbs
    echo  Ruta esperada: %LAUNCHER_VBS%
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: launcher.vbs no creado en %LAUNCHER_VBS%
    pause & exit /b 1
)
echo  [OK] Scripts de arranque creados.
>> "%INSTALL_LOG%" echo [%TIME%] OK: launcher.vbs creado en %LAUNCHER_VBS%

REM ===========================================================================
REM  [4/5] TAREA PROGRAMADA
REM ===========================================================================
echo  [4/5] Configurando inicio automatico...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 4: configurando tarea programada...

REM -- Eliminar tarea previa --------------------------------------------------
schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1
>> "%INSTALL_LOG%" echo [%TIME%] Tarea previa eliminada (si existia)

REM ---------------------------------------------------------------------------
REM  Crear tarea via Register-ScheduledTask (maneja rutas con espacios sin
REM  necesidad de rutas 8.3 ni escaping manual de schtasks /tr)
REM ---------------------------------------------------------------------------
>> "%INSTALL_LOG%" echo [%TIME%] Intentando Register-ScheduledTask...

set "PS1=%TEMP%\qa_task_%RANDOM%.ps1"
> "%PS1%" echo $vbs  = '!LAUNCHER_VBS!'
>> "%PS1%" echo $user = $env:USERNAME
>> "%PS1%" echo $action   = New-ScheduledTaskAction -Execute 'wscript.exe' -Argument ('"' + $vbs + '"')
>> "%PS1%" echo $trigger  = New-ScheduledTaskTrigger -AtLogOn -User $user
>> "%PS1%" echo $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit 0
>> "%PS1%" echo $result   = Register-ScheduledTask -TaskName 'AutomationQA Runner' -Action $action -Trigger $trigger -Settings $settings -Force -ErrorAction Stop
>> "%PS1%" echo Write-Host ('Tarea creada: ' + $result.TaskName + ' Estado: ' + $result.State)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" >>"%INSTALL_LOG%" 2>&1
set "REG_TASK_ERR=!ERRORLEVEL!"
del "%PS1%" >nul 2>&1

if !REG_TASK_ERR! neq 0 (
    >> "%INSTALL_LOG%" echo [%TIME%] Register-ScheduledTask fallo (!REG_TASK_ERR!^) - usando schtasks como fallback
    echo  Intentando metodo alternativo de tarea programada...
    schtasks /create /tn "%TASK_NAME%" /sc ONLOGON /ru "%USERNAME%" /tr "wscript.exe \"%LAUNCHER_VBS%\"" /f /rl LIMITED >>"%INSTALL_LOG%" 2>&1
    if !ERRORLEVEL! neq 0 (
        schtasks /create /tn "%TASK_NAME%" /sc ONLOGON /ru "%USERNAME%" /tr "wscript.exe \"%LAUNCHER_VBS%\"" /f >>"%INSTALL_LOG%" 2>&1
        >> "%INSTALL_LOG%" echo [%TIME%] schtasks fallback resultado: !ERRORLEVEL!
    )
)

REM -- Verificar que la tarea existe -----------------------------------------
>> "%INSTALL_LOG%" echo [%TIME%] Verificando tarea con schtasks /query...
schtasks /query /tn "%TASK_NAME%" >>"%INSTALL_LOG%" 2>&1
if !ERRORLEVEL! neq 0 (
    echo  [AVISO] Tarea programada no registrada - configurando inicio via Startup...
    >> "%INSTALL_LOG%" echo [%TIME%] AVISO: tarea no encontrada, usando carpeta Startup

    set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    copy /Y "%LAUNCHER_VBS%" "!STARTUP!\AutomationQARunner.vbs" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo  [OK] Inicio automatico configurado via carpeta Startup.
        >> "%INSTALL_LOG%" echo [%TIME%] OK: launcher.vbs copiado a Startup
    ) else (
        echo.
        echo  [ERROR] No se pudo configurar el inicio automatico.
        echo.
        >> "%INSTALL_LOG%" echo [%TIME%] ERROR: Startup fallback fallo tambien
        echo  Revisa el log: %INSTALL_LOG%
        pause & exit /b 1
    )
) else (
    echo  [OK] Tarea programada registrada correctamente.
    >> "%INSTALL_LOG%" echo [%TIME%] OK: tarea "%TASK_NAME%" verificada con schtasks /query
)

REM ===========================================================================
REM  [5/5] INICIO DEL RUNNER
REM ===========================================================================
echo.
echo  [5/5] Configuracion completada.
echo.
>> "%INSTALL_LOG%" echo [%TIME%] Paso 5: inicio del runner

set /p START_NOW=  Iniciar el Runner ahora mismo? (S/n):
if "!START_NOW!"=="" set "START_NOW=S"
if /i "!START_NOW!"=="n" goto :SKIP_START

REM -- Pre-flight ------------------------------------------------------------
>> "%INSTALL_LOG%" echo [%TIME%] --- Pre-flight antes de iniciar ---
set "PREFLIGHT_OK=1"

if not exist "%JAR_DST%" (
    echo  [ERROR] No se encuentra el runner: %JAR_DST%
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR pre-flight: JAR no existe
    set "PREFLIGHT_OK=0"
)
if not exist "%LAUNCHER_VBS%" (
    echo  [ERROR] No se encuentra launcher.vbs: %LAUNCHER_VBS%
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR pre-flight: launcher.vbs no existe
    set "PREFLIGHT_OK=0"
)
if not exist "%RUNNER_BAT%" (
    echo  [ERROR] No se encuentra run-runner.bat: %RUNNER_BAT%
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR pre-flight: run-runner.bat no existe
    set "PREFLIGHT_OK=0"
)
if "!PREFLIGHT_OK!"=="0" goto :FAIL

REM -- Iniciar runner --------------------------------------------------------
echo  Iniciando Runner en segundo plano...
>> "%INSTALL_LOG%" echo [%TIME%] Iniciando: wscript.exe "%LAUNCHER_VBS%"
wscript.exe "%LAUNCHER_VBS%"
>> "%INSTALL_LOG%" echo [%TIME%] wscript.exe lanzado

REM -- Verificar proceso Java ------------------------------------------------
echo  Esperando arranque del proceso (5 segundos)...
timeout /t 5 /nobreak >nul

tasklist /fi "imagename eq java.exe" 2>nul | find /i "java.exe" >nul 2>&1
set "JAVA_PROC=!ERRORLEVEL!"
if !JAVA_PROC! neq 0 (
    tasklist /fi "imagename eq javaw.exe" 2>nul | find /i "javaw.exe" >nul 2>&1
    set "JAVA_PROC=!ERRORLEVEL!"
)

if !JAVA_PROC! equ 0 (
    echo  [OK] Proceso Java ejecutandose.
    >> "%INSTALL_LOG%" echo [%TIME%] OK: proceso java detectado en tasklist
) else (
    echo  [AVISO] Proceso Java no detectado aun.
    >> "%INSTALL_LOG%" echo [%TIME%] AVISO: java.exe/javaw.exe no visible a los 5s
)

REM -- Verificar heartbeat en la API -----------------------------------------
echo  Verificando registro en el servidor (espera 25 segundos)...
timeout /t 25 /nobreak >nul

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$rid = '!RUNNER_ID!'; try { $runners = Invoke-RestMethod -Uri '%BACKEND_URL%/api/runners' -UseBasicParsing -TimeoutSec 30; $json = $runners | ConvertTo-Json -Compress -Depth 5; if ($json -like ('*' + $rid + '*')) { Write-Host ('[OK] Runner registrado: ' + $rid); exit 0 } else { Write-Host ('[INFO] ' + $runners.Count + ' runner(s) activos. Este runner aun no visible.'); exit 1 } } catch { Write-Host ('[AVISO] No se pudo verificar: ' + $_.Exception.Message); exit 2 }"
set "HEARTBEAT=!ERRORLEVEL!"
>> "%INSTALL_LOG%" echo [%TIME%] Heartbeat /api/runners: !HEARTBEAT!

if !HEARTBEAT! equ 0 (
    echo  [OK] Runner registrado en el servidor.
    >> "%INSTALL_LOG%" echo [%TIME%] OK: runner visible en /api/runners
) else if !HEARTBEAT! equ 1 (
    echo  [AVISO] El runner aun no aparece en el servidor.
    echo  Puede tardar hasta 60 segundos adicionales en registrarse.
    >> "%INSTALL_LOG%" echo [%TIME%] AVISO: runner no visible en /api/runners
) else (
    echo  [AVISO] No se pudo verificar la conexion con el servidor.
    >> "%INSTALL_LOG%" echo [%TIME%] AVISO: error al consultar /api/runners
)
goto :SUMMARY

:SKIP_START
echo  El Runner se iniciara automaticamente al iniciar sesion de Windows.
>> "%INSTALL_LOG%" echo [%TIME%] Usuario eligio no iniciar ahora

REM ===========================================================================
REM  RESUMEN Y VALIDACION FINAL
REM ===========================================================================
:SUMMARY
echo.
>> "%INSTALL_LOG%" echo [%TIME%] --- Validacion final ---

set "ALL_OK=1"
set "FAIL_REASON="

if not exist "%LAUNCHER_VBS%" (
    echo  [FAIL] launcher.vbs no encontrado: %LAUNCHER_VBS%
    >> "%INSTALL_LOG%" echo [%TIME%] FAIL: launcher.vbs no existe
    set "ALL_OK=0"
    set "FAIL_REASON=launcher.vbs ausente"
)

if not exist "%JAR_DST%" (
    echo  [FAIL] automationqa-runner.jar no encontrado: %JAR_DST%
    >> "%INSTALL_LOG%" echo [%TIME%] FAIL: JAR no existe
    set "ALL_OK=0"
    set "FAIL_REASON=jar ausente"
)

schtasks /query /tn "%TASK_NAME%" >nul 2>&1
if !ERRORLEVEL! neq 0 (
    set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    if not exist "!STARTUP!\AutomationQARunner.vbs" (
        echo  [FAIL] Tarea programada y Startup sin configurar.
        >> "%INSTALL_LOG%" echo [%TIME%] FAIL: ni tarea ni Startup configurados
        set "ALL_OK=0"
        set "FAIL_REASON=sin inicio automatico"
    )
)

if "!ALL_OK!"=="0" goto :FAIL

echo  +===============================================================+
echo  ^|   Automation QA Runner instalado correctamente              ^|
echo  ^|                                                               ^|
echo  ^|   El Runner se conectara al iniciar sesion de Windows.      ^|
echo  ^|   Aparecera en el Dashboard en aproximadamente 30 segundos. ^|
echo  +===============================================================+
echo.
echo  Archivos instalados:
echo    Runner JAR:  %JAR_DST%
echo    Launcher:    %LAUNCHER_VBS%
echo    Log:         %INSTALL_LOG%
echo.
>> "%INSTALL_LOG%" echo [%TIME%] === INSTALACION COMPLETADA EXITOSAMENTE ===
goto :DONE

:FAIL
echo.
echo  +===============================================================+
echo  ^|   [ERROR] La instalacion no se completo correctamente       ^|
echo  +===============================================================+
echo.
echo  Revisa el log para diagnostico detallado:
echo    %INSTALL_LOG%
echo.
>> "%INSTALL_LOG%" echo [%TIME%] === INSTALACION FALLO ===
pause & exit /b 1

:DONE
echo  Log de instalacion: %INSTALL_LOG%
echo.
pause
endlocal
