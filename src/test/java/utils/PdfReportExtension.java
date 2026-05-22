package utils;

import io.appium.java_client.android.AndroidDriver;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PdfReportExtension.class);

    private static final StringBuilder EXECUTED_TESTS = new StringBuilder();

    private static final Path REPORT_DIR = Paths.get("build", "reportes-pdf");
    private static final Path METRICS_PATH = REPORT_DIR.resolve("suite-metrics.properties");
    private static final Path MAIL_LOCK_PATH = REPORT_DIR.resolve("mail-sent.lock");

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(PdfReportExtension.class);

    private static volatile long suiteStartMillis = 0L;

    @Override
    public void beforeAll(ExtensionContext context) {
        String execName = System.getProperty("executionName");
        if (execName == null || execName.isBlank()) execName = System.getenv("EXECUTION_NAME");
        if (execName == null || execName.isBlank()) execName = "Cinépolis";
        final String executionNameFinal = execName;

        context.getRoot().getStore(NS).getOrComputeIfAbsent("RUN_INIT", key -> {
            suiteStartMillis = System.currentTimeMillis();

            EXECUTED_TESTS.setLength(0);
            BaseTestStatusRegistry.resetForRun(executionNameFinal);

            try { Files.createDirectories(REPORT_DIR); } catch (Exception ignored) {}
            try { Files.deleteIfExists(MAIL_LOCK_PATH); } catch (Exception ignored) {}

            log.info("[Suite] Execution started: {}", BaseTestStatusRegistry.getExecutionName());
            return Boolean.TRUE;
        });

        context.getRoot().getStore(NS).getOrComputeIfAbsent(
                "SUITE_MAILER",
                key -> new SuiteMailer(),
                SuiteMailer.class
        );
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        String testName = context.getDisplayName();

        if (EXECUTED_TESTS.length() > 0) EXECUTED_TESTS.append(" | ");
        EXECUTED_TESTS.append(testName);

        TestSteps.startScenario(testName);
        log.info("[Test] Starting: {}", testName);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        BaseTestStatusRegistry.onTestStart(context.getDisplayName());
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        BaseTestStatusRegistry.markPassed(context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        BaseTestStatusRegistry.markFailed(context.getDisplayName(), cause);

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

        getDriver(context).ifPresent(driver -> {
            String path = TestSteps.captureEvidence(driver, "TEST_FINAL", "TEST_FINAL");
            if (path != null) {
                TestSteps.getStepsInternal()
                        .add(new StepResult("Evidencia final (auto)", "OK", path));
            }
        });

        String cinema = TestSteps.getCinema();
        List<StepResult> steps = TestSteps.finishScenario();
        PdfReportGenerator.generate(testName, cinema, steps);

        log.info("[Test] Finished: {}", testName);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        log.debug("[Suite] afterAll: suite email will be sent by SuiteMailer.close() at JVM shutdown.");
    }

    private static class SuiteMailer implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            long durationMillis = Math.max(0, System.currentTimeMillis() - suiteStartMillis);

            try {
                Files.createDirectories(REPORT_DIR);

                try (FileChannel channel = FileChannel.open(
                        MAIL_LOCK_PATH,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                )) {
                    FileLock lock = channel.tryLock();
                    if (lock == null) {
                        log.warn("[Suite] Email send skipped: lock already acquired by another process.");
                        return;
                    }

                    try {
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

                            log.info("[Suite] suite-metrics.properties written by PdfReportExtension: {}",
                                    METRICS_PATH.toAbsolutePath());
                        } else {
                            log.debug("[Suite] suite-metrics.properties already exists; using it for the final email.");
                        }

                        Properties p = new Properties();
                        try (InputStream in = Files.newInputStream(METRICS_PATH)) {
                            p.load(in);
                        }

                        String suiteName = p.getProperty("suiteName", "Cinépolis");
                        int total = parseIntSafe(p.getProperty("totalTests"), 0);
                        int passed = parseIntSafe(p.getProperty("passedTests"), 0);
                        int failed = parseIntSafe(p.getProperty("failedTests"), 0);
                        long dur = parseLongSafe(p.getProperty("durationMillis"), durationMillis);
                        String executed = p.getProperty("executedTests", EXECUTED_TESTS.toString());
                        String mergedPdfName = p.getProperty("mergedPdfName", "");

                        log.info("[Suite] Sending final suite email. suiteName={} total={} passed={} failed={}",
                                suiteName, total, passed, failed);

                        AllureReportSender.sendFinalSuiteReport(
                                suiteName, total, passed, failed, dur, executed, mergedPdfName
                        );

                        channel.write(StandardCharsets.UTF_8.encode("SENT"));
                        log.info("[Suite] Final suite email sent successfully.");

                    } finally {
                        try { lock.release(); } catch (Exception ignored) {}
                    }
                }

            } catch (Exception e) {
                log.error("[Suite] Failed to send final suite email: {}", e.getMessage(), e);
            }
        }
    }

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
