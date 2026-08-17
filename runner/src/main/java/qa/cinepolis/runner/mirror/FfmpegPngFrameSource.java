package qa.cinepolis.runner.mirror;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fuente de frames PNG continua respaldada por un proceso ffmpeg de larga
 * duración (invocado con {@code -f image2pipe -vcodec png}, que concatena
 * archivos PNG completos uno tras otro en stdout, sin ningún separador
 * adicional — cada PNG termina con su propio chunk IEND).
 *
 * Reutilizada por AVFoundationMirrorProvider (macOS, iOS vía USB) y
 * ScrcpyMirrorProvider (Android vía scrcpy) — ambos alimentan un stream de
 * video continuo a ffmpeg y solo necesitan "dame el último frame ya
 * decodificado" en cada captureFrame(), a diferencia de ADB/WDA/
 * libimobiledevice, que sí soportan pedir una captura puntual bajo demanda.
 *
 * Un hilo daemon lee continuamente stdout del proceso ffmpeg y publica cada
 * frame completo en un AtomicReference — captureFrame() nunca bloquea en I/O,
 * solo lee la última referencia publicada.
 */
final class FfmpegPngFrameSource {

    private static final long STALE_MS = 3_000; // sin frame nuevo en este tiempo => se considera muerto

    private final ProcessBuilder processBuilder;
    private final String         label; // para logs, p.ej. "AVFoundation[udid]"

    private volatile Process process;
    private volatile Thread  readerThread;
    private volatile boolean stopped;

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private final AtomicLong              lastFrameAt = new AtomicLong(0L);

    FfmpegPngFrameSource(ProcessBuilder processBuilder, String label) {
        this.processBuilder = processBuilder;
        this.label          = label;
    }

    synchronized boolean start() {
        if (process != null && process.isAlive()) return true;
        try {
            stopped = false;
            process = processBuilder.start();
            readerThread = new Thread(this::readLoop, "ffmpeg-png-reader-" + label);
            readerThread.setDaemon(true);
            readerThread.start();
            return true;
        } catch (Exception e) {
            System.err.println("[FfmpegPngFrameSource][" + label + "] start error: " + e.getMessage());
            return false;
        }
    }

    private void readLoop() {
        Process p = this.process;
        if (p == null) return;
        try (InputStream in = new BufferedInputStream(p.getInputStream(), 1 << 20)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 20);
            // Ventana de los últimos 4 bytes leídos, para detectar el chunk "IEND"
            // (fin de un PNG) sin volver a copiar el buffer completo en cada byte.
            int w0 = 0, w1 = 0, w2 = 0, w3 = 0;
            int windowLen = 0;
            int b;
            boolean loggedFirstFrame = false;

            while (!stopped && (b = in.read()) != -1) {
                buf.write(b);
                w0 = w1; w1 = w2; w2 = w3; w3 = b;
                if (windowLen < 4) windowLen++;

                if (windowLen == 4 && w0 == 'I' && w1 == 'E' && w2 == 'N' && w3 == 'D') {
                    // El chunk IEND va seguido de un CRC de 4 bytes que aún no consumimos.
                    byte[] crc = new byte[4];
                    int read = 0;
                    while (read < 4) {
                        int n = in.read(crc, read, 4 - read);
                        if (n == -1) break;
                        read += n;
                    }
                    if (read == 4) buf.write(crc, 0, 4);

                    byte[] frame = buf.toByteArray();
                    latestFrame.set(frame);
                    lastFrameAt.set(System.currentTimeMillis());
                    if (!loggedFirstFrame) {
                        loggedFirstFrame = true;
                        System.out.println("[FfmpegPngFrameSource][" + label + "] primer frame decodificado ("
                                + frame.length + " bytes)");
                    }
                    buf.reset();
                    windowLen = 0;
                }
            }
        } catch (Exception e) {
            if (!stopped) {
                System.err.println("[FfmpegPngFrameSource][" + label + "] reader error: " + e.getMessage());
            }
        }

        // El bucle de lectura terminó (EOF en stdout) — si no fue por un stop()
        // deliberado, el proceso murió por su cuenta; se reporta el código de
        // salida real en vez de dejar que el llamador solo vea "sin frames".
        if (!stopped) {
            try {
                boolean exited = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                if (exited) {
                    System.err.println("[MirrorProvider] FFmpeg terminó inesperadamente — " + label);
                    System.err.println("[MirrorProvider] Código de salida: " + p.exitValue() + " — " + label);
                } else {
                    System.err.println("[FfmpegPngFrameSource][" + label
                            + "] stdout cerró pero el proceso sigue reportándose vivo (inusual)");
                }
            } catch (Exception ignored) { }
        }
    }

    /** Último frame PNG decodificado, o null si nunca llegó uno o si está obsoleto (proceso colgado). */
    byte[] latestFrame() {
        long ts = lastFrameAt.get();
        if (ts == 0L || System.currentTimeMillis() - ts > STALE_MS) return null;
        return latestFrame.get();
    }

    boolean isAlive() {
        Process p = this.process;
        return p != null && p.isAlive();
    }

    synchronized void stop() {
        stopped = true;
        if (process != null) {
            process.destroyForcibly();
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        latestFrame.set(null);
        lastFrameAt.set(0L);
    }
}
