package qa.cinepolis.runner;

import qa.cinepolis.runner.mirror.WdaEventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ÚNICA autoridad del ciclo de vida de WebDriverAgent (WDA) dentro del Runner.
 *
 * Reemplaza a la antigua WdaLaunchService. Ningún otro componente de este repo invoca
 * directamente a WdaManager.tryStartFromDerivedData()/tryStartFromProject()/
 * stop()/cleanup() — todos pasan por {@link #acquire} / {@link #teardown} de
 * esta clase. WdaManager sigue siendo el MECANISMO (cómo compilar, cómo matar
 * un proceso, cómo consultar el dispositivo); esta clase es la POLÍTICA (cuándo
 * hacerlo, y garantizar que nunca se haga dos veces a la vez para el mismo
 * dispositivo).
 *
 * Garantías que esta clase provee, con evidencia de por qué cada una hace falta
 * (ver investigación previa, ejecución real contra dispositivo físico):
 *
 * 1. UNA SOLA COMPILACIÓN POR DISPOSITIVO A LA VEZ, sin importar quién la pide.
 *    Antes: DriverFactory dejaba que Appium compilara su propia copia de WDA
 *    cuando no había URL viva confirmada (capability skipServerInstallation=
 *    false) — un tercer actor, invisible para este Runner, iniciando xcodebuild
 *    por su cuenta. Ahora: DriverFactory YA NUNCA activa ese camino (ver su
 *    propio cambio) — la única forma de obtener WDA es a través de esta clase.
 *    Además, si dos llamadores de ESTE Runner piden WDA para el mismo UDID casi
 *    al mismo tiempo (una ejecución real y un lanzamiento on-demand del Mirror),
 *    el segundo se une al resultado del primero en vez de lanzar un segundo
 *    xcodebuild — implementado con un CompletableFuture compartido por UDID.
 *
 * 2. CLEANUP DETERMINÍSTICO, verificado contra el dispositivo real (no solo
 *    HTTP /status), y NUNCA dependiente de que un shutdown hook de la JVM
 *    llegue a ejecutarse. teardown() se invoca explícitamente desde
 *    IOSExecutionCleanupManager (fin de cada ejecución) y desde
 *    RunnerAgent.stopAllServices() (apagado ordenado del propio Runner).
 *
 * 3. BARRIDO DE ARRANQUE: sweepStaleProcesses(), invocado una única vez al
 *    iniciar el Runner, mata cualquier xcodebuild de WDA que haya sobrevivido
 *    de una instancia ANTERIOR de este mismo Runner — el escenario demostrado
 *    con evidencia real: un reinicio del Runner (auto-update, crash, kill -9)
 *    deja `wdaProcess` como una referencia Java perdida para siempre, pero el
 *    proceso del SO sigue vivo, reparentado a launchd (PPID=1). Como ese
 *    proceso nunca dependió de ninguna referencia Java para existir, tampoco
 *    necesita ninguna para ser encontrado y destruido: se identifica por línea
 *    de comando (contiene "xcodebuild" y la ruta de WebDriverAgent.xcodeproj),
 *    no por PID recordado.
 */
public final class WdaLifecycleOwner {

    private WdaLifecycleOwner() {}

    /** Resultado de un intento de dejar WDA listo — nunca se reutiliza entre intentos reales. */
    static final class Result {
        final boolean ready;
        final String  reason;
        Result(boolean ready, String reason) { this.ready = ready; this.reason = reason; }
    }

    // UDID → intento en curso. Mientras exista una entrada, cualquier llamador
    // nuevo para ESE UDID se une al mismo Future en vez de lanzar un segundo
    // xcodebuild — esto es lo que garantiza "una sola compilación a la vez",
    // estructuralmente, sin importar el orden de llegada de Mirror vs. ejecución real.
    private static final Map<String, CompletableFuture<Result>> INFLIGHT = new ConcurrentHashMap<>();

    // UDID → motivo del último fallo terminal. Mismo propósito que tenía
    // WdaLifecycleOwner.TERMINAL_ERRORS: evita que el Mirror reintente
    // automáticamente tras un fallo — solo una acción explícita del usuario
    // (resetForRetry, vía el endpoint .../retry) lo libera. Las ejecuciones
    // reales (JobExecutor/IosPreflightManager) nunca consultan este mapa.
    private static final Map<String, String> TERMINAL_ERRORS = new ConcurrentHashMap<>();

    // UDID → true si el último intento realmente invocó xcodebuild (para
    // invalidación de caché en IosPreflightManager) — distingue "se intentó y
    // falló" de "nunca llegó a intentarse" (p.ej. WebDriverAgent.xcodeproj
    // ausente).
    private static final Map<String, Boolean> LAST_LAUNCH_ATTEMPTED = new ConcurrentHashMap<>();

    static boolean wasLastLaunchAttempted(String udid) {
        return Boolean.TRUE.equals(LAST_LAUNCH_ATTEMPTED.get(udid));
    }

    // Hilo dedicado para builds — nunca el ForkJoinPool común, para no competir
    // con el resto de tareas asíncronas de la JVM del Runner. Un solo hilo basta:
    // solo puede haber un build real en curso por Mac (WdaManager.wdaProcess es
    // una única referencia estática), así que paralelizar aquí no tendría efecto.
    private static final ExecutorService BUILD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wda-lifecycle-owner");
        t.setDaemon(true);
        return t;
    });

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

    public static void markTerminalError(String udid, String reason) {
        TERMINAL_ERRORS.put(udid, reason);
        WdaEventBus.publish(
                udid, WdaEventBus.WdaEvent.ERROR, reason);
    }

    // ── Entrada principal ───────────────────────────────────────────────────────

    /**
     * Construye (si hace falta), inicia y verifica WDA para {@code udid}. Si ya
     * hay un intento en curso para este UDID (lanzado por cualquier llamador —
     * ejecución real o Mirror), esta llamada se une a ÉL y espera su resultado
     * en vez de lanzar un segundo xcodebuild. Nunca hay dos compilaciones
     * simultáneas para el mismo dispositivo.
     *
     * @param wdaCached solo decide si se intenta primero el camino rápido
     *                  (test-without-building) antes del build completo —
     *                  nunca decide si se intenta construir.
     * @return resultado con ready=true si WDA quedó confirmado (/status responde).
     */
    static Result acquire(BackendClient client, String executionId, String udid,
                          String teamId, String wdaBundleId, boolean wdaCached) {
        // Fast path: WDA ya está corriendo — no hay nada que construir.
        if (WdaManager.isWdaRunning()) {
            client.sendLog(executionId, "INFO",
                    "✅ [WDA] WebDriverAgent ya está activo — reutilizando, sin reconstruir.");
            resetForRetry(udid);
            return new Result(true, null);
        }

        CompletableFuture<Result> future = INFLIGHT.computeIfAbsent(udid, k ->
                CompletableFuture.supplyAsync(
                        () -> runAttempt(client, executionId, udid, teamId, wdaBundleId, wdaCached),
                        BUILD_EXECUTOR));

        client.sendTechLog(executionId,
                "[WDA] " + (future.isDone() ? "Resultado reciente reutilizado" : "Uniéndose a un intento de WDA")
                + " para " + udid + " — nunca se lanza una segunda compilación mientras haya una en curso.");

        try {
            return future.get();
        } catch (Exception e) {
            String reason = "Error esperando el intento de WDA en curso: " + e.getMessage();
            client.sendLog(executionId, "ERROR", "❌ [WDA] " + reason);
            return new Result(false, reason);
        } finally {
            // Libera el slot SOLO si el Future guardado sigue siendo este (evita
            // borrar el de un intento nuevo que ya haya empezado justo después).
            INFLIGHT.remove(udid, future);
        }
    }

    /**
     * Contiene la lógica real de construcción — idéntica a la que antes vivía en
     * WdaLifecycleOwner.ensureRunning(), sin cambios de comportamiento. Corre
     * dentro de BUILD_EXECUTOR; solo la primera llamada para un UDID dado la
     * ejecuta, cualquier otra se une al mismo Future (ver acquire()).
     */
    private static Result runAttempt(BackendClient client, String executionId, String udid,
                                      String teamId, String wdaBundleId, boolean wdaCached) {
        WdaManager.resetDetectedUrl();
        LAST_LAUNCH_ATTEMPTED.put(udid, false);

        client.sendTechLog(executionId,
                "[WDA] Verificando WebDriverAgent en localhost:" + WdaManager.WDA_PORT + "...");

        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.BUILDING);
        client.sendLog(executionId, "INFO",
                wdaCached
                    ? "🔨 [WDA] WDA precompilado detectado — iniciando en " + udid + "..."
                    : "🔨 [WDA] Compilando WebDriverAgent desde cero para " + udid
                      + " (primera vez en este dispositivo — puede tardar varios minutos)...");

        String projectPath = WdaManager.findWdaProjectPath();

        WdaManager.BuildOutcome outcome = wdaCached
                ? WdaManager.tryStartFromDerivedData(client, executionId, udid)
                : WdaManager.BuildOutcome.notStarted();

        if (!outcome.started) {
            if (projectPath == null) {
                String reason = "No se encontró WebDriverAgent.xcodeproj — reinstala el driver: "
                        + "appium driver install xcuitest";
                client.sendLog(executionId, "ERROR", "❌ [WDA] " + reason);
                markTerminalError(udid, reason);
                return new Result(false, reason);
            }
            outcome = WdaManager.tryStartFromProject(client, executionId, udid, teamId, wdaBundleId, projectPath);
        }

        if (!outcome.started) {
            String reason = "El proceso xcodebuild no pudo iniciarse.";
            client.sendLog(executionId, "ERROR", "❌ [WDA] " + reason);
            markTerminalError(udid, reason);
            return new Result(false, reason);
        }

        LAST_LAUNCH_ATTEMPTED.put(udid, true);

        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.STARTING);
        client.sendTechLog(executionId,
                "[WDA] Proceso WebDriverAgent iniciado. Esperando que el servidor HTTP arranque...");

        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.VERIFYING);
        int timeoutSeconds = wdaCached ? 180 : 600;
        boolean ready = WdaManager.waitForWdaReady(client, executionId, timeoutSeconds);

        // Un WDA anterior sigue instalado, firmado con un Team distinto al actual —
        // iOS rechaza la instalación (MismatchedApplicationIdentifierEntitlement).
        // Desinstalar el bundle conflictivo y reintentar UNA sola vez — nunca de
        // forma preventiva, solo cuando iOS mismo ya confirmó el conflicto.
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
            String reason = outcome.deviceLocked()
                    ? "Dispositivo bloqueado — el arranque de WDA se canceló de inmediato "
                      + "(no se esperó el timeout interno de xcodebuild). Desbloquea el iPhone y reintenta."
                    : captured != null && !captured.isBlank()
                        ? captured
                        : (detectedUrl == null || detectedUrl.isBlank())
                            ? "No apareció ServerURLHere en " + timeoutSeconds + "s — WDA nunca inició."
                            : "WDA inició (" + detectedUrl + ") pero /status no respondió en " + timeoutSeconds + "s.";
            client.sendLog(executionId, "ERROR",
                    "❌ [WDA] " + reason
                    + (outcome.deviceLocked() ? "" : "\n" + WdaManager.diagnoseWdaFailure(udid, teamId)));
            markTerminalError(udid, reason);
            return new Result(false, reason);
        }

        client.sendLog(executionId, "INFO", "✅ [WDA] WebDriverAgent listo.");
        resetForRetry(udid);
        return new Result(true, null);
    }

    // ── Detención / limpieza — único punto de entrada ──────────────────────────

    /**
     * Única forma de detener y limpiar WDA. Determinístico: verifica contra el
     * propio dispositivo (no solo HTTP) que ya no queda ningún proceso WDA vivo
     * antes de devolver el control — ver WdaManager.cleanup() para el detalle de
     * la verificación con devicectl.
     */
    static void teardown(BackendClient client, String executionId, String udid) {
        WdaManager.cleanup(client, executionId, udid);
    }

    // ── Barrido de arranque ──────────────────────────────────────────────────────

    /**
     * Se invoca UNA vez al iniciar el Runner (ver RunnerAgent). Mata cualquier
     * xcodebuild de WDA que haya sobrevivido a un reinicio anterior de este
     * mismo Runner — identificado por línea de comando (contiene "xcodebuild" y
     * la ruta hacia WebDriverAgent.xcodeproj), nunca por PID recordado, porque
     * un reinicio de la JVM pierde cualquier referencia Java pero el proceso del
     * sistema operativo no depende de esa referencia para seguir vivo.
     *
     * No toca ningún otro proceso xcodebuild ajeno a WebDriverAgent (por ejemplo,
     * si el usuario está compilando su propio proyecto en Xcode al mismo tiempo).
     *
     * Idempotente: si no encuentra nada, no hace nada más que informarlo. Además
     * de en el arranque/apagado del Runner, se reutiliza en la verificación final
     * de cleanup() (ver IOSExecutionCleanupManager) — llamarla varias veces nunca
     * tiene efectos secundarios distintos de matar lo que siga vivo en ese instante.
     *
     * @return cuántos procesos huérfanos encontró y detuvo (0 = sistema ya limpio).
     */
    static int sweepStaleProcesses() {
        int killed = 0;
        for (ProcessHandle h : ProcessHandle.allProcesses().toList()) {
            try {
                String cmd = h.info().commandLine().orElse("");
                if (looksLikeOrphanWdaBuild(cmd)) {
                    h.destroyForcibly();
                    killed++;
                    System.out.println("[WdaLifecycleOwner] Barrido: xcodebuild de WDA "
                            + "detenido (PID=" + h.pid() + ").");
                }
            } catch (Exception ignored) {}
        }
        if (killed == 0) {
            System.out.println("[WdaLifecycleOwner] Barrido: sin xcodebuild de WDA huérfanos.");
        }
        return killed;
    }

    /**
     * Cuenta (sin matar) cuántos procesos xcodebuild de WDA siguen vivos AHORA
     * MISMO en el Mac — usado por la verificación final de cleanup() para
     * reportar "xcodebuild = 0" con un número real, no una suposición.
     */
    static int countXcodebuildProcesses() {
        int count = 0;
        for (ProcessHandle h : ProcessHandle.allProcesses().toList()) {
            try {
                if (looksLikeOrphanWdaBuild(h.info().commandLine().orElse(""))) count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    private static boolean looksLikeOrphanWdaBuild(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return false;
        String c = commandLine.toLowerCase();
        return c.contains("xcodebuild") && c.contains("webdriveragent");
    }
}
