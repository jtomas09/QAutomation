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
public final class AppiumXcodebuildLogForwarder {

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
     *
     * @return la causa raíz real detectada (misma que ya se envía al Dashboard),
     *         o null si no hay evidencia de fallo en la ventana — para que el
     *         llamador (IOSExecutionCleanupManager) pueda publicar el evento
     *         WDA correspondiente sin recalcular esta detección.
     */
    static String forwardSince(long sinceEpochMs, BackendClient client, String executionId) {
        Path logFile = AppiumManager.resolveLogFile();
        if (!Files.isRegularFile(logFile)) {
            client.sendTechLog(executionId, "[xcodebuild] appium.log no existe todavía — nada que reenviar.");
            return null;
        }
        if (sinceEpochMs <= 0) {
            client.sendTechLog(executionId, "[xcodebuild] Sin ventana de ejecución registrada — se omite el reenvío.");
            return null;
        }

        String tail = readTail(logFile, MAX_TAIL_BYTES);
        if (tail == null) {
            client.sendTechLog(executionId, "[xcodebuild] No se pudo leer appium.log.");
            return null;
        }

        // La causa raíz se calcula y envía PRIMERO — nunca debe quedar atrapada detrás
        // del volcado completo de abajo. Ese volcado puede implicar cientos de líneas
        // (cada una un POST HTTP síncrono sin timeout — ver BackendClient.post()); si
        // una de esas llamadas se cuelga por una red inestable, el mensaje con la causa
        // real nunca llegaría a tiempo — o nunca — al Dashboard.
        String realError = findRealXcodeError(sinceEpochMs);
        if (realError != null) {
            client.sendLog(executionId, "ERROR",
                    "🔴 [xcodebuild] Causa raíz real (vía appium.log, showXcodeLog): " + realError);
        }

        int forwarded = 0;
        for (String line : tail.split("\n", -1)) {
            if (line.isBlank()) continue;

            Long ts = parseTimestampEpochMs(line);
            if (ts != null && ts < sinceEpochMs) continue; // línea de una sesión/ejecución anterior
            if (!isXcodebuildRelevant(line)) continue;

            client.sendTechLog(executionId, "[xcodebuild] " + line.trim());
            forwarded++;
        }

        if (forwarded == 0) {
            client.sendTechLog(executionId, "[xcodebuild] Sin líneas de xcodebuild en esta ejecución.");
        }

        return realError;
    }

    /**
     * Busca en appium.log, dentro de la ventana [sinceEpochMs, ahora], la línea de error
     * REAL que Xcode imprime (no el resumen genérico que arma Appium/el driver, tipo
     * "xcodebuild failed with code 65..."). Usada tanto por forwardSince() (ejecuciones
     * reales, vía IOSExecutionCleanupManager) como por IOSMirrorProvider (lanzamiento
     * on-demand de WDA para el Mirror) para poblar un motivo de error legible.
     *
     * @return la línea de error real, un resumen genérico de fallback, o null si no hay
     *         evidencia de fallo alguno en la ventana dada.
     */
    public static String findRealXcodeError(long sinceEpochMs) {
        Path logFile = AppiumManager.resolveLogFile();
        if (!Files.isRegularFile(logFile) || sinceEpochMs <= 0) return null;

        String tail = readTail(logFile, MAX_TAIL_BYTES);
        if (tail == null) return null;

        String realError = null;
        String remoteXpcError = null;
        String fallbackSummary = null;

        for (String line : tail.split("\n", -1)) {
            if (line.isBlank()) continue;
            Long ts = parseTimestampEpochMs(line);
            if (ts != null && ts < sinceEpochMs) continue;

            // Prioridad 1: la línea "[Xcode] ... error: ..." — la causa real que imprime Xcode.
            if (realError == null && line.contains("[Xcode]") && line.toLowerCase().contains("error:")) {
                realError = line.substring(line.indexOf("[Xcode]") + "[Xcode]".length()).trim();
            }
            // Prioridad 1b: fallo de RemoteXPC ("Cannot create port forwarder via RemoteXPC
            // tunnel", "RemoteXPC tunnel is not available for this session") — este error lo
            // emite appium-xcuitest-driver/appium-ios-remotexpc, no xcodebuild, por lo que
            // nunca lleva la etiqueta "[Xcode]". Sin este chequeo, el Mirror nunca se enteraba
            // de este fallo — solo quedaba en los logs de Gradle (ver IOSPreSessionRevalidator
            // y classifyIosSessionFailure/REMOTE_XPC_TUNNEL_FAILED, en la JVM de test).
            if (remoteXpcError == null && isRemoteXpcFailureLine(line)) {
                remoteXpcError = line.trim();
            }
            // Fallback: el resumen genérico del driver ("xcodebuild failed with code 65...")
            // — se usa solo si nunca aparece una línea [Xcode] con "error:" explícito.
            if (fallbackSummary == null && line.toLowerCase().contains("xcodebuild failed with code")) {
                int idx = line.indexOf("xcodebuild failed with code");
                fallbackSummary = line.substring(idx).trim();
            }
        }

        if (realError != null) return realError;
        if (remoteXpcError != null) return remoteXpcError;
        return fallbackSummary;
    }

    /**
     * Línea de fallo real de RemoteXPC (no informativa) — exige tanto la mención de
     * RemoteXPC/port forwarder como una palabra que indique fallo, para no capturar
     * líneas de log benignas que solo mencionen RemoteXPC de paso.
     */
    private static boolean isRemoteXpcFailureLine(String line) {
        String lower = line.toLowerCase();
        boolean mentionsRemoteXpc = lower.contains("remotexpc") || lower.contains("port forwarder");
        boolean indicatesFailure  = lower.contains("cannot") || lower.contains("not available")
                                  || lower.contains("error") || lower.contains("fail");
        return mentionsRemoteXpc && indicatesFailure;
    }

    /**
     * Appium re-etiqueta CADA línea de la salida cruda de xcodebuild con "[Xcode]"
     * al proxearla a appium.log (confirmado línea por línea en un log real). Antes
     * este filtro también hacía match por "xcodebuild"/"webdriveragent"/"codesign"/
     * "provisioning" como substrings sueltos — eso capturaba además el tráfico HTTP
     * y de debug del PROPIO driver de Appium (capabilities, "Using WDA path", etc.),
     * que solo MENCIONA esas palabras sin ser salida real de xcodebuild. En una
     * ejecución real esto llegó a inflar el reenvío a 664 líneas, de las cuales 72
     * no tenían nada que ver con xcodebuild — solo ruido del propio driver.
     */
    private static boolean isXcodebuildRelevant(String line) {
        return line.contains("[Xcode]") || isRemoteXpcFailureLine(line);
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
