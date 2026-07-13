package qa.cinepolis.runner.mirror;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Captura de pantalla para dispositivos Android vía ADB.
 *
 * Comportamiento idéntico al que vivía antes directamente en
 * DeviceStreamServer — solo relocalizado detrás de DeviceMirrorProvider,
 * sin cambiar semántica ni concurrencia (mismo semáforo por UDID, mismos
 * timeouts, mismo comando `adb exec-out screencap -p`).
 */
public final class AndroidMirrorProvider implements DeviceMirrorProvider {

    private static final int CAPTURE_TIMEOUT_S = 5;
    private static final int MIN_PNG_BYTES     = 1_024;

    private final String adbPath;

    /** Un semáforo por dispositivo — evita ADB screencap concurrente sobre el mismo UDID. */
    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public AndroidMirrorProvider(String adbPath) {
        this.adbPath = adbPath;
    }

    @Override
    public String name() { return "ADB"; }

    @Override
    public boolean isSupported() {
        return adbPath != null && !adbPath.isBlank() && new File(adbPath).exists();
    }

    @Override
    public boolean isDeviceConnected(String udid) {
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
        // La captura ADB es stateless por-frame — nada que preparar por sesión.
        return isSupported();
    }

    @Override
    public void stop(String udid) {
        // Nada que liberar a nivel de sesión — el semáforo se gestiona por-frame en captureFrame().
    }

    /**
     * Captura un PNG en memoria.
     * Ejecuta: adb -s {udid} exec-out screencap -p
     *
     * Método intencionalmente síncrono y bloqueante — el llamador corre en un
     * hilo daemon del pool cacheado. El semáforo por-dispositivo asegura que
     * solo un screencap corra a la vez para el mismo UDID.
     */
    @Override
    public byte[] captureFrame(String udid) {
        Semaphore lock = locks.computeIfAbsent(udid, k -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            return null; // Otra captura ya en curso para este UDID
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(adbPath, "-s", udid, "exec-out", "screencap", "-p");
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream(1 << 20); // 1 MB initial
            byte[] buf  = new byte[8_192];
            int    read;
            long   deadline = System.currentTimeMillis() + (CAPTURE_TIMEOUT_S * 1_000L);

            try (InputStream in = proc.getInputStream()) {
                while ((read = in.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                    if (System.currentTimeMillis() > deadline) {
                        proc.destroyForcibly();
                        return null;
                    }
                }
            }

            boolean finished = proc.waitFor(1, TimeUnit.SECONDS);
            proc.destroyForcibly();

            if (!finished || proc.exitValue() != 0) return null;

            byte[] data = baos.toByteArray();
            return data.length >= MIN_PNG_BYTES ? data : null;

        } catch (Exception e) {
            System.err.println("[AndroidMirrorProvider] ADB capture error [" + udid + "]: " + e.getMessage());
            return null;
        } finally {
            lock.release();
        }
    }
}
