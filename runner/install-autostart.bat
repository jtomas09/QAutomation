@echo off
chcp 65001 > nul

echo.
echo  ╔══════════════════════════════════════════════════════════╗
echo  ║   Cinepolis QA — Instalar Runner como tarea de inicio   ║
echo  ╚══════════════════════════════════════════════════════════╝
echo.

set TASK_NAME=CinepolisQARunner
set RUNNER_DIR=%~dp0
set RUNNER_SCRIPT=%RUNNER_DIR%start-runner-auto.bat

REM ── Verificar requisitos ─────────────────────────────────────
echo  [1/3] Verificando Java...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ❌ Java no encontrado. Instala JDK 17+ y agrégalo al PATH.
    pause & exit /b 1
)
echo  ✅ Java OK

echo  [2/3] Compilando runner (primera vez)...
cd /d "%RUNNER_DIR%"
if not exist "target\cinepolis-runner.jar" (
    call mvn package -q -DskipTests
    if %ERRORLEVEL% neq 0 (
        echo  ❌ Error al compilar. Ejecuta: mvn package -DskipTests
        pause & exit /b 1
    )
)
echo  ✅ JAR compilado en target\cinepolis-runner.jar

REM ── Registrar en Programador de Tareas ───────────────────────
echo  [3/3] Registrando tarea de inicio de Windows...

REM Eliminar tarea anterior si existe
schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1

REM Crear tarea que corre al iniciar sesión (hidden = sin ventana)
schtasks /create ^
  /tn "%TASK_NAME%" ^
  /tr "wscript.exe \"%RUNNER_DIR%runner-launcher.vbs\"" ^
  /sc ONLOGON ^
  /ru "%USERNAME%" ^
  /rl HIGHEST ^
  /f >nul 2>&1

if %ERRORLEVEL% neq 0 (
    echo  ❌ Error al registrar tarea. Ejecuta este script como Administrador.
    pause & exit /b 1
)

echo  ✅ Tarea registrada: "%TASK_NAME%"
echo.
echo  ══════════════════════════════════════════════════════════
echo   El runner se iniciará automáticamente al abrir sesión.
echo   Logs en: %RUNNER_DIR%logs\runner.log
echo.
echo   Para ver el estado:
echo     schtasks /query /tn "%TASK_NAME%"
echo.
echo   Para desinstalar:
echo     schtasks /delete /tn "%TASK_NAME%" /f
echo  ══════════════════════════════════════════════════════════
echo.
echo  ¿Iniciar el runner AHORA sin reiniciar? (S/N)
set /p START_NOW=  Respuesta:

if /i "%START_NOW%"=="S" (
    echo.
    echo  Iniciando runner en background...
    start "" wscript.exe "%RUNNER_DIR%runner-launcher.vbs"
    echo  ✅ Runner corriendo en background. Revisa la UI en ~10 segundos.
)

echo.
pause
