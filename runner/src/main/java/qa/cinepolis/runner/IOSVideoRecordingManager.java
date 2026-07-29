package qa.cinepolis.runner;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages iOS screen video recording.
 *
 * Recording mechanism by Xcode version:
 *  - Xcode 16 y anteriores: xcrun devicectl device recordVideo (eliminado en
 *    Xcode 26+ / devicectl 518+ — el subcomando ya no existe, "--help" no lo
 *    lista).
 *  - Xcode 26+ (este archivo): ffmpeg -f avfoundation. Cuando un iPhone/iPad
 *    está conectado por USB y es de confianza ("Trust This Computer"), macOS
 *    lo expone como un dispositivo de captura de video más — el MISMO
 *    mecanismo que usa QuickTime Player para "New Movie Recording" con un
 *    iPhone como fuente. ffmpeg puede grabarlo directamente sin pasar por
 *    devicectl en absoluto. Requiere ffmpeg instalado (brew install ffmpeg);
 *    a diferencia del enfoque anterior, esto SÍ se implementa (antes solo se
 *    sugería instalarlo en el mensaje de log, sin ningún código que lo usara).
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

    private static volatile Boolean xcodeVersionKnown = null;
    private static volatile String  xcodeVersion       = null;

    /** Rutas donde Homebrew instala ffmpeg — un LaunchAgent no hereda el PATH
     *  interactivo del usuario (no incluye /opt/homebrew/bin ni /usr/local/bin),
     *  así que "ffmpeg" a secas puede no resolverse aunque SÍ esté instalado.
     *  Se conservan como fallback de desarrollo (máquina con Homebrew manual);
     *  la ruta embebida (AGENT_DATA_DIR/runtime/ffmpeg/ffmpeg, ver
     *  resolveFfmpegBin()) es la resolución primaria en producción — el usuario
     *  final NUNCA instala nada manualmente, igual que Node/Appium/JRE. */
    private static final String[] FFMPEG_CANDIDATES = {
            "/opt/homebrew/bin/ffmpeg", // Homebrew, Apple Silicon
            "/usr/local/bin/ffmpeg",    // Homebrew, Intel
            "ffmpeg"                    // fallback: PATH del proceso, por si acaso
    };
    private static volatile String ffmpegBin = null; // resuelto una vez, cacheado

    private IOSVideoRecordingManager() {}

    /** Returns the cached Xcode version string, e.g. "Xcode 26.0". */
    public static String getXcodeVersion() {
        if (xcodeVersionKnown != null) return xcodeVersion;
        try {
            Process p = new ProcessBuilder("xcodebuild", "-version")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            xcodeVersion = out.startsWith("Xcode") ? out.split("\n")[0].trim() : "unknown";
        } catch (Exception e) {
            xcodeVersion = "unknown";
        }
        xcodeVersionKnown = true;
        return xcodeVersion;
    }

    /**
     * Resuelve y cachea la ruta absoluta de ffmpeg — null si no está instalado en
     * ninguna ruta conocida. Mismo orden de resolución que NODE_BIN/APPIUM_BIN
     * (ver AppiumManager.resolveNodeBin()):
     *   1. AGENT_DATA_DIR/runtime/ffmpeg/ffmpeg  (embebido por el instalador — primario)
     *   2. FFMPEG_BIN system property            (override explícito)
     *   3. Rutas de Homebrew / PATH del proceso  (fallback de desarrollo)
     */
    private static String resolveFfmpegBin() {
        if (ffmpegBin != null) return ffmpegBin;

        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            String embedded = agentDataDir + "/runtime/ffmpeg/ffmpeg";
            if (probarFfmpeg(embedded)) {
                ffmpegBin = embedded;
                return ffmpegBin;
            }
        }

        String prop = System.getProperty("FFMPEG_BIN", "");
        if (!prop.isBlank() && probarFfmpeg(prop)) {
            ffmpegBin = prop;
            return ffmpegBin;
        }

        for (String candidate : FFMPEG_CANDIDATES) {
            if (probarFfmpeg(candidate)) {
                ffmpegBin = candidate;
                return ffmpegBin;
            }
        }
        return null;
    }

    private static boolean probarFfmpeg(String candidate) {
        try {
            Process p = new ProcessBuilder(candidate, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes(); // drenar para no bloquear
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false; // binario no encontrado/ejecutable en esta ruta
        }
    }

    // Formato real de "ffmpeg -f avfoundation -list_devices true -i ''":
    //   [AVFoundation indev @ 0x...] AVFoundation video devices:
    //   [AVFoundation indev @ 0x...] [0] FaceTime HD Camera
    //   [AVFoundation indev @ 0x...] [1] iPhone de Tester
    private static final Pattern AVF_DEVICE_LINE = Pattern.compile("\\[(\\d+)]\\s+(.+)$");

    /**
     * Busca, en la lista de dispositivos de captura de avfoundation, el índice
     * correspondiente al iPhone/iPad conectado — por NOMBRE (avfoundation no
     * expone el UDID directamente, solo el nombre visible en Ajustes/Finder,
     * p. ej. "iPhone de Tester"), cruzado contra el deviceName real que ya
     * resuelve IOSDeviceScanner a partir del UDID (que sí es la fuente de
     * verdad — evita adivinar por posición si hay más de un dispositivo/webcam).
     *
     * @return el índice avfoundation, o -1 si no se encontró.
     */
    private static int findAvfoundationDeviceIndex(String ffmpeg, String physicalUdid) {
        String deviceName = null;
        try {
            for (Map<String, String> d : IOSDeviceScanner.scan()) {
                if (physicalUdid.equalsIgnoreCase(d.get("udid"))) {
                    deviceName = d.get("deviceName");
                    break;
                }
            }
        } catch (Exception ignored) {
            // Si el escaneo falla, se sigue intentando por heurística de nombre más abajo.
        }

        try {
            Process p = new ProcessBuilder(ffmpeg, "-f", "avfoundation", "-list_devices", "true", "-i", "")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(8, TimeUnit.SECONDS);

            boolean inVideoSection = false;
            for (String line : out.split("\n")) {
                if (line.contains("AVFoundation video devices")) { inVideoSection = true; continue; }
                if (line.contains("AVFoundation audio devices")) { inVideoSection = false; continue; }
                if (!inVideoSection) continue;

                Matcher m = AVF_DEVICE_LINE.matcher(line.trim());
                if (!m.find()) continue;
                int    idx  = Integer.parseInt(m.group(1));
                String name = m.group(2).trim();

                boolean matches = deviceName != null && !deviceName.isBlank()
                        && (deviceName.toLowerCase().contains(name.toLowerCase())
                            || name.toLowerCase().contains(deviceName.split(" ")[0].toLowerCase()));
                // Heurística de respaldo si IOSDeviceScanner no devolvió nombre: cualquier
                // entrada que no sea una cámara/pantalla conocida del propio Mac.
                boolean looksLikeIosDevice = name.toLowerCase().contains("iphone") || name.toLowerCase().contains("ipad");
                boolean looksLikeBuiltin   = name.toLowerCase().contains("facetime")
                        || name.toLowerCase().contains("capture screen")
                        || name.toLowerCase().contains("desktop");

                if (matches || (deviceName == null && looksLikeIosDevice && !looksLikeBuiltin)) {
                    return idx;
                }
            }
        } catch (Exception ignored) {
            // Sin ffmpeg funcional o timeout — se reporta como "no encontrado" más arriba.
        }
        return -1;
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

        String ffmpeg = resolveFfmpegBin();
        if (ffmpeg == null) {
            client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
            client.sendTechLog(executionId,
                    "[Video] ffmpeg no encontrado (probado: " + String.join(", ", FFMPEG_CANDIDATES) + "). "
                    + "xcrun devicectl device recordVideo ya no existe en " + getXcodeVersion()
                    + " — instala ffmpeg: brew install ffmpeg");
            return null;
        }

        int deviceIndex = findAvfoundationDeviceIndex(ffmpeg, physicalUdid);
        if (deviceIndex < 0) {
            client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
            client.sendTechLog(executionId,
                    "[Video] El dispositivo " + physicalUdid + " no aparece en la lista de captura de "
                    + "avfoundation (macOS). Verifica que esté conectado por USB y sea de confianza "
                    + "(\"Trust This Computer\") — sin eso, macOS no lo expone como cámara/pantalla capturable.");
            return null;
        }

        try {
            videosDir.mkdirs();
            File out = new File(videosDir, "ios-" + executionId + ".mp4");
            outputFile    = out;
            processOutput = null;
            captureThread = null;

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y",
                    "-f", "avfoundation",
                    "-framerate", "30",
                    "-i", deviceIndex + ":none", // video device N, sin audio
                    "-pix_fmt", "yuv420p",
                    "-vcodec", "libx264",
                    "-preset", "ultrafast",
                    out.getAbsolutePath());
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

            // ffmpeg necesita más que 500ms para abrir el dispositivo avfoundation la
            // primera vez (negociación de formato) — 500ms bastaba para devicectl, que
            // arrancaba casi instantáneo; aquí un falso negativo reportaría "no
            // soportado" en una grabación que en realidad solo tardó un poco más en
            // levantar. 1500ms da margen real sin alargar perceptiblemente el inicio.
            try { Thread.sleep(1500); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            if (!p.isAlive()) {
                try { ct.join(1500); } catch (InterruptedException ignored) {}
                String cause = processOutput != null ? processOutput.trim() : "";
                client.sendTechLog(executionId, "[Video] Proceso terminó de inmediato."
                        + (cause.isBlank() ? "" : " Causa: " + cause.lines().reduce((a, b) -> b).orElse(cause)));
                client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
                recordingProcess = null;
                outputFile       = null;
                captureThread    = null;
                return null;
            }

            client.sendTechLog(executionId, "[Video] Grabación iniciada (ffmpeg/avfoundation, device index="
                    + deviceIndex + ") → " + out.getName());
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
                        + (out.isBlank() ? "" : " Causa: " + out.lines().reduce((a, b) -> b).orElse(out)));
            } else {
                // SIGTERM — ffmpeg trata SIGTERM/SIGINT igual: finaliza el contenedor MP4
                // (escribe el moov atom/trailer) antes de salir, en vez de dejar un
                // archivo corrupto — mismo comportamiento que Ctrl+C interactivo.
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
                        + (out.isBlank() ? "" : " Causa: " + out.lines().reduce((a, b) -> b).orElse(out)));
                client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
            }
        } catch (Exception e) {
            client.sendTechLog(executionId, "[Video] Error al detener grabación: " + e.getMessage());
            client.sendLog(executionId, "WARN", "⚠ Grabación no soportada para esta configuración");
        }
    }
}
