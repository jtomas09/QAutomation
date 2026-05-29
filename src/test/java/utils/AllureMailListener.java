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

    private final Set<String>    executedMenus = new LinkedHashSet<>();
    private final AtomicInteger  totalCount    = new AtomicInteger(0);
    private final AtomicInteger  passedCount   = new AtomicInteger(0);
    private final AtomicInteger  failedCount   = new AtomicInteger(0);
    private final Set<String>    failedMenus   = new LinkedHashSet<>();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        startMs = System.currentTimeMillis();
        executedMenus.clear();
        totalCount.set(0);
        passedCount.set(0);
        failedCount.set(0);
        failedMenus.clear();
        log.info("[AllureMailListener] Suite execution started.");

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

        // Contar cada test que terminó (PASSED, FAILED o ABORTED)
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
        int  total       = totalCount.get();
        int  passed      = passedCount.get();
        int  failed      = failedCount.get();
        String executedTests = executedMenus.stream().collect(Collectors.joining(" | "));

        log.info("[AllureMailListener] Menus executed: {} | total: {} | passed: {} | failed: {} | failedMenus: {}",
                executedTests, total, passed, failed, failedMenus);

        // Logs de diagnóstico del correo
        log.info("[EMAIL] ExecutionId detectado: {}", System.getProperty("executionName", "Cinepolis"));
        log.info("[EMAIL] Total tests encontrados (JUnit events): {}", total);
        log.info("[EMAIL] Passed encontrados (JUnit events): {}", passed);
        log.info("[EMAIL] Failed encontrados (JUnit events): {}", failed);
        log.info("[EMAIL] Duración real del run: {}ms", duration);
        log.info("[EMAIL] Fuente de datos: AllureMailListener via JUnit TestExecutionResult");
        log.info("[EMAIL] Resultado calculado por JUnit: {}", failed > 0 ? "FAILED" : (total == 0 ? "UNKNOWN" : "PASSED"));

        String netlifyUrl = "";
        try {
            netlifyUrl = AllureUrlStore.readUrl();
            log.info("[AllureMailListener] Netlify URL for report: {}", netlifyUrl);
        } catch (Exception e) {
            log.info("[AllureMailListener] No Netlify URL available; email will be sent without it.");
        }

        try {
            // Pasar los conteos REALES de JUnit (total y passed ahora son precisos).
            // sendFinalSuiteReport usará estos valores directamente y solo recurrirá a
            // AllureSummaryReader si total == 0 (modo compatibilidad / AllureMailRunner).
            AllureReportSender.sendFinalSuiteReport(
                    "Cinepolis",
                    total,
                    passed,
                    failed,
                    duration,
                    executedTests,
                    netlifyUrl
            );
        } catch (Exception e) {
            log.error("[AllureMailListener] Failed to send final suite email: {}", e.getMessage(), e);
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
