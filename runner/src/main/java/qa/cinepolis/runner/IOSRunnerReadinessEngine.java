package qa.cinepolis.runner;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * TAREA 2 — primera autoridad única (en construcción) para determinar si un
 * dispositivo iOS está listo para ejecutar una prueba.
 *
 * NO reemplaza todavía a ninguna clase existente. Reutiliza, sin duplicar su
 * lógica interna:
 *   - {@link CoreDeviceTunnelManager#readConnectionState} — conectividad/pairing
 *     (lectura pura, SIN acciones de recuperación — esas siguen siendo
 *     responsabilidad exclusiva de WdaLifecycleOwner/IosPreflightManager
 *     cuando una ejecución real lo requiere).
 *   - {@link DevicectlParser} — parseo estructural de devicectl (Developer Mode).
 *   - {@link WdaLifecycleOwner#isTerminalError}/{@link WdaLifecycleOwner#terminalErrorReason} —
 *     último fallo conocido de WDA para este UDID, SIN disparar un nuevo intento.
 *   - {@link AppleDeveloperTeamManager#selectTeam} — Team/certificado.
 *   - {@link AppleSigningUtils#discoverCertificateTeams}/{@link AppleSigningUtils#discoverProfileTeams} —
 *     validez de certificado/provisioning.
 *   - {@link WdaManager#isWdaRunning} — WDA alcanzable ahora mismo (probe HTTP,
 *     sin construir ni lanzar nada).
 *
 * Esta clase NUNCA construye, instala, lanza ni recupera WDA — es de solo
 * lectura/diagnóstico. Tampoco automatiza Xcode de ninguna forma (sin
 * AppleScript, sin System Events, sin clics).
 *
 * Progresión conceptual (se detiene en la primera etapa que bloquea):
 *   DISCOVERED → CONNECTED → PAIRED → DEVELOPER_MODE → TRUSTED → PROVISIONED
 *   → WDA_READY → READY
 */
public final class IOSRunnerReadinessEngine {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private IOSRunnerReadinessEngine() {}

    /**
     * Evalúa el estado real de un dispositivo iOS, componiendo únicamente
     * fuentes ya existentes. No requiere que haya una ejecución de Job en
     * curso — puede llamarse en cualquier momento (p.ej. discovery periódico).
     *
     * @param client      usado únicamente por AppleDeveloperTeamManager.selectTeam
     *                    para su propio logging — no se usa para reportar nada
     *                    específico de este Engine.
     * @param executionId identificador de correlación para los logs internos
     *                    de las clases reutilizadas (p.ej. "readiness-check").
     * @param udid        UDID físico del dispositivo a evaluar.
     */
    public static IOSRunnerReadinessResult evaluate(BackendClient client, String executionId, String udid) {
        long start = System.currentTimeMillis();
        log("IOS_READINESS", "START");

        IOSRunnerReadinessResult.Builder r = IOSRunnerReadinessResult.builder()
                .deviceId(udid)
                .udid(udid)
                .platform("iOS");

        // ── DISCOVERED / CONNECTED / PAIRED ─────────────────────────────────
        // Lectura pura — CoreDeviceTunnelManager.readConnectionState() no ejecuta
        // ninguna acción de recuperación (no mata daemons, no reconecta).
        //
        // TAREA 27 — FIX real (causa raíz de la divergencia Engine vs Preflight,
        // evidencia real 2026-09-10): antes, "connected" dependía únicamente de
        // xctraceVisible, una señal independiente de devicectl que puede quedar
        // desactualizada para USB incluso con el dispositivo genuinamente utilizable
        // (confirmado: devicectl siguió reportando "available (paired)" mientras
        // xctrace dejó de listarlo, incluso tras 60s de recuperación activa completa).
        // IosPreflightManager.runPreflight() nunca dependió de xctraceVisible para
        // WIRED — ver CoreDeviceTunnelManager.isConnectedForAppium(), extraída de esa
        // misma lógica ya correcta para que ambos flujos compartan un único criterio.
        CoreDeviceTunnelManager.DeviceConnectionState conn = CoreDeviceTunnelManager.readConnectionState(udid);
        boolean xctraceVisible = conn != null ? conn.xctraceVisible : CoreDeviceTunnelManager.isVisibleInXctrace(udid);
        // Fallback cuando devicectl no devuelve nada en absoluto (conn==null): xctrace
        // sigue siendo la única señal disponible — mismo criterio ya usado por
        // CoreDeviceTunnelManager.ensureTunnelConnected() en ese mismo caso.
        boolean connected      = conn != null ? CoreDeviceTunnelManager.isConnectedForAppium(conn) : xctraceVisible;
        String  transportType  = conn != null ? conn.transportType.name() : "UNKNOWN";
        String  tunnelState    = conn != null ? conn.tunnelState : "unknown";
        String  pairingState   = conn != null ? conn.pairingState : "unknown";

        r.transport(transportType).tunnel(tunnelState).pairing(pairingState);

        log("DEVICE", connected ? "CONNECTED" : "NOT_CONNECTED");
        if (!connected) {
            return finish(r, start, IOSRunnerReadinessResult.Status.OFFLINE,
                    IOSRunnerReadinessResult.Stage.DISCOVERED,
                    "Dispositivo no detectado — verifica el cable USB o la conexión WiFi.",
                    "IOS_DEVICE_OFFLINE");
        }

        boolean paired = !"unpaired".equalsIgnoreCase(pairingState);
        log("PAIRING", paired ? "OK" : "REQUIRED");
        if (!paired) {
            return finish(r, start, IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                    IOSRunnerReadinessResult.Stage.CONNECTED,
                    "Dispositivo no emparejado — desbloquea el iPhone y acepta «Confiar en este Mac».",
                    "IOS_PAIRING_REQUIRED");
        }

        // ── DEVELOPER_MODE ───────────────────────────────────────────────────
        boolean developerMode = fetchDeveloperModeEnabled(udid);
        r.developerMode(developerMode);
        log("DEVELOPER MODE", developerMode ? "OK" : "REQUIRED");
        if (!developerMode) {
            return finish(r, start, IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                    IOSRunnerReadinessResult.Stage.PAIRED,
                    "Developer Mode desactivado — Ajustes → Privacidad y seguridad → Modo desarrollador → Activar.",
                    "IOS_DEVELOPER_MODE_REQUIRED");
        }

        // ── TRUSTED ──────────────────────────────────────────────────────────
        // Apple no expone una señal consultable de "¿el certificado de desarrollo
        // está confiado en este dispositivo?" (ver comentario de IOSExecutionCleanupManager
        // sobre "Automation Running"). La única evidencia disponible es el motivo
        // del último fallo real de WDA para este UDID, ya capturado por
        // WdaLifecycleOwner sin que este Engine dispare un nuevo intento.
        log("TRUST CHECK", "RUNNING");
        String terminalReason = WdaLifecycleOwner.isTerminalError(udid)
                ? WdaLifecycleOwner.terminalErrorReason(udid) : null;
        // TAREA 4: la clasificación de texto ya NO vive aquí duplicada — se delega
        // por completo a IOSWdaErrorClassifier (único punto de clasificación de
        // errores WDA, ver TAREA 3.2). NONE cuando no hay terminalReason que
        // clasificar (nada que ver con un error real de WDA todavía).
        IOSWdaErrorCode terminalClassification = terminalReason != null
                ? IOSWdaErrorClassifier.classify(terminalReason) : IOSWdaErrorCode.NONE;
        boolean trustRequired = terminalClassification == IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED;
        r.developerTrust(!trustRequired);
        if (trustRequired) {
            log("TRUST", "REQUIRED");
            logErrorClassification(udid, terminalClassification, terminalReason);
            return finish(r, start, IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                    IOSRunnerReadinessResult.Stage.DEVELOPER_MODE,
                    "El dispositivo requiere confiar en la aplicación de desarrollo.",
                    "IOS_DEVELOPER_TRUST_REQUIRED", terminalClassification);
        }
        log("TRUST", "OK");

        // ── ACCOUNT SESSION (TAREA 26A) ──────────────────────────────────────
        // AppleSigningProbe (TAREA 25) puede determinar, ANTES de intentar compilar
        // WDA, que xcodebuild no puede completar el provisioning automático porque
        // la sesión de cuenta Apple ID que necesita para llamar en vivo al portal de
        // Apple no está disponible — distinto de "certificado no confiado en el
        // dispositivo" (TRUST, arriba) y de "no hay provisioning profile instalado"
        // (chequeo estático más abajo, PROVISIONED): aquí Team/certificado SÍ pueden
        // ser USABLE localmente (evidencia TAREA 24), pero xcodebuild ya demostró en
        // vivo que no puede usarlos. Mismo mecanismo que TRUST arriba — se lee vía
        // WdaLifecycleOwner.terminalErrorReason(), sin disparar ningún intento nuevo
        // aquí; IosPreflightManager es quien llama markTerminalError() cuando el
        // probe reporta ACCOUNT_SESSION_REQUIRED. NO se afirma que la sesión haya
        // "expirado" (TAREA 24 dejó ese mecanismo explícitamente sin demostrar) —
        // solo que no está disponible ahora mismo.
        boolean accountSessionRequired =
                terminalClassification == IOSWdaErrorCode.IOS_ACCOUNT_SESSION_REQUIRED;
        if (accountSessionRequired) {
            log("ACCOUNT SESSION", "REQUIRED");
            logErrorClassification(udid, terminalClassification, terminalReason);
            return finish(r, start, IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                    IOSRunnerReadinessResult.Stage.TRUSTED,
                    "La sesión de Apple ID / Xcode necesaria para provisioning automático no está "
                    + "disponible ahora mismo — requiere reautenticación en Xcode.",
                    "IOS_ACCOUNT_SESSION_REQUIRED", terminalClassification);
        }

        // ── PROVISIONED (Team / certificado / perfil) ───────────────────────
        String teamId = AppleDeveloperTeamManager.selectTeam(client, executionId);
        boolean certValid = teamId != null && !teamId.isBlank()
                && AppleSigningUtils.discoverCertificateTeams().stream()
                       .anyMatch(c -> teamId.equals(c.teamId()) && c.valid());
        r.certificate(certValid ? "valid" : "not_found");
        log("SIGNING", certValid ? "OK" : "MISSING");
        if (!certValid) {
            return finish(r, start, IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                    IOSRunnerReadinessResult.Stage.TRUSTED,
                    "Certificado de desarrollo no disponible o no vinculado a una cuenta Apple autenticada en Xcode.",
                    "IOS_CERTIFICATE_REQUIRED");
        }

        boolean provisioned = AppleSigningUtils.discoverProfileTeams().stream()
                .anyMatch(p -> teamId.equals(p.teamId()) && p.wdaMatch() && p.unexpired());
        r.provisioning(provisioned ? "valid" : "missing");
        log("PROVISIONING", provisioned ? "OK" : "MISSING");
        if (!provisioned) {
            return finish(r, start, IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                    IOSRunnerReadinessResult.Stage.TRUSTED,
                    "No hay un provisioning profile vigente para WebDriverAgent con este Team "
                    + "— requiere el registro inicial del App ID (setup de la máquina RunnerAgent).",
                    "IOS_PROVISIONING_REQUIRED");
        }

        // ── WDA_READY / READY ────────────────────────────────────────────────
        boolean wdaReachable = WdaManager.isWdaRunning();
        r.wdaReachable(wdaReachable).wda(wdaReachable ? "reachable" : "unreachable");
        log("WDA", wdaReachable ? "READY" : "NOT_READY");

        if (wdaReachable) {
            r.readyForExecution(true);
            return finish(r, start, IOSRunnerReadinessResult.Status.READY,
                    IOSRunnerReadinessResult.Stage.READY, null, null);
        }

        if (WdaLifecycleOwner.isBuildInFlight(udid)) {
            return finish(r, start, IOSRunnerReadinessResult.Status.RECOVERING,
                    IOSRunnerReadinessResult.Stage.PROVISIONED,
                    "WebDriverAgent se está compilando/verificando en este momento.",
                    "IOS_WDA_BUILDING");
        }

        if (terminalReason != null) {
            // TAREA 4: se conserva EXACTAMENTE el mismo stage/errorCode String/reason
            // que antes (PROVISIONED / IOS_WDA_BUILD_FAILED / texto original) para
            // cualquier terminalReason clasificado. wdaErrorCode es información
            // adicional: la clasificación REAL del texto (puede ser IOS_SIGNING_REQUIRED,
            // IOS_PROVISIONING_REQUIRED, IOS_WDA_STARTUP_FAILED, IOS_WDA_BUILD_FAILED o
            // UNKNOWN según el texto real preservado por WdaManager/TAREA 3.1).
            //
            // TAREA 10: IOS_WDA_STARTUP_FAILED ("Timed out while enabling automation
            // mode.") es la misma familia semántica que IOS_DEVELOPER_TRUST_REQUIRED
            // (ver rama TRUSTED más arriba) — ambos son bloqueos reales de xcodebuild/WDA
            // que exigen una acción física del usuario en el propio iPhone (confirmar
            // trust / confirmar el prompt nativo "Enable UI Automation"), no un fallo
            // reintentable automáticamente. Se reutiliza tal cual la clasificación ya
            // calculada por IOSWdaErrorClassifier — no se agrega una segunda detección
            // textual aquí. Solo cambia el Status resultante; errorCode String,
            // wdaErrorCode y reason permanecen exactamente igual que antes de esta tarea.
            logErrorClassification(udid, terminalClassification, terminalReason);
            IOSRunnerReadinessResult.Status statusForTerminalError =
                    terminalClassification == IOSWdaErrorCode.IOS_WDA_STARTUP_FAILED
                            ? IOSRunnerReadinessResult.Status.ACTION_REQUIRED
                            : IOSRunnerReadinessResult.Status.ERROR;
            return finish(r, start, statusForTerminalError,
                    IOSRunnerReadinessResult.Stage.PROVISIONED,
                    terminalReason, "IOS_WDA_BUILD_FAILED", terminalClassification);
        }

        // TAREA 6 — CASO A: no hay ninguna operación real en curso (ya se descartó
        // arriba vía WdaLifecycleOwner.isBuildInFlight()) y no hay un fallo previo
        // registrado — esto es simplemente "todavía no se ha intentado", NO una
        // recuperación en progreso. status=NOT_READY (nunca RECOVERING) para no
        // sugerir una operación que no existe. wdaErrorCode permanece NONE: no hay
        // ningún texto real de fallo de WDA que clasificar — "IOS_WDA_NOT_STARTED"
        // sigue existiendo únicamente como el errorCode String ya existente desde
        // TAREA 2 (etiqueta de etapa, distinta de IOSWdaErrorCode — ver Javadoc de
        // IOSRunnerReadinessResult.wdaErrorCode).
        return finish(r, start, IOSRunnerReadinessResult.Status.NOT_READY,
                IOSRunnerReadinessResult.Stage.PROVISIONED,
                "WebDriverAgent aún no se ha iniciado para este dispositivo.",
                "IOS_WDA_NOT_STARTED");
    }

    // ── Developer Mode (única pieza sin accesor de solo lectura existente) ────

    /**
     * Único punto de este Engine que ejecuta directamente {@code xcrun devicectl}
     * en vez de delegar — no existe hoy un accesor público de solo lectura para
     * {@code developerModeEnabled} fuera de IosPreflightManager (privado). Reutiliza
     * {@link DevicectlParser} para el parseo estructural, tal como ya hacen
     * CoreDeviceTunnelManager, IosPreflightManager e IOSDeviceScanner — no se
     * reimplementa ningún parseo, solo se agrega un punto de consulta más,
     * exactamente lo que esa clase fue diseñada para soportar (ver su Javadoc).
     * Optimista (true) cuando el dato no puede determinarse — misma política ya
     * usada por DeviceScreenLockChecker y DeviceReadinessEvaluator en este repo.
     */
    private static boolean fetchDeveloperModeEnabled(String udid) {
        try {
            Process p = new ProcessBuilder("xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(12, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return true; }
            DevicectlParser.DeviceInfo info = DevicectlParser.findByUdid(json, udid);
            return info == null || info.developerModeEnabled;
        } catch (Exception e) {
            return true;
        }
    }

    // ── Logging estructurado ────────────────────────────────────────────────

    private static void log(String stage, String status) {
        System.out.printf("[%s] %s %s%n", LocalTime.now().format(TS), stage, status);
    }

    /**
     * TAREA 4 — log SHADOW puramente diagnóstico, solo cuando existe un
     * terminalReason real que clasificar (nunca para OFFLINE/PAIRING/DEVELOPER_MODE/
     * READY/RECOVERING-sin-error, que no tienen ningún texto de WDA que clasificar).
     */
    private static void logErrorClassification(String udid, IOSWdaErrorCode code, String reason) {
        System.out.printf("[%s] IOS_READINESS_ERROR_CLASSIFIED UDID=%s ERROR_CODE=%s REASON=%s%n",
                LocalTime.now().format(TS), udid, code, reason);
    }

    /** Compatibilidad: mismo comportamiento que antes, wdaErrorCode=NONE (sin error real que clasificar). */
    private static IOSRunnerReadinessResult finish(
            IOSRunnerReadinessResult.Builder r, long startMs,
            IOSRunnerReadinessResult.Status status, IOSRunnerReadinessResult.Stage stage,
            String reason, String errorCode) {
        return finish(r, startMs, status, stage, reason, errorCode, IOSWdaErrorCode.NONE);
    }

    private static IOSRunnerReadinessResult finish(
            IOSRunnerReadinessResult.Builder r, long startMs,
            IOSRunnerReadinessResult.Status status, IOSRunnerReadinessResult.Stage stage,
            String reason, String errorCode, IOSWdaErrorCode wdaErrorCode) {
        r.status(status).lastStageReached(stage).reason(reason).errorCode(errorCode)
                .wdaErrorCode(wdaErrorCode);
        long durationMs = System.currentTimeMillis() - startMs;
        log("READINESS", status.name());
        System.out.printf("[%s] IOS_READINESS END status=%s stage=%s durationMs=%d%n",
                LocalTime.now().format(TS), status, stage, durationMs);
        return r.build();
    }
}
