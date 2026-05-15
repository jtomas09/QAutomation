@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

:: ══════════════════════════════════════════════════════════════════════
::  CINEPOLIS - EJECUTOR DE TESTS
:: ══════════════════════════════════════════════════════════════════════

:: ──────────────────────────────────────────────────────────────────────
:MENU_PAIS
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║            Selecciona un pais                   ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Mexico                                     ║
echo   ║   2.  Argentina                                  ║
echo   ║   3.  Chile                                      ║
echo   ║   4.  Colombia                                   ║
echo   ║   5.  Peru                                       ║
echo   ║   6.  Espana                                     ║
echo   ║                                                  ║
echo   ║   0.  Salir                                      ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Selecciona el pais [0-6]:

if "%op%"=="1" goto MENU_MEXICO
if "%op%"=="2" goto MENU_ARGENTINA
if "%op%"=="3" goto MENU_CHILE
if "%op%"=="4" goto MENU_COLOMBIA
if "%op%"=="5" goto MENU_PERU
if "%op%"=="6" goto MENU_ESPANA
if "%op%"=="0" goto FIN
goto MENU_PAIS

:: ──────────────────────────────────────────────────────────────────────
:MENU_MEXICO
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║                   Mexico                        ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Tests de Asientos                          ║
echo   ║   2.  Tests de Alimentos                         ║
echo   ║   3.  Ejecutar TODO                              ║
echo   ║                                                  ║
echo   ║   0.  Volver al menu de paises                   ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Elige una opcion [0-3]:

if "%op%"=="1" goto MENU_ASIENTOS_MX
if "%op%"=="2" goto MENU_ALIMENTOS_MX
if "%op%"=="3" call :RUN_TEST "tests.*" "Todos los tests - Mexico"
if "%op%"=="0" goto MENU_PAIS
goto MENU_MEXICO

:: ──────────────────────────────────────────────────────────────────────
:MENU_ARGENTINA
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║                  Argentina                      ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Tests de Asientos                          ║
echo   ║   2.  Tests de Alimentos                         ║
echo   ║   3.  Ejecutar TODO                              ║
echo   ║                                                  ║
echo   ║   0.  Volver al menu de paises                   ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Elige una opcion [0-3]:

if "%op%"=="1" goto MENU_ASIENTOS_AR
if "%op%"=="2" goto MENU_ALIMENTOS_AR
if "%op%"=="3" call :RUN_TEST "tests.*" "Todos los tests - Argentina"
if "%op%"=="0" goto MENU_PAIS
goto MENU_ARGENTINA

:: ──────────────────────────────────────────────────────────────────────
:MENU_CHILE
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║                    Chile                        ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Tests de Asientos                          ║
echo   ║   2.  Tests de Alimentos                         ║
echo   ║   3.  Ejecutar TODO                              ║
echo   ║                                                  ║
echo   ║   0.  Volver al menu de paises                   ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Elige una opcion [0-3]:

if "%op%"=="1" goto MENU_ASIENTOS_CL
if "%op%"=="2" goto MENU_ALIMENTOS_CL
if "%op%"=="3" call :RUN_TEST "tests.*" "Todos los tests - Chile"
if "%op%"=="0" goto MENU_PAIS
goto MENU_CHILE

:: ──────────────────────────────────────────────────────────────────────
:MENU_COLOMBIA
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║                   Colombia                      ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Tests de Asientos                          ║
echo   ║   2.  Tests de Alimentos                         ║
echo   ║   3.  Ejecutar TODO                              ║
echo   ║                                                  ║
echo   ║   0.  Volver al menu de paises                   ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Elige una opcion [0-3]:

if "%op%"=="1" goto MENU_ASIENTOS_CO
if "%op%"=="2" goto MENU_ALIMENTOS_CO
if "%op%"=="3" call :RUN_TEST "tests.*" "Todos los tests - Colombia"
if "%op%"=="0" goto MENU_PAIS
goto MENU_COLOMBIA

:: ──────────────────────────────────────────────────────────────────────
:MENU_PERU
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║                    Peru                         ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Tests de Asientos                          ║
echo   ║   2.  Tests de Alimentos                         ║
echo   ║   3.  Ejecutar TODO                              ║
echo   ║                                                  ║
echo   ║   0.  Volver al menu de paises                   ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Elige una opcion [0-3]:

if "%op%"=="1" goto MENU_ASIENTOS_PE
if "%op%"=="2" goto MENU_ALIMENTOS_PE
if "%op%"=="3" call :RUN_TEST "tests.*" "Todos los tests - Peru"
if "%op%"=="0" goto MENU_PAIS
goto MENU_PERU

:: ──────────────────────────────────────────────────────────────────────
:MENU_ESPANA
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║         CINEPOLIS  -  EJECUTOR DE TESTS         ║
echo   ║                    Espana                       ║
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Tests de Asientos                          ║
echo   ║   2.  Tests de Alimentos                         ║
echo   ║   3.  Ejecutar TODO                              ║
echo   ║                                                  ║
echo   ║   0.  Volver al menu de paises                   ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
set /p op=  Elige una opcion [0-3]:

