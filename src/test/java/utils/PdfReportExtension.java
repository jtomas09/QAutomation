package utils;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
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

        // Register cinema from @Cinema annotation BEFORE @BeforeEach runs, so it's
        // captured in the report even when setup steps fail before ensureCinemaSelectedFromAlimentos().
        context.getTestMethod().ifPresent(method -> {
            Cinema ann = method.getAnnotation(Cinema.class);
            if (ann != null && !ann.value().isBlank()) {
                TestSteps.setCinema(ann.value());
                context.getStore(NS).put("cinema", ann.value());
                try { Allure.label("cinema", ann.value()); } catch (Exception ignored) {}
                log.debug("[Test] Cinema from @Cinema annotation: {}", ann.value());
            }
        });

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

        String cinema = context.getStore(NS).get("cinema", String.class);
        if (cinema == null || cinema.isBlank()) cinema = TestSteps.getCinema();
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
                        // Counts come directly from BaseTestStatusRegistry — authoritative here
                        // because all TestWatcher callbacks (testFailed/testSuccessful) have already
                        // fired by the time SuiteMailer.close() runs (root-store cleanup happens after
                        // @AfterEach + TestWatcher). Do NOT read counts from suite-metrics.properties:
                        // BaseTest.afterAllSuiteAndCloseDriverIfNeeded writes that file from @AfterEach,
                        // which runs BEFORE TestWatcher.testFailed, so those counts are wrong for
                        // tests that fail via JUnit assertions.
                        int total  = BaseTestStatusRegistry.getTotal();
                        int passed = BaseTestStatusRegistry.getPassed();
                        int failed = BaseTestStatusRegistry.getFailed();
                        String suiteName = BaseTestStatusRegistry.getExecutionName();
                        long dur = durationMillis;
                        String executed = EXECUTED_TESTS.toString();
                        String mergedPdfName = "";

                        // Supplement with mergedPdfName from file if BaseTest already wrote it
                        if (Files.exists(METRICS_PATH)) {
                            try {
                                Properties fileProps = new Properties();
                                try (InputStream in = Files.newInputStream(METRICS_PATH)) {
                                    fileProps.load(in);
                                }
                                String fn = fileProps.getProperty("mergedPdfName");
                                if (fn != null && !fn.isBlank()) mergedPdfName = fn;
                            } catch (Exception ignored) {}
                        }

                        // Write correct values so other consumers (e.g. diagnostics) see accurate data
                        try {
                            Properties write = new Properties();
                            write.setProperty("suiteName", suiteName);
                            write.setProperty("totalTests", String.valueOf(total));
                            write.setProperty("passedTests", String.valueOf(passed));
                            write.setProperty("failedTests", String.valueOf(failed));
                            write.setProperty("durationMillis", String.valueOf(dur));
                            write.setProperty("executedTests", executed);
                            write.setProperty("mergedPdfName", mergedPdfName);
                            try (OutputStream out = Files.newOutputStream(
                                    METRICS_PATH,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING
                            )) {
                                write.store(out, "Suite metrics corrected by PdfReportExtension (SuiteMailer)");
                            }
                            log.info("[Suite] suite-metrics.properties written with registry counts: total={} passed={} failed={}",
                                    total, passed, failed);
                        } catch (Exception e) {
                            log.warn("[Suite] Could not write suite-metrics.properties: {}", e.getMessage());
                        }

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

}
