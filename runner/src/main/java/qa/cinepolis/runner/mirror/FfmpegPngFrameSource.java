package qa.cinepolis.runner.mirror;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * solo lee la última referencia publicada. Un SEGUNDO hilo daemon lee stderr
 * por separado — ffmpeg reporta prácticamente todo su diagnóstico real ahí
 * (códecs, permisos de macOS para Screen Recording/Cámara, errores de
 * dispositivo) — sin esto, un fallo silencioso (p.ej. permiso no concedido,
 * que en macOS produce frames NEGROS en vez de un error) es invisible.
 */
final class FfmpegPngFrameSource {

    private static final long STALE_MS = 3_000; // sin frame nuevo en este tiempo => se considera muerto
    private static final int  STDERR_BUFFER_LINES = 200; // últimas N líneas conservadas para volcar en caso de fallo

    private final ProcessBuilder processBuilder;
    private final String         label; // para logs, p.ej. "AVFoundation[udid]"

    private volatile Process process;
    private volatile Thread  readerThread;
    private volatile Thread  stderrThread;
    private volatile boolean stopped;

    private final AtomicReference<byte[]> latestFrame  = new AtomicReference<>();
    private final AtomicLong              lastFrameAt  = new AtomicLong(0L);
    private final AtomicLong              bytesReceived = new AtomicLong(0L);
    private final AtomicBoolean           loggedFirstFrame = new AtomicBoolean(false);
    private final Deque<String> stderrTail = new ArrayDeque<>(); // sincronizado manualmente (synchronized en cada acceso)

    FfmpegPngFrameSource(ProcessBuilder processBuilder, String label) {
        this.processBuilder = processBuilder;
        this.label          = label;
    }

    synchronized boolean start() {
        if (process != null && process.isAlive()) return true;
        try {
            stopped = false;
            bytesReceived.set(0L);
            loggedFirstFrame.set(false);
            synchronized (stderrTail) { stderrTail.clear(); }

            String command = String.join(" ", processBuilder.command());
            process = processBuilder.start();
            System.out.println("[FFmpeg] Started PID=" + process.pid() + " — " + label);
            System.out.println("[FFmpeg] Command=" + command + " — " + label);

            readerThread = new Thread(this::readLoop, "ffmpeg-png-reader-" + label);
            readerThread.setDaemon(true);
            readerThread.start();

            stderrThread = new Thread(this::stderrLoop, "ffmpeg-stderr-reader-" + label);
            stderrThread.setDaemon(true);
            stderrThread.start();

            System.out.println("[FFmpeg] Waiting for frames — " + label);
            return true;
        } catch (Exception e) {
            System.err.println("[FfmpegPngFrameSource][" + label + "] start error: " + e.getMessage());
            return false;
        }
    }

