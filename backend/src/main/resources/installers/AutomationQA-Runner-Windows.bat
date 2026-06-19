@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

REM ============================================================================
REM  Automation QA Runner - Instalador Windows v7.0
REM
REM  CAMBIOS v7.0:
REM  - ADB embebido: descarga Android Platform Tools en %INSTALL_DIR%\platform-tools\
REM    durante la instalacion. El Agent NO depende de Android Studio, ANDROID_HOME ni PATH.
REM  - Pasos renumerados a 6 (nuevo paso [3/6] para platform-tools).
REM
REM  CAMBIOS v6.0:
REM  - Validacion de Java 17+: descarga JRE 17 portatil si la version es menor.
REM  - run-runner.bat generado con $variable += (sin @() arrays — evita PS ParserError).
REM  - Logs de exit code y stderr de cada invocacion PowerShell.
REM ============================================================================

set "BACKEND_URL=https://qautomation-production.up.railway.app"
set "RUNNER_TOKEN=runner-local-token"
set "TASK_NAME=AutomationQA Runner"

echo.
echo  +===============================================================+
echo  ^|   Automation QA Runner - Instalacion v7.0                   ^|
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
> "%INSTALL_LOG%" echo [%DATE% %TIME%] === AutomationQA Runner v7.0 - Inicio instalacion ===
>> "%INSTALL_LOG%" echo [%TIME%] INSTALL_DIR=%INSTALL_DIR%
>> "%INSTALL_LOG%" echo [%TIME%] USERNAME=%USERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] COMPUTERNAME=%COMPUTERNAME%
>> "%INSTALL_LOG%" echo [%TIME%] LOCALAPPDATA=%LOCALAPPDATA%

REM ===========================================================================
REM  [1/5] JAVA (requiere 17+)
REM ===========================================================================
echo  [1/6] Verificando entorno Java...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 1: verificando Java...

set "JAVA_BIN=java"
set "JRE_DIR=%INSTALL_DIR%\jre17"

REM -- Try Java from PATH first ----------------------------------------------
java -version >nul 2>&1
if !ERRORLEVEL! equ 0 goto :check_java_ver

REM -- Try embedded JRE from a previous install ------------------------------
if exist "!JRE_DIR!\bin\java.exe" (
    set "JAVA_BIN=!JRE_DIR!\bin\java.exe"
    goto :java_ready
)

REM -- Auto-download portable JRE 17 ----------------------------------------
:DOWNLOAD_JRE_17
echo.
echo  [INFO] Java no encontrado o version insuficiente. Descargando JRE 17 portatil...
echo  [INFO] Esto puede tardar unos minutos segun la velocidad de Internet.
echo.
>> "%INSTALL_LOG%" echo [%TIME%] INFO: descargando JRE 17 portatil...

set "JRE_ZIP=%TEMP%\qa_jre17_%RANDOM%.zip"
set "JRE_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { Invoke-WebRequest -Uri '%JRE_URL%' -OutFile '!JRE_ZIP!' -UseBasicParsing -TimeoutSec 300; Write-Host 'Descarga OK' } catch { Write-Host ('ERROR: ' + $_.Exception.Message); exit 1 }" ^
    >>"%INSTALL_LOG%" 2>&1
if !ERRORLEVEL! neq 0 (
    echo.
    echo  [ERROR] No se pudo descargar el JRE 17.
    echo  Instala Java manualmente desde: https://adoptium.net
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: fallo descarga JRE
    start "" https://adoptium.net
    pause & exit /b 1
)

echo  [INFO] Extrayendo JRE...
if not exist "!JRE_DIR!" mkdir "!JRE_DIR!" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$zip='!JRE_ZIP!'; $dst='!JRE_DIR!'; Expand-Archive -Path $zip -DestinationPath $dst -Force; $inner=Get-ChildItem $dst -Directory | Select-Object -First 1; if ($inner -and $inner.FullName -ne $dst) { Get-ChildItem $inner.FullName | Move-Item -Destination $dst -Force; Remove-Item $inner.FullName -Recurse -Force -ErrorAction SilentlyContinue }" ^
    >>"%INSTALL_LOG%" 2>&1
del "!JRE_ZIP!" >nul 2>&1

if not exist "!JRE_DIR!\bin\java.exe" (
    echo  [ERROR] La extraccion del JRE fallo.
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: JRE extraido pero java.exe no encontrado
    pause & exit /b 1
)

