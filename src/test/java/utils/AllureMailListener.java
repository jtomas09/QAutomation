package utils;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AllureMailListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AllureMailListener.class);

    private long startMs;

    private final Set<String>   executedMenus = new LinkedHashSet<>();
    private final AtomicInteger totalCount    = new AtomicInteger(0);
    private final AtomicInteger passedCount   = new AtomicInteger(0);
    private final AtomicInteger failedCount   = new AtomicInteger(0);
    private final Set<String>   failedMenus   = new LinkedHashSet<>();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        startMs = System.currentTimeMillis();
        executedMenus.clear();
        totalCount.set(0);
        passedCount.set(0);
        failedCount.set(0);
        failedMenus.clear();
        log.info("[AllureMailListener] Suite execution started.");

        // Resetear BaseTestStatusRegistry desde aquí como seguridad adicional:
        // PdfReportExtension.beforeAll() también lo hace antes del primer test, pero
        // si se corre sin esa extensión, este reset garantiza un estado limpio.
        try {
            BaseTestStatusRegistry.resetForRun(System.getProperty("executionName", "Cinepolis"));
        } catch (Exception e) {
            log.warn("[AllureMailListener] Could not reset BaseTestStatusRegistry: {}", e.getMessage());
        }

        try {
            AllureReportSender.resetMailLock();
        } catch (Exception e) {
            log.warn("[AllureMailListener] Could not reset mail lock: {}", e.getMessage());
        }

        // Limpiar resultados de ejecuciones anteriores para evitar contaminación del correo.
        cleanForNewRun();
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) return;

        // Conteos propios (nivel Platform). Se usan como fallback si BaseTestStatusRegistry
        // no está disponible (no se usa PdfReportExtension).
        totalCount.incrementAndGet();
        testIdentifier.getSource().ifPresent(this::extractAndStoreMenu);

        TestExecutionResult.Status status = result.getStatus();
        if (status == TestExecutionResult.Status.FAILED
                || status == TestExecutionResult.Status.ABORTED) {
            failedCount.incrementAndGet();
            testIdentifier.getSource().ifPresent(src -> extractMenuName(src, failedMenus));
        } else if (status == TestExecutionResult.Status.SUCCESSFUL) {
            passedCount.incrementAndGet();
        }
    }

    private void extractAndStoreMenu(TestSource source) {
        extractMenuName(source, executedMenus);
    }

    private void extractMenuName(TestSource source, Set<String> target) {
        try {
            if (source instanceof MethodSource ms) {
                String className = ms.getClassName();
                if (className == null) return;
                String simpleName = className.substring(className.lastIndexOf('.') + 1);
                if (simpleName.startsWith("Menu")) {
                    target.add(simpleName);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        long duration    = System.currentTimeMillis() - startMs;
        String executedTests = executedMenus.stream().collect(Collectors.joining(" | "));

        // ---------------------------------------------------------------
        // Fuente de conteos: BaseTestStatusRegistry (nivel Jupiter)
        //
        // PdfReportExtension implementa TestWatcher (JUnit Jupiter) y actualiza
        // BaseTestStatusRegistry en testFailed()/testSuccessful(). Este nivel es
        // más fiable que los eventos Platform porque:
        //  1. El latch "ya fallado" evita que un retry exitoso marque como passed
        //     un test que falló en un intento anterior.
        //  2. Es el mismo origen que usa el PDF, garantizando consistencia.
        //
        // Fallback: si BaseTestStatusRegistry está vacío (PdfReportExtension no activo),
        // se usan los conteos propios del listener de plataforma.
        // ---------------------------------------------------------------
        int registryTotal  = BaseTestStatusRegistry.getTotal();
        int registryPassed = BaseTestStatusRegistry.getPassed();
        int registryFailed = BaseTestStatusRegistry.getFailed();

        int total;
        int passed;
        int failed;

        if (registryTotal > 0) {
            // BaseTestStatusRegistry tiene datos reales del run actual → usarlo
            total  = registryTotal;
            passed = registryPassed;
            failed = registryFailed;
            // Seguridad adicional: si el listener Platform vio más fallos que Jupiter, prevalece el mayor
            if (failedCount.get() > failed) {
                log.info("[AllureMailListener] failedCount(Platform)={} > BaseTestStatusRegistry.failed={}" +
                         " — usando el mayor.", failedCount.get(), failed);
                failed = failedCount.get();
            }
            log.info("[AllureMailListener] Fuente: BaseTestStatusRegistry — total={} passed={} failed={}",
                    total, passed, failed);
        } else {
            // Fallback: sin PdfReportExtension
            total  = totalCount.get();
            passed = passedCount.get();
            failed = failedCount.get();
            log.info("[AllureMailListener] Fuente: conteos propios (fallback) — total={} passed={} failed={}",
                    total, passed, failed);
        }

        log.info("[AllureMailListener] Menus executed: {} | failedMenus: {}", executedTests, failedMenus);

        // ── Purgar archivos de allure-results de runs anteriores ──────────────
        // Se hace al FINAL (todos los tests completaron) para que solo queden
        // archivos del run actual. cleanForNewRun() al inicio es el primer nivel
        // de defensa; esta purga es el segundo nivel por si Allure escribió
        // archivos post-cleanup de un run anterior.
        purgeStaleAllureResults(startMs);

        // Logs de diagnóstico del correo
        log.info("[EMAIL] ExecutionId: {}", System.getProperty("executionName", "Cinepolis"));
        log.info("[EMAIL] Total={} | Passed={} | Failed={} | Duration={}ms", total, passed, failed, duration);
        log.info("[EMAIL] Resultado final: {}", failed > 0 ? "FAILED" : (total == 0 ? "UNKNOWN" : "PASSED"));

        String netlifyUrl = "";
        try {
            netlifyUrl = AllureUrlStore.readUrl();
            log.info("[AllureMailListener] Netlify URL for report: {}", netlifyUrl);
        } catch (Exception e) {
            log.info("[AllureMailListener] No Netlify URL available; email will be sent without it.");
        }

        try {
            AllureReportSender.sendFinalSuiteReport(
                    "Cinepolis",
                    total,
                    passed,
                    failed,
                    duration,
                    executedTests,
                    netlifyUrl,
                    startMs
            );
        } catch (Exception e) {
            log.error("[AllureMailListener] Failed to send final suite email: {}", e.getMessage(), e);
        }
    }

    // =====================================================================
    // Purga de archivos obsoletos justo antes de enviar el correo
    // =====================================================================

    /**
     * Elimina de build/allure-results/ cualquier *-result.json cuya fecha de
     * modificación sea anterior a runStartMs. Se llama al FINAL del plan (todos
     * los tests ya completaron), por lo que solo permanecerán archivos del run actual.
     * Es el segundo nivel de defensa tras cleanForNewRun() al inicio.
     */
    private static void purgeStaleAllureResults(long runStartMs) {
        if (runStartMs <= 0) return;
        Path dir = Paths.get("build", "allure-results");
        if (!Files.exists(dir)) return;
        int removed = 0;
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (!name.endsWith("-result.json") && !name.endsWith("-container.json")) continue;
                try {
                    boolean isStale = false;

                    // Primero: intentar leer el campo "start" del JSON (criterio primario)
                    if (name.endsWith("-result.json")) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode root =
                                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(p.toFile());
                            long testStart = root.path("start").asLong(0);
                            if (testStart > 0) {
                                isStale = testStart < runStartMs;
                            }
                        } catch (Exception ignored) {}
                    }

                    // Fallback: usar lastModified si no hay campo start
                    if (!isStale) {
                        long modified = Files.getLastModifiedTime(p).toMillis();
                        isStale = modified < runStartMs;
                    }

                    if (isStale) {
                        Files.deleteIfExists(p);
                        removed++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("[AllureMailListener] No se pudo purgar allure-results obsoletos: {}", e.getMessage());
        }
        if (removed > 0) {
            log.info("[AllureMailListener] Purgados {} archivo(s) de allure-results de runs anteriores.", removed);
        }
    }

    // =====================================================================
    // Limpieza de resultados antes de cada nueva ejecución
    // =====================================================================

    /**
     * Elimina archivos de resultados de ejecuciones anteriores para garantizar que
     * readAllureFailures() en el correo muestre ÚNICAMENTE los fallos del run actual.
     *
     * Limpia:
     *  - build/allure-results/  → JSONs de resultados/contenedores/adjuntos anteriores
     *  - build/reportes-pdf/    → PDFs de tests anteriores que contaminarían el adjunto
     *  - build/reportes-pdf/suite-metrics.properties → métricas de la suite anterior
     */
    private static void cleanForNewRun() {
        log.info("[AllureMailListener] Limpiando resultados de ejecuciones anteriores...");

        cleanAllureResultsDir();
        cleanReportesPdfDir();

        // suite-metrics.properties: PdfReportExtension solo lo escribe si NO existe;
        // si queda de una ejecución anterior, SuiteMailer usaría métricas incorrectas.
        try {
            Path metricsFile = Paths.get("build", "reportes-pdf", "suite-metrics.properties");
            if (Files.deleteIfExists(metricsFile)) {
                log.info("[AllureMailListener] suite-metrics.properties eliminado (datos de run anterior).");
            }
        } catch (Exception e) {
            log.warn("[AllureMailListener] No se pudo eliminar suite-metrics.properties: {}", e.getMessage());
        }

        log.info("[AllureMailListener] Limpieza completada. Run actual comenzará con estado limpio.");
    }

    /**
     * Elimina archivos de resultados de Allure ({uuid}-result.json, -container.json y adjuntos)
     * pero conserva environment.properties y executor.json.
     */
    private static void cleanAllureResultsDir() {
        Path dir = Paths.get("build", "allure-results");
        if (!Files.exists(dir)) {
            log.debug("[AllureMailListener] allure-results no existe; no requiere limpieza.");
            return;
        }
        try {
            int count = 0;
            try (var stream = Files.list(dir)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    String name = p.getFileName().toString();
                    boolean isResult    = name.endsWith("-result.json");
                    boolean isContainer = name.endsWith("-container.json");
                    // adjuntos: UUID de 36 chars seguido de cualquier extensión
                    boolean isAttachment = name.matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");

                    if (isResult || isContainer || isAttachment) {
                        try { Files.deleteIfExists(p); count++; } catch (Exception ignored) {}
                    }
                }
            }
            log.info("[AllureMailListener] allure-results limpiado: {} archivo(s) eliminado(s).", count);
        } catch (Exception e) {
            log.warn("[AllureMailListener] No se pudo limpiar allure-results: {}", e.getMessage());
        }
    }

    /**
     * Elimina PDFs de tests anteriores de build/reportes-pdf/ para que el adjunto del correo
     * contenga únicamente los PDFs generados durante la ejecución actual.
     */
    private static void cleanReportesPdfDir() {
        Path dir = Paths.get("build", "reportes-pdf");
        if (!Files.exists(dir)) {
            log.debug("[AllureMailListener] reportes-pdf no existe; no requiere limpieza.");
            return;
        }
        try {
            int count = 0;
            try (var stream = Files.list(dir)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (p.getFileName().toString().endsWith(".pdf")) {
                        try { Files.deleteIfExists(p); count++; } catch (Exception ignored) {}
                    }
                }
            }
            log.info("[AllureMailListener] reportes-pdf limpiado: {} PDF(s) eliminado(s).", count);
        } catch (Exception e) {
            log.warn("[AllureMailListener] No se pudo limpiar reportes-pdf: {}", e.getMessage());
        }
    }
}
