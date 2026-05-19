package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.util.Optional;

/**
 * QA Runner Agent — polls the cloud backend for pending jobs and executes
 * them locally where Appium and the Android device are available.
 *
 * Usage:
 *   java -jar cinepolis-runner.jar
 *
 * Environment variables (all optional, have defaults):
 *   BACKEND_URL        — https://qautomation-production.up.railway.app
 *   RUNNER_TOKEN       — shared secret for Authorization header
 *   POLL_INTERVAL_MS   — milliseconds between polls (default: 5000)
 *   TEST_COMMAND       — gradle/mvn command to run tests (default: ./gradlew test)
 *   WORK_DIR           — working directory for test command (default: .)
 *   ALLURE_BASE_URL    — base URL for published Allure reports (optional)
 */
public class RunnerAgent {

    public static void main(String[] args) throws Exception {
        RunnerConfig  config   = RunnerConfig.fromEnv();
        BackendClient client   = new BackendClient(config.backendUrl, config.runnerToken);
        JobExecutor   executor = new JobExecutor(config, client);

        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   Cinepolis QA Runner Agent  v1.0.0            ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.println("  Backend:  " + config.backendUrl);
        System.out.println("  WorkDir:  " + config.workDir);
        System.out.println("  Command:  " + config.testCommand);
        System.out.println("  Poll:     " + config.pollIntervalMs + " ms");
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