set "JAVA_BIN=!JRE_DIR!\bin\java.exe"
echo  [OK] JRE 17 portatil instalado: !JRE_DIR!
>> "%INSTALL_LOG%" echo [%TIME%] OK: JRE 17 portatil listo en !JRE_DIR!
goto :java_ready

REM -- Validate Java version from PATH is >= 17 ------------------------------
:check_java_ver
set "PS_VER=%TEMP%\qa_ver_%RANDOM%.ps1"
>  "!PS_VER!" echo $v = (java -version 2^>^&1) ^| Select-Object -First 1
>> "!PS_VER!" echo if     ($v -match '"1\.(\d+)') { [int]$Matches[1] }
>> "!PS_VER!" echo elseif ($v -match '"(\d+)')     { [int]$Matches[1] }
>> "!PS_VER!" echo else                             { 0 }
set "JAVA_MAJOR=0"
for /f "usebackq" %%n in (`powershell -NoProfile -ExecutionPolicy Bypass -File "!PS_VER!" 2^>nul`) do set "JAVA_MAJOR=%%n"
del "!PS_VER!" >nul 2>&1
>> "%INSTALL_LOG%" echo [%TIME%] Java version detectada: !JAVA_MAJOR!
if !JAVA_MAJOR! lss 17 (
    echo  [INFO] Java !JAVA_MAJOR! detectado ^(se requiere 17+^). Instalando JRE 17 portatil...
    >> "%INSTALL_LOG%" echo [%TIME%] INFO: Java !JAVA_MAJOR! menor a 17, descargando JRE 17
    if exist "!JRE_DIR!\bin\java.exe" (
        set "JAVA_BIN=!JRE_DIR!\bin\java.exe"
        echo  [OK] JRE 17 portatil ya disponible.
        >> "%INSTALL_LOG%" echo [%TIME%] OK: reutilizando JRE 17 en !JRE_DIR!
        goto :java_ready
    )
    goto :DOWNLOAD_JRE_17
)

