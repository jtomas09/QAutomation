@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ╔══════════════════════════════════════════════════════════╗
echo  ║   Cinepolis QA  —  Runner Agent  v2.1.0                 ║
echo  ║   Conectando con Railway Backend...                      ║
echo  ╚══════════════════════════════════════════════════════════╝
echo.

REM ══════════════════════════════════════════════════════════════
REM  CONFIGURACIÓN
REM ══════════════════════════════════════════════════════════════

REM  URL del backend desplegado en Railway
set BACKEND_URL=https://qautomation-production.up.railway.app

REM  Token compartido (debe coincidir con RUNNER_TOKEN en Railway)
set RUNNER_TOKEN=runner-local-token

REM  Intervalo de polling en milisegundos (5000 = 5 segundos)
set POLL_INTERVAL_MS=5000

REM  Directorio raíz del proyecto de pruebas (donde está gradlew.bat)
REM  ".." apunta a CinepolisAutomation/ (padre de runner/)
set WORK_DIR=%~dp0..

REM  Appium server local
set APPIUM_HUB=http://127.0.0.1:4723

REM  (Opcional) URL base para reportes Allure publicados
REM  set ALLURE_BASE_URL=https://mi-servidor/allure

echo  Configuración:
echo    Backend:    %BACKEND_URL%
echo    WorkDir:    %WORK_DIR%
echo    Appium:     %APPIUM_HUB%
echo    Polling:    cada %POLL_INTERVAL_MS% ms
echo.

REM ══════════════════════════════════════════════════════════════
REM  VALIDACIONES
REM ══════════════════════════════════════════════════════════════

echo  [Check] Verificando requisitos...

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ❌ ERROR: Java no encontrado en PATH. Instala JDK 17+.
    pause & exit /b 1
)
echo  ✅ Java OK

where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ❌ ERROR: Maven no encontrado en PATH. Instala Apache Maven 3.8+.
    pause & exit /b 1
)
echo  ✅ Maven OK

if not exist "%WORK_DIR%\gradlew.bat" (
    echo  ❌ ERROR: gradlew.bat no encontrado en %WORK_DIR%
    echo     WORK_DIR debe apuntar al directorio raiz del proyecto de pruebas.
    pause & exit /b 1
)
echo  ✅ gradlew.bat OK

where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ⚠  WARN: adb no encontrado en PATH. Las pruebas Android requieren ADB.
) else (
    echo  ✅ ADB OK
    echo.
    echo  [ADB] Dispositivos conectados:
    adb devices
)

echo.
echo  [Build] Compilando Runner Agent...
echo.

cd /d "%~dp0"
call mvn package -q -DskipTests
if %ERRORLEVEL% neq 0 (
    echo  ❌ ERROR: Fallo al compilar el runner. Revisa los errores de Maven arriba.
    pause & exit /b 1
)
echo  ✅ Runner compilado correctamente.
echo.

REM ══════════════════════════════════════════════════════════════
REM  INICIO DEL AGENTE
REM ══════════════════════════════════════════════════════════════

echo  ┌──────────────────────────────────────────────────────────┐
echo  │  Runner iniciado. Consultando Railway cada 5 segundos... │
echo  │  Ctrl+C para detener el runner.                          │
echo  └──────────────────────────────────────────────────────────┘
echo.

java -jar target\cinepolis-runner.jar

pause
