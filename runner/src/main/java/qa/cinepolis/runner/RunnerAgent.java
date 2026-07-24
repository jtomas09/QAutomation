package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Universal Runner Agent v6.0 — Real Lifecycle Management
 *
 * State machine:
 *   STOPPED → STARTING → RUNNING → STOPPING → STOPPED
 *
 * The JVM never exits on STOP/RESTART commands.  Instead the runner enters
 * a STOPPED state where it keeps sending OFFLINE heartbeats and waits for
 * a START command to re-initialize all services.
 *
 * Commands received via heartbeat X-Runner-Command header:
 *   START   → (if STOPPED/ERROR) full initialization cycle
 *   STOP    → (if RUNNING/BUSY)  graceful teardown
 *   RESTART → teardown + re-initialization
 *
 * Platform detection (unchanged from v5):
 *   Windows → Android (ADB)
 *   macOS   → Android (ADB) + iOS (Xcode)
 *   Linux   → Android (ADB)
 */
public class RunnerAgent {

    // ── Lifecycle state ────────────────────────────────────────────────────────

    enum LifecycleState { STARTING, RUNNING, STOPPING, STOPPED, RESTARTING, ERROR }

    private static volatile LifecycleState  lifecycleState = LifecycleState.STOPPED;
    private static volatile boolean         jvmShutting    = false;

    // ── Long-lived components (survive start/stop cycles) ─────────────────────

    private static RunnerConfig             config;
    private static BackendClient            client;
    private static PlatformToolsManager     platformTools;
    private static UpdateManager            updateMgr;

    /** Cliente autenticado compartido — reutilizado por IOSMirrorProvider para el lanzamiento on-demand de WDA. */
    public static BackendClient getClient() { return client; }

    // ── Short-lived components (created on START, nulled on STOP) ─────────────

    private static volatile AppiumManager               appiumMgr;
    private static volatile DeviceStreamServer          streamServer;
    private static volatile JobExecutor                 jobExecutor;
    private static volatile DependencySelfHealingManager selfHealing;
    private static volatile DeviceSelfHealingManager    deviceHealer;
    private static volatile ScheduledExecutorService    workScheduler;
    private static volatile Thread                      jobPollThread;

    // ── Shared references ─────────────────────────────────────────────────────

    private static final AtomicReference<String> pendingCommand = new AtomicReference<>();
    private static final AtomicBoolean           prevAdbOk      = new AtomicBoolean(false);
    private static final AtomicReference<DeviceSelfHealingManager> deviceHealerRef
            = new AtomicReference<>();

    // ── Diagnóstico PROCESS FLOW / ShutdownHook ────────────────────────────────
    // Motivo del cierre de JVM, fijado por quien decide terminar el proceso (p.ej.
    // UpdateManager ANTES de System.exit(0)) para que el shutdown hook pueda
    // reportar POR QUÉ se está cerrando, no solo QUE se está cerrando.
    //
    // Tipo fuerte en vez de String: el compilador impide pasar un motivo inválido
    // o inventado ad-hoc en un call site nuevo — solo existen los valores
    // declarados aquí, y agregar uno nuevo exige tocar este enum explícitamente.
    public enum ShutdownReason {
        /** Runner reinicia su propia JVM tras aplicar una actualización de versión (UpdateManager). */
        AUTO_UPDATE,
        /** OutOfMemoryError / StackOverflowError / InternalError / UnknownError no recuperable en un hilo de larga vida. */
        FATAL_VM_ERROR,
        /** Valor por defecto: la JVM se está cerrando sin haber pasado por requestShutdown()
         *  (kill externo, Ctrl+C, SIGTERM, o cualquier terminación no iniciada por este código). */
        UNKNOWN
    }

    private static final AtomicReference<ShutdownReason> shutdownReason =
            new AtomicReference<>(ShutdownReason.UNKNOWN);

    // Java no expone a los shutdown hooks el exitCode pasado a System.exit() —
    // se guarda aquí, junto al motivo, en el mismo instante en requestShutdown().
    // -1 = ningún exitCode registrado todavía (kill externo/SIGTERM/Ctrl+C).
    private static final AtomicInteger shutdownExitCode = new AtomicInteger(-1);

    /** Diagnóstico de solo lectura — no cambia ningún comportamiento. */
    public static boolean isJobActive() {
        JobExecutor je = jobExecutor;
        return je != null && je.hasActiveProcess();
    }

    /** Llamar ANTES de cualquier System.exit()/Runtime.halt() para que el shutdown hook explique el motivo. */
    private static void recordShutdownReason(ShutdownReason reason) {
        shutdownReason.set(reason);
    }

