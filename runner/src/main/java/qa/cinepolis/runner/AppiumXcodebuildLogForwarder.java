package qa.cinepolis.runner;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae y reenvía al backend la salida CRUDA de xcodebuild que Appium ya
 * captura en su propio log de servidor (appium.log, gracias a la capability
 * showXcodeLog=true) — salida que hasta ahora nunca llegaba al Dashboard.
 *
 * Por qué faltaba: el servidor Appium es un proceso Node.js separado y de
 * larga duración (arrancado una sola vez por AppiumManager, puerto 4723).
 * El xcodebuild que compila WebDriverAgent lo lanza ESE proceso internamente
 * — su salida se escribe en appium.log, no en el stdout del proceso
 * Gradle/JUnit que JobExecutor sí reenvía al backend. El resumen que llega
 * hoy al usuario ("xcodebuild failed with code 65...") es solo el mensaje de
 * la excepción que Appium propaga por HTTP, no la causa real que Xcode
 * imprime (p.ej. "No profiles for '...' were found: Automatic signing is
 * disabled...").
 *
 * Esta clase NO modifica el flujo de JobExecutor ni de DriverFactory — solo
 * lee un archivo que Appium ya escribe y reenvía las líneas relevantes de la
 * ventana temporal de la ejecución actual (delimitada por
 * WdaManager.markTestExecutionStart()/markTestExecutionEnd()).
 */
final class AppiumXcodebuildLogForwarder {

    private static final long MAX_TAIL_BYTES = 4L * 1024 * 1024; // 4 MB — de sobra para una sesión

    // Appium --log-timestamp: "2026-07-14 00:53:20:222 [...] mensaje"
    private static final Pattern TIMESTAMP = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}):(\\d{3})\\s");
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppiumXcodebuildLogForwarder() {}

    /**
     * Lee appium.log, se queda solo con las líneas cuyo timestamp cae dentro
     * de la ventana [sinceEpochMs, ahora] y que son relevantes a xcodebuild/
     * WebDriverAgent, y las reenvía íntegras al backend (Logs Técnicos).
     * Si encuentra la línea de error real de Xcode, la destaca también en el
     * log principal para que no quede oculta.
     */
    static void forwardSince(long sinceEpochMs, BackendClient client, String executionId) {
        Path logFile = AppiumManager.resolveLogFile();
        if (!Files.isRegularFile(logFile)) {
            client.sendTechLog(executionId, "[xcodebuild] appium.log no existe todavía — nada que reenviar.");
            return;
        }
        if (sinceEpochMs <= 0) {
            client.sendTechLog(executionId, "[xcodebuild] Sin ventana de ejecución registrada — se omite el reenvío.");
            return;
        }

        String tail = readTail(logFile, MAX_TAIL_BYTES);
        if (tail == null) {
            client.sendTechLog(executionId, "[xcodebuild] No se pudo leer appium.log.");
            return;
        }

        int    forwarded = 0;
        String realError = null;

        for (String line : tail.split("\n", -1)) {
            if (line.isBlank()) continue;

            Long ts = parseTimestampEpochMs(line);
            if (ts != null && ts < sinceEpochMs) continue; // línea de una sesión/ejecución anterior
            if (!isXcodebuildRelevant(line)) continue;

            client.sendTechLog(executionId, "[xcodebuild] " + line.trim());
            forwarded++;

            // La línea de error REAL de Xcode (no el resumen que arma Appium/el driver)
            // se destaca aparte para que no quede enterrada entre Logs Técnicos.
            if (realError == null && line.contains("[Xcode]") && line.toLowerCase().contains("error:")) {
                realError = line.substring(line.indexOf("[Xcode]") + "[Xcode]".length()).trim();
            }
        }

        if (forwarded == 0) {
            client.sendTechLog(executionId, "[xcodebuild] Sin líneas de xcodebuild en esta ejecución.");
        } else if (realError != null) {
            client.sendLog(executionId, "ERROR",
                    "🔴 [xcodebuild] Causa raíz real (vía appium.log, showXcodeLog): " + realError);
        }
    }

    private static boolean isXcodebuildRelevant(String line) {
        String lower = line.toLowerCase();
        return lower.contains("[xcode]") || lower.contains("xcodebuild")
                || lower.contains("webdriveragent") || lower.contains("build failed")
                || lower.contains("codesign") || lower.contains("provisioning");
    }

    private static Long parseTimestampEpochMs(String line) {
        Matcher m = TIMESTAMP.matcher(line);
        if (!m.find()) return null;
        try {
            LocalDateTime dt = LocalDateTime.parse(m.group(1), TS_FMT);
            long millis = Long.parseLong(m.group(2));
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + millis;
        } catch (Exception e) {
            return null;
        }
    }

    /** Lee únicamente los últimos maxBytes del archivo — evita cargar semanas de log acumulado. */
    private static String readTail(Path path, long maxBytes) {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long len   = raf.length();
            long start = Math.max(0, len - maxBytes);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
