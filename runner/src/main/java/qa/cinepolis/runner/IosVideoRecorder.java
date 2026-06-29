package qa.cinepolis.runner;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Records screen video from a physical iOS device using
 * {@code xcrun devicectl device recordVideo --device <udid> <output.mp4>}.
 *
 * Available on Xcode 16+ / Xcode 26. Errors are logged as WARN and never
 * stop test execution. Only one recording can be active at a time.
 *
 * Process output is captured (not discarded) so that startup failures and
 * runtime errors are visible in the log when the video file is missing or empty.
 */
public final class IosVideoRecorder {

    private static volatile Process recordingProcess = null;
    private static volatile File    outputFile       = null;
    private static volatile String  processOutput    = null;
    private static volatile Thread  captureThread    = null;

    // Cached result of the recordVideo availability check (null = not yet checked).
    private static volatile Boolean recordVideoAvailable = null;

    private IosVideoRecorder() {}

    /**
     * Returns true if `xcrun devicectl device recordVideo` exists on this system.
     * The subcommand was removed in Xcode 26 (devicectl 518+). Result is cached.
     */
    private static boolean isRecordVideoAvailable() {
        if (recordVideoAvailable != null) return recordVideoAvailable;
        try {
            Process p = new ProcessBuilder("xcrun", "devicectl", "device", "--help")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).toLowerCase();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            recordVideoAvailable = out.contains("recordvideo");
        } catch (Exception e) {
            recordVideoAvailable = false;
        }
        return recordVideoAvailable;
    }

    /**
     * Starts recording the given physical iOS device. Returns the output File
     * on success, or null if recording could not be started.
     *
     * @param client       for sending status logs to the backend
     * @param executionId  current execution identifier
     * @param physicalUdid 00008110-... format physical UDID
     * @param videosDir    directory where the MP4 will be written (created if absent)
     * @return output File, or null on failure
     */
    public static File start(BackendClient client, String executionId,
                             String physicalUdid, File videosDir) {
        if (physicalUdid == null || physicalUdid.isBlank()) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] No se puede grabar: UDID de dispositivo vacío");
            return null;
        }

        // xcrun devicectl device recordVideo was removed in Xcode 26 (devicectl 518+).
        // Detect this before creating the output directory so uploadVideos() stays quiet.
        if (!isRecordVideoAvailable()) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] Grabación de video iOS no disponible: "
                    + "xcrun devicectl device recordVideo fue eliminado en Xcode 26.\n"
                    + "   Para habilitar grabación instala ffmpeg: brew install ffmpeg");
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
                    "--device", physicalUdid,
                    out.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            recordingProcess = p;

            // Capture process output for error diagnosis — replaces drain-to-null so we
            // can report the exact reason if the process exits or the file ends up empty.
            Thread ct = new Thread(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
                processOutput = sb.toString();
            }, "ios-video-capture");
            ct.setDaemon(true);
            ct.start();
            captureThread = ct;

            // 500 ms health-check — detect immediate startup failures
            // (wrong UDID, device not reachable, command not found, etc.)
            try { Thread.sleep(500); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            if (!p.isAlive()) {
                try { ct.join(1500); } catch (InterruptedException ignored) {}
                String out2  = processOutput != null ? processOutput.trim() : "";
                String cause = out2.isBlank() ? "sin detalles"
                        : out2.lines().findFirst().orElse(out2);
                client.sendLog(executionId, "WARN",
                        "⚠️ [Video] xcrun devicectl recordVideo terminó de inmediato. Causa: " + cause);
                recordingProcess = null;
                outputFile       = null;
                captureThread    = null;
                return null;
            }

            client.sendLog(executionId, "INFO",
                    "📹 [Video] Grabación iOS iniciada → " + out.getName());
            return out;

        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] No se pudo iniciar grabación iOS: " + e.getMessage());
            recordingProcess = null;
            outputFile       = null;
            captureThread    = null;
            return null;
        }
    }

    /**
     * Stops an active recording. Sends SIGTERM and waits up to 30 s for devicectl
     * to flush and finalize the MP4 container. Force-kills if needed.
     * Validates that the output file exists and has non-zero size.
     * Logs the captured process output when the file is empty.
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

        client.sendLog(executionId, "INFO", "Finalizando grabación...");

        try {
            if (!p.isAlive()) {
                // Process died before stop() was called
                if (ct != null) try { ct.join(2000); } catch (InterruptedException ignored) {}
                String output = processOutput != null ? processOutput.trim() : "";
                String cause  = output.isBlank() ? ""
                        : " Causa: " + output.lines().findFirst().orElse(output);
                client.sendLog(executionId, "WARN",
                        "⚠️ [Video] El proceso de grabación terminó prematuramente." + cause);
            } else {
                // SIGTERM — devicectl finalizes the MP4 container before exiting
                p.destroy();
                boolean done = p.waitFor(30, TimeUnit.SECONDS);
                if (!done) {
                    client.sendLog(executionId, "WARN",
                            "⚠️ [Video] Timeout (30 s) esperando finalización de grabación — forzando término.");
                    p.destroyForcibly();
                    p.waitFor(5, TimeUnit.SECONDS);
                }
                // Let the capture thread finish reading remaining output
                if (ct != null) try { ct.join(2000); } catch (InterruptedException ignored) {}
            }

            // Brief settle to allow the FS to complete the final write
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            if (f != null && f.exists() && f.length() > 0) {
                client.sendLog(executionId, "INFO",
                        "✓ Video guardado (" + f.length() / 1024 + " KB)");
            } else {
                String output = processOutput != null ? processOutput.trim() : "";
                String cause  = output.isBlank() ? ""
                        : " Causa: " + output.lines().findFirst().orElse(output);
                client.sendLog(executionId, "WARN",
                        "⚠️ [Video] Archivo de video vacío o no generado"
                        + (f != null ? ": " + f.getName() : "") + cause);
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] Error al detener grabación iOS: " + e.getMessage());
        }
    }
}