    /**
     * Único punto de entrada oficial para terminar deliberadamente esta JVM.
     *
     * No reemplaza ni duplica el mecanismo de limpieza — el shutdown hook
     * registrado en main() (que llama stopAllServices(true)) YA se dispara para
     * cualquier System.exit(), sin importar el hilo que lo invoque; eso es
     * garantía de la propia JVM, no de este método. Lo que este método fija es
     * el RITUAL previo: es estructuralmente imposible terminar el proceso desde
     * este código sin dejar registrado el motivo — evita que un futuro punto de
     * llamada invoque System.exit() directamente y deje el shutdown hook sin
     * explicación. El motivo es un ShutdownReason (no String): el compilador
     * rechaza cualquier valor que no sea uno de los declarados en el enum.
     *
     * Todo componente que decida terminar el Runner (auto-update aplicado,
     * un Error fatal en el job-poll thread, o cualquier caso futuro) debe pasar
     * por aquí en vez de llamar a System.exit() por su cuenta.
     */
    public static void requestShutdown(int exitCode, ShutdownReason reason) {
        shutdownExitCode.set(exitCode);
        recordShutdownReason(reason);
        System.exit(exitCode);
    }

    // ── Gate exclusivo: aceptación de Jobs vs. auto-update ─────────────────────
    // Mismo idioma que WdaLaunchCoordinator.Owner (AtomicReference<Owner>, null =
    // libre): un único punto de CAS compartido entre el job-poll thread (que
    // reclama jobs del backend) y UpdateManager (que aplica actualizaciones).
    //
    // Por qué esto cierra la carrera POR COMPLETO y no solo la reduce: con un
    // simple "if (isJobActive()) return;" siempre queda una ventana entre LEER
    // el estado y ACTUAR sobre él (check-then-act), sin importar cuántas veces
    // se re-chequee. Aquí no hay lectura seguida de acción — el propio CAS ES
    // la acción: tryBeginJob() y beginExclusiveUpdate() compiten por la MISMA
    // referencia, así que exactamente uno de los dos puede tener éxito en un
    // instante dado. Si UPDATE tiene el gate, tryBeginJob() falla de forma
    // determinística — el job-poll thread ni siquiera le pide un job nuevo al
    // backend ese ciclo (no hay job que "deshacer" después).
    private enum GateOwner { JOB, UPDATE }
    private static final AtomicReference<GateOwner> gate = new AtomicReference<>(null);

    /** Job-poll thread: reserva el gate ANTES de pedir un job al backend.
     *  Si falla, un auto-update ya lo tiene — no se debe llamar a getNextJob(). */
    private static boolean tryBeginJob() {
        return gate.compareAndSet(null, GateOwner.JOB);
    }

    /** Libera el gate del lado job — llamar siempre (con o sin job encontrado). */
    private static void endJob() {
        gate.compareAndSet(GateOwner.JOB, null);
    }

    /**
     * UpdateManager: reserva el gate en EXCLUSIVA antes de descargar/aplicar una
     * actualización. Solo tiene éxito si el gate está libre — es decir, si el
     * job-poll thread ni está reclamando un job ni tiene uno en ejecución en
     * este instante. A partir de que esto retorna true y hasta endExclusiveUpdate()
     * (o System.exit()), tryBeginJob() falla determinísticamente: ningún job
     * nuevo puede empezar, sin importar cuánto tarde la descarga.
     */
    public static boolean beginExclusiveUpdate() {
        return gate.compareAndSet(null, GateOwner.UPDATE);
    }

    /** Libera el gate de actualización — llamar siempre que beginExclusiveUpdate()
     *  haya devuelto true y NO se vaya a llamar System.exit() (p.ej. descarga fallida). */
    public static void endExclusiveUpdate() {
        gate.compareAndSet(GateOwner.UPDATE, null);
    }

    // ── Lifecycle scheduler (always alive, drives heartbeats + commands) ──────

    private static ScheduledExecutorService lifecycleScheduler;

