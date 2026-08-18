package qa.cinepolis.runner.mirror;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captura de pantalla para iOS vía AVFoundation/CoreMediaIO — el MISMO
 * mecanismo oficial que usa QuickTime al grabar la pantalla de un iPhone
 * conectado por USB, pero invocado programáticamente (sin abrir QuickTime,
 * sin selección manual del dispositivo). Sin WebDriverAgent, sin Appium, sin
 * sesión de automatización de ningún tipo.
 *
 * Proveedor PRIMARIO para iOS en macOS — máxima calidad/FPS de las tres
 * opciones (WDA, libimobiledevice, esta). Fallback: LibimobiledeviceMirrorProvider
 * (aunque en la práctica no funciona contra iOS 17+ — ver LibimobiledeviceMirrorProvider).
 *
 * ── Por qué NO usa ffmpeg (a diferencia de ScrcpyMirrorProvider) ──────────
 * ffmpeg's demuxer `avfoundation` NUNCA puede ver un iPhone conectado por USB:
 * no activa `kCMIOHardwarePropertyAllowScreenCaptureDevices` (la propiedad de
 * CoreMediaIO que expone dispositivos iOS como fuente de video) ni enumera
 * dispositivos de tipo `.external`/`.muxed` — solo cámaras y pantallas del Mac
 * (confirmado leyendo el código fuente de avfoundation.m). Por eso este
 * provider usa un binario propio, `ios-screen-capture` (ver
 * runner/native/macos/), que sí implementa ese mecanismo vía Swift/AVFoundation.
 *
 * ── Requisito operativo real (investigado a fondo, no folclore) ──────────
 * NO requiere abrir QuickTime ni seleccionar el iPhone manualmente — eso era
 * una creencia popular, no un requisito técnico real (confirmado: la propiedad
 * CoreMediaIO se activa correctamente vía API sin ninguna app de por medio).
 * El requisito real es el permiso de macOS "Screen Recording" (TCC), que Apple
 * exige para CUALQUIER app que capture pantalla — la misma categoría que
 * Zoom/OBS/Loom ya piden. Es la ÚNICA acción que Apple no permite automatizar
 * desde ningún API (ni siquiera con sudo): debe concederse una vez, por el
 * usuario, en System Settings → Privacy & Security → Screen Recording. El
 * binario aparece ahí automáticamente en su primer intento de captura — no
 * hace falta agregarlo manualmente con "+". Sin este permiso, ios-screen-capture
 * no falla ni lanza error: simplemente reporta 0 dispositivos encontrados
 * (mismo comportamiento silencioso que la propia `screencapture` de Apple sin
 * el permiso) — por eso este provider falla limpio (isSupported()/start()
 * devuelven false) en vez de bloquear, y el Mirror cae a
 * LibimobiledeviceMirrorProvider.
 *
 * ── Resolución de índice (la parte de mayor incertidumbre) ────────────────
 * ios-screen-capture no expone el UDID del dispositivo en su lista — solo un
 * nombre amigable ("iPhone de Tester"), igual que antes con ffmpeg. Este
 * provider sigue correlacionando UDID → nombre amigable vía `idevicename`
 * (libimobiledevice, si está disponible) contra la lista que reporta el
 * binario; si no puede resolver un match único, falla limpio (no adivina un
 * índice al azar) — misma lógica ya probada en AVFoundationMirrorProviderTest,
 * sin cambios.
 */
public final class AVFoundationMirrorProvider implements DeviceMirrorProvider {

    private static final String OS_MAC = "mac";

    private static final Pattern DEVICE_LINE =
            Pattern.compile("^(\\d+)\\t(.+)\\t(.+)$");

    private final String iosScreenCapturePath;
    private final String idevicenamePath;

    private final ConcurrentHashMap<String, FfmpegPngFrameSource> sessions = new ConcurrentHashMap<>();

