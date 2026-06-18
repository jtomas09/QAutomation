@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ╔═══════════════════════════════════════════════════════════════╗
echo  ║   Automation QA Runner — Instalador Windows v2.2.0          ║
echo  ║   Configura el servicio de auto-inicio                       ║
echo  ╚═══════════════════════════════════════════════════════════════╝
echo.
echo  [PROVISIONAL] Este es el instalador de configuracion.
echo  Requiere: Java 17+ instalado en el equipo.
echo.

:: ─── Verificar Java ─────────────────────────────────────────────────────────
echo  [1/5] Verificando Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  [ERROR] Java 17+ no encontrado.
    echo  Descarga e instala Java desde: https://adoptium.net
    echo  Luego vuelve a ejecutar este instalador.
    echo.
    pause & exit /b 1
)
for /f "tokens=3 delims= " %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set _JVER=%%V
    goto :JVER_OK
)
:JVER_OK
echo  [OK] Java detectado: !_JVER!

:: ─── Buscar JAR ─────────────────────────────────────────────────────────────
echo  [2/5] Buscando runner JAR...
set RUNNER_JAR=

for %%P in (
    "%~dp0cinepolis-runner.jar"
    "%~dp0automationqa-runner.jar"
    "%~dp0target\cinepolis-runner.jar"
    "%~dp0runner\target\cinepolis-runner.jar"
) do (
    if exist %%P (
        set RUNNER_JAR=%%~fP
        goto :JAR_FOUND
    )
)

echo.
echo  [AVISO] No se encontro el archivo cinepolis-runner.jar.
echo.
echo  Para completar la instalacion necesitas el archivo JAR del Runner.
echo  Opciones:
echo    A) Compila el proyecto: desde la carpeta runner\ ejecuta mvn package -DskipTests
echo    B) Copia cinepolis-runner.jar a la misma carpeta que este script
echo.
echo  Una vez tengas el JAR, vuelve a ejecutar este instalador.
echo.
pause & exit /b 1

:JAR_FOUND
echo  [OK] JAR encontrado: !RUNNER_JAR!

:: ─── Instalar ───────────────────────────────────────────────────────────────
echo  [3/5] Instalando en %LOCALAPPDATA%\AutomationQA\runner\...
set INSTALL_DIR=%LOCALAPPDATA%\AutomationQA\runner
mkdir "%INSTALL_DIR%" 2>nul
copy /Y "!RUNNER_JAR!" "%INSTALL_DIR%\automationqa-runner.jar" >nul
echo  [OK] Archivo copiado.

:: ─── Crear wrapper de inicio ────────────────────────────────────────────────
echo  [4/5] Configurando inicio automatico...

:: Script de inicio con reinicio automatico
(
    echo @echo off
    echo :loop
    echo   javaw -Dfile.encoding=UTF-8 -DBACKEND_URL=https://qautomation-production.up.railway.app -DRUNNER_TOKEN=runner-local-token -jar "%INSTALL_DIR%\automationqa-runner.jar"
    echo   timeout /t 15 /nobreak ^> nul
    echo goto :loop
) > "%INSTALL_DIR%\run-runner.bat"

:: VBScript para ocultar la ventana CMD
(
    echo Set objShell = CreateObject^("WScript.Shell"^)
    echo objShell.Run "%INSTALL_DIR%\run-runner.bat", 0, False
) > "%INSTALL_DIR%\launcher.vbs"

:: Registrar en Task Scheduler (sin admin)
schtasks /delete /tn "AutomationQA Runner" /f >nul 2>&1
schtasks /create /tn "AutomationQA Runner" /sc ONLOGON /ru "%USERNAME%" ^
    /tr "wscript.exe \"%INSTALL_DIR%\launcher.vbs\"" /f /rl HIGHEST >nul 2>&1

if errorlevel 1 (
    echo  [AVISO] Task Scheduler no disponible. Usando carpeta Inicio como alternativa.
    copy /Y "%INSTALL_DIR%\launcher.vbs" ^
        "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\AutomationQA-Runner.vbs" >nul 2>&1
    echo  [OK] Acceso directo creado en carpeta Inicio.
) else (
    echo  [OK] Tarea programada creada. Se ejecutara al iniciar sesion.
)

:: ─── Resultado ──────────────────────────────────────────────────────────────
echo.
echo  [5/5] Instalacion completada.
echo.
echo  ╔═══════════════════════════════════════════════════════════════╗
echo  ║   Automation QA Runner instalado correctamente              ║
echo  ║                                                               ║
echo  ║   El Runner arrancara automaticamente en el proximo          ║
echo  ║   inicio de sesion en Windows.                               ║
echo  ║                                                               ║
echo  ║   Puedes verificarlo en el Dashboard en ~15 segundos.        ║
echo  ╚═══════════════════════════════════════════════════════════════╝
echo.

set /p START_NOW=  Iniciar el Runner ahora? (S/n):
if /i "!START_NOW!"=="" set START_NOW=S
if /i "!START_NOW!"=="n" goto :DONE

echo  Iniciando Runner...
wscript.exe "%INSTALL_DIR%\launcher.vbs"
echo  [OK] Runner iniciado en segundo plano.
echo       Aparecera en el Dashboard en ~15 segundos.

:DONE
echo.
pause
