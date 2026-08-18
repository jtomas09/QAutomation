// ios-screen-capture — captura de pantalla de un iPhone/iPad conectado por USB
// vía CoreMediaIO (el mismo mecanismo oficial que usa QuickTime "Nueva
// grabación de película"), sin ffmpeg. ffmpeg NO puede hacer esto: su demuxer
// avfoundation no activa kCMIOHardwarePropertyAllowScreenCaptureDevices ni
// enumera dispositivos .external/.muxed (confirmado leyendo su código fuente).
//
// Dos modos, para no duplicar en Swift la lógica de correlación UDID→índice
// que ya existe y está probada en AVFoundationMirrorProvider.java:
//
//   list-devices              → imprime "<index>\t<localizedName>\t<uniqueID>"
//                                por línea (uno por dispositivo .external/.muxed)
//                                y termina. Java decide cuál usar.
//   capture --index <n>       → abre ESE dispositivo y escribe un stream
//                                continuo de PNG completos a stdout — mismo
//                                formato que ffmpeg -f image2pipe -vcodec png,
//                                que FfmpegPngFrameSource.java ya sabe parsear
//                                (cada PNG termina en su propio chunk IEND).
//
// Requiere permiso de macOS "Screen Recording" concedido a este binario en
// System Settings → Privacy & Security → Screen Recording (aparece ahí solo
// DESPUÉS del primer intento de captura — no antes). Esto es un límite de
// privacidad impuesto por Apple, no automatizable desde ningún API: sin él,
// list-devices imprime 0 líneas (sin error) — no es un bug de este programa.

import Foundation
import AVFoundation
import CoreMediaIO
import CoreImage

func log(_ s: String) {
    FileHandle.standardError.write((s + "\n").data(using: .utf8)!)
}

func allowScreenCaptureDevices() {
    let element = CMIOObjectPropertyElement(kCMIOObjectPropertyElementMain)
    var prop = CMIOObjectPropertyAddress(
        mSelector: CMIOObjectPropertySelector(kCMIOHardwarePropertyAllowScreenCaptureDevices),
        mScope: CMIOObjectPropertyScope(kCMIOObjectPropertyScopeGlobal),
        mElement: element)
    var allow: UInt32 = 1
    _ = CMIOObjectSetPropertyData(CMIOObjectID(kCMIOObjectSystemObject), &prop, 0, nil, 4, &allow)
}

func discoverDevices(warmupSeconds: Double) -> [AVCaptureDevice] {
    allowScreenCaptureDevices()
    // Requisito documentado: los dispositivos tardan unos segundos en
    // aparecer tras activar la propiedad — no es instantáneo.
    Thread.sleep(forTimeInterval: warmupSeconds)
    _ = AVCaptureDevice.devices() // warm-up: necesario antes de DiscoverySession según Apple
    let session = AVCaptureDevice.DiscoverySession(
        deviceTypes: [.external],
        mediaType: .muxed,
        position: .unspecified)
    return session.devices
}

func runListDevices() {
    let devices = discoverDevices(warmupSeconds: 3.0)
    for (i, d) in devices.enumerated() {
        print("\(i)\t\(d.localizedName)\t\(d.uniqueID)")
    }
}

final class FrameWriter: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let ciContext = CIContext()
    private let stdout = FileHandle.standardOutput
    private let targetFps: Double
    private var lastEmit: Date = .distantPast

    init(targetFps: Double) {
        self.targetFps = targetFps
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        if now.timeIntervalSince(lastEmit) < (1.0 / targetFps) { return }
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else { return }

        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, "public.png" as CFString, 1, nil) else { return }
        CGImageDestinationAddImage(dest, cgImage, nil)
        guard CGImageDestinationFinalize(dest) else { return }

        stdout.write(data as Data)
        lastEmit = now
    }
}

func runCapture(index: Int) {
    let devices = discoverDevices(warmupSeconds: 3.0)
    guard index >= 0 && index < devices.count else {
        log("[ios-screen-capture] índice fuera de rango: \(index) (encontrados: \(devices.count))")
        exit(1)
    }
    let device = devices[index]
    log("[ios-screen-capture] Abriendo dispositivo — nombre='\(device.localizedName)' uniqueID='\(device.uniqueID)'")

    let session = AVCaptureSession()
    session.beginConfiguration()
    do {
        let input = try AVCaptureDeviceInput(device: device)
        guard session.canAddInput(input) else {
            log("[ios-screen-capture] No se pudo agregar el input de captura")
            exit(1)
        }
        session.addInput(input)
    } catch {
        log("[ios-screen-capture] Error creando AVCaptureDeviceInput: \(error)")
        exit(1)
    }

    let output = AVCaptureVideoDataOutput()
    let writer = FrameWriter(targetFps: 15)
    let queue = DispatchQueue(label: "ios-screen-capture.frames")
    output.setSampleBufferDelegate(writer, queue: queue)
    guard session.canAddOutput(output) else {
        log("[ios-screen-capture] No se pudo agregar el output de video")
        exit(1)
    }
    session.addOutput(output)
    session.commitConfiguration()

    log("[ios-screen-capture] Sesión iniciada — esperando frames")
    session.startRunning()

    // Mantener el proceso vivo indefinidamente — el padre (Java) lo termina
    // con destroyForcibly() al desconectar, igual que hace con ffmpeg.
    RunLoop.main.run()
}

let args = CommandLine.arguments
if args.count >= 2 && args[1] == "list-devices" {
    runListDevices()
} else if args.count >= 4 && args[1] == "capture" && args[2] == "--index" {
    guard let idx = Int(args[3]) else {
        log("[ios-screen-capture] --index inválido")
        exit(1)
    }
    runCapture(index: idx)
} else {
    log("Uso: ios-screen-capture list-devices")
    log("     ios-screen-capture capture --index <n>")
    exit(1)
}
