package qa.cinepolis.runner;

import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.RunnerConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JobExecutor {

    private static final Map<String, String> SUITE_MAP = Map.of(
            "smoke tests",   "smoke.xml",
            "asientos",      "asientos.xml",
            "checkout",      "checkout.xml",
            "alimentos",     "alimentos.xml",
            "full suite",    "smoke.xml",
            "regresión",     "regresion.xml",
            "sanity",        "sanity.xml"
    );

    private final RunnerConfig  config;
    private final BackendClient client;

    public JobExecutor(RunnerConfig config, BackendClient client) {
        this.config = config;
        this.client = client;
    }

    public void execute(JobDto job) {
        System.out.println("\n[Executor] ▶ Job: " + job.executionId
                + " | Suite: " + job.suite + " | Env: " + job.env);

        AtomicInteger passed  = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        try {
            client.sendLog(job.executionId, "INFO",
                    "▶ Iniciando suite: " + job.suite
                    + "  |  Env: " + job.env
                    + "  |  Device: " + job.device);

            // ── Pre-flight checks ──────────────────────────────────────────────
            checkAdbDevices(job.executionId);
            checkAppiumServer(job.executionId);

            // ── Build & run Maven command ──────────────────────────────────────
            List<String> cmd = buildCommand(job);
            client.sendLog(job.executionId, "INFO", "Ejecutando: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(config.workDir));
            pb.redirectErrorStream(true);
            // also pass as env vars so test code can read them directly
            pb.environment().put("SUITE_ID",    nvl(job.suite));
            pb.environment().put("ENV",          nvl(job.env));
            pb.environment().put("DEVICE_NAME",  nvl(job.device));
            pb.environment().put("COUNTRY",      nvl(job.country));
            pb.environment().put("APPIUM_HUB",   config.appiumHub);

            Process process = pb.start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String level = detectLevel(line);
                    client.sendLog(job.executionId, level, line);
                    if ("PASS".equals(level))      passed.incrementAndGet();
                    else if ("FAIL".equals(level)) failed.incrementAndGet();
                    else if ("SKIP".equals(level)) skipped.incrementAndGet();
                }
            }

            int exitCode = process.waitFor();
            String summary = passed.get() + " PASSED · "
                           + failed.get() + " FAILED · "
                           + skipped.get() + " SKIPPED";

            client.sendLog(job.executionId, exitCode == 0 ? "PASS" : "FAIL",
                    exitCode == 0
                        ? "✅ Suite completada — " + summary
                        : "❌ Suite terminó con errores (exit " + exitCode + ") — " + summary);

            String allureUrl = generateAllureReport(job.executionId);
            client.sendResult(job.executionId, passed.get(), failed.get(), skipped.get(), allureUrl);
            System.out.println("[Executor] ✓ Finalizado: " + job.executionId);

        } catch (Exception e) {
            System.err.println("[Executor] Error: " + e.getMessage());
            client.sendLog(job.executionId, "ERROR", "❌ Error interno: " + e.getMessage());
            try {
                client.sendResult(job.executionId, passed.get(), failed.get() + 1, skipped.get(), null);
            } catch (Exception ignored) {}
        }
    }

    // ── Command builder ────────────────────────────────────────────────────────

    private List<String> buildCommand(JobDto job) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String suiteXml   = resolveSuiteXml(job.suite);

        List<String> cmd = new ArrayList<>();
        if (isWindows) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.add("mvn");
        cmd.add("test");
        cmd.add("-B");                                              // batch / non-interactive
        cmd.add("-DsuiteXmlFile=" + suiteXml);
        cmd.add("-Denv="          + nvl(job.env,    "QA"));
        cmd.add("-DdeviceName="   + nvl(job.device, "Galaxy A56 5G"));
        cmd.add("-Dcountry="      + nvl(job.country,"mexico"));
        cmd.add("-Dappium.hub="   + config.appiumHub + "/wd/hub");
        return cmd;
    }

    private static String resolveSuiteXml(String suiteName) {
        if (suiteName == null || suiteName.isBlank()) return "smoke.xml";
        return SUITE_MAP.getOrDefault(suiteName.toLowerCase().trim(), "smoke.xml");
    }

    // ── Pre-flight: ADB ────────────────────────────────────────────────────────

    private void checkAdbDevices(String executionId) {
        try {
            Process p = new ProcessBuilder("adb", "devices")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();

            long count = Arrays.stream(output.split("\n"))
                    .filter(l -> l.contains("\tdevice"))
                    .count();

            if (count > 0) {
                client.sendLog(executionId, "INFO",
                        "📱 " + count + " dispositivo(s) Android detectado(s) via ADB");
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️ No se detectaron dispositivos ADB — asegúrate de que el dispositivo esté conectado");
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️ ADB no disponible: " + e.getMessage());
        }
    }

    // ── Pre-flight: Appium ─────────────────────────────────────────────────────

    private void checkAppiumServer(String executionId) {
        String statusUrl = config.appiumHub.replaceAll("/wd/hub$", "") + "/status";
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code == 200) {
                client.sendLog(executionId, "INFO",
                        "✅ Appium server online: " + config.appiumHub);
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️ Appium respondió HTTP " + code + " en " + statusUrl);
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️ Appium no disponible en " + statusUrl
                    + " — inícialo con: appium --port 4723");
        }
    }

    // ── Allure ─────────────────────────────────────────────────────────────────

    private String generateAllureReport(String executionId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "allure", "generate", "allure-results",
                    "-o", "allure-report/" + executionId, "--clean");
            pb.directory(new File(config.workDir));
            if (pb.start().waitFor() == 0 && !config.allureBaseUrl.isBlank()) {
                return config.allureBaseUrl + "/" + executionId;
            }
        } catch (Exception ignored) { /* Allure CLI no instalado */ }
        return null;
    }

    // ── Log level detection (Maven/TestNG output) ──────────────────────────────

    private String detectLevel(String line) {
        String u = line.toUpperCase();
        if (u.contains("BUILD FAILURE") || u.contains("TESTS RUN:") && u.contains("FAILURES:") && !u.contains("FAILURES: 0")
                || u.contains("FAILED") || u.contains("[ERROR]"))         return "FAIL";
        if (u.contains("BUILD SUCCESS") || u.contains("TESTS RUN:") && u.contains("FAILURES: 0") && u.contains("ERRORS: 0")
                || u.contains("PASSED") || u.contains("TEST PASSED"))     return "PASS";
        if (u.contains("SKIPPED") || u.contains("SKIPS:") && !u.contains("SKIPS: 0")) return "SKIP";
        if (u.contains("[WARNING]") || u.contains("WARN"))               return "WARN";
        return "INFO";
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }
}
