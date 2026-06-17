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
        System.out.println();

        // Print ADB path for diagnostics
        String adbPath = BackendClient.findAdb();
        System.out.println("  ADB:       " + adbPath);
        System.out.println();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Runner] Cerrando — deteniendo ejecucion activa...");
            stopping = true;
            executor.killActiveProcess();
        }, "shutdown-hook"));

        // ── Heartbeat inmediato al arrancar ──────────────────────────────
        // Descubre dispositivos y registra ANTES de iniciar el scheduler
        System.out.println("[Runner] Iniciando registro en Device Farm...");
        try {
            List<Map<String, String>> initDevices = new ArrayList<>(BackendClient.discoverAndroidDevices());
            if ("ios".equalsIgnoreCase(config.platform)) {
                initDevices.addAll(BackendClient.discoverIosDevices());
            }
            client.registerDevices(config.runnerId, initDevices);
            client.sendHeartbeat(config.runnerId, config.platform, config.version, "ONLINE", initDevices);
            System.out.printf("[Runner] Registro inicial completado: %d dispositivo(s)%n", initDevices.size());
            if (initDevices.isEmpty()) {
                System.out.println("[Runner] ADVERTENCIA: No se detectaron dispositivos.");
                System.out.println("         - Verifica que el dispositivo este conectado");
                System.out.println("         - Verifica que ADB este en PATH: " + adbPath);
                System.out.println("         - Ejecuta manualmente: adb devices -l");
                System.out.println("         - Acepta el permiso de depuracion USB en el dispositivo");
            }
        } catch (Exception e) {
            System.err.println("[Runner] Error en registro inicial: " + e.getMessage());
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "runner-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Legacy job ping every 10s
        scheduler.scheduleAtFixedRate(client::ping, 0, 10, TimeUnit.SECONDS);

        // Enterprise heartbeat every 30s: discover devices → register → runner heartbeat
        AtomicReference<String> pendingCommand = new AtomicReference<>(null);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Map<String, String>> devices = new ArrayList<>(BackendClient.discoverAndroidDevices());
                if ("ios".equalsIgnoreCase(config.platform)) {
                    devices.addAll(BackendClient.discoverIosDevices());
                }

                client.registerDevices(config.runnerId, devices);

                String runnerStatus = stopping ? "STOPPING"
                        : executor.hasActiveProcess() ? "BUSY" : "ONLINE";
                String cmd = client.sendHeartbeat(
                        config.runnerId, config.platform, config.version, runnerStatus, devices);
                if (cmd != null) {
                    pendingCommand.set(cmd);
                    System.out.println("[Runner] Comando recibido: " + cmd);
                }

                System.out.printf("[Runner] Heartbeat: %d dispositivo(s) | estado: %s%n",
                        devices.size(), runnerStatus);
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
