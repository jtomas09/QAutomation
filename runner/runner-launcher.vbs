' runner-launcher.vbs
' Lanza start-runner-auto.bat sin mostrar ninguna ventana de consola.
' Invocado por el Programador de Tareas de Windows.

Dim objShell
Set objShell = CreateObject("WScript.Shell")

Dim scriptDir
scriptDir = Left(WScript.ScriptFullName, InStrRev(WScript.ScriptFullName, "\"))

' 0 = sin ventana, False = no esperar a que termine
objShell.Run "cmd /c """ & scriptDir & "start-runner-auto.bat""", 0, False

Set objShell = Nothing
