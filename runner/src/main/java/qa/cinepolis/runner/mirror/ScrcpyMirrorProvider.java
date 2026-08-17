package qa.cinepolis.runner.mirror;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Captura de pantalla para Android vía scrcpy — mayor calidad/latencia que
 * `adb exec-out screencap` (AndroidMirrorProvider), que queda como fallback
 * automático cuando scrcpy no está disponible.
 *
 * A diferencia de ADB screencap (una captura PNG completa por invocación),
 * scrcpy transmite video H.264 continuo del dispositivo — este provider lo
 * graba hacia un FIFO (pipe con nombre) y usa ffmpeg para decodificarlo a un
 * stream continuo de frames PNG (ver FfmpegPngFrameSource), reutilizando el
 * mismo mecanismo que AVFoundationMirrorProvider (iOS).
 *
 * ── Parte de mayor incertidumbre — validar con hardware real ─────────────
 * El contenedor MKV está diseñado para ser reproducible progresivamente (por
 * eso funciona para streaming en vivo), así que ffmpeg debería poder ir
 * decodificando frames del FIFO a medida que scrcpy escribe — pero esta
 * combinación específica (scrcpy --record hacia un FIFO, leído en vivo por
 * ffmpeg) no se ha probado contra un dispositivo real en este entorno. El
 * nombre exacto de la flag para modo sin ventana puede variar entre
 * versiones de scrcpy (`--no-window` aquí; versiones más nuevas podrían
 * usar otro nombre) — si scrcpy no arranca, revisar `scrcpy --help` de la
 * versión instalada.
 *
 * mkfifo es una utilidad POSIX — este provider se autodescarta en Windows
 * (isSupported() devuelve false); AndroidMirrorProvider (ADB directo) sigue
 * cubriendo Android en Runners Windows sin cambios.
 */
public final class ScrcpyMirrorProvider implements DeviceMirrorProvider {

    private static final int CAPTURE_FPS = 15;
    private static final int MAX_SIZE_PX = 720;

    private final String scrcpyPath;
    private final String ffmpegPath;
    private final String adbPath;

    private static final class Session {
        Process               scrcpyProcess;
        FfmpegPngFrameSource  frameSource;
        Path                  fifoPath;
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /** Sin auto-descarga (compatibilidad/pruebas) — solo busca en Homebrew/PATH. */
    public ScrcpyMirrorProvider(String adbPath) {
        this(adbPath, null);
    }

    /**
     * @param agentDataDir si no es null, intenta descargar scrcpy y ffmpeg
     *                     automáticamente (sin acción del usuario) antes de
     *                     caer a Homebrew/PATH — ver MirrorDependencyManager.
     */
    public ScrcpyMirrorProvider(String adbPath, String agentDataDir) {
        String embeddedScrcpy = null;
        String embeddedFfmpeg = null;
        if (agentDataDir != null) {
            MirrorDependencyManager deps = new MirrorDependencyManager(java.nio.file.Path.of(agentDataDir));
            if (deps.ensureScrcpy()) embeddedScrcpy = deps.embeddedScrcpyPath();
            if (deps.ensureFfmpeg()) embeddedFfmpeg = deps.embeddedFfmpegPath();
        }
        this.scrcpyPath = BinaryLocator.resolve("scrcpy", embeddedScrcpy);
        this.ffmpegPath = BinaryLocator.resolve("ffmpeg", embeddedFfmpeg);
        this.adbPath    = adbPath;
    }

    @Override
    public String name() { return "SCRCPY"; }

