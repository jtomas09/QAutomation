package config;

import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Revalida el estado del dispositivo iOS INMEDIATAMENTE antes de crear IOSDriver.
 *
 * Problema que resuelve:
 *   IOSDeviceState.fromRunnerProps() es una foto tomada minutos/segundos antes,
 *   dentro de IosPreflightManager.runPreflight() (proceso del Runner). Entre ese
 *   instante y la creación real de IOSDriver (arranque de Gradle sin daemon, boot
 *   de JVM, JUnit discovery — ver DriverFactory) el transporte CoreDevice puede
 *   cambiar (WIRED → LOCAL_NETWORK) sin que ningún componente de este repo lo
 *   cause: CoreDeviceTunnelManager ya documenta que el Runner solo OBSERVA el
 *   tunnel, nunca lo posee ni lo destruye. Appium, sin embargo, puede fallar al
 *   construir su port forwarder RemoteXPC si el transporte cambió
 *   ("Cannot create port forwarder via RemoteXPC tunnel").
 *
 * Causa raíz demostrada (evidencia real, ver investigación previa): webDriverAgentUrl
 * se descubría UNA vez en el Runner (WdaManager, parseando ServerURLHere del stdout de
 * xcodebuild) y se transportaba como propiedad JVM congelada. Ninguna clase la
 * revalidaba — quedaba obsoleta exactamente cuando el transporte cambiaba, produciendo
 * ECONNREFUSED reproducible. La autoridad real (WdaManager) sigue viva durante toda
 * la ejecución en el proceso Runner; el problema nunca fue la URL en sí, sino que su
 * ownership desaparecía antes de consumirla.
 *
 * Esta clase resuelve DOS preguntas independientes, deliberadamente separadas:
 *
 *   1. {@link #revalidate} — ¿sigue vigente el snapshot de HARDWARE/transporte del
 *      Runner (xctrace, CoreDevice, tunnel, pairing)? Reutiliza
 *      {@link IOSDeviceStateService#refresh}, la misma consulta xctrace+devicectl ya
 *      usada por IOSDeviceSynchronizationManager. Devuelve un IOSDeviceState
 *      actualizado — sigue siendo, y solo es, un snapshot (ver IOSDeviceState).
 *
 *   2. {@link #resolveLiveWdaUrl} — ¿cuál es el webDriverAgentUrl vigente AHORA?
 *      Pregunta directamente a la única autoridad que existe para este dato
 *      (WdaManager, en el proceso Runner, vía DeviceStreamServer.GET /api/wda/url).
 *      Devuelve un String, nunca un IOSDeviceState — este dato deliberadamente NO
 *      forma parte de ningún snapshot: es un hecho de conectividad en vivo, y
 *      guardarlo en un objeto de snapshot (aunque fuera "refrescado" después)
 *      crearía una segunda representación de la misma autoridad. DriverFactory debe
 *      llamar a este método directamente, en el último instante antes de
 *      new IOSDriver(), y usar su respuesta sin intermediarios.
 *
 * Separación de responsabilidades:
 *   - IOSDeviceStateService: única fuente de subprocesos xctrace/devicectl + caché
 *     (hardware/transporte).
 *   - IOSDeviceState: objeto de valor inmutable — SOLO snapshot, nunca conectividad viva.
 *   - IOSPreSessionRevalidator (esta clase): política que se ejecuta en el último
 *     instante antes de crear la sesión Appium — combina la revalidación de hardware
 *     (snapshot) con la consulta viva de WDA (no-snapshot), pero nunca mezcla ambas
 *     en un mismo objeto de retorno.
 *   No duplica ninguna lógica de subprocess ni de parsing — todo delega en
 *   IOSDeviceStateService/DevicectlParser/WdaManager (vía HTTP), ya existentes.
 */
public final class IOSPreSessionRevalidator {

    private static final Duration LIVE_WDA_URL_TIMEOUT = Duration.ofSeconds(3);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(LIVE_WDA_URL_TIMEOUT)
            .build();

    private IOSPreSessionRevalidator() {}

    /**
     * Revalida ÚNICAMENTE hardware/transporte (xctrace, CoreDevice, tunnel, pairing).
     * No conoce ni toca webDriverAgentUrl — ver {@link #resolveLiveWdaUrl} para eso.
     *
     * @param snapshot snapshot original del Runner (IOSDeviceState.fromRunnerProps())
     * @param udid     UDID físico del dispositivo
     * @param log      logger del llamador (DriverFactory)
     * @return snapshot actualizado con el estado de hardware/transporte más reciente;
     *         el snapshot original sin cambios si udid es nulo/vacío.
     */
    public static IOSDeviceState revalidate(IOSDeviceState snapshot, String udid, Logger log) {
        if (udid == null || udid.isBlank()) return snapshot;

        log.info("[PreSessionRevalidator] Revalidando estado del dispositivo justo antes de IOSDriver...");
        IOSDeviceStateService.DeviceState fresh = IOSDeviceStateService.refresh(udid, log);
        logDrift(snapshot, fresh, log);
        return snapshot.withFreshHardwareState(fresh);
    }

    /**
     * Única autoridad de webDriverAgentUrl en todo el pipeline. Consulta UNA vez, de
     * forma síncrona, al propio Runner (vía DeviceStreamServer.GET /api/wda/url, que
     * expone WdaManager — el único proceso con visión continua de WDA durante toda
     * la ejecución). Sin reintentos, sin sleeps, sin caché: una sola pregunta, en el
     * instante exacto en que la respuesta importa.
     *
     * Deliberadamente devuelve un {@code String}, no un IOSDeviceState — este dato no
     * es un snapshot y no debe guardarse como si lo fuera (ver javadoc de clase).
     * DriverFactory debe llamar a este método directamente, inmediatamente antes de
     * new IOSDriver(), y aplicar su resultado sin pasar por ningún objeto intermedio.
     *
     * runnerControlPort es la DIRECCIÓN donde preguntar, no el dato en sí — la fija
     * JobExecutor una vez al lanzar Gradle porque el propio puerto HTTP del Runner es,
     * en sí mismo, estable durante toda la ejecución (a diferencia de la URL de WDA,
     * que depende del transporte).
     *
     * @return la URL confirmada en vivo ahora mismo, o cadena vacía si el Runner no
     *         respondió, WDA no está activo, o el puerto de control no fue provisto.
     *         Nunca inventa ni reutiliza un valor anterior — cadena vacía significa
     *         explícitamente "no hay autoridad confirmable en este instante".
     */
    public static String resolveLiveWdaUrl(Logger log) {
        String portStr = System.getProperty("runnerControlPort", "");
        if (portStr.isBlank()) return "";

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portStr.trim() + "/api/wda/url"))
                    .timeout(LIVE_WDA_URL_TIMEOUT)
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) return resp.body().trim();
        } catch (Exception e) {
            log.debug("[PreSessionRevalidator] Consulta viva de webDriverAgentUrl falló: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Registra, sin abortar, exactamente qué cambió entre el snapshot del Runner y el
     * estado recién consultado. Formato deliberadamente explícito (antes → después) para
     * que quede evidencia directa en los logs cuando Appium falle más adelante.
     */
    private static void logDrift(IOSDeviceState before, IOSDeviceStateService.DeviceState after, Logger log) {
        StringBuilder drift = new StringBuilder();

        String beforeTransport = before.transportType.isBlank() ? "UNKNOWN" : before.transportType;
        String afterTransport  = after.transportType.isBlank()  ? "UNKNOWN" : after.transportType;
        if (!beforeTransport.equalsIgnoreCase(afterTransport)) {
            drift.append("\n   Transport  : ").append(beforeTransport).append(" → ").append(afterTransport);
        }

        boolean afterTunnelConnected = "connected".equalsIgnoreCase(after.tunnelState);
        if (before.tunnelConnected != afterTunnelConnected) {
            drift.append("\n   Tunnel     : ").append(before.tunnelConnected ? "connected" : "disconnected")
                 .append(" → ").append(afterTunnelConnected ? "connected" : "disconnected");
        }

        boolean afterPaired = !"unpaired".equalsIgnoreCase(after.pairingState);
        if (before.paired != afterPaired) {
            drift.append("\n   Pairing    : ").append(before.paired ? "paired" : "unpaired")
                 .append(" → ").append(after.pairingState);
        }

        if (before.xctraceVisible != after.xctraceVisible) {
            drift.append("\n   xctrace    : ").append(before.xctraceVisible ? "visible" : "no visible")
                 .append(" → ").append(after.xctraceVisible ? "visible" : "no visible");
        }

        if (before.coreDeviceVisible != after.coreDeviceVisible) {
            drift.append("\n   CoreDevice : ").append(before.coreDeviceVisible ? "visible" : "no visible")
                 .append(" → ").append(after.coreDeviceVisible ? "visible" : "no visible");
        }

        if (drift.length() == 0) {
            log.info("[PreSessionRevalidator] ✅ Sin cambios respecto al snapshot del Runner ({}s de antigüedad).",
                    before.ageSeconds());
        } else {
            log.warn("[PreSessionRevalidator] ⚠ Runner Snapshot difiere del estado actual "
                    + "(no bloqueante — transportType nunca es un gate):{}", drift);
        }
    }
}
