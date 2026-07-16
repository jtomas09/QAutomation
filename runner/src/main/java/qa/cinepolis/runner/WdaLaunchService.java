package qa.cinepolis.runner;

import qa.cinepolis.runner.mirror.WdaEventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Única puerta de entrada para construir, iniciar, verificar y reutilizar WDA.
 *
 * Reemplaza la lógica que antes vivía repartida entre WdaManager.ensureWdaRunning()
 * (decidía CUÁNDO compilar), IOSMirrorProvider.triggerOnDemandLaunch() (decidía el
 * motivo de error leyendo appium.log — un archivo de OTRO proceso) e
 * IosPreflightManager (invocaba a WdaManager directamente). Ahora ambos flujos
 * (JobExecutor y el Mirror on-demand) pasan por este único método.
 *
 * Corrige dos causas raíz encontradas en la investigación previa:
 *
 *  1. "El Mirror no puede construir WDA por sí mismo": el antiguo
 *     ensureWdaRunning() retornaba false sin más SI wdaCached=false, delegando
 *     silenciosamente en Appium — pero el Mirror nunca crea una sesión Appium,
 *     así que ese camino jamás construía nada. Aquí, la ausencia de caché ya NO
 *     es una condición de "no intentar" — solo decide si se prueba primero el
 *     camino rápido (Attempt A, DerivedData existente) antes de caer siempre al
 *     build completo (Attempt B, funciona sin nada previo). Construir o no
 *     construir deja de depender de una ejecución previa de Appium.
 *
 *  2. "El Mirror puede mostrar errores de otra ejecución": el motivo de un
 *     fallo ya NO se busca en appium.log (el log del proceso Node de Appium,
 *     compartido entre ejecuciones) — se captura en tiempo real del PROPIO
 *     proceso xcodebuild que este método invocó (ver WdaManager.BuildOutcome),
 *     así que cada intento tiene su propio resultado, sin excepción.
 *
 * Máquina de estados publicada (ver IOSMirrorStateTracker.Phase):
 *   INITIALIZING_WDA → BUILDING_WDA → STARTING_WDA → VERIFYING_WDA → (MIRROR_ACTIVE | ERROR)
 *
 * ERROR es un estado TERMINAL: un fallo se recuerda por UDID (terminalErrors) y
 * ensureRunning() no vuelve a intentar automáticamente para ese UDID — el único
 * camino de salida es resetForRetry(), invocado exclusivamente por una acción
 * explícita del usuario (ver DeviceStreamServer, endpoint .../retry). Esto
 * elimina el retry infinito sin agregar temporizadores ni TTLs: es memoria
 * explícita, no una expiración de tiempo.
 *
 * Una ejecución real (JobExecutor) nunca consulta este estado terminal — solo
 * IOSMirrorProvider lo hace antes de decidir si reintentar on-demand. Una
 * ejecución real siempre construye/intenta de nuevo, y su resultado (éxito o
 * fallo) reemplaza cualquier estado terminal previo para ese UDID.
 */
public final class WdaLaunchService {

    /** UDID → motivo del último fallo terminal. Ausente = sin fallo terminal pendiente. */
    private static final Map<String, String> TERMINAL_ERRORS = new ConcurrentHashMap<>();

    /** UDID → true si el último intento realmente invocó xcodebuild (para invalidación de caché). */
    private static final Map<String, Boolean> LAST_LAUNCH_ATTEMPTED = new ConcurrentHashMap<>();

    private WdaLaunchService() {}

    // ── Consulta de estado terminal (usada solo por IOSMirrorProvider) ─────────

    public static boolean isTerminalError(String udid) {
        return TERMINAL_ERRORS.containsKey(udid);
    }

    public static String terminalErrorReason(String udid) {
        return TERMINAL_ERRORS.get(udid);
    }

    /** Única forma de salir de ERROR_TERMINAL — llamar solo ante una acción explícita del usuario. */
    public static void resetForRetry(String udid) {
        TERMINAL_ERRORS.remove(udid);
    }