    @Override
    public boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean posix = os.contains("mac") || os.contains("nux") || os.contains("nix");
        return posix && scrcpyPath != null && ffmpegPath != null;
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        if (adbPath == null) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(adbPath, "-s", udid, "get-state");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            String  out  = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.destroyForcibly();
            return done && "device".equalsIgnoreCase(out);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean start(String udid) {
        if (!isSupported()) return false;
        Session existing = sessions.get(udid);
        if (existing != null && existing.frameSource != null && existing.frameSource.isAlive()) return true;

        System.out.println("[MirrorProvider][Scrcpy] Creando sesión — udid=" + udid);

        Path fifoDir = null;
        try {
            fifoDir = Files.createTempDirectory("scrcpy-mirror-");
            Path fifo = fifoDir.resolve(sanitize(udid) + ".mkv");

            Process mkfifo = new ProcessBuilder("mkfifo", fifo.toString()).start();
            boolean mkDone = mkfifo.waitFor(3, TimeUnit.SECONDS);
            if (!mkDone || mkfifo.exitValue() != 0) {
                System.err.println("[ScrcpyMirrorProvider] mkfifo falló para udid=" + udid);
                return false;
            }

            ProcessBuilder scrcpyPb = new ProcessBuilder(
                    scrcpyPath,
                    "-s", udid,
                    "--no-window",
                    "--no-audio",
                    "--max-size=" + MAX_SIZE_PX,
                    "--max-fps=" + CAPTURE_FPS,
                    "--record=" + fifo,
                    "--record-format=mkv"
            );
            if (adbPath != null) scrcpyPb.environment().put("ADB", adbPath);
            scrcpyPb.redirectErrorStream(true);
            Process scrcpyProc = scrcpyPb.start();

            ProcessBuilder ffmpegPb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", fifo.toString(),
                    "-f", "image2pipe",
                    "-vcodec", "png",
                    "-r", String.valueOf(CAPTURE_FPS),
                    "pipe:1"
            );
            ffmpegPb.redirectErrorStream(false);
            FfmpegPngFrameSource frameSource = new FfmpegPngFrameSource(ffmpegPb, "Scrcpy-" + udid);

            if (!frameSource.start()) {
                scrcpyProc.destroyForcibly();
                deleteQuietly(fifo, fifoDir);
                return false;
            }
            System.out.println("[Scrcpy] FFmpeg iniciado — udid=" + udid);

            // Igual que AVFoundationMirrorProvider: bloquea hasta el primer frame
            // real (o cae al siguiente candidato) para no depender de la
            // tolerancia a fallos de DeviceStreamServer, que solo sabe esperar por
            // una compilación de WDA en curso — algo que este provider nunca usa.
            if (!waitForFirstFrame(frameSource)) {
                frameSource.stop();
                scrcpyProc.destroyForcibly();
                deleteQuietly(fifo, fifoDir);
                return false;
            }

            System.out.println("[Mirror] Primer frame recibido — udid=" + udid + " provider=Scrcpy");
            Session session = new Session();
            session.scrcpyProcess = scrcpyProc;
            session.frameSource   = frameSource;
            session.fifoPath      = fifo;
            sessions.put(udid, session);
            return true;

        } catch (Exception e) {
            System.err.println("[ScrcpyMirrorProvider] start error [" + udid + "]: " + e.getMessage());
            if (fifoDir != null) deleteQuietly(fifoDir);
            return false;
        }
    }

    private static boolean waitForFirstFrame(FfmpegPngFrameSource source) {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (source.latestFrame() != null) return true;
            if (!source.isAlive()) return false;
            try { Thread.sleep(100); } catch (InterruptedException ignored) { return false; }
        }
        return false;
    }

    @Override
    public void stop(String udid) {
        Session session = sessions.remove(udid);
        if (session == null) return;
        if (session.frameSource != null) session.frameSource.stop();
        if (session.scrcpyProcess != null) session.scrcpyProcess.destroyForcibly();
        if (session.fifoPath != null) deleteQuietly(session.fifoPath, session.fifoPath.getParent());
        System.out.println("[Mirror] Sesión finalizada — udid=" + udid + " provider=Scrcpy");
    }

    @Override
    public byte[] captureFrame(String udid) {
        Session session = sessions.get(udid);
        return session != null && session.frameSource != null ? session.frameSource.latestFrame() : null;
    }

    private static String sanitize(String udid) {
        return udid.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static void deleteQuietly(Path... paths) {
        for (Path p : paths) {
            if (p == null) continue;
            try { Files.deleteIfExists(p); } catch (Exception ignored) { }
        }
    }
}
