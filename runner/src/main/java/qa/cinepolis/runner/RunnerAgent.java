package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class RunnerAgent {

    private static volatile boolean stopping = false;

    public static void main(String[] args) throws Exception {
        RunnerConfig  config   = RunnerConfig.fromEnv();
        BackendClient client   = new BackendClient(config.backendUrl, config.runnerToken, config.runnerId);
        JobExecutor   executor = new JobExecutor(config, client);

        System.out.println("=================================================");
        System.out.println("  Cinepolis QA Runner Agent  v" + config.version);
        System.out.println("=================================================");
        System.out.println("  Runner ID: " + config.runnerId);
        System.out.println("  Platform:  " + config.platform);
        System.out.println("  Backend:   " + config.backendUrl);
        System.out.println("  WorkDir:   " + config.workDir);
        System.out.println("  AppiumHub: " + config.appiumHub);
        System.out.println("  Poll:      " + config.pollIntervalMs + " ms");
        System.out.println("\n[Runner] Iniciando...\n");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Runner] Cerrando runner — deteniendo ejecución activa...");
            stopping = true;
            executor.killActiveProcess();
        }, "shutdown-hook"));

        // Scheduler for enterprise heartbeat (30s) and job ping (10s)
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "runner-heartbeat");
            t.setDaemon(true);
            return t;
        });

        // Legacy job ping every 10s (keeps existing /api/jobs/next aliveness)
        scheduler.scheduleAtFixedRate(client::ping, 0, 10, TimeUnit.SECONDS);

        // Enterprise heartbeat every 30s: registers runner + devices on /api/runners AND /api/devices/register
        AtomicReference<String> pendingCommand = new AtomicReference<>(null);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Discover physical devices
                List<Map<String, String>> devices = new ArrayList<>(BackendClient.discoverAndroidDevices());
                if ("ios".equalsIgnoreCase(config.platform)) {
                    devices.addAll(BackendClient.discoverIosDevices());
                }

                // 1) Register devices in Device Farm (udid, deviceName, platformVersion, etc.)
                client.registerDevices(config.runnerId, devices);

                // 2) Runner heartbeat with summary (runner status + count)
                String runnerStatus = stopping ? "STOPPING"
                        : executor.hasActiveProcess() ? "BUSY" : "ONLINE";
                String cmd = client.sendHeartbeat(
                        config.runnerId, config.platform, config.version,
                        runnerStatus, devices);
                if (cmd != null) {
                    pendingCommand.set(cmd);
                    System.out.println("[Runner] Comando recibido del dashboard: " + cmd);
                }

                if (!devices.isEmpty()) {
                    System.out.printf("[Runner] %d dispositivos registrados (%s)%n",
                            devices.size(), runnerStatus);
                }
            } catch (Exception e) {
                System.err.println("[Runner] heartbeat error: " + e.getMessage());
            }
        }, 2, 30, TimeUnit.SECONDS);

        int dots = 0;
        while (!stopping) {
            // Process any pending dashboard command
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

    private static void handleCommand(String cmd, JobExecutor executor) {
        switch (cmd.toUpperCase()) {
            case "STOP" -> {
                System.out.println("[Runner] Ejecutando comando STOP del dashboard...");
                stopping = true;
                executor.killActiveProcess();
                System.exit(0);
            }
            case "RESTART" -> {
                System.out.println("[Runner] Ejecutando comando RESTART del dashboard...");
                executor.killActiveProcess();
                // The process manager (launchd/NSSM/systemd) will restart the JVM
                System.exit(0);
            }
            case "START" -> System.out.println("[Runner] Comando START recibido — ya en ejecución.");
            default      -> System.out.println("[Runner] Comando desconocido: " + cmd);
        }
    }
}
