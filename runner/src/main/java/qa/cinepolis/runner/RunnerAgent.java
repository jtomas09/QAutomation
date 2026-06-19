package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Universal Runner Agent — discovers Android and iOS devices automatically
 * based on OS capabilities. No manual RUNNER_PLATFORM configuration needed.
 *
 * Windows → Android only (ADB)
 * macOS   → Android + iOS (ADB + Xcode)
 * Linux   → Android only (ADB)
 */
public class RunnerAgent {

    private static volatile boolean stopping = false;

    public static void main(String[] args) throws Exception {
        RunnerConfig  config   = RunnerConfig.fromEnv();
        BackendClient client   = new BackendClient(config.backendUrl, config.runnerToken, config.runnerId);

        // ── Tool managers ────────────────────────────────────────────────────
        Path agentDataDir = Path.of(config.agentDataDir);
        PlatformToolsManager platformTools = new PlatformToolsManager(agentDataDir, config.os);
        AppiumManager        appiumMgr     = new AppiumManager(config.os);
        UpdateManager        updateMgr     = new UpdateManager(
                config.backendUrl, config.runnerToken, config.version, agentDataDir);

        // Resolve ADB (download if needed) before first device discovery
        String adbPath = platformTools.resolveAdb();
        if (adbPath != null) {
            System.setProperty("ADB_PATH", adbPath);
            config.androidSupported = true;
        }

        // Ensure Appium is running before accepting jobs
        try {
            appiumMgr.ensureRunning();
        } catch (Exception e) {
            System.err.println("[Runner] Appium no pudo iniciarse: " + e.getMessage());
            System.err.println("[Runner] Los tests continuaran pero fallaran si requieren Appium.");
        }

        JobExecutor executor = new JobExecutor(config, client, appiumMgr);

        printBanner(config);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Runner] Cerrando — deteniendo ejecucion activa...");
            stopping = true;
            executor.killActiveProcess();
            appiumMgr.stop();
        }, "shutdown-hook"));

        // ── Heartbeat inmediato al arrancar ──────────────────────────────────
        System.out.println("[Runner] Registrando en Backend...");
        try {
            List<Map<String, String>> initDevices = discoverAllDevices(config);
            client.registerDevices(config.runnerId, initDevices);
            client.sendHeartbeat(
                    config.runnerId, config.platform, config.version,
                    "ONLINE", config.os, config.hostname,
                    config.androidSupported, config.iosSupported,
                    initDevices);
            System.out.printf("[Runner] Registro completado: %d dispositivo(s) [%s]%n",
                    initDevices.size(), config.capabilitySummary());

            if (initDevices.isEmpty()) {
                System.out.println("[Runner]");
                System.out.println("[Runner] ⚠ Sin dispositivos detectados.");
                System.out.println("[Runner]   → Verifica que el dispositivo este conectado por USB");
                System.out.println("[Runner]   → En Android: acepta 'Depuracion USB' en el dispositivo");
                System.out.println("[Runner]   → Ejecuta manualmente: adb devices -l");
                if (config.iosSupported) {
                    System.out.println("[Runner]   → En iOS: desbloquea el dispositivo y acepta 'Confiar en esta PC'");
                }
            }
        } catch (Exception e) {
            System.err.println("[Runner] Error en registro inicial: " + e.getMessage());
        }

        // ── Scheduler: heartbeat cada 30s + job ping cada 10s ───────────────
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "runner-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(client::ping, 0, 10, TimeUnit.SECONDS);

        // Appium watchdog (restart if crashed)
        appiumMgr.startWatchdog(scheduler);

        // Auto-update check every 60 minutes
        scheduler.scheduleAtFixedRate(updateMgr::checkAndApply, 60, 60, TimeUnit.MINUTES);

        AtomicReference<String> pendingCommand = new AtomicReference<>(null);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Map<String, String>> devices = discoverAllDevices(config);
                client.registerDevices(config.runnerId, devices);

                String runnerStatus = stopping              ? "STOPPING"
                        : executor.hasActiveProcess()       ? "BUSY"
                        : "ONLINE";

                String cmd = client.sendHeartbeat(
                        config.runnerId, config.platform, config.version,
                        runnerStatus, config.os, config.hostname,
                        config.androidSupported, config.iosSupported,
                        devices);

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

    /**
     * Discovers all physical devices based on platform capabilities.
     * Android discovery is always attempted.
     * iOS discovery runs automatically when iosSupported=true (macOS + Xcode).
     */
    private static List<Map<String, String>> discoverAllDevices(RunnerConfig config) {
        List<Map<String, String>> devices = new ArrayList<>(BackendClient.discoverAndroidDevices());
        if (config.iosSupported) {
            devices.addAll(BackendClient.discoverIosDevices());
        }
        return devices;
    }

    private static void printBanner(RunnerConfig config) {
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
        System.out.println("  Capacidades detectadas:");
        System.out.println("  " + (config.androidSupported ? "✓" : "✗") + " Android  (ADB)");
        System.out.println("  " + (config.iosSupported     ? "✓" : "✗") + " iOS      (Xcode/xcrun)");
        System.out.println("  ADB:        " + BackendClient.findAdb());
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
            case "START" -> System.out.println("[Runner] Comando START recibido — ya en ejecucion.");
            default      -> System.out.println("[Runner] Comando desconocido: " + cmd);
        }
    }
}
