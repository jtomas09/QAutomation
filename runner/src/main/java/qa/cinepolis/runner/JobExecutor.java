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

    /**
     * Maps UI suite name (lowercase) to Gradle --tests filter.
     * The root project uses JUnit 5 + Gradle; tests live in packages like:
     *   tests.México.asientos.SeleccionAsientos
     *   tests.México.alimentos.*
     *   tests.México.E2E.FlujosCompraNoLogin
     *   tests.RunAllTests  (JUnit Platform Suite that selects @SelectPackages("tests"))
     */
    private static final Map<String, String> SUITE_MAP = Map.of(
            "smoke tests",  "tests.RunAllTests",
            "full suite",   "tests.RunAllTests",
            "regresión",    "tests.RunAllTests",
            "regresion",    "tests.RunAllTests",
            "sanity",       "tests.RunAllTests",
            "asientos",     "tests.México.asientos.SeleccionAsientos",
            "alimentos",    "tests.México.alimentos.*",
            "checkout",     "tests.México.E2E.FlujosCompraNoLogin"
    );

    private final RunnerConfig  config;
    private final BackendClient client;

    public JobExecutor(RunnerConfig config, BackendClient client) {
        this.config = config;
        this.client = client;
    }

    public void execute(JobDto job) {
        System.out.printf("%n[Executor] ▶  %s  |  Suite: %s  |  Env: %s  |  País: %s%n",
                job.executionId, job.suite, job.env, job.country);

        AtomicInteger passed  = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        try {
            client.sendLog(job.executionId, "INFO",
                    "▶ Iniciando suite: " + job.suite
                    + "  |  Env: "    + job.env
                    + "  |  Device: " + job.device
                    + "  |  País: "   + job.country);

            // ── Pre-flight ────────────────────────────────────────────────────
            checkAdbDevices(job.executionId);
            checkAppiumServer(job.executionId);

            // ── Build Gradle command ──────────────────────────────────────────
            List<String> cmd = buildCommand(job);
            client.sendLog(job.executionId, "INFO",
                    "🔧 Comando: " + String.join(" ", cmd));
            System.out.println("[Executor] Comando: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(config.workDir));
            pb.redirectErrorStream(true);

            // Environment variables — tests read these via System.getenv()
            pb.environment().put("SUITE_ID",      nvl(job.suite));
            pb.environment().put("ENV",            nvl(job.env));
            pb.environment().put("DEVICE_NAME",    nvl(job.device));
            pb.environment().put("COUNTRY",        nvl(job.country));
            pb.environment().put("APPIUM_HUB",     config.appiumHub);
            pb.environment().put("EXECUTION_NAME", nvl(job.suite));
            pb.environment().put("REUSE_DRIVER",   "true");

            Process process = pb.start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String level = detectLevel(line);
                    client.sendLog(job.executionId, level, line);
                    System.out.println("[" + level + "] " + line);
                    if      ("PASS".equals(level)) passed.incrementAndGet();
                    else if ("FAIL".equals(level)) failed.incrementAndGet();
                    else if ("SKIP".equals(level)) skipped.incrementAndGet();
                }
            }

            int exitCode = process.waitFor();

            // If Gradle crashed with no test output (e.g. compilation error),
            // record at least one failure so the execution doesn't finish as PASSED.
            if (passed.get() == 0 && failed.get() == 0 && skipped.get() == 0 && exitCode != 0) {
                failed.incrementAndGet();
            }

            String summary = passed.get() + " PASSED · "
                           + failed.get() + " FAILED · "
                           + skipped.get() + " SKIPPED";

            client.sendLog(job.executionId,
                    exitCode == 0 ? "PASS" : "FAIL",
                    exitCode == 0
                        ? "✅ Suite completada — " + summary
                        : "❌ Suite terminó con errores (exit " + exitCode + ") — " + summary);

            String allureUrl = generateAllureReport(job.executionId);
            client.sendResult(job.executionId,
                    passed.get(), failed.get(), skipped.get(), allureUrl);
            System.out.println("[Executor] ✓ Finalizado: " + job.executionId);

        } catch (Exception e) {
            System.err.println("[Executor] Error fatal: " + e.getMessage());
            e.printStackTrace();
            client.sendLog(job.executionId, "ERROR",
                    "❌ Error interno del runner: " + e.getMessage());
            try {
                client.sendResult(job.executionId,
                        passed.get(), Math.max(failed.get(), 1), skipped.get(), null);
            } catch (Exception ignored) {}
        }
    }

    // ── Gradle command builder ─────────────────────────────────────────────────

    private List<String> buildCommand(JobDto job) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String  testFilter = resolveTestFilter(job.suite);

        List<String> cmd = new ArrayList<>();
        if (isWindows) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("gradlew.bat");
        } else {
            cmd.add("./gradlew");
        }

        cmd.add("test");
        cmd.add("--tests");
        cmd.add(testFilter);
        cmd.add("--rerun-tasks");

        // build.gradle contains: systemProperties System.getProperties()
        // so -D flags on the Gradle JVM are visible to tests as System.getProperty()
        cmd.add("-DdeviceName="    + nvl(job.device,  "Galaxy A56 5G"));
        cmd.add("-Denv="           + nvl(job.env,     "QA"));
        cmd.add("-Dcountry="       + nvl(job.country, "mexico"));
        cmd.add("-Dappium.hub="    + config.appiumHub + "/wd/hub");
        cmd.add("-DexecutionName=" + nvl(job.suite,   "Suite"));
        cmd.add("-DREUSE_DRIVER=true");

        return cmd;
    }

    private static String resolveTestFilter(String suiteName) {
        if (suiteName == null || suiteName.isBlank()) return "tests.RunAllTests";
        String key = suiteName.toLowerCase().trim();
        return SUITE_MAP.getOrDefault(key, "tests.RunAllTests");
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

            client.sendLog(executionId, count > 0 ? "INFO" : "WARN",
                    count > 0
                        ? "📱 " + count + " dispositivo(s) Android detectado(s) via ADB"
                        : "⚠️  No se detectaron dispositivos ADB. Conecta el dispositivo y habilita depuración USB.");
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  ADB no disponible: " + e.getMessage());
        }
    }

    // ── Pre-flight: Appium ─────────────────────────────────────────────────────

    private void checkAppiumServer(String executionId) {
        String hubBase    = config.appiumHub.replaceAll("/wd/hub$", "");
        String statusUrl  = hubBase + "/status";
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            client.sendLog(executionId, code == 200 ? "INFO" : "WARN",
                    code == 200
                        ? "✅ Appium server online: " + config.appiumHub
                        : "⚠️  Appium respondió HTTP " + code + " en " + statusUrl);
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  Appium no disponible en " + config.appiumHub
                    + " — inícialo con: appium --port 4723");
        }
    }

    // ── Allure report (optional — requires allure CLI installed) ───────────────

    private String generateAllureReport(String executionId) {
        try {
            // Results dir is build/allure-results (Gradle allure plugin default)
            ProcessBuilder pb = new ProcessBuilder(
                    "allure", "generate", "build/allure-results",
                    "-o", "build/reports/allure-report/" + executionId, "--clean");
            pb.directory(new File(config.workDir));
            int exit = pb.start().waitFor();
            if (exit == 0 && !config.allureBaseUrl.isBlank()) {
                String url = config.allureBaseUrl + "/" + executionId;
                System.out.println("[Executor] Allure report generado: " + url);
                return url;
            }
        } catch (Exception e) {
            System.out.println("[Executor] Allure CLI no disponible (opcional): " + e.getMessage());
        }
        return null;
    }

    // ── Log level detection — Gradle / JUnit5 output ──────────────────────────
    // JUnit5 format: "ClassName > methodName() PASSED/FAILED/SKIPPED"
    // Gradle build:  "BUILD SUCCESSFUL in Xs" / "BUILD FAILED"

    private String detectLevel(String line) {
        String trim  = line.trim();
        String upper = trim.toUpperCase();

        // JUnit5 individual test result (only these increment the counters)
        if (trim.contains(" > ")) {
            if (upper.endsWith(" PASSED"))  return "PASS";
            if (upper.endsWith(" FAILED"))  return "FAIL";
            if (upper.endsWith(" SKIPPED")) return "SKIP";
        }

        // Build-level status (shown in logs but NOT counted as tests)
        if (upper.contains("BUILD SUCCESSFUL") || upper.contains("BUILD FAILED")) return "INFO";

        // Errors and warnings in Gradle output
        if (upper.contains("[ERROR]") || upper.startsWith("E: ")) return "FAIL";
        if (upper.contains("[WARNING]") || upper.startsWith("W: "))  return "WARN";

        return "INFO";
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }
}
