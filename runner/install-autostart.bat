@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║   Automation QA — Instalador Enterprise (Windows)          ║
echo  ║   Universal Runner v2 — Auto-Start sin CMD ni Terminal     ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
echo  Este instalador configura el runner para que arranque
echo  automaticamente cada vez que inicies sesion en Windows.
echo  No tendras que ejecutar ningun script manualmente.
echo.
echo  Metodo de instalacion:
echo  [1] Tarea Programada (recomendado — sin permisos de admin)
echo  [2] Servicio Windows WinSW (requiere Administrador)
echo.
set /p METODO=  Selecciona metodo (1 o 2, Enter = 1):
if "!METODO!"=="" set METODO=1

if "!METODO!"=="2" (
    echo.
    echo  Abriendo instalador WinSW...
    start "" "%~dp0service\windows\install-service.bat"
    goto :eof
)

REM ── Metodo 1: Tarea Programada ───────────────────────────────────
call "%~dp0service\windows\install-service.bat"