    /** Sin auto-extracción (compatibilidad/pruebas) — solo busca en Homebrew/PATH. */
    public AVFoundationMirrorProvider() {
        this((String) null);
    }

    /**
     * @param agentDataDir si no es null, extrae automáticamente el binario
     *                     ios-screen-capture embebido en el JAR (sin acción
     *                     del usuario) — ver MirrorDependencyManager.
     */
    public AVFoundationMirrorProvider(String agentDataDir) {
        String embedded = null;
        if (agentDataDir != null) {
            MirrorDependencyManager deps = new MirrorDependencyManager(java.nio.file.Path.of(agentDataDir));
            if (deps.ensureIosScreenCapture()) embedded = deps.embeddedIosScreenCapturePath();
        }
        this.iosScreenCapturePath = embedded;
        this.idevicenamePath = BinaryLocator.resolve("idevicename");
    }

    @Override
    public String name() { return "AVFOUNDATION"; }

    @Override
    public boolean isSupported() {
        return iosScreenCapturePath != null
                && System.getProperty("os.name", "").toLowerCase().contains(OS_MAC);
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        // La conexión física real la reporta IOSDeviceRegistry (misma fuente que
        // usa IOSMirrorProvider) — este provider no reimplementa esa detección,
        // solo intenta resolver SU índice de captura particular.
        return qa.cinepolis.runner.IOSDeviceRegistry.isPresent(udid);
    }

    @Override
    public boolean start(String udid) {
        if (!isSupported()) return false;
        FfmpegPngFrameSource existing = sessions.get(udid);
        if (existing != null && existing.isAlive()) return true;

        System.out.println("[MirrorProvider][AVFoundation] Creando sesión — udid=" + udid);
        System.out.println("[AVFoundation] Session created — udid=" + udid);

        java.util.Map<Integer, String> devices = listCaptureDevices();
        System.out.println("[AVFoundation] Capture source detected — " + devices.size()
                + " dispositivo(s) de video en la lista de ios-screen-capture — udid=" + udid);

        String friendlyName = resolveFriendlyName(udid);
        Integer index = matchDeviceIndex(udid, friendlyName, devices);
        if (index == null) {
            System.out.println("[AVFoundation] No se pudo resolver el índice de captura para udid="
                    + udid + " — cae a libimobiledevice.");
            return false;
        }
        System.out.println("[AVFoundation] Capture device identified — índice=" + index
                + " nombre='" + devices.get(index) + "' udid=" + udid);

        ProcessBuilder pb = new ProcessBuilder(
                iosScreenCapturePath, "capture", "--index", String.valueOf(index)
        );
        pb.redirectErrorStream(false);

        FfmpegPngFrameSource source = new FfmpegPngFrameSource(pb, "AVFoundation-" + udid);
        if (!source.start()) return false;
        System.out.println("[AVFoundation] Capture started — udid=" + udid);
        System.out.println("[AVFoundation] ios-screen-capture iniciado — udid=" + udid + " índice=" + index);

        // DeviceStreamServer da por perdido el stream tras ~12 capturas fallidas
        // seguidas si no detecta una compilación de WDA en curso (ver comentario
        // en el loop de MirrorHandler) — como este provider nunca toca WDA, esa
        // condición de espera nunca aplicaría y el stream se cortaría antes de
        // que la captura termine de arrancar. Por eso start() BLOQUEA aquí hasta
        // el primer frame real (o hasta agotar el timeout, cayendo al siguiente
        // candidato de la cadena) — cuando captureFrame() empieza a llamarse ya
        // hay frames listos, sin depender de ninguna tolerancia a fallos ajena.
        if (!waitForFirstFrame(source)) {
            source.stop();
            return false;
        }
        System.out.println("[Mirror] Primer frame recibido — udid=" + udid + " provider=AVFoundation");
        sessions.put(udid, source);
        return true;
    }

