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
 *  2. WdaLifecycleOwner.release(JOB_EXECUTION, ...) — única autoridad del ciclo de
 *     vida de WDA: libera la referencia de ESTA ejecución. Si ningún otro
 *     consumidor (p.ej. el Mirror, viendo el mismo dispositivo desde el
 *     Dashboard) sigue activo, ejecuta el teardown real — mata Mac-side
 *     xcodebuild + descendientes, termina WDA en el dispositivo, escala
 *     SIGTERM→SIGKILL, y verifica contra devicectl (no solo HTTP) que ya no
 *     queda ningún proceso. Si el Mirror sigue activo, la instancia se
 *     mantiene viva a propósito — release() lo reporta, nunca lo oculta.
 *  3. Final state verification (adaptada: si WDA se mantuvo vivo para el
 *     Mirror, "xcodebuild activo"/"WDA en el dispositivo" dejan de ser
 *     fallos — son el resultado esperado)
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
                WdaLaunchCoordinator.executionStartedAtMs(), client, executionId);

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

        // 3. Libera la referencia de ESTA ejecución sobre WDA — a través de la ÚNICA
        //    autoridad de ciclo de vida (WdaLifecycleOwner.release()). Si el Mirror
        //    todavía está consumiendo esta misma instancia (usuario viendo el
        //    dispositivo en el Dashboard), release() NO la destruye — la mantiene
        //    viva y lo registra. Solo cuando ningún consumidor queda activo se
        //    ejecuta el teardown real, que verifica contra el dispositivo (no solo
        //    HTTP) antes de darlo por cerrado, y escala SIGTERM→SIGKILL si hace falta.
        boolean tornDown = WdaLifecycleOwner.release(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, executionId, udid);

        // Sin error detectado — WDA terminó su ciclo de vida normalmente para esta
        // ejecución (éxito, o un fallo ajeno a WDA). Si además se destruyó la
        // instancia (tornDown), el Mirror vuelve al estado neutral (dispositivo
        // detectado) en vez de quedar congelado en "Iniciando WebDriverAgent…" para
        // siempre — la causa raíz original del bug que motivó este mecanismo. Si el
        // Mirror la mantiene viva (tornDown=false), NO se publica STOPPED — sería
        // falso: WDA sigue arriba y el Mirror lo sabe por su propio captureFrame().
        if (realXcodeError == null && tornDown) {
            qa.cinepolis.runner.mirror.WdaEventBus.publish(
                    udid, qa.cinepolis.runner.mirror.WdaEventBus.WdaEvent.STOPPED);
        }

        // 4. Verificación final EXPLÍCITA — cada punto se verifica por separado y se
        //    registra siempre (nunca silenciosamente), en ERROR si no quedó como debía.
        //    Idempotente: son solo lecturas + (si hace falta) un barrido adicional que
        //    ya es en sí mismo idempotente (WdaLifecycleOwner.sweepStaleProcesses()) —
        //    llamar a cleanup() más de una vez para la misma ejecución no tiene ningún
        //    efecto secundario distinto de repetir estas mismas comprobaciones.
        //
        //    IMPORTANTE: si tornDown=false (release() detectó que el Mirror sigue
        //    usando esta misma instancia de WDA), "xcodebuild activo", "WDA vivo en
        //    el dispositivo" y "Mirror transmitiendo" dejan de ser fallos — son
        //    exactamente el resultado esperado de mantener la instancia viva a
        //    propósito. Verificarlos como si debieran estar en cero produciría un
        //    falso positivo en cada ejecución mientras el Mirror esté abierto.
        boolean allClear = true;

        int xcodebuildLeft;
        java.util.Set<String> devicePids;
        boolean deviceClear;

        if (!tornDown) {
            client.sendLog(executionId, "INFO",
                    "ℹ️ [Cleanup] WebDriverAgent se mantiene activo intencionalmente — "
                    + "el Mirror sigue usando esta instancia (ver WdaLifecycleOwner.release()).");
            xcodebuildLeft = WdaLifecycleOwner.countXcodebuildProcesses();
            devicePids     = WdaManager.queryWdaPidsOnDevice(udid);
            deviceClear    = true; // no aplica como condición de fallo mientras el Mirror la use
        } else {
            // 4a. xcodebuild = 0 (system-wide, no solo la referencia que este Runner
            //     recordaba) — si algo sigue vivo, es un hallazgo real, así que además
            //     de reportarlo se remedia aquí mismo (mismo mecanismo idempotente que
            //     el barrido de arranque/apagado).
            xcodebuildLeft = WdaLifecycleOwner.countXcodebuildProcesses();
            if (xcodebuildLeft > 0) {
                client.sendLog(executionId, "ERROR",
                        "❌ [Cleanup] xcodebuild aún activo tras el cleanup (" + xcodebuildLeft
                        + " proceso(s)) — deteniendo ahora.");
                WdaLifecycleOwner.sweepStaleProcesses();
                xcodebuildLeft = WdaLifecycleOwner.countXcodebuildProcesses();
                if (xcodebuildLeft > 0) allClear = false;
            }

            // 4b. WebDriverAgentRunner / XCTest = 0 en el dispositivo (misma consulta que
            //     terminateWdaOnDevice ya usa — "xctrunner" cubre el proceso XCTest en el
            //     dispositivo, no existe un proceso "XCTest" distinto observable vía devicectl).
            devicePids  = WdaManager.queryWdaPidsOnDevice(udid);
            deviceClear = devicePids.isEmpty();
            if (!deviceClear) {
                client.sendLog(executionId, "ERROR",
                        "❌ [Cleanup] WebDriverAgentRunner/XCTest siguen vivos en el dispositivo tras "
                        + "el cleanup — PIDs: " + devicePids);
                allClear = false;
            }
        }

        // 4c. Sesión Appium cerrada
        boolean appiumOk = !hasOpenAppiumSession(appiumHubBase, udid);
        if (!appiumOk) {
            client.sendLog(executionId, "ERROR", "❌ [Cleanup] Sesión Appium sigue activa tras el cleanup.");
            allClear = false;
        }

        // 4d. Mirror — solo es un fallo si SE DESTRUYÓ la instancia (tornDown=true) y
        //     aun así el Mirror sigue transmitiendo (contradicción real: estaría
        //     mostrando frames de un WDA que ya no debería existir). Si tornDown=false,
        //     el Mirror transmitiendo es precisamente la razón por la que se mantuvo viva.
        boolean mirrorStreaming = MirrorService.isStreaming(udid);
        boolean mirrorClear = tornDown ? !mirrorStreaming : true;
        if (tornDown && mirrorStreaming) {
            client.sendLog(executionId, "ERROR",
                    "❌ [Cleanup] El Mirror sigue transmitiendo para " + udid + " tras destruir WDA "
                    + "— estado inconsistente (¿se liberó su referencia sin que release() lo supiera?).");
            allClear = false;
        } else if (!tornDown && mirrorStreaming) {
            client.sendLog(executionId, "INFO",
                    "ℹ️ [Cleanup] Mirror activo para " + udid + " — motivo por el que WDA no se destruyó.");
        }

        // 4e. Tunnel CoreDevice — SOLO informativo. Este repo nunca posee ni destruye
        //     el túnel (CoreDeviceTunnelManager: "el Runner solo OBSERVA el túnel") —
        //     reportar su estado aquí es para visibilidad, nunca una condición de fallo.
        String tunnelState;
        try {
            tunnelState = CoreDeviceTunnelManager.readConnectionState(udid).tunnelState;
        } catch (Exception e) {
            tunnelState = "desconocido";
        }

        // "Automation Running" no es consultable directamente (Apple no expone esa
        // señal) — su proxy real es "¿sigue vivo el proceso que lo produce en el
        // dispositivo?", ya verificado en 4b (solo aplica cuando tornDown=true).
        boolean autoRunningOff = tornDown && deviceClear;

        // 5. Reporte estructurado — SIEMPRE visible, nunca omitido. Cuando tornDown=false
        //    se reporta el estado REAL (sigue activo) con la razón, nunca "0 ✓" falso.
        String xcodebuildCol = tornDown
                ? (xcodebuildLeft == 0 ? "0 ✓" : xcodebuildLeft + " ❌")
                : xcodebuildLeft + " (activo — Mirror en uso, esperado)";
        String deviceCol = tornDown
                ? (deviceClear ? "0 ✓" : devicePids.size() + " ❌ " + devicePids)
                : devicePids.size() + " (activo — Mirror en uso, esperado) " + devicePids;
        String autoRunningCol = tornDown
                ? (autoRunningOff ? "OFF ✓" : "ON ❌")
                : "ON (esperado — Mirror en uso)";

        client.sendLog(executionId, allClear ? "INFO" : "ERROR", String.format(
                "════════ Verificación final de liberación ════════%n"
                + "  WDA se mantiene vivo : %s%n"
                + "  xcodebuild (Mac)     : %s%n"
                + "  WebDriverAgentRunner  : %s%n"
                + "  XCTest (dispositivo)  : %s%n"
                + "  Automation Running    : %s%n"
                + "  Sesión Appium         : %s%n"
                + "  Mirror                : %s%n"
                + "  Tunnel CoreDevice     : %s (informativo — no gestionado por este Runner)%n"
                + "═══════════════════════════════════════════════",
                tornDown ? "NO" : "SÍ (consumidor activo — ver detalle arriba)",
                xcodebuildCol,
                deviceCol,
                deviceCol,
                autoRunningCol,
                appiumOk ? "CERRADA ✓" : "ACTIVA ❌",
                mirrorClear ? (tornDown ? "DETENIDO ✓" : "ACTIVO (esperado)") : "ACTIVO ❌",
                tunnelState));

        // 6. Resultado funcional — nunca silencioso: siempre uno de los dos mensajes.
        if (allClear) {
            client.sendLog(executionId, "INFO", "✓ Dispositivo liberado completamente y verificado.");
        } else {
            client.sendLog(executionId, "ERROR",
                    "❌ El dispositivo NO quedó completamente liberado — ver detalle arriba. "
                    + "Si 'Automation Running' persiste, mantén presionados los botones de volumen "
                    + "para forzar la detención.");
        }

        // Libera la sesión de ejecución — a partir de aquí IOSMirrorProvider puede
        // volver a consumir WDA si otra ejecución real lo levanta. Se llama siempre,
        // incluso si el dispositivo quedó OCUPADO arriba: el intento de esta ejecución
        // terminó de cualquier forma.
        WdaLaunchCoordinator.endExecutionSession();
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