:java_ready
for /f "tokens=* usebackq" %%v in (`"!JAVA_BIN!" -version 2^>^&1`) do (
    set "JAVA_VER=%%v"
    goto :java_ver_done
)
:java_ver_done
echo  [OK] Java: !JAVA_VER!
echo  [OK] Ejecutable: !JAVA_BIN!
>> "%INSTALL_LOG%" echo [%TIME%] OK: Java - !JAVA_VER!
REM ===========================================================================
REM  [2/5] JAR
REM ===========================================================================
echo  [2/6] Verificando componentes del Runner...
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
set "JAR_PS1=%TEMP%\qa_dl_jar_%RANDOM%.ps1"
set "JAR_OUT=%TEMP%\qa_jar_out_%RANDOM%.txt"
>  "!JAR_PS1!" echo [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
>> "!JAR_PS1!" echo $jarUrl = "%BACKEND_URL%/api/runner/download/jar"
>> "!JAR_PS1!" echo $jarDst = "%JAR_DST%"
>> "!JAR_PS1!" echo try {
>> "!JAR_PS1!" echo     Invoke-WebRequest -Uri $jarUrl -OutFile $jarDst -UseBasicParsing -TimeoutSec 120
>> "!JAR_PS1!" echo     Write-Host "Descarga JAR OK"
>> "!JAR_PS1!" echo } catch {
>> "!JAR_PS1!" echo     Write-Host ("Error JAR: " + $_.Exception.Message)
>> "!JAR_PS1!" echo     exit 1
>> "!JAR_PS1!" echo }
powershell -NoProfile -ExecutionPolicy Bypass -File "!JAR_PS1!" > "!JAR_OUT!" 2>&1
set "JAR_DL_ERR=!ERRORLEVEL!"
type "!JAR_OUT!"
type "!JAR_OUT!" >> "%INSTALL_LOG%"
del "!JAR_OUT!" >nul 2>&1
del "!JAR_PS1!" >nul 2>&1

if !JAR_DL_ERR! neq 0 (
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
REM  [3/6] ANDROID PLATFORM TOOLS (ADB embebido)
REM  Target: %INSTALL_DIR%\platform-tools\adb.exe
REM  Fuente: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
REM ===========================================================================
echo  [3/6] Instalando Android Platform Tools ^(ADB embebido^)...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 3: platform-tools...

set "PT_DIR=%INSTALL_DIR%\platform-tools"
set "ADB_EXE=!PT_DIR!\adb.exe"
set "PT_URL=https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

>> "%INSTALL_LOG%" echo [%TIME%] ADB Path esperado: !ADB_EXE!

REM -- Ya instalado y funcional -----------------------------------------------
if exist "!ADB_EXE!" (
    echo  [OK] ADB embebido ya instalado: !PT_DIR!
    >> "%INSTALL_LOG%" echo [%TIME%] OK: adb.exe ya disponible
    goto :PT_VALIDATE
)

REM ---------------------------------------------------------------------------
REM  Descarga via script PS1 (evita TerminatorExpectedAtEndOfString cuando
REM  rutas contienen apostrofes — ej. C:\Users\John's PC\...)
REM ---------------------------------------------------------------------------
set "PT_ZIP=%TEMP%\qa_pt_%RANDOM%.zip"
set "PT_PS1=%TEMP%\qa_dl_pt_%RANDOM%.ps1"
set "PT_DOWNLOAD_OK=0"

>> "%INSTALL_LOG%" echo [%TIME%] PT_ZIP destino: !PT_ZIP!
>> "%INSTALL_LOG%" echo [%TIME%] PT_PS1 script:  !PT_PS1!

REM -- Escribir script PS1 de descarga (sin comillas simples en rutas) --------
>  "!PT_PS1!" echo $downloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
>> "!PT_PS1!" echo $backendUrl  = "%BACKEND_URL%/api/runner/download/platform-tools/windows"
>> "!PT_PS1!" echo $zipPath     = "%PT_ZIP%"
>> "!PT_PS1!" echo $minBytes    = 5000000
>> "!PT_PS1!" echo $downloaded  = $false
>> "!PT_PS1!" echo [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
>> "!PT_PS1!" echo Write-Host "URL: $downloadUrl"
>> "!PT_PS1!" echo Write-Host "Destino: $zipPath"
>> "!PT_PS1!" echo.
>> "!PT_PS1!" echo # Intento 1: Invoke-WebRequest directo
>> "!PT_PS1!" echo try {
>> "!PT_PS1!" echo     Write-Host "Intento 1/3: Invoke-WebRequest..."
>> "!PT_PS1!" echo     Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -UseBasicParsing -TimeoutSec 300
>> "!PT_PS1!" echo     $downloaded = $true
>> "!PT_PS1!" echo     Write-Host "OK intento 1"
>> "!PT_PS1!" echo } catch { Write-Host "IWR fallo: $($_.Exception.Message)" }
>> "!PT_PS1!" echo.
>> "!PT_PS1!" echo # Intento 2: WebClient
>> "!PT_PS1!" echo if (-not $downloaded) {
>> "!PT_PS1!" echo     try {
>> "!PT_PS1!" echo         Write-Host "Intento 2/3: WebClient..."
>> "!PT_PS1!" echo         (New-Object System.Net.WebClient).DownloadFile($downloadUrl, $zipPath)
>> "!PT_PS1!" echo         $downloaded = $true
>> "!PT_PS1!" echo         Write-Host "OK intento 2"
>> "!PT_PS1!" echo     } catch { Write-Host "WebClient fallo: $($_.Exception.Message)" }
>> "!PT_PS1!" echo }
>> "!PT_PS1!" echo.
>> "!PT_PS1!" echo # Intento 3: Backend proxy
>> "!PT_PS1!" echo if (-not $downloaded) {
>> "!PT_PS1!" echo     try {
>> "!PT_PS1!" echo         Write-Host "Intento 3/3: proxy $backendUrl..."
>> "!PT_PS1!" echo         Invoke-WebRequest -Uri $backendUrl -OutFile $zipPath -UseBasicParsing -TimeoutSec 300
>> "!PT_PS1!" echo         $downloaded = $true
>> "!PT_PS1!" echo         Write-Host "OK proxy IWR"
>> "!PT_PS1!" echo     } catch {
>> "!PT_PS1!" echo         try {
>> "!PT_PS1!" echo             (New-Object System.Net.WebClient).DownloadFile($backendUrl, $zipPath)
>> "!PT_PS1!" echo             $downloaded = $true
>> "!PT_PS1!" echo             Write-Host "OK proxy WebClient"
>> "!PT_PS1!" echo         } catch { Write-Host "Proxy fallo: $($_.Exception.Message)" }
>> "!PT_PS1!" echo     }
>> "!PT_PS1!" echo }
>> "!PT_PS1!" echo.
>> "!PT_PS1!" echo if (-not $downloaded) {
>> "!PT_PS1!" echo     Write-Host "ERROR FINAL: $($Error[0])"
>> "!PT_PS1!" echo     exit 1
>> "!PT_PS1!" echo }
>> "!PT_PS1!" echo $sz = (Get-Item $zipPath -ErrorAction SilentlyContinue).Length
>> "!PT_PS1!" echo Write-Host "Tamano ZIP: $sz bytes"
>> "!PT_PS1!" echo if (-not $sz -or $sz -lt $minBytes) {
>> "!PT_PS1!" echo     Write-Host "ERROR: ZIP invalido ($sz bytes, minimo $minBytes)"
>> "!PT_PS1!" echo     exit 1
>> "!PT_PS1!" echo }
>> "!PT_PS1!" echo Write-Host "Descarga OK: $([math]::Round($sz/1MB,1)) MB"

echo  Descargando Android Platform Tools (puede tardar 1-2 minutos)...
set "PT_OUT=%TEMP%\qa_pt_out_%RANDOM%.txt"
powershell -NoProfile -ExecutionPolicy Bypass -File "!PT_PS1!" > "!PT_OUT!" 2>&1
set "PT_DL_ERR=!ERRORLEVEL!"
type "!PT_OUT!"
type "!PT_OUT!" >> "%INSTALL_LOG%"
del "!PT_OUT!" >nul 2>&1
del "!PT_PS1!" >nul 2>&1
if !PT_DL_ERR! equ 0 set "PT_DOWNLOAD_OK=1"
>> "%INSTALL_LOG%" echo [%TIME%] Descarga platform-tools result: PT_DOWNLOAD_OK=!PT_DOWNLOAD_OK!

if "!PT_DOWNLOAD_OK!"=="0" (
    del "!PT_ZIP!" >nul 2>&1
    echo.
    echo  [ERROR] No se pudo descargar Android Platform Tools.
    echo  Sin ADB el Agent no puede detectar dispositivos Android.
    echo.
    echo  Ultimas lineas del log:
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: fallo descarga platform-tools en 3 intentos
    >> "%INSTALL_LOG%" echo [%TIME%] INSTALACION INCOMPLETA: adb.exe no disponible
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Content $env:INSTALL_LOG | Select-Object -Last 12 | ForEach-Object { Write-Host ('    ' + $_) }"
    echo.
    echo  Verifica la conexion a internet y vuelve a ejecutar el instalador.
    pause & exit /b 1
)

REM -- Extraccion -------------------------------------------------------------
echo  Extrayendo Platform Tools en: !INSTALL_DIR!
>> "%INSTALL_LOG%" echo [%TIME%] INFO: extrayendo !PT_ZIP! en !INSTALL_DIR!...

set "PT_EX_PS1=%TEMP%\qa_ex_pt_%RANDOM%.ps1"
>  "!PT_EX_PS1!" echo $zip = "%PT_ZIP%"
>> "!PT_EX_PS1!" echo $dst = "%INSTALL_DIR%"
>> "!PT_EX_PS1!" echo try {
>> "!PT_EX_PS1!" echo     Expand-Archive -Path $zip -DestinationPath $dst -Force
>> "!PT_EX_PS1!" echo     Write-Host "Extraccion OK"
>> "!PT_EX_PS1!" echo } catch {
>> "!PT_EX_PS1!" echo     Write-Host "ERROR extraccion: $($_.Exception.Message)"
>> "!PT_EX_PS1!" echo     exit 1
>> "!PT_EX_PS1!" echo }

set "PT_EX_OUT=%TEMP%\qa_pt_ex_out_%RANDOM%.txt"
powershell -NoProfile -ExecutionPolicy Bypass -File "!PT_EX_PS1!" > "!PT_EX_OUT!" 2>&1
set "PT_EXTRACT_ERR=!ERRORLEVEL!"
type "!PT_EX_OUT!"
type "!PT_EX_OUT!" >> "%INSTALL_LOG%"
del "!PT_EX_OUT!" >nul 2>&1
del "!PT_EX_PS1!" >nul 2>&1
del "!PT_ZIP!" >nul 2>&1
>> "%INSTALL_LOG%" echo [%TIME%] Extraccion exit code: !PT_EXTRACT_ERR!

if !PT_EXTRACT_ERR! neq 0 (
    echo  [ERROR] Fallo la extraccion del ZIP de platform-tools.
    echo  Revisa el log: %INSTALL_LOG%
    >> "%INSTALL_LOG%" echo [%TIME%] INSTALACION INCOMPLETA: extraccion fallida
    pause & exit /b 1
)

:PT_VALIDATE
>> "%INSTALL_LOG%" echo [%TIME%] Validando: !ADB_EXE!
if exist "!ADB_EXE!" (
    echo  [OK] ADB embebido instalado: !ADB_EXE!
    >> "%INSTALL_LOG%" echo [%TIME%] ADB Exists: true
    >> "%INSTALL_LOG%" echo [%TIME%] ADB Path: !ADB_EXE!
    set "PT_VER_PS1=%TEMP%\qa_ver_adb_%RANDOM%.ps1"
    >  "!PT_VER_PS1!" echo $adb = "%ADB_EXE%"
    >> "!PT_VER_PS1!" echo $v = (^& $adb version 2^>^&1) ^| Select-Object -First 1
    >> "!PT_VER_PS1!" echo Write-Host $v
    powershell -NoProfile -ExecutionPolicy Bypass -File "!PT_VER_PS1!" >>"%INSTALL_LOG%" 2>&1
    del "!PT_VER_PS1!" >nul 2>&1
) else (
    echo.
    echo  [ERROR] adb.exe NO encontrado en: !ADB_EXE!
    echo  La instalacion esta INCOMPLETA. El Agent no podra detectar dispositivos.
    echo  Revisa el log: %INSTALL_LOG%
    echo.
    >> "%INSTALL_LOG%" echo [%TIME%] ADB Exists: false
    >> "%INSTALL_LOG%" echo [%TIME%] INSTALACION INCOMPLETA: adb.exe ausente tras extraccion
    pause & exit /b 1
)

:PT_READY
>> "%INSTALL_LOG%" echo [%TIME%] Paso 3 platform-tools completado OK.

REM ===========================================================================
REM  [4/6] SCRIPTS DE ARRANQUE
REM ===========================================================================
echo  [4/6] Creando scripts de arranque...
>> "%INSTALL_LOG%" echo [%TIME%] Paso 4: creando scripts...

REM -- RUNNER_ID --------------------------------------------------------------
for /f "tokens=* usebackq" %%h in (`hostname`) do set "HOST_NAME=%%h"
set "RUNNER_ID=win-!HOST_NAME!"
>> "%INSTALL_LOG%" echo [%TIME%] RUNNER_ID=!RUNNER_ID!

REM ---------------------------------------------------------------------------
REM  run-runner.bat via PowerShell (garantiza codificacion ASCII sin BOM)
REM ---------------------------------------------------------------------------
>> "%INSTALL_LOG%" echo [%TIME%] Creando run-runner.bat via PowerShell...

set "PS1=%TEMP%\qa_bat_%RANDOM%.ps1"
>  "%PS1%" echo $dst     = '!RUNNER_BAT!'
>> "%PS1%" echo $jar     = '!JAR_DST!'
>> "%PS1%" echo $logFile = '!LOG_DIR!\runner.log'
>> "%PS1%" echo $javaBin = '!JAVA_BIN!'
>> "%PS1%" echo $crlf    = [string][char]13 + [string][char]10
>> "%PS1%" echo $bat     = '@echo off'  + $crlf
>> "%PS1%" echo $bat    += 'setlocal'   + $crlf
>> "%PS1%" echo $bat    += ':loop' + $crlf
>> "%PS1%" echo $bat    += ('"' + $javaBin + '" -Dfile.encoding=UTF-8 -DBACKEND_URL=!BACKEND_URL! -DRUNNER_TOKEN=!RUNNER_TOKEN! -DRUNNER_ID=!RUNNER_ID! -DPOLL_INTERVAL_MS=30000 -jar "' + $jar + '" ^>^> "' + $logFile + '" 2^>^&1') + $crlf
>> "%PS1%" echo $bat    += 'timeout /t 15 /nobreak ^>nul' + $crlf
>> "%PS1%" echo $bat    += 'goto loop'  + $crlf
>> "%PS1%" echo [IO.File]::WriteAllText($dst, $bat, [Text.Encoding]::ASCII)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" >>"%INSTALL_LOG%" 2>&1
set "PS_BAT_EXIT=!ERRORLEVEL!"
del "%PS1%" >nul 2>&1
>> "%INSTALL_LOG%" echo [%TIME%] PowerShell run-runner.bat exit code: !PS_BAT_EXIT!
if !PS_BAT_EXIT! neq 0 (
    >> "%INSTALL_LOG%" echo [%TIME%] ERROR: PowerShell fallo al generar run-runner.bat
)

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
REM  [5/6] TAREA PROGRAMADA
REM ===========================================================================
echo  [5/6] Configurando inicio automatico...
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
REM  [6/6] INICIO DEL RUNNER
REM ===========================================================================
echo.
echo  [6/6] Configuracion completada.
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
