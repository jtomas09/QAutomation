package qa.cinepolis.runner;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Records screen video from a physical iOS device using
 * {@code xcrun devicectl device recordVideo --device <udid> <output.mp4>}.
 *
 * Available on Xcode 16+ / Xcode 26. Errors are logged as WARN and never
 * stop test execution. Only one recording can be active at a time.
 */
public final class IosVideoRecorder {

    private static volatile Process recordingProcess = null;
    private static volatile File    outputFile       = null;

    private IosVideoRecorder() {}

    /**
     * Starts recording the given physical iOS device. Returns the output File
     * on success, or null if recording could not be started.
     *
     * @param client      for sending status logs to the backend
     * @param executionId current execution identifier
     * @param physicalUdid 00008110-... format physical UDID
     * @param videosDir   directory where the MP4 will be written (created if absent)
     * @return output File, or null on failure
     */
    public static File start(BackendClient client, String executionId,
                             String physicalUdid, File videosDir) {
        if (physicalUdid == null || physicalUdid.isBlank()) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] No se puede grabar: UDID de dispositivo vacío");
            return null;
        }

        try {
            videosDir.mkdirs();
            File out = new File(videosDir, "ios-" + executionId + ".mp4");
            outputFile = out;

            ProcessBuilder pb = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "recordVideo",
                    "--device", physicalUdid,
                    out.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            recordingProcess = p;

            // Drain stdout to prevent OS pipe buffer from blocking the recording process
            Thread drain = new Thread(() -> {
                try { p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            }, "ios-video-drain");
            drain.setDaemon(true);
            drain.start();

            client.sendLog(executionId, "INFO",
                    "📹 [Video] Grabación iOS iniciada → " + out.getName());
            return out;

        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] No se pudo iniciar grabación iOS: " + e.getMessage());
            recordingProcess = null;
            outputFile = null;
            return null;
        }
    }

    /**
     * Stops an active recording. Sends SIGTERM and waits up to 15 s for the
     * process to flush and finalize the MP4 container. Force-kills if needed.
     * Never throws.
     */
    public static void stop(BackendClient client, String executionId) {
        Process p = recordingProcess;
        File    f = outputFile;
        recordingProcess = null;
        outputFile       = null;

        if (p == null) return;

        try {
            p.destroy(); // SIGTERM → devicectl finalizes the MP4 container
            boolean done = p.waitFor(15, TimeUnit.SECONDS);
            if (!done) p.destroyForcibly();

            if (f != null && f.exists() && f.length() > 0) {
                client.sendLog(executionId, "INFO",
                        "📹 [Video] Grabación iOS detenida → "
                        + f.getName() + " (" + f.length() / 1024 + " KB)");
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️ [Video] Archivo de video vacío o no generado"
                        + (f != null ? ": " + f.getName() : ""));
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️ [Video] Error al detener grabación iOS: " + e.getMessage());
        }
    }
}
