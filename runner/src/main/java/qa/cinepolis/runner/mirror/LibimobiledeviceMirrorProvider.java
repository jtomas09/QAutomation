package qa.cinepolis.runner.mirror;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Captura de pantalla para iOS vía {@code idevicescreenshot} (libimobiledevice)
 * — USB directo, sin WebDriverAgent, sin Appium, sin sesión de automatización
 * de ningún tipo. Fallback de AVFoundationMirrorProvider cuando este no está
 * disponible (Runner no-macOS, ffmpeg ausente, o el índice de captura AV no
 * se pudo resolver para este UDID).
 *
 * Mismo patrón síncrono de un frame por invocación que AndroidMirrorProvider
 * (spawnea un proceso nuevo por captura) — a diferencia de
 * AVFoundationMirrorProvider/ScrcpyMirrorProvider, que mantienen un proceso
 * de video continuo, idevicescreenshot SÍ soporta "toma una captura ahora",
 * así que no necesita FfmpegPngFrameSource.
 *
 * Formato de salida real: idevicescreenshot escribe TIFF por defecto en
 * versiones antiguas de libimobiledevice, y convierte a PNG automáticamente
 * cuando el archivo destino termina en ".png" (requiere libpng en el build de
 * Homebrew — el caso común). Se pide explícitamente ".png"; si la build del
 * usuario no soporta esa conversión, la captura fallará limpiamente (devuelve
 * null) y MirrorProviderRegistry no tiene más fallback para iOS después de
 * este — el mirror mostrará el overlay de "no disponible", nunca WDA.
 */
public final class LibimobiledeviceMirrorProvider implements DeviceMirrorProvider {

    private static final int CAPTURE_TIMEOUT_S = 5;
    private static final int MIN_PNG_BYTES     = 1_024;

    private final String screenshotBinPath;
    private final String deviceIdBinPath;

    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public LibimobiledeviceMirrorProvider() {
        this.screenshotBinPath = BinaryLocator.resolve("idevicescreenshot");
        this.deviceIdBinPath   = BinaryLocator.resolve("idevice_id");
    }

    @Override
    public String name() { return "LIBIMOBILEDEVICE"; }

    @Override
    public boolean isSupported() {
        return screenshotBinPath != null;
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        if (deviceIdBinPath == null) return true; // sin forma de verificar; no bloquear por esto
        try {
            ProcessBuilder pb = new ProcessBuilder(deviceIdBinPath, "-l");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            String  out  = new String(p.getInputStream().readAllBytes());
            p.destroyForcibly();
            return done && out.contains(udid);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean start(String udid) {
        // Captura stateless por-frame — nada que preparar por sesión.
        return isSupported();
    }

    @Override
    public void stop(String udid) {
        // Nada que liberar a nivel de sesión.
    }

    @Override
    public byte[] captureFrame(String udid) {
        if (screenshotBinPath == null) return null;
        Semaphore lock = locks.computeIfAbsent(udid, k -> new Semaphore(1));
        if (!lock.tryAcquire()) return null; // otra captura ya en curso para este UDID

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("mirror-ios-", ".png");
            ProcessBuilder pb = new ProcessBuilder(
                    screenshotBinPath, "-u", udid, tempFile.toString());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().readAllBytes(); // drena stdout/stderr combinados

            boolean finished = proc.waitFor(CAPTURE_TIMEOUT_S, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return null;
            }
            if (proc.exitValue() != 0) return null;

            byte[] data = Files.readAllBytes(tempFile);
            return data.length >= MIN_PNG_BYTES ? data : null;

        } catch (Exception e) {
            System.err.println("[LibimobiledeviceMirrorProvider] capture error [" + udid + "]: " + e.getMessage());
            return null;
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) { }
            }
            lock.release();
        }
    }
}
