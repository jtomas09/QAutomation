package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class JobExecutor {

    private final RunnerConfig  config;
    private final BackendClient client;

    public JobExecutor(RunnerConfig config, BackendClient client) {
        this.config = config;
        this.client = client;
    }

    public void execute(JobDto job) {
        System.out.println("\n[Executor] ▶ Job: " + job.executionId + " | Suite: " + job.suite);
        AtomicInteger passed  = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        try {
            client.sendLog(job.executionId, "INFO",
                    "▶ Iniciando suite: " + job.suite + " | Env: " + job.env + " | Device: " + job.device);

            List<String> cmd = buildCommand(job);
            client.sendLog(job.executionId, "INFO", "Comando: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(config.workDir));
            pb.redirectErrorStream(true);
            pb.environment().put("SUITE_ID",    job.suite);
            pb.environment().put("ENV",          job.env);
            pb.environment().put("DEVICE_NAME",  job.device);
            pb.environment().put("COUNTRY",      job.country);

            Process process = pb.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String level = detectLevel(line);
                    client.sendLog(job.executionId, level, line);
                    if ("PASS".equals(level)) passed.incrementAndGet();
                    else if ("FAIL".equals(level)) failed.incrementAndGet();
                    else if ("SKIP".equals(level)) skipped.incrementAndGet();
                }
            }

            int exitCode = process.waitFor();
            String summary = passed.get() + " PASSED · " + failed.get() + " FAILED · " + skipped.get() + " SKIPPED";
            client.sendLog(job.executionId, exitCode == 0 ? "PASS" : "FAIL",
                    exitCode == 0 ? "✅ Suite completada — " + summary
                                  : "❌ Suite terminó con errores (exit " + exitCode + ") — " + summary);

            String allureUrl = generateAllureReport(job.executionId);
            client.sendResult(job.executionId, passed.get(), failed.get(), skipped.get(), allureUrl);
            System.out.println("[Executor] ✓ Job finalizado: " + job.executionId);

        } catch (Exception e) {
            System.err.println("[Executor] Error: " + e.getMessage());
            client.sendLog(job.executionId, "ERROR", "❌ Error interno del runner: " + e.getMessage());
            try {
                client.sendResult(job.executionId, passed.get(), failed.get() + 1, skipped.get(), null);
            } catch (Exception ignored) {}
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private List<String> buildCommand(JobDto job) {
        List<String> cmd = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (config.testCommand.contains("gradlew")) {
            if (isWindows) {
                cmd.add("cmd"); cmd.add("/c");
                cmd.add(config.testCommand.replace("./gradlew", "gradlew.bat"));
            } else {
                cmd.add("/bin/sh"); cmd.add("-c"); cmd.add(config.testCommand);
            }
        } else {
            cmd.addAll(List.of(config.testCommand.split("\\s+")));
        }

        // Pass suite parameters as system properties
        cmd.add("-DsuiteId="    + job.suite);
        cmd.add("-Denv="        + job.env);
        cmd.add("-DdeviceName=" + job.device);
        cmd.add("-Dcountry="    + job.country);
        return cmd;
    }

    private String generateAllureReport(String executionId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "allure", "generate", "allure-results",
                    "-o", "allure-report/" + executionId, "--clean");
            pb.directory(new File(config.workDir));
            if (pb.start().waitFor() == 0) {
                String base = System.getenv("ALLURE_BASE_URL");
                if (base != null && !base.isBlank()) return base + "/" + executionId;
            }
        } catch (Exception ignored) {
            // Allure CLI not installed — skip
        }
        return null;
    }

    private String detectLevel(String line) {
        String u = line.toUpperCase();
        if (u.contains("BUILD FAILED") || u.contains("TESTS FAILED") || u.contains("FAILURE")) return "FAIL";
        if (u.contains("ERROR"))                                                                  return "FAIL";
        if (u.contains("BUILD SUCCESSFUL") || u.contains("TESTS PASSED") || u.contains("PASSED")) return "PASS";
        if (u.contains("SKIPPED"))                                                                return "SKIP";
        if (u.contains("WARN"))                                                                   return "WARN";
        return "INFO";
    }
}
