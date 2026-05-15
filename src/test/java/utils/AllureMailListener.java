package utils;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class AllureMailListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AllureMailListener.class);

    private long startMs;

    private final Set<String> executedMenus = new LinkedHashSet<>();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        startMs = System.currentTimeMillis();
        executedMenus.clear();
        log.info("[AllureMailListener] Suite execution started.");

        try {
            AllureReportSender.resetMailLock();
        } catch (Exception e) {
            log.warn("[AllureMailListener] Could not reset mail lock: {}", e.getMessage());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest()) return;
        testIdentifier.getSource().ifPresent(this::extractAndStoreMenu);
    }

    private void extractAndStoreMenu(TestSource source) {
        try {
            if (source instanceof MethodSource ms) {
                String className = ms.getClassName();
                if (className == null) return;

                String simpleName = className.substring(className.lastIndexOf('.') + 1);

                if (simpleName.startsWith("Menu")) {
                    executedMenus.add(simpleName);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        long duration = System.currentTimeMillis() - startMs;

        String executedTests = executedMenus.stream().collect(Collectors.joining(" | "));

        String netlifyUrl = "";
        try {
            netlifyUrl = AllureUrlStore.readUrl();
            log.info("[AllureMailListener] Netlify URL for report: {}", netlifyUrl);
        } catch (Exception e) {
            log.info("[AllureMailListener] No Netlify URL available; email will be sent without it.");
        }

        try {
            log.info("[AllureMailListener] Menus executed: {}", executedTests);

            AllureReportSender.sendFinalSuiteReport(
                    "Cinepolis",
                    0,
                    0,
                    0,
                    duration,
                    executedTests,
                    netlifyUrl
            );
        } catch (Exception e) {
            log.error("[AllureMailListener] Failed to send final suite email: {}", e.getMessage(), e);
        }
    }
}
