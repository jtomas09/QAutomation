' runner-launcher.vbs
' Lanza start-runner-auto.bat sin mostrar ninguna ventana de consola.
' Invocado por el Programador de Tareas de Windows o desde Startup.

Dim sh, scriptDir, targetBat

Set sh = CreateObject("WScript.Shell")

' Obtener directorio donde reside ESTE archivo VBS
scriptDir = Left(WScript.ScriptFullName, InStrRev(WScript.ScriptFullName, "\"))

targetBat = scriptDir & "start-runner-auto.bat"

' Validar que start-runner-auto.bat existe antes de intentar lanzarlo
Dim fso
Set fso = CreateObject("Scripting.FileSystemObject")

If Not fso.FileExists(targetBat) Then
    WScript.Echo "Automation QA Runner - Error de inicio" & vbCrLf & vbCrLf & _
        "No se encontro el archivo:" & vbCrLf & _
        targetBat & vbCrLf & vbCrLf & _
        "Asegurate de que runner-launcher.vbs este en la carpeta runner\ del proyecto."
    WScript.Quit 1
End If

' Lanzar el bat sin ventana (0 = oculto, False = no esperar)
' Las comillas adicionales protegen rutas con espacios
sh.Run "cmd /c """ & targetBat & """", 0, False

Set fso = Nothing
Set sh = Nothing