if "%op%"=="1" goto MENU_ASIENTOS_ES
if "%op%"=="2" goto MENU_ALIMENTOS_ES
if "%op%"=="3" call :RUN_TEST "tests.*" "Todos los tests - Espana"
if "%op%"=="0" goto MENU_PAIS
goto MENU_ESPANA

:: ══════════════════════════════════════════════════════════════════════
::  SUBMENUS DE ASIENTOS  (por pais)
:: ══════════════════════════════════════════════════════════════════════

:MENU_ASIENTOS_MX
call :SHOW_ASIENTOS_MENU "Mexico"
set /p op=  Elige una opcion [0-10]:
call :HANDLE_ASIENTOS "%op%" "Mexico" MENU_MEXICO
goto MENU_ASIENTOS_MX

:MENU_ASIENTOS_AR
call :SHOW_ASIENTOS_MENU "Argentina"
set /p op=  Elige una opcion [0-10]:
call :HANDLE_ASIENTOS "%op%" "Argentina" MENU_ARGENTINA
goto MENU_ASIENTOS_AR

:MENU_ASIENTOS_CL
call :SHOW_ASIENTOS_MENU "Chile"
set /p op=  Elige una opcion [0-10]:
call :HANDLE_ASIENTOS "%op%" "Chile" MENU_CHILE
goto MENU_ASIENTOS_CL

:MENU_ASIENTOS_CO
call :SHOW_ASIENTOS_MENU "Colombia"
set /p op=  Elige una opcion [0-10]:
call :HANDLE_ASIENTOS "%op%" "Colombia" MENU_COLOMBIA
goto MENU_ASIENTOS_CO

:MENU_ASIENTOS_PE
call :SHOW_ASIENTOS_MENU "Peru"
set /p op=  Elige una opcion [0-10]:
call :HANDLE_ASIENTOS "%op%" "Peru" MENU_PERU
goto MENU_ASIENTOS_PE

:MENU_ASIENTOS_ES
call :SHOW_ASIENTOS_MENU "Espana"
set /p op=  Elige una opcion [0-10]:
call :HANDLE_ASIENTOS "%op%" "Espana" MENU_ESPANA
goto MENU_ASIENTOS_ES

:: ══════════════════════════════════════════════════════════════════════
::  SUBMENUS DE ALIMENTOS  (por pais)
:: ══════════════════════════════════════════════════════════════════════

:MENU_ALIMENTOS_MX
call :SHOW_ALIMENTOS_MENU "Mexico"
set /p op=  Elige una opcion [0-6]:
call :HANDLE_ALIMENTOS "%op%" "Mexico" MENU_MEXICO
goto MENU_ALIMENTOS_MX

:MENU_ALIMENTOS_AR
call :SHOW_ALIMENTOS_MENU "Argentina"
set /p op=  Elige una opcion [0-6]:
call :HANDLE_ALIMENTOS "%op%" "Argentina" MENU_ARGENTINA
goto MENU_ALIMENTOS_AR

:MENU_ALIMENTOS_CL
call :SHOW_ALIMENTOS_MENU "Chile"
set /p op=  Elige una opcion [0-6]:
call :HANDLE_ALIMENTOS "%op%" "Chile" MENU_CHILE
goto MENU_ALIMENTOS_CL

:MENU_ALIMENTOS_CO
call :SHOW_ALIMENTOS_MENU "Colombia"
set /p op=  Elige una opcion [0-6]:
call :HANDLE_ALIMENTOS "%op%" "Colombia" MENU_COLOMBIA
goto MENU_ALIMENTOS_CO

:MENU_ALIMENTOS_PE
call :SHOW_ALIMENTOS_MENU "Peru"
set /p op=  Elige una opcion [0-6]:
call :HANDLE_ALIMENTOS "%op%" "Peru" MENU_PERU
goto MENU_ALIMENTOS_PE

:MENU_ALIMENTOS_ES
call :SHOW_ALIMENTOS_MENU "Espana"
set /p op=  Elige una opcion [0-6]:
call :HANDLE_ALIMENTOS "%op%" "Espana" MENU_ESPANA
goto MENU_ALIMENTOS_ES

:: ──────────────────────────────────────────────────────────────────────
:SHOW_ASIENTOS_MENU  <pais>
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║  %~1  /  Tests de Asientos
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.   Todos los tests de Asientos               ║
echo   ║                                                  ║
echo   ║   2.   Seleccion de 1 Asiento                    ║
echo   ║   3.   Seleccion de Multiples Asientos           ║
echo   ║   4.   Asientos Consecutivos                     ║
echo   ║   5.   Seleccion y Deseleccion                   ║
echo   ║   6.   Mas de 10 Asientos (Alerta limite)        ║
echo   ║   7.   Cambio de Horario en Asientos             ║
echo   ║   8.   Verificacion de Banner 3D                 ║
echo   ║   9.   Validacion de Alerta Asiento Especial     ║
echo   ║   10.  Verificacion de Banner Sala Junior        ║
echo   ║                                                  ║
echo   ║   0.   Volver                                    ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
exit /b