    // ─────────────────────────────────────────────────────────────────────────
    // main
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // ── DefaultUncaughtExceptionHandler ────────────────────────────────────
        // Cualquier excepción no capturada en CUALQUIER hilo de esta JVM (incluidos
        // work-scheduler, job-poll, abort-watcher) queda registrada con nombre del
        // hilo y stack completa — para descartar/confirmar que un Error no
        // manejado en un hilo daemon está matando la JVM de forma indirecta.
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("\n==========================");
            System.err.println("PROCESS FLOW — UNCAUGHT EXCEPTION");
            System.err.println("==========================");
            System.err.println("[PROCESS FLOW] Fecha/hora : " + java.time.LocalDateTime.now());
            System.err.println("[PROCESS FLOW] Hilo       : " + t.getName());
            System.err.println("[PROCESS FLOW] Excepción  : " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            System.err.println("==========================");
        });

        config        = RunnerConfig.fromEnv();
        client        = new BackendClient(config.backendUrl, config.runnerToken, config.runnerId);

        Path agentDataDir = Path.of(config.agentDataDir);
        Path runnerDir    = "WINDOWS".equals(config.os)
                ? agentDataDir.resolve("runner")
                : agentDataDir;

        platformTools = new PlatformToolsManager(runnerDir, config.os, config.backendUrl);
        updateMgr     = new UpdateManager(
                config.backendUrl, config.runnerToken, config.version, agentDataDir);

        // JVM shutdown hook — hard cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jvmShutting = true;
            boolean jobWasActive = isJobActive();
            System.out.println("\n==========================");
            System.out.println("PROCESS FLOW — SHUTDOWN HOOK EJECUTADO");
            System.out.println("==========================");
            System.out.println("[PROCESS FLOW] Fecha/hora        : " + java.time.LocalDateTime.now());
            System.out.println("[PROCESS FLOW] Hilo del hook     : " + Thread.currentThread().getName());
            System.out.println("[PROCESS FLOW] Shutdown reason   : " + shutdownReason.get().name());
            System.out.println("[PROCESS FLOW] Exit code         : "
                    + (shutdownExitCode.get() == -1 ? "N/A (no pasó por requestShutdown — kill externo/SIGTERM/Ctrl+C)"
                                                     : shutdownExitCode.get()));
            System.out.println("[PROCESS FLOW] ¿Job activo ahora?: " + jobWasActive
                    + (jobWasActive ? "  ⚠ HABÍA UN PROCESO GRADLE VIVO — este shutdown lo va a destruir." : ""));
            System.out.println("[PROCESS FLOW] Estado lifecycle  : " + lifecycleState);
            System.out.println("[PROCESS FLOW] Stack traces de TODOS los hilos vivos en este instante:");
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread th = entry.getKey();
                System.out.println("  -- Hilo: " + th.getName() + " (daemon=" + th.isDaemon() + ", estado=" + th.getState() + ")");
                for (StackTraceElement el : entry.getValue()) {
                    System.out.println("       at " + el);
                }
            }
            System.out.println("==========================");
            System.out.println("[Runner] JVM cerrando — limpiando recursos...");
            stopAllServices(true);
        }, "shutdown-hook"));

        // ── Lifecycle scheduler (never stops) ─────────────────────────────────
        lifecycleScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lifecycle-scheduler");
            t.setDaemon(true);
            return t;
        });
        lifecycleScheduler.scheduleAtFixedRate(RunnerAgent::lifecycleTick, 5, 5, TimeUnit.SECONDS);

        // ── Initial startup ────────────────────────────────────────────────────
        System.out.println("[Runner] Iniciando servicios...");
        startAllServices();

        // ── Keep JVM alive ────────────────────────────────────────────────────
        while (!jvmShutting) {
            try { Thread.sleep(2_000); } catch (InterruptedException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle tick  (runs every 5 s on the lifecycle-scheduler thread)
    // ─────────────────────────────────────────────────────────────────────────

    private static void lifecycleTick() {
        if (jvmShutting) return;
        try {
            String cmd = pendingCommand.getAndSet(null);

            if (lifecycleState == LifecycleState.STOPPED
                    || lifecycleState == LifecycleState.ERROR) {

                // Send OFFLINE heartbeat to stay visible in the backend, and
                // receive any pending START command.
                try {
                    String rcvCmd = client.sendHeartbeat(
                            config.runnerId, config.platform, config.version,
                            "OFFLINE", config.os, config.hostname,
                            config.androidSupported, config.iosSupported,
                            Collections.emptyList());
                    if (rcvCmd != null && cmd == null) cmd = rcvCmd;
                } catch (Exception e) {
                    System.err.println("[Runner] OFFLINE heartbeat error: " + e.getMessage());
                }
            }

            if (cmd != null) dispatchCommand(cmd);

        } catch (Exception e) {
            System.err.println("[Runner] lifecycleTick error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // dispatchCommand — runs lifecycle transitions on a dedicated thread
    //                   so they never block the lifecycle-scheduler
    // ─────────────────────────────────────────────────────────────────────────

    private static void dispatchCommand(String cmd) {
        System.out.println("[Runner] Comando recibido: " + cmd);
        if (("STOP".equalsIgnoreCase(cmd) || "RESTART".equalsIgnoreCase(cmd)) && isJobActive()) {
            System.out.println("[PROCESS FLOW] Comando " + cmd + " recibido MIENTRAS HAY UN JOB ACTIVO — "
                    + "esto va a forzar killActiveProcess() sobre el proceso Gradle en ejecución.");
        }
        switch (cmd.toUpperCase()) {
            case "START" -> {
                if (lifecycleState == LifecycleState.STOPPED
                        || lifecycleState == LifecycleState.ERROR) {
                    System.out.println("[Runner] Ejecutando START...");
                    new Thread(RunnerAgent::startAllServices, "lifecycle-start").start();
                } else {
                    System.out.println("[Runner] START ignorado — estado actual: " + lifecycleState);
                }
            }
            case "STOP" -> {
                if (lifecycleState == LifecycleState.RUNNING) {
                    System.out.println("[Runner] Ejecutando STOP...");
                    new Thread(() -> stopAllServices(false), "lifecycle-stop").start();
                } else {
                    System.out.println("[Runner] STOP ignorado — estado actual: " + lifecycleState);
                }
            }
            case "RESTART" -> {
                System.out.println("[Runner] Ejecutando RESTART...");
                new Thread(() -> {
                    if (lifecycleState == LifecycleState.RUNNING) stopAllServices(false);
                    try { Thread.sleep(2_000); } catch (InterruptedException ignored) {}
                    startAllServices();
                }, "lifecycle-restart").start();
            }
            default -> System.out.println("[Runner] Comando desconocido: " + cmd);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startAllServices  (synchronized — prevents concurrent starts)
    // ─────────────────────────────────────────────────────────────────────────

    private static synchronized void startAllServices() {
        if (lifecycleState == LifecycleState.RUNNING
                || lifecycleState == LifecycleState.STARTING) {
            System.out.println("[Runner] startAllServices ignorado — estado: " + lifecycleState);
            return;
        }
        System.out.println("[Runner] ===== INICIANDO RUNNER =====");
        lifecycleState = LifecycleState.STARTING;

        try {
            // Send STARTING heartbeat immediately so the dashboard can show the
            // transitional state.
            silentHeartbeat("STARTING");

            // ── 1. ADB ─────────────────────────────────────────────────────────
            String  adbPath       = platformTools.resolveAdb();
            boolean adbFunctional = platformTools.isAdbFunctional();
            String  adbVersion    = platformTools.getAdbVersion();
            System.setProperty("ADB_PATH",    adbPath);
            System.setProperty("ADB_VERSION", adbVersion);
            System.setProperty("ADB_OK",      String.valueOf(adbFunctional));
            config.androidSupported = adbFunctional;
            System.out.println("[Runner] ADB: path=" + adbPath
                    + "  version=" + adbVersion + "  ok=" + adbFunctional);
            if (!adbFunctional)
                System.out.println("[Runner] ADB no disponible. Continuando en DEGRADED.");

            // ── 2. Android SDK ─────────────────────────────────────────────────
            AndroidEnvironmentBootstrap androidBootstrap = AndroidEnvironmentBootstrap.get();
            if (androidBootstrap.isValid()) {
                System.setProperty("ANDROID_HOME",     androidBootstrap.getSdkPath());
                System.setProperty("ANDROID_SDK_ROOT", androidBootstrap.getSdkPath());
                System.out.println("[Runner] Android SDK: " + androidBootstrap.getSdkPath());
            } else {
                System.out.println("[Runner] Android SDK no encontrado.");
            }

            // ── 3. Appium ──────────────────────────────────────────────────────
            appiumMgr = new AppiumManager(config.os);
            try { appiumMgr.ensureRunning(); }
            catch (Exception e) {
                System.err.println("[Runner] Appium no pudo iniciarse: " + e.getMessage());
            }
            boolean appiumOk      = appiumMgr.isAlive();
            String  appiumVersion = appiumOk ? appiumMgr.getAppiumVersion() : "unavailable";
            System.setProperty("APPIUM_OK",      String.valueOf(appiumOk));
            System.setProperty("APPIUM_VERSION", appiumVersion);
            appiumMgr.logDiagnostic();

            // ── 4. JRE telemetry ───────────────────────────────────────────────
            System.setProperty("JRE_VERSION", System.getProperty("java.version", "unavailable"));

            // ── 5. Node telemetry ──────────────────────────────────────────────
            boolean nodeOk = detectNode();

            // ── 6. Xcode (macOS only) ──────────────────────────────────────────
            boolean xcodeOk;
            if ("MACOS".equals(config.os)) {
                XcodeValidator.XcodeInfo xcodeInfo = XcodeValidator.validate();
                xcodeOk             = xcodeInfo.installed;
                config.iosSupported = xcodeInfo.installed;
                System.setProperty("XCODE_OK",      String.valueOf(xcodeOk));
                System.setProperty("XCODE_VERSION",
                        xcodeInfo.xcodeVersion != null ? xcodeInfo.xcodeVersion : "unavailable");
                System.out.printf("[Runner] Xcode: %s%n",
                        xcodeOk ? "ok (" + xcodeInfo.xcodeVersion + ")" : "no instalado");
            } else {
                xcodeOk = true;
                System.setProperty("XCODE_OK",      "false");
                System.setProperty("XCODE_VERSION", "N/A");
            }

            // ── 6b. Barrido de procesos WDA huérfanos de una instancia anterior ──
            // Un reinicio del Runner (auto-update, crash, kill -9) pierde cualquier
            // referencia Java a un xcodebuild en curso, pero el proceso del sistema
            // operativo no depende de esa referencia para seguir vivo (confirmado
            // con ejecución real: PPID reparentado a launchd). Se barre una sola vez,
            // al arrancar, antes de aceptar cualquier job nuevo.
            if ("MACOS".equals(config.os)) {
                WdaLifecycleOwner.sweepStaleProcesses();
            }

            // ── 7. HostStatusManager ───────────────────────────────────────────
            HostStatusManager.HostReport hostReport = HostStatusManager.evaluate(
                    true, nodeOk, appiumOk, adbFunctional, xcodeOk, config.iosSupported);
            HostStatusManager.apply(hostReport);

            // ── 8. Runner config from backend ──────────────────────────────────
            System.out.println("[Runner] Obteniendo configuracion desde Backend...");
            BackendClient.RunnerConfigResponse runnerCfg = client.getRunnerConfig();
            if (runnerCfg != null && runnerCfg.isConfigured()) {
                config.repoUrl     = runnerCfg.repositoryUrl;
                config.repoBranch  = runnerCfg.branch;
                config.projectName = runnerCfg.projectName;
                System.out.println("[Runner] Configuracion recibida: " + config.repoUrl);
            }

            // ── 9. Device Stream Server ────────────────────────────────────────
            streamServer = new DeviceStreamServer(config.streamPort, adbPath, config.agentDataDir);
            try {
                streamServer.start();
                String streamUrl = "http://" + config.hostname + ":" + config.streamPort;
                System.setProperty("STREAM_URL", streamUrl);
                System.out.println("[Runner] Device Stream: " + streamUrl);
            } catch (Exception e) {
                System.err.println("[Runner] DeviceStreamServer error: " + e.getMessage());
            }

            // ── 10. JobExecutor ────────────────────────────────────────────────
            jobExecutor = new JobExecutor(config, client, appiumMgr);

            // ── 11. Self-healing managers ──────────────────────────────────────
            prevAdbOk.set(adbFunctional);
            DependencySelfHealingManager.HealthReport initialHealth =
                    new DependencySelfHealingManager.HealthReport(
                            true, nodeOk, appiumOk, adbFunctional, xcodeOk);

            deviceHealerRef.set(null);
            selfHealing = new DependencySelfHealingManager(
                    platformTools, appiumMgr, config.os, initialHealth,
                    report -> {
                        boolean adbJustHealed =
                                !prevAdbOk.getAndSet(report.adbOk) && report.adbOk;
                        config.androidSupported = report.adbOk;
                        if ("MACOS".equals(config.os)) config.iosSupported = report.xcodeOk;
                        System.setProperty("APPIUM_OK", String.valueOf(report.appiumOk));
                        System.setProperty("NODE_OK",   String.valueOf(report.nodeOk));

                        if (adbJustHealed) {
                            DeviceSelfHealingManager dh = deviceHealerRef.get();
                            if (dh != null) dh.onAdbHealed();
                        }

                        HostStatusManager.HostReport healedHost =
                                HostStatusManager.fromHealthReport(report, config.iosSupported);
                        HostStatusManager.apply(healedHost);

                        String newStatus = report.isFullyOperational() ? "ONLINE" : "DEGRADED";
                        try {
                            List<Map<String, String>> healed = discoverAllDevices(config);
                            client.syncDevices(config.runnerId, healed);
                            client.sendHeartbeat(
                                    config.runnerId, config.platform, config.version,
                                    newStatus, config.os, config.hostname,
                                    config.androidSupported, config.iosSupported, healed);
                            System.out.printf("[Runner] DependencyHealer: %s — %d dispositivo(s).%n",
                                    newStatus, healed.size());
                        } catch (Exception e) {
                            System.err.println("[Runner] heartbeat post-healing: " + e.getMessage());
                        }
                    });
            selfHealing.start();

            List<Map<String, String>> initDevices = discoverAllDevices(config);
            deviceHealer = new DeviceSelfHealingManager(
                    platformTools, appiumMgr, client, config);
            deviceHealer.init(adbFunctional, appiumOk, initDevices.size());
            deviceHealerRef.set(deviceHealer);

            // ── 12. Work scheduler ─────────────────────────────────────────────
            workScheduler = Executors.newScheduledThreadPool(3, r -> {
                Thread t = new Thread(r, "work-scheduler");
                t.setDaemon(true);
                return t;
            });

            workScheduler.scheduleAtFixedRate(client::ping, 0, 10, TimeUnit.SECONDS);
            appiumMgr.startWatchdog(workScheduler, () -> {
                DeviceSelfHealingManager dh = deviceHealerRef.get();
                if (dh != null) dh.onAppiumRestarted();
            });
            deviceHealer.startMonitor(workScheduler);
            workScheduler.scheduleAtFixedRate(updateMgr::checkAndApply, 60, 60, TimeUnit.MINUTES);

            // ── 13. Heartbeat task (every 30 s) ───────────────────────────────
            workScheduler.scheduleAtFixedRate(() -> {
                if (lifecycleState != LifecycleState.RUNNING) return;
                try {
                    List<Map<String, String>> devices = discoverAllDevices(config);
                    client.syncDevices(config.runnerId, devices);

                    DependencySelfHealingManager.HealthReport health =
                            selfHealing != null ? selfHealing.getLastReport() : null;
                    String runnerStatus =
                            (jobExecutor != null && jobExecutor.hasActiveProcess()) ? "BUSY"
                            : (health != null && !health.isFullyOperational())       ? "DEGRADED"
                            : "ONLINE";

                    String cmd = client.sendHeartbeat(
                            config.runnerId, config.platform, config.version,
                            runnerStatus, config.os, config.hostname,
                            config.androidSupported, config.iosSupported, devices);
                    if (cmd != null) pendingCommand.set(cmd);

                    System.out.printf("[Runner] Heartbeat: %d dispositivo(s) | %s | %s%n",
                            devices.size(), runnerStatus, config.capabilitySummary());
                } catch (Exception e) {
                    System.err.println("[Runner] Error en heartbeat: " + e.getMessage());
                }
            }, 30, 30, TimeUnit.SECONDS);

            // ── 14. Initial registration ───────────────────────────────────────
            boolean allOk        = adbFunctional && appiumOk && nodeOk;
            String  initialStatus = allOk ? "ONLINE" : "DEGRADED";
            try {
                client.syncDevices(config.runnerId, initDevices);
                client.sendHeartbeat(
                        config.runnerId, config.platform, config.version,
                        initialStatus, config.os, config.hostname,
                        config.androidSupported, config.iosSupported, initDevices);
                System.out.printf("[Runner] Registro completado: %d dispositivo(s) [%s]%n",
                        initDevices.size(), config.capabilitySummary());
            } catch (Exception e) {
                System.err.println("[Runner] Error en registro inicial: " + e.getMessage());
            }

            // ── 15. Job poll thread ────────────────────────────────────────────
            startJobPollThread();

            lifecycleState = LifecycleState.RUNNING;
            printBanner(config, adbPath);
            System.out.println("[Runner] ===== RUNNER ACTIVO =====");

        } catch (Exception e) {
            System.err.println("[Runner] Error critico al iniciar: " + e.getMessage());
            e.printStackTrace();

            // Cierra cualquier recurso que ESTE intento fallido ya haya creado o
            // arrancado (pasos anteriores al que lanzó la excepción — p.ej. Appium
            // ya iniciado en el paso 3, workScheduler ya con tareas programadas en
            // el paso 12). Sin esto, un START posterior exitoso sobreescribe estas
            // referencias estáticas con instancias nuevas, dejando las anteriores
            // huérfanas (proceso Appium, hilos del workScheduler) corriendo
            // indefinidamente sin que nada vuelva a referenciarlas. Mismo patrón
            // guard-null-y-detener que ya usa stopAllServices() — cada liberación
            // en su propio try/catch para garantizar que lifecycleState=ERROR y el
            // heartbeat DEGRADED de abajo siempre se ejecuten, incluso si una
            // liberación individual falla.
            try { if (workScheduler != null) workScheduler.shutdownNow(); } catch (Exception ignored) {}
            workScheduler = null;
            try { if (selfHealing != null) selfHealing.stop(); } catch (Exception ignored) {}
            selfHealing = null;
            try { if (appiumMgr != null) appiumMgr.stop(); } catch (Exception ignored) {}
            appiumMgr = null;
            try { if (streamServer != null) streamServer.stop(); } catch (Exception ignored) {}
            streamServer = null;
            jobExecutor = null;
            deviceHealer = null;
            deviceHealerRef.set(null);

            lifecycleState = LifecycleState.ERROR;
            silentHeartbeat("DEGRADED");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // stopAllServices  (synchronized — prevents concurrent stops)
    // ─────────────────────────────────────────────────────────────────────────

    private static synchronized void stopAllServices(boolean jvmExit) {
        if (lifecycleState == LifecycleState.STOPPED && !jvmExit) {
            System.out.println("[Runner] stopAllServices ignorado — ya detenido.");
            return;
        }
        System.out.println("[Runner] ===== DETENIENDO RUNNER =====");
        System.out.println("[PROCESS FLOW] RunnerAgent.stopAllServices(jvmExit=" + jvmExit + ") START — "
                + "hilo=" + Thread.currentThread().getName() + " | job activo=" + isJobActive());
        lifecycleState = LifecycleState.STOPPING;

        // Notify backend immediately
        silentHeartbeat("STOPPING");

        // ── Kill active job FIRST ─────────────────────────────────────────────────
        // MUST happen before interrupting the job-poll thread.  If we interrupt the
        // thread first, jobPollThread is blocked inside process.waitFor() and the
        // interrupt propagates as InterruptedException — which JobExecutor.execute()
        // would (wrongly) treat as a fatal error while Gradle is still running.
        // Killing the process first lets waitFor() return naturally before the
        // thread interrupt arrives, so execute() can finalize the execution cleanly.
        if (jobExecutor != null) {
            jobExecutor.killActiveProcess();
        }

        // ── Job poll thread ────────────────────────────────────────────────────
        if (jobPollThread != null) {
            System.out.println("[PROCESS FLOW] Thread interrumpido: jobPollThread.interrupt()");
            jobPollThread.interrupt();
            try { jobPollThread.join(5_000); } catch (InterruptedException ignored) {}
            jobPollThread = null;
        }

        // ── Work scheduler ─────────────────────────────────────────────────────
        if (workScheduler != null) {
            System.out.println("[PROCESS FLOW] ExecutorService shutdownNow(): workScheduler "
                    + "(incluye updateMgr.checkAndApply, heartbeats, self-healing, ping)");
            workScheduler.shutdownNow();
            try { workScheduler.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) {}
            workScheduler = null;
        }

        // ── Self-healing ───────────────────────────────────────────────────────
        if (selfHealing != null)  { selfHealing.stop(); selfHealing = null; }
        deviceHealerRef.set(null);
        deviceHealer = null;

        // ── Null out jobExecutor (process already killed above) ───────────────
        if (jobExecutor != null)  { jobExecutor = null; }

        // ── WDA (Mac-side) ──────────────────────────────────────────────────────
        // Apagado ordenado: mata cualquier xcodebuild de WDA que este Runner haya
        // lanzado, determinísticamente, sin depender de que un shutdown hook de la
        // JVM llegue a ejecutarse (esta llamada es explícita, parte del propio
        // flujo de stopAllServices). El barrido de arranque (ver runStartupSequence)
        // sigue siendo la red de seguridad para apagados NO ordenados (kill -9).
        if ("MACOS".equals(config.os)) {
            WdaLifecycleOwner.sweepStaleProcesses();
        }

        // ── Appium ─────────────────────────────────────────────────────────────
        if (appiumMgr != null)    { appiumMgr.stop(); appiumMgr = null; }

        // ── Device Stream Server ───────────────────────────────────────────────
        if (streamServer != null) { streamServer.stop(); streamServer = null; }

        if (!jvmExit) {
            lifecycleState = LifecycleState.STOPPED;
            // Flush empty device list to backend — device lists must go empty
            // when the runner is stopped so the dashboard shows no devices.
            silentHeartbeat("OFFLINE");
            System.out.println("[Runner] ===== RUNNER DETENIDO — esperando START =====");
        }
        System.out.println("[PROCESS FLOW] RunnerAgent.stopAllServices(jvmExit=" + jvmExit + ") END — "
                + "Runner considera la(s) ejecución(es) finalizada(s).");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job poll thread — sequential job polling (same semantics as the old
    //                   main while-loop, but runs on a dedicated thread)
    // ─────────────────────────────────────────────────────────────────────────

    private static void startJobPollThread() {
        jobPollThread = new Thread(() -> {
            System.out.println("[Runner] Job-poll thread iniciado.");
            while (lifecycleState == LifecycleState.RUNNING && !Thread.currentThread().isInterrupted()) {
                try {
                    // Reservar el gate ANTES de pedirle un job al backend. Si un
                    // auto-update ya lo tiene (GateOwner.UPDATE), ni siquiera se llama
                    // a getNextJob() — no hay job que "deshacer" después, y el ciclo
                    // se resuelve con el MISMO sleep que ya existía para "sin trabajo",
                    // sin polling ni espera adicional.
                    if (!tryBeginJob()) {
                        Thread.sleep(config.pollIntervalMs);
                        continue;
                    }
                    boolean claimed = false;
                    try {
                        Optional<JobDto> job = client.getNextJob();
                        if (job.isPresent() && jobExecutor != null) {
                            claimed = true;
                            System.out.println("[Runner] Job recibido: " + job.get().executionId);
                            jobExecutor.execute(job.get());
                        }
                    } finally {
                        // Libera el gate tanto si hubo job (ya terminó execute()) como
                        // si no había ninguno — siempre en el mismo ciclo del poll.
                        endJob();
                    }
                    if (!claimed) Thread.sleep(config.pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ThreadDeath td) {
                    // Contrato histórico de Thread.stop() (JDK): si se captura, DEBE
                    // relanzarse sin excepción — nunca absorberse. Prácticamente
                    // inalcanzable desde JDK 20 (Thread.stop() ya lanza
                    // UnsupportedOperationException), pero un catch(Throwable) sin
                    // esta guarda violaría el contrato si alguna vez ocurriera.
                    throw td;
                } catch (VirtualMachineError fatal) {
                    // OutOfMemoryError / InternalError / UnknownError / StackOverflowError.
                    // El propio Javadoc de VirtualMachineError: "la JVM se quedó sin
                    // recursos... o está de alguna manera corrupta". No es seguro asumir
                    // que el resto del proceso (heap, GC, estado nativo de Appium/WDA)
                    // sigue siendo confiable. Absorber esto y seguir el loop dejaría un
                    // Runner "vivo" (heartbeat ONLINE) pero ciego a jobs para siempre —
                    // el peor tipo de fallo silencioso. Se registra con el máximo detalle
                    // posible y se termina la JVM deliberadamente: System.exit (no
                    // Runtime.halt) para que el shutdown hook YA existente limpie WDA/
                    // Appium/streamServer antes de morir. El wrapper externo reinicia el
                    // proceso — mismo mecanismo que ya usa UpdateManager tras un update.
                    System.err.println("\n==========================");
                    System.err.println("[Runner] FATAL: VirtualMachineError en job-poll thread — "
                            + "terminando la JVM deliberadamente (no se puede confiar en que "
                            + "el resto del proceso siga íntegro).");
                    System.err.println("==========================");
                    fatal.printStackTrace();
                    // El detalle rico (qué subtipo exacto, dónde) ya quedó en el stack trace
                    // de arriba — el motivo tipado solo necesita identificar la CATEGORÍA.
                    requestShutdown(1, ShutdownReason.FATAL_VM_ERROR);
                } catch (Exception e) {
                    System.err.println("[Runner] Error en job poll: " + e.getMessage());
                    try { Thread.sleep(config.pollIntervalMs); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } catch (Error nonFatal) {
                    // AssertionError, LinkageError y cualquier otro Error que NO sea
                    // VirtualMachineError: indica un defecto de código/classpath/aserción
                    // aislado, no corrupción de la JVM. Matar el poller entero por un
                    // defecto de UNA clase/job sería peor que sobrevivir dejando
                    // constancia ruidosa — mismo criterio que ya se usa para Exception.
                    System.err.println("[Runner] ⚠ Error no fatal en job-poll thread "
                            + "(revisar causa — no se oculta): " + nonFatal);
                    nonFatal.printStackTrace();
                    try { Thread.sleep(config.pollIntervalMs); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            System.out.println("[Runner] Job-poll thread detenido.");
        }, "job-poll");
        jobPollThread.setDaemon(true);
        jobPollThread.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Best-effort heartbeat that swallows exceptions. */
    private static void silentHeartbeat(String status) {
        try {
            client.sendHeartbeat(
                    config.runnerId, config.platform, config.version,
                    status, config.os, config.hostname,
                    config.androidSupported, config.iosSupported,
                    Collections.emptyList());
        } catch (Exception ignored) {}
    }

    private static boolean detectNode() {
        String  nodeBin = System.getProperty("NODE_BIN", "");
        boolean nodeOk  = !nodeBin.isBlank() && Files.isExecutable(Path.of(nodeBin));
        if (!nodeOk && nodeBin.isBlank()) {
            try {
                Process pn = new ProcessBuilder("node", "--version")
                        .redirectErrorStream(true).start();
                nodeOk = pn.waitFor(3, TimeUnit.SECONDS) && pn.exitValue() == 0;
                pn.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            } catch (Exception ignored) {}
        }
        System.setProperty("NODE_OK", String.valueOf(nodeOk));
        if (nodeOk) {
            try {
                String nbCmd = nodeBin.isBlank() ? "node" : nodeBin;
                Process pn = new ProcessBuilder(nbCmd, "--version")
                        .redirectErrorStream(true).start();
                pn.waitFor(3, TimeUnit.SECONDS);
                String v = new String(pn.getInputStream().readAllBytes()).trim();
                System.setProperty("NODE_VERSION", v.isEmpty() ? "unavailable" : v);
            } catch (Exception ignored) {
                System.setProperty("NODE_VERSION", "unavailable");
            }
        }
        return nodeOk;
    }

    private static List<Map<String, String>> discoverAllDevices(RunnerConfig cfg) {
        List<Map<String, String>> devices = new ArrayList<>(BackendClient.discoverAndroidDevices());
        if (cfg.iosSupported) devices.addAll(IOSDeviceScanner.scan());

        long iosCount     = devices.stream().filter(d -> "IOS".equals(d.get("platform"))).count();
        long androidCount = devices.stream().filter(d -> "ANDROID".equals(d.get("platform"))).count();
        long available    = devices.stream().filter(d -> "AVAILABLE".equals(d.get("status"))).count();
        long ready        = devices.stream().filter(d -> "true".equals(d.get("readyForExecution"))).count();

        System.out.printf("[Runner] Dispositivos: total=%d  iOS=%d  Android=%d  disponibles=%d  listos=%d%n",
                devices.size(), iosCount, androidCount, available, ready);

        if (!devices.isEmpty()) {
            System.out.println("[Runner] Lista:");
            for (Map<String, String> d : devices) {
                String readyFlag = d.containsKey("readyForExecution")
                        ? ("true".equals(d.get("readyForExecution")) ? " ✓ready" : " ✗not-ready")
                        : "";
                System.out.printf("[Runner]   %-30s %-10s %-12s %s%s%n",
                        d.getOrDefault("deviceName", "?"),
                        d.getOrDefault("platform",   "?"),
                        d.getOrDefault("status",     "?"),
                        d.getOrDefault("udid",       "?"),
                        readyFlag);
            }
        }
        return devices;
    }

    private static void printBanner(RunnerConfig cfg, String adbPath) {
        System.out.println("=================================================");
        System.out.println("  Cinepolis QA Universal Runner  v" + cfg.version);
        System.out.println("=================================================");
        System.out.println("  Runner ID:  " + cfg.runnerId);
        System.out.println("  Hostname:   " + cfg.hostname);
        System.out.println("  OS:         " + cfg.os);
        System.out.println("  Backend:    " + cfg.backendUrl);
        System.out.println("  WorkDir:    " + cfg.workDir);
        System.out.println("  AppiumHub:  " + cfg.appiumHub);
        System.out.println("  Poll:       " + cfg.pollIntervalMs + " ms");
        System.out.println();
        System.out.println("  Componentes:");
        System.out.println("  + JRE:    " + System.getProperty("JRE_VERSION", "?"));
        System.out.println("  " + (Boolean.parseBoolean(System.getProperty("NODE_OK",   "false")) ? "+" : "-")
                + " Node:   " + System.getProperty("NODE_VERSION", "no disponible"));
        System.out.println("  " + (Boolean.parseBoolean(System.getProperty("APPIUM_OK", "false")) ? "+" : "-")
                + " Appium: " + System.getProperty("APPIUM_VERSION", "no disponible"));
        System.out.println("  " + (cfg.androidSupported ? "+" : "-") + " Android (ADB embebido)");
        System.out.println("  " + (cfg.iosSupported     ? "+" : "-") + " iOS     (Xcode/xcrun)");
        System.out.println("  ADB ver: " + System.getProperty("ADB_VERSION", "-"));
        if ("MACOS".equals(cfg.os))
            System.out.println("  Xcode:   " + System.getProperty("XCODE_VERSION", "-"));
        System.out.println();
        if (cfg.repoUrl != null && !cfg.repoUrl.isBlank()) {
            System.out.println("  Repositorio: " + cfg.repoUrl + "  [" + cfg.repoBranch + "]");
            System.out.println("    Workspace: " + cfg.workspaceDir + File.separator
                    + WorkspaceManager.repoNameFromUrl(cfg.repoUrl));
        } else {
            System.out.println("  Repositorio no configurado en el backend.");
        }
        System.out.println();
    }
}
