package qa.cinepolis.runner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the complete iOS device cleanup sequence after test execution.
 * Replaces inline ad-hoc cleanup in JobExecutor with a verified, ordered process.
 *
 * Cleanup sequence:
 *  1. Close Appium session via HTTP DELETE (lets Appium stop WDA gracefully)
 *  2. WdaManager.cleanup() — kills Mac-side xcodebuild, terminates WDA on device,
 *     waits for SIGTERM/SIGKILL to take effect, verifies via HTTP probe
 *  3. Final state verification
 *  4. Logs structured device state table
 *  5. Logs "✓ Dispositivo liberado correctamente" ONLY when WDA confirmed stopped
 *
 * "✓ Dispositivo liberado correctamente" is never logged without prior verification.
 *
 * Android: not referenced by any Android code path.
 */
public final class IOSExecutionCleanupManager {

    private static final int HTTP_TIMEOUT_S    = 3;
    private static final int SESSION_TIMEOUT_S = 20;

    private IOSExecutionCleanupManager() {}

    /**
     * Full cleanup with verification. Always call from a finally block.
     *
     * @param client        BackendClient for sending log messages
     * @param executionId   current execution identifier
     * @param udid          physical iOS device UDID
     * @param appiumHubBase Appium hub URL without /wd/hub (e.g. http://127.0.0.1:4723)
     */
    public static void cleanup(BackendClient client, String executionId,
                                String udid,          String appiumHubBase) {

        // 1. Reenviar al backend la salida CRUDA de xcodebuild que Appium ya capturó
        //    en su propio log de servidor (appium.log) durante esta ejecución — ver
        //    AppiumXcodebuildLogForwarder. Antes de esto, esa salida existía en disco
        //    pero nunca llegaba al Dashboard; el usuario solo veía el resumen
        //    ("xcodebuild failed with code 65...") sin la causa real de Xcode.
        String realXcodeError = AppiumXcodebuildLogForwarder.forwardSince(
                WdaManager.getTestExecutionStartedAtMs(), client, executionId);

        // Publica hacia el Mirror el MISMO evento que publicaría un lanzamiento
        // on-demand fallido (WdaEventBus es el único canal — ver su javadoc). Antes,
        // un fallo de xcodebuild durante una ejecución real nunca llegaba al Mirror;
        // este es el punto que cierra esa brecha. Se publica de inmediato, sin
        // esperar al cierre de sesión/kill de WDA de abajo (que puede tardar
        // varios segundos) — igual que forwardSince() ya prioriza el mensaje real.
        if (realXcodeError != null) {
            qa.cinepolis.runner.mirror.WdaEventBus.publish(
                    udid, qa.cinepolis.runner.mirror.WdaEventBus.WdaEvent.ERROR, realXcodeError);
        }

        // 2. Close Appium session via HTTP — Appium stops WDA gracefully on session delete
        closeAppiumSession(client, executionId, appiumHubBase, udid);

        // 3. Kill Mac-side xcodebuild + terminate WDA on device + verify
        //    WdaManager.cleanup() waits after SIGTERM, probes /status, escalates to SIGKILL
        //    if needed, and logs the honest result.
        WdaManager.cleanup(client, executionId, udid);

        // Sin error detectado — WDA terminó su ciclo de vida normalmente para esta
        // ejecución (éxito, o un fallo ajeno a WDA). El Mirror vuelve al estado
        // neutral (dispositivo detectado) en vez de quedar congelado en
        // "Iniciando WebDriverAgent…" para siempre — la causa raíz original del
        // bug que motivó este mecanismo.
        if (realXcodeError == null) {
            qa.cinepolis.runner.mirror.WdaEventBus.publish(
                    udid, qa.cinepolis.runner.mirror.WdaEventBus.WdaEvent.STOPPED);
        }

        // 4. Final state checks
        boolean wdaDown  = !WdaManager.isWdaRunning();
        boolean appiumOk = !hasOpenAppiumSession(appiumHubBase, udid);
        boolean deviceOk = wdaDown;

        // 5. Structured state summary — always visible
        String sessionState = appiumOk ? "CERRADA ✓"  : "ACTIVA ⚠";
        String wdaState     = wdaDown  ? "DETENIDO ✓" : "ACTIVO ⚠";
        String autoState    = wdaDown  ? "NO ✓"       : "SÍ ⚠";
        String deviceState  = deviceOk ? "LIBRE ✓"    : "OCUPADO ⚠";

        client.sendLog(executionId, "INFO", String.format(
                "════════ Estado final del dispositivo ════════%n"
                + "  Sesión Appium     : %s%n"
                + "  WebDriverAgent    : %s%n"
                + "  Automation Running: %s%n"
                + "  Dispositivo       : %s%n"
                + "══════════════════════════════════════════════",
                sessionState, wdaState, autoState, deviceState));

        // 5. Functional outcome — "✓ Dispositivo liberado correctamente" only when verified
        if (deviceOk) {
            client.sendLog(executionId, "INFO", "✓ Dispositivo liberado correctamente");
        } else {
            client.sendLog(executionId, "WARN",
                    "⚠ El dispositivo podría requerir intervención manual.\n"
                    + "  Si el banner 'Automation Running' persiste, mantén presionados"
                    + " los botones de volumen para forzar la detención.");
        }

        // Cierra la ventana abierta en IosPreflightManager.runPreflight() — a partir de
        // aquí IOSMirrorProvider puede volver a lanzar WDA bajo demanda si hace falta.
        // Se llama siempre, incluso si el dispositivo quedó "OCUPADO" arriba: el intento
        // de esta ejecución terminó de cualquier forma.
        WdaManager.markTestExecutionEnd();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Finds and deletes the Appium session for the given UDID via HTTP.
     * Returns true if a session was found and deleted.
     */
    private static boolean closeAppiumSession(BackendClient client, String executionId,
                                               String hubBase, String udid) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_S))
                    .build();

            HttpResponse<String> resp;
            try {
                resp = http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(hubBase + "/sessions"))
                                .timeout(Duration.ofSeconds(5))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                return false; // Appium not reachable
            }

            if (resp.statusCode() != 200 || !resp.body().contains(udid)) {
                return false; // no open session for this device
            }

            String sessionId = findSessionId(resp.body(), udid);
            if (sessionId == null) return false;

            client.sendLog(executionId, "INFO", "Finalizando sesión Appium...");
            try {
                http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(hubBase + "/session/" + sessionId))
                                .timeout(Duration.ofSeconds(SESSION_TIMEOUT_S))
                                .DELETE().build(),
                        HttpResponse.BodyHandlers.discarding());
                client.sendLog(executionId, "INFO", "✓ Sesión Appium cerrada");
                // Brief pause so Appium has time to begin WDA shutdown before force-terminate
                try { Thread.sleep(2_000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                client.sendTechLog(executionId, "[Cleanup] Session delete error: " + e.getMessage());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns true if Appium still has an open session for the UDID. */
    private static boolean hasOpenAppiumSession(String hubBase, String udid) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_S))
                    .build();
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(hubBase + "/sessions"))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 && resp.body().contains(udid);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the session ID whose capabilities block contains the UDID.
     * "id" appears before the capabilities block in Appium JSON, so search backward.
     */
    private static String findSessionId(String body, String udid) {
        int pos = body.indexOf(udid);
        if (pos < 0) return null;
        String prefix = body.substring(Math.max(0, pos - 3000), pos);
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(prefix);
        String sessionId = null;
        while (m.find()) sessionId = m.group(1); // last match before the UDID
        return sessionId;
    }
}