    /**
     * Registra un fallo TERMINAL para {@code udid} y publica el evento correspondiente.
     * Público (no solo de uso interno de {@link #ensureRunning}) para que llamadores
     * que fallan ANTES de siquiera invocar ensureRunning (p.ej. IOSMirrorProvider si
     * BackendClient no está disponible aún) también respeten la misma máquina de
     * estados en vez de publicar ERROR directo sin registrar el estado absorbente.
     */
    public static void markTerminalError(String udid, String reason) {
        TERMINAL_ERRORS.put(udid, reason);
        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.ERROR, reason);
    }

    public static boolean wasLastLaunchAttempted(String udid) {
        return Boolean.TRUE.equals(LAST_LAUNCH_ATTEMPTED.get(udid));
    }

    // ── Entrada principal ───────────────────────────────────────────────────────

    /**
     * Construye (si hace falta), inicia y verifica WDA para {@code udid}.
     * Sincronizado globalmente — igual garantía de exclusión que el antiguo
     * ensureWdaRunning(): nunca dos invocaciones de xcodebuild en paralelo en
     * este Runner, sin importar si las piden JobExecutor o el Mirror.
     *
     * @param wdaCached solo decide si se intenta primero el camino rápido
     *                  (Attempt A, DerivedData existente) — NUNCA decide si se
     *                  intenta construir. Si no hay caché, o el camino rápido
     *                  no aplica, siempre se cae al build completo (Attempt B).
     * @return true si WDA quedó confirmado listo (/status responde)
     */
    public static synchronized boolean ensureRunning(BackendClient client, String executionId,
                                                      String udid, String teamId,
                                                      String wdaBundleId, boolean wdaCached) {
        WdaManager.resetDetectedUrl();
        LAST_LAUNCH_ATTEMPTED.put(udid, false);

        client.sendTechLog(executionId,
                "[WDA] Verificando WebDriverAgent en localhost:" + WdaManager.WDA_PORT + "...");

        // Fast path: WDA ya está corriendo (sobrevivió de una sesión anterior).
        if (WdaManager.isWdaRunning()) {
            String active = WdaManager.getDetectedWdaUrl() != null
                    ? WdaManager.getDetectedWdaUrl() : WdaManager.getWdaBaseUrl();
            client.sendLog(executionId, "INFO",
                    "✅ [WDA] WebDriverAgent ya está activo en " + active + ".");
            resetForRetry(udid);
            return true;
        }

        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.BUILDING);
        client.sendLog(executionId, "INFO",
                wdaCached
                    ? "🔨 [WDA] WDA precompilado detectado — iniciando en " + udid + "..."
                    : "🔨 [WDA] Compilando WebDriverAgent desde cero para " + udid
                      + " (primera vez en este dispositivo — puede tardar varios minutos)...");

        // Resuelto una sola vez — lo necesita tanto el Attempt B inicial como el
        // reintento tras desinstalar un WDA en conflicto (ver más abajo).
        String projectPath = WdaManager.findWdaProjectPath();

        // Attempt A: test-without-building — solo si hay caché, es el camino rápido.
        WdaManager.BuildOutcome outcome = wdaCached
                ? WdaManager.tryStartFromDerivedData(client, executionId, udid)
                : WdaManager.BuildOutcome.notStarted();

        // Attempt B: build completo — SIEMPRE se intenta si A no aplicó/falló.
        // Esto es lo que elimina la dependencia de una ejecución previa de Appium:
        // ya no importa si wdaCached es false, este camino funciona desde cero.
        if (!outcome.started) {
            if (projectPath == null) {
                String reason = "No se encontró WebDriverAgent.xcodeproj — reinstala el driver: "
                        + "appium driver install xcuitest";
                client.sendLog(executionId, "ERROR", "❌ [WDA] " + reason);
                markTerminalError(udid, reason);
                return false;
            }
            outcome = WdaManager.tryStartFromProject(client, executionId, udid, teamId, wdaBundleId, projectPath);
        }

        if (!outcome.started) {
            String reason = "El proceso xcodebuild no pudo iniciarse.";
            client.sendLog(executionId, "ERROR", "❌ [WDA] " + reason);
            markTerminalError(udid, reason);
            return false;
        }

        LAST_LAUNCH_ATTEMPTED.put(udid, true);
        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.STARTING);
        client.sendTechLog(executionId,
                "[WDA] Proceso WebDriverAgent iniciado. Esperando que el servidor HTTP arranque...");

        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.VERIFYING);
        // Un build desde cero puede tardar varios minutos (documentado al usuario
        // arriba) — el plazo de espera es mayor que el del camino rápido (caché),
        // pero sigue siendo un límite real, nunca una espera indefinida.
        int timeoutSeconds = wdaCached ? 180 : 600;
        boolean ready = WdaManager.waitForWdaReady(client, executionId, timeoutSeconds);

        // Causa raíz #2 (demostrada con ejecución real): un WDA anterior sigue
        // instalado en el dispositivo, firmado con un Team distinto al actual — iOS
        // rechaza la instalación (MismatchedApplicationIdentifierEntitlement).
        // Equivalente exacto al comportamiento de Appium ante este mismo error
        // (installToRealDevice, appium-webdriveragent): desinstalar el bundle
        // conflictivo y reintentar UNA sola vez — nunca de forma preventiva, solo
        // cuando iOS mismo ya confirmó el conflicto.
        if (!ready && outcome.mismatchedIdentifier() && projectPath != null) {
            String xctestBundleId = wdaBundleId + ".xctrunner";
            client.sendLog(executionId, "WARN",
                    "♻️ [WDA] Hay una instalación anterior de WebDriverAgent firmada con un Team "
                    + "distinto — desinstalando " + xctestBundleId + " y reintentando...");
            if (WdaManager.uninstallApp(udid, xctestBundleId)) {
                outcome = WdaManager.tryStartFromProject(client, executionId, udid, teamId, wdaBundleId, projectPath);
                if (outcome.started) {
                    ready = WdaManager.waitForWdaReady(client, executionId, timeoutSeconds);
                }
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️ [WDA] No se pudo desinstalar " + xctestBundleId + " — no se reintenta.");
            }
        }

        if (!ready) {
            String captured = outcome.capturedError();
            String detectedUrl = WdaManager.getDetectedWdaUrl();
            String reason = captured != null && !captured.isBlank()
                    ? captured
                    : (detectedUrl == null || detectedUrl.isBlank())
                        ? "No apareció ServerURLHere en " + timeoutSeconds + "s — WDA nunca inició."
                        : "WDA inició (" + detectedUrl + ") pero /status no respondió en " + timeoutSeconds + "s.";
            client.sendLog(executionId, "ERROR",
                    "❌ [WDA] " + reason + "\n" + WdaManager.diagnoseWdaFailure(udid, teamId));
            markTerminalError(udid, reason);
            return false;
        }

        client.sendLog(executionId, "INFO", "✅ [WDA] WebDriverAgent listo.");
        resetForRetry(udid);
        return true;
    }

}
