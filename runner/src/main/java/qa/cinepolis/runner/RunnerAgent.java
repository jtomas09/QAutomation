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

    // ── Lifecycle scheduler (always alive, drives heartbeats + commands) ──────

    private static ScheduledExecutorService lifecycleScheduler;

    // ─────────────────────────────────────────────────────────────────────────
    // main
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
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
            System.out.println("\n[Runner] JVM cerrando — limpiando recursos...");
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
            streamServer = new DeviceStreamServer(config.streamPort, adbPath);
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
        lifecycleState = LifecycleState.STOPPING;

        // Notify backend immediately
        silentHeartbeat("STOPPING");

        // ── Job poll thread ────────────────────────────────────────────────────
        if (jobPollThread != null) {
            jobPollThread.interrupt();
            try { jobPollThread.join(3_000); } catch (InterruptedException ignored) {}
            jobPollThread = null;
        }

        // ── Work scheduler ─────────────────────────────────────────────────────
        if (workScheduler != null) {
            workScheduler.shutdownNow();
            try { workScheduler.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) {}
            workScheduler = null;
        }

        // ── Self-healing ───────────────────────────────────────────────────────
        if (selfHealing != null)  { selfHealing.stop(); selfHealing = null; }
        deviceHealerRef.set(null);
        deviceHealer = null;

        // ── Kill active job ────────────────────────────────────────────────────
        if (jobExecutor != null)  { jobExecutor.killActiveProcess(); jobExecutor = null; }

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
                    Optional<JobDto> job = client.getNextJob();
                    if (job.isPresent() && jobExecutor != null) {
                        System.out.println("[Runner] Job recibido: " + job.get().executionId());
                        jobExecutor.execute(job.get());
                    } else {
                        Thread.sleep(config.pollIntervalMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("[Runner] Error en job poll: " + e.getMessage());
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
