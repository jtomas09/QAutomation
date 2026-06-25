package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Universal Runner Agent v5.0
 *
 * Discovers Android and iOS devices automatically based on OS capabilities.
 * No manual RUNNER_PLATFORM configuration needed.
 *
 * Windows → Android (ADB)
 * macOS   → Android (ADB) + iOS (Xcode)  — requires Xcode CLT
 * Linux   → Android (ADB)
 *
 * V5 additions over V4:
 *  - DeviceSelfHealingManager: reactive device rescan after ADB/Appium recovery
 *  - AppiumManager.startWatchdog now fires onAppiumRestarted callback
 *  - DependencySelfHealingManager fires onAdbHealed via AtomicReference bridge
 *  - Driver presence check folded into appiumOk (DependencySelfHealingManager)
 */
public class RunnerAgent {

    private static volatile boolean stopping = false;

    public static void main(String[] args) throws Exception {
        RunnerConfig  config = RunnerConfig.fromEnv();
        BackendClient client = new BackendClient(config.backendUrl, config.runnerToken, config.runnerId);

        // ── Tool managers ──────────────────────────────────────────────────────
        Path agentDataDir = Path.of(config.agentDataDir);
        Path runnerDir    = "WINDOWS".equals(config.os) ? agentDataDir.resolve("runner") : agentDataDir;

        PlatformToolsManager platformTools = new PlatformToolsManager(runnerDir, config.os, config.backendUrl);
        AppiumManager        appiumMgr     = new AppiumManager(config.os);
        UpdateManager        updateMgr     = new UpdateManager(
                config.backendUrl, config.runnerToken, config.version, agentDataDir);

        // ── ADB ────────────────────────────────────────────────────────────────
        String  adbPath       = platformTools.resolveAdb();
        boolean adbFunctional = platformTools.isAdbFunctional();
        String  adbVersion    = platformTools.getAdbVersion();

        System.setProperty("ADB_PATH",    adbPath);
        System.setProperty("ADB_VERSION", adbVersion);
        System.setProperty("ADB_OK",      String.valueOf(adbFunctional));
        config.androidSupported = adbFunctional;

        System.out.println("[Runner] === Diagnostico ADB ===");
        System.out.println("[Runner] ADB Path:    " + adbPath);
        System.out.println("[Runner] ADB Exists:  " + Files.exists(Path.of(adbPath)));
        System.out.println("[Runner] ADB Version: " + adbVersion);
        System.out.println("[Runner] ADB OK:      " + adbFunctional);
        if (!adbFunctional) System.out.println("[Runner] ADB no disponible. Iniciando en DEGRADED.");

        // ── Android SDK (Plug & Play — sin ANDROID_HOME manual) ───────────────
        AndroidEnvironmentBootstrap androidBootstrap = AndroidEnvironmentBootstrap.get();
        System.out.println("[Runner] === Android SDK ===");
        if (androidBootstrap.isValid()) {
            System.setProperty("ANDROID_HOME",     androidBootstrap.getSdkPath());
            System.setProperty("ANDROID_SDK_ROOT", androidBootstrap.getSdkPath());
            System.out.println("[Runner] SDK Path:          " + androidBootstrap.getSdkPath());
            String sdkAdb = AndroidSdkLocator.locateAdb(androidBootstrap.getSdkPath());
            System.out.println("[Runner] SDK ADB:           " + (sdkAdb != null ? sdkAdb : "no encontrado"));
            System.out.println("[Runner] ANDROID_HOME:      " + androidBootstrap.getSdkPath());
            System.out.println("[Runner] ANDROID_SDK_ROOT:  " + androidBootstrap.getSdkPath());
        } else {
            System.out.println("[Runner] Android SDK no encontrado — Gradle usará local.properties si existe.");
        }

        // ── Appium ─────────────────────────────────────────────────────────────
        try {
            appiumMgr.ensureRunning();
        } catch (Exception e) {
            System.err.println("[Runner] Appium no pudo iniciarse: " + e.getMessage());
        }
        boolean appiumOk      = appiumMgr.isAlive();
        String  appiumVersion = appiumOk ? appiumMgr.getAppiumVersion() : "unavailable";
        System.setProperty("APPIUM_OK",      String.valueOf(appiumOk));
        System.setProperty("APPIUM_VERSION",  appiumVersion);

        // Full binary + version + /status diagnostic
        appiumMgr.logDiagnostic();

        // ── JRE telemetry ──────────────────────────────────────────────────────
        System.setProperty("JRE_VERSION", System.getProperty("java.version", "unavailable"));

        // ── Node telemetry ─────────────────────────────────────────────────────
        String  nodeBin = System.getProperty("NODE_BIN", "");
        boolean nodeOk  = !nodeBin.isBlank() && Files.isExecutable(Path.of(nodeBin));
        if (!nodeOk && nodeBin.isBlank()) {
            // Check system Node as fallback
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

        // ── Xcode (macOS only) ─────────────────────────────────────────────────
        boolean xcodeOk;
        if ("MACOS".equals(config.os)) {
            XcodeValidator.XcodeInfo xcodeInfo = XcodeValidator.validate();
            xcodeOk             = xcodeInfo.installed;
            config.iosSupported = xcodeInfo.installed;
            System.setProperty("XCODE_OK",      String.valueOf(xcodeOk));
            System.setProperty("XCODE_VERSION",
                    xcodeInfo.xcodeVersion != null ? xcodeInfo.xcodeVersion : "unavailable");
            System.out.printf("[Runner] Xcode: %s%n",
                    xcodeOk ? "disponible (" + xcodeInfo.xcodeVersion + ")" : "no instalado");
        } else {
            xcodeOk = true; // non-macOS: not applicable
            System.setProperty("XCODE_OK",      "false");
            System.setProperty("XCODE_VERSION", "N/A");
        }

        // ── HostStatusManager (v6) ────────────────────────────────────────────
        // ONLINE = JRE + Node + Appium + ADB. Xcode affects iosReady only.
        HostStatusManager.HostReport hostReport = HostStatusManager.evaluate(
                true, nodeOk, appiumOk, adbFunctional, xcodeOk, config.iosSupported);
        HostStatusManager.apply(hostReport);

        // ── Runner config (single source of truth — fetched from backend, never stored locally) ──
        System.out.println("[Runner] 📥 Obteniendo configuración desde Backend...");
        BackendClient.RunnerConfigResponse runnerCfg = client.getRunnerConfig();
        if (runnerCfg != null && runnerCfg.isConfigured()) {
            config.repoUrl     = runnerCfg.repositoryUrl;
            config.repoBranch  = runnerCfg.branch;
            config.projectName = runnerCfg.projectName;
            System.out.println("[Runner] ✅ Configuración recibida.");
            System.out.println("[Runner]    Repositorio: " + config.repoUrl + " [" + config.repoBranch + "]");
        } else {
            System.out.println("[Runner] ⚠ No fue posible obtener la configuración del proyecto.");
        }

        // ── Device Stream Service (Phase 10 — Live Preview) ───────────────────────
        DeviceStreamServer streamServer = new DeviceStreamServer(config.streamPort, adbPath);
        try {
            streamServer.start();
            String streamUrl = "http://" + config.hostname + ":" + config.streamPort;
            System.setProperty("STREAM_URL", streamUrl);
            System.out.println("[Runner] Device Stream Service: " + streamUrl);
        } catch (Exception e) {
            System.err.println("[Runner] DeviceStreamServer no pudo iniciar en puerto "
                    + config.streamPort + ": " + e.getMessage());
            System.err.println("[Runner] Live Preview no estara disponible.");
        }

        JobExecutor executor = new JobExecutor(config, client, appiumMgr);
        printBanner(config, adbPath);

        // Declared before shutdown hook so lambdas can capture the references
        AtomicReference<DependencySelfHealingManager> selfHealingRef  = new AtomicReference<>();
        AtomicReference<DeviceSelfHealingManager>     deviceHealerRef = new AtomicReference<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Runner] Cerrando — deteniendo ejecucion activa...");
            stopping = true;
            DependencySelfHealingManager sh = selfHealingRef.get();
            if (sh != null) sh.stop();
            executor.killActiveProcess();
            appiumMgr.stop();
            streamServer.stop();
        }, "shutdown-hook"));

        // ── Initial heartbeat ──────────────────────────────────────────────────
        System.out.println("[Runner] Registrando en Backend...");
        // ONLINE requires JRE + Node + Appium + ADB. Xcode affects iosReady only.
        boolean allOk        = adbFunctional && appiumOk && nodeOk;
        String  initialStatus = allOk ? "ONLINE" : "DEGRADED";
        try {
            List<Map<String, String>> initDevices = discoverAllDevices(config);
            System.out.println("[Runner] Devices Found: " + initDevices.size());
            client.syncDevices(config.runnerId, initDevices);
            client.sendHeartbeat(
                    config.runnerId, config.platform, config.version,
                    initialStatus, config.os, config.hostname,
                    config.androidSupported, config.iosSupported, initDevices);
            System.out.printf("[Runner] Registro completado: %d dispositivo(s) [%s]%n",
                    initDevices.size(), config.capabilitySummary());

            if (initDevices.isEmpty()) {
                System.out.println("[Runner] Sin dispositivos detectados.");
                System.out.println("[Runner]   -> Verifica que el dispositivo este conectado por USB");
                System.out.println("[Runner]   -> En Android: acepta 'Depuracion USB' en el dispositivo");
                if (config.iosSupported)
                    System.out.println("[Runner]   -> En iOS: desbloquea el dispositivo y acepta confiar en este Mac");
            }
        } catch (Exception e) {
            System.err.println("[Runner] Error en registro inicial: " + e.getMessage());
        }

        // ── DependencySelfHealingManager ───────────────────────────────────────
        DependencySelfHealingManager.HealthReport initialHealth =
                new DependencySelfHealingManager.HealthReport(
                        true,           // JRE: always present (we're running)
                        nodeOk,
                        appiumOk,
                        adbFunctional,
                        xcodeOk);

        // Track previous ADB state so the callback can detect a recovery transition
        java.util.concurrent.atomic.AtomicBoolean prevAdbOk = new java.util.concurrent.atomic.AtomicBoolean(adbFunctional);

        DependencySelfHealingManager selfHealing = new DependencySelfHealingManager(
                platformTools, appiumMgr, config.os, initialHealth,
                report -> {
                    boolean adbJustHealed = !prevAdbOk.getAndSet(report.adbOk) && report.adbOk;
                    config.androidSupported = report.adbOk;
                    if ("MACOS".equals(config.os)) config.iosSupported = report.xcodeOk;
                    System.setProperty("APPIUM_OK", String.valueOf(report.appiumOk));
                    System.setProperty("NODE_OK",   String.valueOf(report.nodeOk));

                    // Immediate rescan when ADB recovers (don't wait for monitor poll)
                    if (adbJustHealed) {
                        DeviceSelfHealingManager dh = deviceHealerRef.get();
                        if (dh != null) dh.onAdbHealed();
                    }

                    // Update HostStatusManager properties
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
                        System.out.printf("[Runner] DependencyHealer: %s — %d dispositivo(s) re-registrado(s).%n",
                                newStatus, healed.size());
                    } catch (Exception e) {
                        System.err.println("[Runner] Error en heartbeat post-healing: " + e.getMessage());
                    }
                });
        selfHealingRef.set(selfHealing);
        selfHealing.start();

        // ── DeviceSelfHealingManager ────────────────────────────────────────────
        List<Map<String, String>> initDevicesList = discoverAllDevices(config);
        DeviceSelfHealingManager deviceHealer = new DeviceSelfHealingManager(
                platformTools, appiumMgr, client, config);
        deviceHealer.init(adbFunctional, appiumOk, initDevicesList.size());
        deviceHealerRef.set(deviceHealer);

        // ── Scheduler ──────────────────────────────────────────────────────────
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "runner-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(client::ping, 0, 10, TimeUnit.SECONDS);
        appiumMgr.startWatchdog(scheduler, () -> {
            DeviceSelfHealingManager dh = deviceHealerRef.get();
            if (dh != null) dh.onAppiumRestarted();
        });
        deviceHealer.startMonitor(scheduler);
        scheduler.scheduleAtFixedRate(updateMgr::checkAndApply, 60, 60, TimeUnit.MINUTES);

        AtomicReference<String> pendingCommand = new AtomicReference<>(null);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Map<String, String>> devices = discoverAllDevices(config);
                client.syncDevices(config.runnerId, devices);

                DependencySelfHealingManager sh     = selfHealingRef.get();
                DependencySelfHealingManager.HealthReport health =
                        sh != null ? sh.getLastReport() : null;

                String runnerStatus = stopping                                          ? "STOPPING"
                        : executor.hasActiveProcess()                                   ? "BUSY"
                        : (health != null && !health.isFullyOperational())              ? "DEGRADED"
                        : "ONLINE";

                String cmd = client.sendHeartbeat(
                        config.runnerId, config.platform, config.version,
                        runnerStatus, config.os, config.hostname,
                        config.androidSupported, config.iosSupported, devices);

                if (cmd != null) {
                    pendingCommand.set(cmd);
                    System.out.println("[Runner] Comando recibido: " + cmd);
                }

                System.out.printf("[Runner] Heartbeat: %d dispositivo(s) | %s | %s%n",
                        devices.size(), runnerStatus, config.capabilitySummary());

            } catch (Exception e) {
                System.err.println("[Runner] Error en heartbeat: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);

        System.out.println("[Runner] Listo. Consultando trabajos cada " + config.pollIntervalMs + "ms...\n");

        int dots = 0;
        while (!stopping) {
            String cmd = pendingCommand.getAndSet(null);
            if (cmd != null) handleCommand(cmd, executor);

            try {
                Optional<JobDto> job = client.getNextJob();
                if (job.isPresent()) {
                    if (dots > 0) System.out.println();
                    dots = 0;
                    executor.execute(job.get());
                } else {
                    System.out.print(".");
                    System.out.flush();
                    if (++dots % 60 == 0) System.out.println();
                }
            } catch (Exception e) {
                System.err.println("\n[Runner] Error de conexion: " + e.getMessage());
            }
            Thread.sleep(config.pollIntervalMs);
        }
    }

    private static List<Map<String, String>> discoverAllDevices(RunnerConfig config) {
        List<Map<String, String>> devices = new ArrayList<>(BackendClient.discoverAndroidDevices());
        if (config.iosSupported) devices.addAll(IOSDeviceScanner.scan());

        long iosCount     = devices.stream().filter(d -> "IOS".equals(d.get("platform"))).count();
        long androidCount = devices.stream().filter(d -> "ANDROID".equals(d.get("platform"))).count();
        long available    = devices.stream().filter(d -> "AVAILABLE".equals(d.get("status"))).count();

        System.out.printf("[Runner] Dispositivos: total=%d  iOS=%d  Android=%d  disponibles=%d%n",
                devices.size(), iosCount, androidCount, available);

        if (!devices.isEmpty()) {
            System.out.println("[Runner] Lista de dispositivos:");
            for (Map<String, String> d : devices) {
                System.out.printf("[Runner]   %-30s %-8s %s%n",
                        d.getOrDefault("deviceName", "?"),
                        d.getOrDefault("platform", "?"),
                        d.getOrDefault("udid", "?"));
            }
        }
        return devices;
    }

    private static void printBanner(RunnerConfig config, String adbPath) {
        System.out.println("=================================================");
        System.out.println("  Cinepolis QA Universal Runner  v" + config.version);
        System.out.println("=================================================");
        System.out.println("  Runner ID:  " + config.runnerId);
        System.out.println("  Hostname:   " + config.hostname);
        System.out.println("  OS:         " + config.os);
        System.out.println("  Backend:    " + config.backendUrl);
        System.out.println("  WorkDir:    " + config.workDir);
        System.out.println("  AppiumHub:  " + config.appiumHub);
        System.out.println("  Poll:       " + config.pollIntervalMs + " ms");
        System.out.println();
        System.out.println("  Componentes:");
        System.out.println("  + JRE:      " + System.getProperty("JRE_VERSION", "?"));
        System.out.println("  " + (Boolean.parseBoolean(System.getProperty("NODE_OK", "false")) ? "+" : "-")
                + " Node:     " + System.getProperty("NODE_VERSION", "no disponible"));
        System.out.println("  " + (Boolean.parseBoolean(System.getProperty("APPIUM_OK", "false")) ? "+" : "-")
                + " Appium:   " + System.getProperty("APPIUM_VERSION", "no disponible"));
        System.out.println("  " + (config.androidSupported ? "+" : "-") + " Android  (ADB embebido)");
        System.out.println("  " + (config.iosSupported     ? "+" : "-") + " iOS      (Xcode/xcrun)");
        System.out.println("  ADB ver:    " + System.getProperty("ADB_VERSION", "-"));
        if ("MACOS".equals(config.os))
            System.out.println("  Xcode:      " + System.getProperty("XCODE_VERSION", "-"));
        System.out.println();
        // Workspace / repo status
        if (config.repoUrl != null && !config.repoUrl.isBlank()) {
            System.out.println("  ✓ Repositorio: " + config.repoUrl + "  [" + config.repoBranch + "]");
            System.out.println("    Workspace:   " + config.workspaceDir + File.separator
                    + WorkspaceManager.repoNameFromUrl(config.repoUrl));
        } else {
            System.out.println("  ⚠ Repositorio no configurado en el backend.");
            System.out.println("    Configure la variable REPO_URL en el backend (Railway → Variables).");
        }
        System.out.println();
    }

    private static void handleCommand(String cmd, JobExecutor executor) {
        switch (cmd.toUpperCase()) {
            case "STOP" -> {
                System.out.println("[Runner] Ejecutando STOP...");
                stopping = true;
                executor.killActiveProcess();
                System.exit(0);
            }
            case "RESTART" -> {
                System.out.println("[Runner] Ejecutando RESTART...");
                executor.killActiveProcess();
                System.exit(0);
            }
            case "START"  -> System.out.println("[Runner] Comando START recibido — ya en ejecucion.");
            default       -> System.out.println("[Runner] Comando desconocido: " + cmd);
        }
    }
}
