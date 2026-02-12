package utils;

import io.appium.java_client.android.AndroidDriver;
import org.junit.jupiter.api.extension.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class PdfReportExtension implements
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        TestWatcher,
        BeforeTestExecutionCallback {

    // 👉 Guarda los nombres reales de los tests ejecutados
    private static final StringBuilder EXECUTED_TESTS = new StringBuilder();

    private static final Path REPORT_DIR = Paths.get("build", "reportes-pdf");
    private static final Path METRICS_PATH = REPORT_DIR.resolve("suite-metrics.properties");

    // ✅ Lock para garantizar 1 solo envío aunque JUnit dispare múltiples callbacks
    private static final Path MAIL_LOCK_PATH = REPORT_DIR.resolve("mail-sent.lock");

    // ✅ Store global (ROOT) para inicializar 1 vez y ejecutar cierre 1 vez
    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(PdfReportExtension.class);

    private static volatile long suiteStartMillis = 0L;

    // =========================
    // SUITE
    // =========================

    @Override
    public void beforeAll(ExtensionContext context) {
        // ✅ Resuelve nombre de ejecución UNA SOLA VEZ (y úsalo como final)
        String execName = System.getProperty("executionName");
        if (execName == null || execName.isBlank()) execName = System.getenv("EXECUTION_NAME");
        if (execName == null || execName.isBlank()) execName = "Cinépolis Alimentos";
        final String executionNameFinal = execName;

        // ✅ Init global 1 vez por corrida (aunque haya varias clases)
        context.getRoot().getStore(NS).getOrComputeIfAbsent("RUN_INIT", key -> {
            suiteStartMillis = System.currentTimeMillis();

            // Limpia tests ejecutados (solo 1 vez por corrida real)
            EXECUTED_TESTS.setLength(0);

            // Reset métricas
            BaseTestStatusRegistry.resetForRun(executionNameFinal);

            // Prepara folder
            try { Files.createDirectories(REPORT_DIR); } catch (Exception ignored) {}

            // ✅ Importantísimo: borra lock al inicio para permitir nuevo envío en la siguiente corrida
            try { Files.deleteIfExists(MAIL_LOCK_PATH); } catch (Exception ignored) {}

            System.out.println("[SUITE] Iniciando ejecución: " + BaseTestStatusRegistry.getExecutionName());
            return Boolean.TRUE;
        });

        // ✅ Registrar el “mailer” global: se ejecuta SOLO al final de TODA la corrida (close)
        context.getRoot().getStore(NS).getOrComputeIfAbsent(
                "SUITE_MAILER",
                key -> new SuiteMailer(),
                SuiteMailer.class
        );
    }

    // =========================
    // TEST
    // =========================

    @Override
    public void beforeEach(ExtensionContext context) {
        String testName = context.getDisplayName();

        // Guarda nombre del test
        if (EXECUTED_TESTS.length() > 0) EXECUTED_TESTS.append(" | ");
        EXECUTED_TESTS.append(testName);

        TestSteps.startScenario(testName);
        System.out.println("[TEST] Iniciando: " + testName);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        // ✅ Usa DisplayName (compatible con tu BaseTest actual)
        BaseTestStatusRegistry.onTestStart(context.getDisplayName());
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        BaseTestStatusRegistry.markPassed(context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        BaseTestStatusRegistry.markFailed(context.getDisplayName(), cause);

        // Evidencia automática en fallo
        getDriver(context).ifPresent(driver -> {
            String path = TestSteps.captureEvidence(driver, "TEST_FAILED", "TEST_FAILED");
            if (path != null) {
                TestSteps.getStepsInternal()
                        .add(new StepResult("Evidencia - fallo de test (auto)", "ERROR", path));
            }
        });
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        BaseTestStatusRegistry.markFailed(context.getDisplayName(), cause);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        String testName = context.getDisplayName();

        // Screenshot final
        getDriver(context).ifPresent(driver -> {
            String path = TestSteps.captureEvidence(driver, "TEST_FINAL", "TEST_FINAL");
            if (path != null) {
                TestSteps.getStepsInternal()
                        .add(new StepResult("Evidencia final (auto)", "OK", path));
            }
        });

        List<StepResult> steps = TestSteps.finishScenario();
        PdfReportGenerator.generate(testName, steps);

        System.out.println("[TEST] Finalizó: " + testName);
    }

    // =========================
    // FIN DE SUITE (por clase)
    // =========================

    @Override
    public void afterAll(ExtensionContext context) {
        // ✅ NO envíes aquí (esto corre por cada clase).
        // El envío real se hace en SuiteMailer.close() (una sola vez al final de TODO).
        System.out.println("[SUITE] (OK) PdfReportExtension no envía correo aquí. Envío se hace al cerrar ROOT (SuiteMailer).");
    }

    // =========================
    // MAILER GLOBAL (1 sola vez)
    // =========================

    private static class SuiteMailer implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            long durationMillis = Math.max(0, System.currentTimeMillis() - suiteStartMillis);

            try {
                Files.createDirectories(REPORT_DIR);

                // ✅ 1) Evitar duplicados (si algo intenta enviar más de una vez)
                try (FileChannel channel = FileChannel.open(
                        MAIL_LOCK_PATH,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                )) {
                    FileLock lock = channel.tryLock();
                    if (lock == null) {
                        System.out.println("[SUITE] (SKIP) Envío ya realizado o en curso (lock activo).");
                        return;
                    }

                    try {
                        // 1) Si existe suite-metrics.properties (por BaseTest), úsalo.
                        // 2) Si no existe, lo generamos con fallback de registry + EXECUTED_TESTS.
                        if (!Files.exists(METRICS_PATH)) {
                            Properties props = new Properties();
                            props.setProperty("suiteName", BaseTestStatusRegistry.getExecutionName());
                            props.setProperty("totalTests", String.valueOf(BaseTestStatusRegistry.getTotal()));
                            props.setProperty("passedTests", String.valueOf(BaseTestStatusRegistry.getPassed()));
                            props.setProperty("failedTests", String.valueOf(BaseTestStatusRegistry.getFailed()));
                            props.setProperty("durationMillis", String.valueOf(durationMillis));
                            props.setProperty("executedTests", EXECUTED_TESTS.toString());
                            props.setProperty("mergedPdfName", "");

                            try (OutputStream out = Files.newOutputStream(
                                    METRICS_PATH,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING
                            )) {
                                props.store(out, "Suite metrics generated by PdfReportExtension");
                            }

                            System.out.println("[SUITE] suite-metrics.properties creado por PdfReportExtension: " + METRICS_PATH.toAbsolutePath());
                        } else {
                            System.out.println("[SUITE] suite-metrics.properties ya existe (probablemente escrito por BaseTest).");
                            System.out.println("[SUITE] Se usará ese archivo para enviar el correo final.");
                        }

                        // Cargar métricas
                        Properties p = new Properties();
                        try (InputStream in = Files.newInputStream(METRICS_PATH)) {
                            p.load(in);
                        }

                        String suiteName = p.getProperty("suiteName", "Cinépolis Alimentos");
                        int total = parseIntSafe(p.getProperty("totalTests"), 0);
                        int passed = parseIntSafe(p.getProperty("passedTests"), 0);
                        int failed = parseIntSafe(p.getProperty("failedTests"), 0);
                        long dur = parseLongSafe(p.getProperty("durationMillis"), durationMillis);
                        String executed = p.getProperty("executedTests", EXECUTED_TESTS.toString());
                        String mergedPdfName = p.getProperty("mergedPdfName", "");

                        System.out.println("[SUITE] Enviando correo ÚNICO final (desde SuiteMailer.close).");
                        System.out.println("[SUITE] suiteName=" + suiteName);
                        System.out.println("[SUITE] Total=" + total + " Passed=" + passed + " Failed=" + failed);
                        System.out.println("[SUITE] executedTests=" + executed);
                        System.out.println("[SUITE] mergedPdfName=" + mergedPdfName);

                        // ✅ ENVÍO ÚNICO FINAL
                        AllureReportSender.sendFinalSuiteReport(
                                suiteName,
                                total,
                                passed,
                                failed,
                                dur,
                                executed,
                                mergedPdfName
                        );

                        // Marca visible (además del lock)
                        channel.write(StandardCharsets.UTF_8.encode("SENT"));
                        System.out.println("[SUITE] ✔ Correo final enviado (lock escrito).");

                    } finally {
                        try { lock.release(); } catch (Exception ignored) {}
                    }
                }

            } catch (Exception e) {
                System.out.println("[SUITE] ERROR enviando correo final: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // =========================
    // DRIVER
    // =========================

    private Optional<AndroidDriver> getDriver(ExtensionContext context) {
        return context.getTestInstance().flatMap(instance -> {
            try {
                var field = instance.getClass().getDeclaredField("driver");
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof AndroidDriver) {
                    return Optional.of((AndroidDriver) value);
                }
            } catch (Exception ignored) {}
            return Optional.empty();
        });
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static long parseLongSafe(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
}
