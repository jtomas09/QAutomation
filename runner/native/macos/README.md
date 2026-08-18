# ios-screen-capture

Helper nativo (Swift) que reemplaza a ffmpeg específicamente para el mirror
de iOS vía AVFoundation. ffmpeg no puede hacer esto: su demuxer `avfoundation`
no activa `kCMIOHardwarePropertyAllowScreenCaptureDevices` ni enumera
dispositivos `.external`/`.muxed` (confirmado leyendo su código fuente) — por
eso un iPhone conectado por USB nunca aparece en `ffmpeg -f avfoundation
-list_devices`, sin importar la configuración.

Requiere macOS 14+ (por el `AVCaptureDevice.DeviceType.external`) y que el
usuario conceda el permiso "Screen Recording" al binario en System Settings →
Privacy & Security → Screen Recording — aparece ahí solo DESPUÉS de que el
binario intenta una captura por primera vez. Esto es un límite de privacidad
de Apple, no automatizable desde ningún API (ver AVFoundationMirrorProvider.java
para el detalle completo de la investigación).

## Rebuild

```bash
cd runner/native/macos
swiftc ios_screen_capture.swift -o ios-screen-capture-arm64  -target arm64-apple-macosx14.0  -framework AVFoundation -framework CoreMediaIO -framework CoreImage
swiftc ios_screen_capture.swift -o ios-screen-capture-x86_64 -target x86_64-apple-macosx14.0 -framework AVFoundation -framework CoreMediaIO -framework CoreImage
lipo -create -output ios-screen-capture ios-screen-capture-arm64 ios-screen-capture-x86_64
codesign -s - --force ios-screen-capture
cp ios-screen-capture ../../src/main/resources/native/macos/ios-screen-capture
rm ios-screen-capture-arm64 ios-screen-capture-x86_64 ios-screen-capture
```

Requiere Xcode (no solo Command Line Tools) instalado en la máquina que
compila — solo se ejecuta manualmente al modificar este archivo, nunca en el
Runner del usuario final (que solo recibe el binario ya compilado, embebido
en el JAR).