    private static boolean waitForFirstFrame(FfmpegPngFrameSource source) {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (source.latestFrame() != null) return true;
            if (!source.isAlive()) return false; // el proceso murió antes de producir nada
            try { Thread.sleep(100); } catch (InterruptedException ignored) { return false; }
        }
        return false;
    }

    @Override
    public void stop(String udid) {
        FfmpegPngFrameSource source = sessions.remove(udid);
        if (source != null) {
            source.stop();
            System.out.println("[Mirror] Sesión finalizada — udid=" + udid + " provider=AVFoundation");
        }
    }

    @Override
    public byte[] captureFrame(String udid) {
        FfmpegPngFrameSource source = sessions.get(udid);
        return source != null ? source.latestFrame() : null;
    }

    /**
     * Correlaciona un UDID con un índice de dispositivo de captura, vía nombre
     * amigable (idevicename) contra la lista que reporta ios-screen-capture.
     * Devuelve null si no hay EXACTAMENTE un match — nunca adivina, y nunca
     * toma "el primero que coincida" cuando hay ambigüedad (crítico con varios
     * iPhones conectados a la vez: dos dispositivos con el mismo nombre de
     * fábrica, p.ej. "iPhone", NO deben cruzar índices).
     *
     * Método puro (sin I/O) — extraído así específicamente para poder probar
     * la lógica de correlación/desambiguación con casos concretos de múltiples
     * dispositivos sin depender de binarios/idevicename reales. Sin cambios
     * respecto a la versión que usaba ffmpeg — la fuente de la lista cambió,
     * esta lógica no.
     */
    static Integer matchDeviceIndex(String udid, String friendlyName, java.util.Map<Integer, String> devices) {
        if (devices.isEmpty()) return null;

        if (friendlyName != null && !friendlyName.isBlank()) {
            java.util.List<Integer> matches = new java.util.ArrayList<>();
            for (var entry : devices.entrySet()) {
                if (entry.getValue().toLowerCase().contains(friendlyName.toLowerCase())
                        || friendlyName.toLowerCase().contains(entry.getValue().toLowerCase())) {
                    matches.add(entry.getKey());
                }
            }
            if (matches.size() == 1) return matches.get(0);
            if (matches.size() > 1) {
                System.out.println("[AVFoundationMirrorProvider] Nombre '" + friendlyName
                        + "' coincide con " + matches.size() + " candidatos — ambiguo, no se adivina (udid=" + udid + ")");
                return null;
            }
        }

        // Sin nombre amigable resuelto (o sin match): si solo hay UN candidato, lo
        // usamos como último recurso (ya no hay cámaras/pantallas del Mac en esta
        // lista — ios-screen-capture solo reporta dispositivos .external/.muxed,
        // que en la práctica son siempre iOS; a diferencia de la lista de ffmpeg
        // no hace falta filtrar "FaceTime"/"Capture screen").
        return devices.size() == 1 ? devices.keySet().iterator().next() : null;
    }

    private String resolveFriendlyName(String udid) {
        if (idevicenamePath == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(idevicenamePath, "-u", udid);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.destroyForcibly();
            return done && !out.isBlank() ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Parsea la salida de `ios-screen-capture list-devices` ("<index>\t<nombre>\t<uniqueID>" por línea). */
    private java.util.Map<Integer, String> listCaptureDevices() {
        java.util.Map<Integer, String> result = new java.util.LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(iosScreenCapturePath, "list-devices");
            pb.redirectErrorStream(false);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = DEVICE_LINE.matcher(line);
                    if (m.matches()) {
                        result.put(Integer.parseInt(m.group(1)), m.group(2).trim());
                    }
                }
            }
            // list-devices espera ~3s (activación de kCMIOHardwarePropertyAllowScreenCaptureDevices)
            // antes de imprimir — dar margen suficiente sin bloquear indefinidamente.
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            if (!done) p.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[AVFoundationMirrorProvider] error listando dispositivos: " + e.getMessage());
        }
        return result;
    }
}