    /** Lee stderr línea por línea — se imprime en vivo Y se conserva un buffer acotado para volcar completo si el proceso muere. */
    private void stderrLoop() {
        Process p = this.process;
        if (p == null) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFmpeg][stderr][" + label + "] " + line);
                synchronized (stderrTail) {
                    if (stderrTail.size() >= STDERR_BUFFER_LINES) stderrTail.removeFirst();
                    stderrTail.addLast(line);
                }
            }
        } catch (Exception ignored) {
            // Normal cuando el proceso se destruye (destroyForcibly cierra el stream).
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

            while (!stopped && (b = in.read()) != -1) {
                buf.write(b);
                bytesReceived.incrementAndGet();
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
                    if (read == 4) { buf.write(crc, 0, 4); bytesReceived.addAndGet(4); }

                    byte[] frame = buf.toByteArray();
                    latestFrame.set(frame);
                    lastFrameAt.set(System.currentTimeMillis());
                    if (loggedFirstFrame.compareAndSet(false, true)) {
                        logFirstFrameDiagnostics(frame);
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
        // salida real Y el stderr completo acumulado, en vez de dejar que el
        // llamador solo vea "sin frames".
        if (!stopped) {
            try {
                boolean exited = p.waitFor(2, TimeUnit.SECONDS);
                if (exited) {
                    System.err.println("[FFmpeg] Process exited code=" + p.exitValue() + " — " + label);
                    dumpStderrTail();
                } else {
                    System.err.println("[FfmpegPngFrameSource][" + label
                            + "] stdout cerró pero el proceso sigue reportándose vivo (inusual)");
                }
            } catch (Exception ignored) { }
        }
    }

    /**
     * Prueba 3 del diagnóstico — evidencia inequívoca del primer frame: tamaño
     * real, dimensiones decodificadas, y una muestra de píxeles para detectar
     * el caso MÁS COMÚN de fallo silencioso en macOS (permiso de Screen
     * Recording/Cámara no concedido al proceso ffmpeg — AVFoundation no lanza
     * ningún error, simplemente entrega frames completamente negros).
     */
    private void logFirstFrameDiagnostics(byte[] frame) {
        System.out.println("[FFmpeg] First frame received — " + label + " (" + frame.length + " bytes)");
        System.out.println("[AVFoundation] Frame received — " + label);
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frame));
            if (img == null) {
                System.err.println("[Mirror] Primer frame recibido pero ImageIO no pudo decodificarlo — " + label);
                return;
            }
            int w = img.getWidth(), h = img.getHeight();
            long sumBrightness = 0;
            int samples = 0;
            int[] xs = {w / 2, 4, w - 5, 4,     w - 5};
            int[] ys = {h / 2, 4, 4,     h - 5, h - 5};
            for (int i = 0; i < xs.length; i++) {
                if (xs[i] < 0 || ys[i] < 0 || xs[i] >= w || ys[i] >= h) continue;
                int rgb = img.getRGB(xs[i], ys[i]);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
                sumBrightness += (r + g + bl) / 3;
                samples++;
            }
            double avgBrightness = samples > 0 ? (double) sumBrightness / samples : -1;
            boolean looksBlack = avgBrightness >= 0 && avgBrightness < 8; // umbral conservador — negro real, no solo oscuro
            System.out.println("[Mirror] Primer frame — dimensiones=" + w + "x" + h
                    + " tamaño=" + frame.length + " bytes brilloPromedioMuestra=" + String.format("%.1f", avgBrightness)
                    + " ¿parece negro?=" + looksBlack + " — " + label);
            if (looksBlack) {
                System.err.println("[Mirror] ADVERTENCIA: el primer frame parece completamente negro. "
                        + "Causa más común en macOS: permiso de Screen Recording (o Cámara, según el dispositivo "
                        + "AVFoundation resuelto) no concedido al proceso del Runner/ffmpeg — revisar Ajustes del "
                        + "Sistema > Privacidad y Seguridad > Grabación de Pantalla. AVFoundation NO reporta un "
                        + "error en este caso, solo entrega frames negros. — " + label);
            }
        } catch (Exception e) {
            System.err.println("[Mirror] Error al analizar el primer frame para diagnóstico: " + e.getMessage() + " — " + label);
        }
    }

    private void dumpStderrTail() {
        java.util.List<String> lines;
        synchronized (stderrTail) { lines = new java.util.ArrayList<>(stderrTail); }
        System.err.println("[FFmpeg][stderr-completo] ── inicio (" + lines.size() + " líneas) — " + label + " ──");
        for (String line : lines) System.err.println("[FFmpeg][stderr-completo] " + line);
        System.err.println("[FFmpeg][stderr-completo] ── fin — " + label + " ──");
    }

    /** Último frame PNG decodificado, o null si nunca llegó uno o si está obsoleto (proceso colgado). */
    byte[] latestFrame() {
        long ts = lastFrameAt.get();
        if (ts == 0L || System.currentTimeMillis() - ts > STALE_MS) return null;
        return latestFrame.get();
    }

    /** Bytes crudos recibidos por stdout hasta ahora — diagnóstico ("Prueba 1/2": ¿realmente llegan datos?). */
    long bytesReceived() {
        return bytesReceived.get();
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
        if (stderrThread != null) {
            stderrThread.interrupt();
        }
        latestFrame.set(null);
        lastFrameAt.set(0L);
    }
}
