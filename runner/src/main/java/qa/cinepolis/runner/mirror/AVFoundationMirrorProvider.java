package qa.cinepolis.runner.mirror;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captura de pantalla para iOS vía AVFoundation + ffmpeg — el MISMO mecanismo
 * que usa QuickTime al grabar la pantalla de un iPhone conectado por USB
 * ("New Movie Recording" → seleccionar el iPhone como cámara). Sin
 * WebDriverAgent, sin Appium, sin sesión de automatización de ningún tipo.
 *
 * Proveedor PRIMARIO para iOS en macOS — máxima calidad/FPS de las tres
 * opciones (WDA, libimobiledevice, esta). Fallback: LibimobiledeviceMirrorProvider.
 *
 * ── Requisito operativo (fuera del control de este código) ──────────────
 * macOS debe reconocer al iPhone como dispositivo de captura AVFoundation.
 * Esto normalmente se activa la primera vez que el usuario abre QuickTime →
 * Archivo → Nueva grabación de película → selecciona el iPhone en el menú de
 * cámara (una sola vez por dispositivo/Mac). Si el usuario nunca hizo esto,
 * el iPhone puede no aparecer en la lista de dispositivos de ffmpeg aunque
 * esté confiado y conectado — en ese caso este provider falla limpiamente
 * (isSupported()/start() devuelven false) y el Mirror cae a
 * LibimobiledeviceMirrorProvider.
 *
 * ── Resolución de índice AVFoundation (la parte de mayor incertidumbre) ──
 * ffmpeg no expone el UDID del dispositivo en su lista de cámaras — solo un
 * nombre amigable ("Jairo's iPhone"). Este provider intenta correlacionar
 * UDID → nombre amigable vía `idevicename` (libimobiledevice) y hace match
 * por substring contra la lista de ffmpeg; si no puede resolver un match
 * único, falla limpio (no adivina un índice al azar).
 */
public final class AVFoundationMirrorProvider implements DeviceMirrorProvider {

    private static final int    CAPTURE_FPS = 15;
    private static final String OS_MAC      = "mac";

    private static final Pattern DEVICE_LINE =
            Pattern.compile("\\[(\\d+)]\\s+(.+)$");

    private final String ffmpegPath;
    private final String idevicenamePath;

    private final ConcurrentHashMap<String, FfmpegPngFrameSource> sessions = new ConcurrentHashMap<>();

    /** Sin auto-descarga (compatibilidad/pruebas) — solo busca en Homebrew/PATH. */
    public AVFoundationMirrorProvider() {
        this((String) null);
    }

    /**
     * @param agentDataDir si no es null, intenta descargar ffmpeg automáticamente
     *                     (sin acción del usuario) antes de caer a Homebrew/PATH —
     *                     ver MirrorDependencyManager.
     */
    public AVFoundationMirrorProvider(String agentDataDir) {
        String embeddedFfmpeg = null;
        if (agentDataDir != null) {
            MirrorDependencyManager deps = new MirrorDependencyManager(java.nio.file.Path.of(agentDataDir));
            if (deps.ensureFfmpeg()) embeddedFfmpeg = deps.embeddedFfmpegPath();
        }
        this.ffmpegPath      = BinaryLocator.resolve("ffmpeg", embeddedFfmpeg);
        this.idevicenamePath = BinaryLocator.resolve("idevicename");
    }

    @Override
    public String name() { return "AVFOUNDATION"; }

    @Override
    public boolean isSupported() {
        return ffmpegPath != null && System.getProperty("os.name", "").toLowerCase().contains(OS_MAC);
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

        java.util.Map<Integer, String> devices = listAvfoundationVideoDevices();
        System.out.println("[AVFoundation] Capture source detected — " + devices.size()
                + " dispositivo(s) de video en la lista de AVFoundation — udid=" + udid);

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
                ffmpegPath,
                "-f", "avfoundation",
                "-framerate", String.valueOf(CAPTURE_FPS),
                "-i", index + ":none",       // video del índice resuelto, sin audio
                "-f", "image2pipe",
                "-vcodec", "png",
                "-r", String.valueOf(CAPTURE_FPS),
                "pipe:1"
        );
        pb.redirectErrorStream(false);

        FfmpegPngFrameSource source = new FfmpegPngFrameSource(pb, "AVFoundation-" + udid);
        if (!source.start()) return false;
        System.out.println("[AVFoundation] Capture started — udid=" + udid);
        System.out.println("[AVFoundation] FFmpeg iniciado — udid=" + udid + " índice=" + index);

        // DeviceStreamServer da por perdido el stream tras ~12 capturas fallidas
        // seguidas si no detecta una compilación de WDA en curso (ver comentario
        // en el loop de MirrorHandler) — como este provider nunca toca WDA, esa
        // condición de espera nunca aplicaría y el stream se cortaría antes de
        // que ffmpeg termine de arrancar. Por eso start() BLOQUEA aquí hasta el
        // primer frame real (o hasta agotar el timeout, cayendo al siguiente
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
     * Correlaciona un UDID con un índice de dispositivo de captura de
     * AVFoundation, vía nombre amigable (idevicename) contra la lista que
     * reporta ffmpeg. Devuelve null si no hay EXACTAMENTE un match — nunca
     * adivina, y nunca toma "el primero que coincida" cuando hay ambigüedad
     * (crítico con varios iPhones conectados a la vez: dos dispositivos con el
     * mismo nombre de fábrica, p.ej. "iPhone", NO deben cruzar índices).
     *
     * Método puro (sin I/O) — extraído así específicamente para poder probar
     * la lógica de correlación/desambiguación con casos concretos de múltiples
     * dispositivos sin depender de ffmpeg/idevicename reales.
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

        // Sin nombre amigable resuelto (o sin match): si solo hay UN candidato que
        // no es una cámara integrada obvia, lo usamos como último recurso.
        java.util.List<Integer> candidates = new java.util.ArrayList<>();
        for (var entry : devices.entrySet()) {
            String lower = entry.getValue().toLowerCase();
            boolean looksBuiltIn = lower.contains("facetime") || lower.contains("built-in")
                    || lower.contains("capture screen");
            if (!looksBuiltIn) candidates.add(entry.getKey());
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
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

    /** Parsea `ffmpeg -f avfoundation -list_devices true -i ""` (la lista sale por stderr). */
    private java.util.Map<Integer, String> listAvfoundationVideoDevices() {
        java.util.Map<Integer, String> result = new java.util.LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-f", "avfoundation", "-list_devices", "true", "-i", "");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean sawVideoHeader = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AVFoundation video devices")) { sawVideoHeader = true; continue; }
                    if (line.contains("AVFoundation audio devices")) break; // fin de la sección de video
                    if (!sawVideoHeader) continue;
                    Matcher m = DEVICE_LINE.matcher(line.trim());
                    if (m.find()) {
                        result.put(Integer.parseInt(m.group(1)), m.group(2).trim());
                    }
                }
            }
            p.waitFor(3, TimeUnit.SECONDS);
            p.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[AVFoundationMirrorProvider] error listando dispositivos: " + e.getMessage());
        }
        return result;
    }
}