:: ──────────────────────────────────────────────────────────────────────
:HANDLE_ASIENTOS  <op>  <pais>  <menu_volver>
:: ──────────────────────────────────────────────────────────────────────
if "%~1"=="1"  call :RUN_TEST "tests.asientos.SeleccionAsientos.*"                             "Todos los tests de Asientos - %~2"
if "%~1"=="2"  call :RUN_TEST "tests.asientos.SeleccionAsientos.seleccion1Asiento"             "Seleccion de 1 Asiento - %~2"
if "%~1"=="3"  call :RUN_TEST "tests.asientos.SeleccionAsientos.seleccionMultiplesAsientos"    "Seleccion de Multiples Asientos - %~2"
if "%~1"=="4"  call :RUN_TEST "tests.asientos.SeleccionAsientos.seleccionAsientosConsecutivos" "Asientos Consecutivos - %~2"
if "%~1"=="5"  call :RUN_TEST "tests.asientos.SeleccionAsientos.seleccionAsientosYDeseleccion" "Seleccion y Deseleccion - %~2"
if "%~1"=="6"  call :RUN_TEST "tests.asientos.SeleccionAsientos.seleccion11Asientos"           "Mas de 10 Asientos - %~2"
if "%~1"=="7"  call :RUN_TEST "tests.asientos.SeleccionAsientos.cambioHorarioAsientos"         "Cambio de Horario - %~2"
if "%~1"=="8"  call :RUN_TEST "tests.asientos.SeleccionAsientos.asientos3D"                    "Banner 3D - %~2"
if "%~1"=="9"  call :RUN_TEST "tests.asientos.SeleccionAsientos.alertaAsientoEspecial"         "Alerta Asiento Especial - %~2"
if "%~1"=="10" call :RUN_TEST "tests.asientos.SeleccionAsientos.asientosSalaJunior"            "Banner Sala Junior - %~2"
if "%~1"=="0"  goto %~3
exit /b

:: ──────────────────────────────────────────────────────────────────────
:SHOW_ALIMENTOS_MENU  <pais>
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   ╔══════════════════════════════════════════════════╗
echo   ║  %~1  /  Tests de Alimentos
echo   ╠══════════════════════════════════════════════════╣
echo   ║                                                  ║
echo   ║   1.  Todos los tests de Alimentos               ║
echo   ║   2.  Menu Tradicional                           ║
echo   ║   3.  Menu Atmosfera                             ║
echo   ║   4.  Menu VIP                                   ║
echo   ║   5.  Menu Coffee Tree                           ║
echo   ║   6.  Menu Mi Cine                               ║
echo   ║                                                  ║
echo   ║   0.  Volver                                     ║
echo   ║                                                  ║
echo   ╚══════════════════════════════════════════════════╝
echo.
exit /b

:: ──────────────────────────────────────────────────────────────────────
:HANDLE_ALIMENTOS  <op>  <pais>  <menu_volver>
:: ──────────────────────────────────────────────────────────────────────
if "%~1"=="1" call :RUN_TEST "tests.alimentos.*"                  "Todos los tests de Alimentos - %~2"
if "%~1"=="2" call :RUN_TEST "tests.alimentos.MenuTradicional.*"  "Menu Tradicional - %~2"
if "%~1"=="3" call :RUN_TEST "tests.alimentos.MenuAtmosfera.*"    "Menu Atmosfera - %~2"
if "%~1"=="4" call :RUN_TEST "tests.alimentos.MenuVIP.*"          "Menu VIP - %~2"
if "%~1"=="5" call :RUN_TEST "tests.alimentos.MenuCoffeTree.*"    "Menu Coffee Tree - %~2"
if "%~1"=="6" call :RUN_TEST "tests.alimentos.MenuMiCine.*"       "Menu Mi Cine - %~2"
if "%~1"=="0" goto %~3
exit /b

:: ──────────────────────────────────────────────────────────────────────
:RUN_TEST  <filtro>  <descripcion>
:: ──────────────────────────────────────────────────────────────────────
echo.
echo   ┌──────────────────────────────────────────────────┐
echo   │  Ejecutando : %~2
echo   │  Filtro     : %~1
echo   └──────────────────────────────────────────────────┘
echo.
call gradlew.bat test --tests %~1 --rerun-tasks
echo.
if %errorlevel%==0 (
    echo   Tests finalizados correctamente.
) else (
    echo   Tests finalizados con errores.
)
echo.
pause
exit /b

:: ──────────────────────────────────────────────────────────────────────
:FIN
:: ──────────────────────────────────────────────────────────────────────
cls
echo.
echo   Hasta luego.
echo.
exit /b
