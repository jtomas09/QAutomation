package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RunnerAgent {

    public static void main(String[] args) throws Exception {
        RunnerConfig  config   = RunnerConfig.fromEnv();
        BackendClient client   = new BackendClient(config.backendUrl, config.runnerToken);
        JobExecutor   executor = new JobExecutor(config, client);

        System.out.println("=================================================");
        System.out.println("  Cinepolis QA Runner Agent  v2.1.0");
        System.out.println("=================================================");
        System.out.println("  Backend:   " + config.backendUrl);
        System.out.println("  WorkDir:   " + config.workDir);
        System.out.println("  AppiumHub: " + config.appiumHub);
        System.out.println("  Poll:      " + config.pollIntervalMs + " ms");
        System.out.println("\n[Runner] Iniciando...\n");

        // Background heartbeat — pings every 10 s independent of job execution
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(client::ping, 0, 10, TimeUnit.SECONDS);

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
                System.err.println("\n[Runner] Error de conexion: " + e.getMessage());
            }
            Thread.sleep(config.pollIntervalMs);
        }
    }
}
