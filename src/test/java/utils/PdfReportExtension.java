package utils;

import io.appium.java_client.AppiumDriver;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public class PdfReportExtension implements
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        TestWatcher,
        BeforeTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(PdfReportExtension.class);

    private static final StringBuilder EXECUTED_TESTS   = new StringBuilder();
    private static final Set<String>   EXECUTED_CLASSES = new LinkedHashSet<>();

    private static final Path REPORT_DIR = Paths.get("build", "reportes-pdf");
    private static final Path METRICS_PATH = REPORT_DIR.resolve("suite-metrics.properties");
    private static final Path MAIL_LOCK_PATH = REPORT_DIR.resolve("mail-sent.lock");

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(PdfReportExtension.class);

    private static volatile long suiteStartMillis = 0L;

    // ── Diagnóstico EMAIL FLOW ──────────────────────────────────────────────
    // Prueba, con evidencia de log, si SuiteMailer.close() (el único disparador de
    // correo cuando se corre vía `gradlew test`) realmente se invoca al final del
    // run. Si el JVM termina (shutdown hook) y RUN_INIT sí arrancó pero close()
    // nunca marcó CLOSE_INVOKED, la causa es que el motor JUnit nunca llegó a
    // cerrar el root store (p.ej. la JVM murió antes de completar el engine),
    // no un fallo dentro de SuiteMailer/AllureReportSender.
    private static volatile boolean RUN_STARTED     = false;
    private static volatile boolean CLOSE_INVOKED   = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (RUN_STARTED && !CLOSE_INVOKED) {
                log.error("[EMAIL FLOW] JVM shutdown hook: SuiteMailer.close() NUNCA fue invocado "
                        + "aunque el run sí inició (RUN_INIT=true). El root store de JUnit no llegó a "
                        + "cerrarse con normalidad — el JVM terminó antes de que el engine completara su "
                        + "ciclo de vida (crash, kill, o salida anómala), no un fallo dentro del envío de correo.");
            }
        }, "email-flow-suite-mailer-watchdog"));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        String execName = System.getProperty("executionName");
        if (execName == null || execName.isBlank()) execName = System.getenv("EXECUTION_NAME");
        if (execName == null || execName.isBlank()) execName = "Cinépolis";
        final String executionNameFinal = execName;

        context.getRoot().getStore(NS).getOrComputeIfAbsent("RUN_INIT", key -> {
            suiteStartMillis = System.currentTimeMillis();
            RUN_STARTED = true;

            EXECUTED_TESTS.setLength(0);
            EXECUTED_CLASSES.clear();
            BaseTestStatusRegistry.resetForRun(executionNameFinal);

            try { Files.createDirectories(REPORT_DIR); } catch (Exception ignored) {}
            try { Files.deleteIfExists(MAIL_LOCK_PATH); } catch (Exception ignored) {}

            log.info("[Suite] Execution started: {}", BaseTestStatusRegistry.getExecutionName());
            return Boolean.TRUE;
        });

        context.getRoot().getStore(NS).getOrComputeIfAbsent(
                "SUITE_MAILER",
                key -> {
                    log.info("[EMAIL FLOW] SuiteMailer registrado en el root store (se ejecutará en "
                            + "close() cuando el engine de JUnit termine todo el run).");
                    return new SuiteMailer();
                },
                SuiteMailer.class
        );
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        String testName = context.getDisplayName();

        if (EXECUTED_TESTS.length() > 0) EXECUTED_TESTS.append(" | ");
        EXECUTED_TESTS.append(testName);

        context.getTestClass().map(Class::getSimpleName).ifPresent(EXECUTED_CLASSES::add);

        TestSteps.startScenario(testName);
        TestFlowEventPublisher.testStarted(System.getProperty("executionName", ""), testName);

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
        TestFlowEventPublisher.testFinished(
                System.getProperty("executionName", ""), context.getDisplayName(), true);
        // clear() al final, no en @AfterEach: JUnit 5 invoca TestWatcher DESPUÉS de
        // @AfterEach — limpiar antes (como hacía BaseTest.tearDown()) borra el latch
        // "ya falló"/"ya contado" justo antes de que este callback pudiera leerlo,
        // causando doble conteo. Ver BaseTestStatusRegistry.clear() para el porqué
        // de la limpieza en sí (colisiones de clave entre tests con el mismo
        // getDisplayName() en distintas clases).
        BaseTestStatusRegistry.clear(context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        BaseTestStatusRegistry.markFailed(context.getDisplayName(), cause);
        TestFlowEventPublisher.testFinished(
                System.getProperty("executionName", ""), context.getDisplayName(), false);

        getDriver(context).ifPresent(driver -> {
            String path = TestSteps.captureEvidence(driver, "TEST_FAILED", "TEST_FAILED");
            if (path != null) {
                TestSteps.getStepsInternal()
                        .add(new StepResult("Evidencia - fallo de test (auto)", "ERROR", path));
            }
        });

        // clear() al final — ver comentario en testSuccessful().
        BaseTestStatusRegistry.clear(context.getDisplayName());
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        // Test abortado vía Assumptions.abort() → se cuenta como skipped, NO como fallido.
        // El conteo skipped se deriva de (total - passed - failed); no incrementar FAILED aquí.
        TestFlowEventPublisher.testFinished(
                System.getProperty("executionName", ""), context.getDisplayName(), false);
        // Igual que en testSuccessful()/testFailed(): limpiar aquí, al final del ciclo
        // de vida del test, para no interferir con el latch antes de que se consulte.
        BaseTestStatusRegistry.clear(context.getDisplayName());
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
        log.info("[EMAIL FLOW] Entrando a AfterAll (PdfReportExtension) — clase: {}. "
                + "El correo se enviará luego, en SuiteMailer.close(), cuando el root store "
                + "cierre al terminar TODO el run (no al terminar esta clase).",
                context.getTestClass().map(Class::getSimpleName).orElse("?"));
    }

    private static class SuiteMailer implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            CLOSE_INVOKED = true;
            log.info("[EMAIL FLOW] Entrando a SuiteMailer.close() — root store cerrando, fin real del run.");
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
                                suiteName, total, passed, failed, dur, executed, mergedPdfName,
                                suiteStartMillis,
                                java.util.Collections.unmodifiableSet(EXECUTED_CLASSES)
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

    private Optional<AppiumDriver> getDriver(ExtensionContext context) {
        return context.getTestInstance().flatMap(instance -> {
            try {
                var field = instance.getClass().getDeclaredField("driver");
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof AppiumDriver) {
                    return Optional.of((AppiumDriver) value);
                }
            } catch (Exception ignored) {}
            return Optional.empty();
        });
    }

}
