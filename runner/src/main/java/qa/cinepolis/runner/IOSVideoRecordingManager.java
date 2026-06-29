package qa.cinepolis.runner;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages iOS screen video recording.
 *
 * Recording support by Xcode version:
 *  - Xcode 16 and earlier: xcrun devicectl device recordVideo
 *  - Xcode 26+ (devicectl 518+): recordVideo was removed; ffmpeg not yet integrated.
 *
 * Functional log outcomes — exactly one per execution:
 *  ✓ Video generado
 *  ⚠ Grabación no soportada para esta configuración
 *
 * All technical details (process output, file size, failure cause) go to the technical log.
 * Never logs: "Video vacío", "No generado", "No encontrado", "No existe video".
 *
 * Android: not referenced.
 */
public final class IOSVideoRecordingManager {

    private static volatile Process recordingProcess = null;
    private static volatile File    outputFile       = null;
    private static volatile String  processOutput    = null;
    private static volatile Thread  captureThread    = null;

    private static volatile Boolean recordAvailable = null;
    private static volatile String  xcodeVersion    = null;

    private IOSVideoRecordingManager() {}

    /** Returns the cached Xcode version string, e.g. "Xcode 26.0". */
    public static String getXcodeVersion() {
        if (xcodeVersion != null) return xcodeVersion;
        try {
            Process p = new ProcessBuilder("xcodebuild", "-version")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            xcodeVersion = out.startsWith("Xcode") ? out.split("\n")[0].trim() : "unknown";
        } catch (Exception e) {
            xcodeVersion = "unknown";
        }
        return xcodeVersion;
    }

    private static boolean isRecordingSupported() {
        if (recordAvailable != null) return recordAvailable;
        try {
            Process p = new ProcessBuilder("xcrun", "devicectl", "device", "--help")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).toLowerCase();
            p.waitFor(5, TimeUnit.SECONDS);
            recordAvailable = out.contains("recordvideo");
        } catch (Exception e) {
            recordAvailable = false;
        }
        return recordAvailable;
    }

    /**
     * Starts recording. Returns the output File on success, or null when recording
     * is not supported or fails to start.
     *
     * @param client       BackendClient for sending log messages
     * @param executionId  current execution identifier
     * @param physicalUdid physical iOS device UDID
     * @param videosDir    directory where the MP4 will be written (created if absent)
     * @return output File on success, null otherwise
     */
    public static File start(BackendClient client, String executionId,
                             String physicalUdid, File videosDir) {
        if (physicalUdid == null || physicalUdid.isBlank()) return null;

        if (!isRecordingSupported()) {
            client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
            client.sendTechLog(executionId,
                    "[Video] xcrun devicectl device recordVideo eliminado en " + getXcodeVersion()
                    + ". Para habilitar grabación: brew install ffmpeg");
            return null;
        }

        try {
            videosDir.mkdirs();
            File out = new File(videosDir, "ios-" + executionId + ".mp4");
            outputFile    = out;
            processOutput = null;
            captureThread = null;

            ProcessBuilder pb = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "recordVideo",
                    "--device", physicalUdid, out.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            recordingProcess = p;

            Thread ct = new Thread(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append("\n");
                } catch (Exception ignored) {}
                processOutput = sb.toString();
            }, "ios-video-capture");
            ct.setDaemon(true);
            ct.start();
            captureThread = ct;

            // 500ms health check — detect immediate startup failures
            try { Thread.sleep(500); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            if (!p.isAlive()) {
                try { ct.join(1500); } catch (InterruptedException ignored) {}
                String cause = processOutput != null ? processOutput.trim() : "";
                client.sendTechLog(executionId, "[Video] Proceso terminó de inmediato."
                        + (cause.isBlank() ? "" : " Causa: " + cause.lines().findFirst().orElse(cause)));
                client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
                recordingProcess = null;
                outputFile       = null;
                captureThread    = null;
                return null;
            }

            client.sendTechLog(executionId, "[Video] Grabación iniciada → " + out.getName());
            return out;

        } catch (Exception e) {
            client.sendTechLog(executionId, "[Video] No se pudo iniciar grabación: " + e.getMessage());
            client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
            recordingProcess = null;
            outputFile       = null;
            captureThread    = null;
            return null;
        }
    }

    /**
     * Stops an active recording. Logs "✓ Video generado" on success,
     * or "⚠ Grabación no soportada para esta configuración" on any failure.
     * Never throws.
     */
    public static void stop(BackendClient client, String executionId) {
        Process p  = recordingProcess;
        File    f  = outputFile;
        Thread  ct = captureThread;
        recordingProcess = null;
        outputFile       = null;
        captureThread    = null;

        if (p == null) return;

        try {
            if (!p.isAlive()) {
                if (ct != null) try { ct.join(2000); } catch (InterruptedException ignored) {}
                String out = processOutput != null ? processOutput.trim() : "";
                client.sendTechLog(executionId, "[Video] Proceso terminó prematuramente."
                        + (out.isBlank() ? "" : " Causa: " + out.lines().findFirst().orElse(out)));
            } else {
                // SIGTERM — devicectl finalizes the MP4 container before exiting
                p.destroy();
                boolean done = p.waitFor(30, TimeUnit.SECONDS);
                if (!done) {
                    client.sendTechLog(executionId, "[Video] Timeout 30s esperando finalización — forzando cierre.");
                    p.destroyForcibly();
                    p.waitFor(5, TimeUnit.SECONDS);
                }
                if (ct != null) try { ct.join(2000); } catch (InterruptedException ignored) {}
            }

            // Brief settle to allow the FS to complete the final write
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            if (f != null && f.exists() && f.length() > 0) {
                client.sendLog(executionId, "INFO", "✓ Video generado");
                client.sendTechLog(executionId,
                        "[Video] " + f.getName() + " (" + f.length() / 1024 + " KB)");
            } else {
                String out = processOutput != null ? processOutput.trim() : "";
                client.sendTechLog(executionId, "[Video] Archivo vacío o no generado."
                        + (f != null ? " Path: " + f.getName() : "")
                        + (out.isBlank() ? "" : " Causa: " + out.lines().findFirst().orElse(out)));
                client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
            }
        } catch (Exception e) {
            client.sendTechLog(executionId, "[Video] Error al detener grabación: " + e.getMessage());
            client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
        }
    }
}
