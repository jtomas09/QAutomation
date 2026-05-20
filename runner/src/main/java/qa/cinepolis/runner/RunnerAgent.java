package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.util.Optional;

public class RunnerAgent {

    public static void main(String[] args) throws Exception {
        RunnerConfig  config   = RunnerConfig.fromEnv();
        BackendClient client   = new BackendClient(config.backendUrl, config.runnerToken);
        JobExecutor   executor = new JobExecutor(config, client);

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Cinepolis QA Runner Agent  v2.0.0              ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("  Backend:    " + config.backendUrl);
        System.out.println("  WorkDir:    " + config.workDir);
        System.out.println("  AppiumHub:  " + config.appiumHub);
        System.out.println("  Poll:       " + config.pollIntervalMs + " ms");
        System.out.println("\n  Suites disponibles:");
        System.out.println("    Smoke Tests  → smoke.xml");
        System.out.println("    Asientos     → asientos.xml");
        System.out.println("    Checkout     → checkout.xml");
        System.out.println("    Alimentos    → alimentos.xml");
        System.out.println("\n[Runner] Iniciando polling...\n");

        int dots = 0;
        while (true) {
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
                System.err.println("\n[Runner] Error de conexión: " + e.getMessage());
            }
            Thread.sleep(config.pollIntervalMs);
        }
    }
}
