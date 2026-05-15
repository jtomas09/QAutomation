package utils;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class AllureAutoPublishListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AllureAutoPublishListener.class);

    private static final String SUITE_FQCN = "tests.RunAllTests";
    private static final Path URL_FILE = Paths.get("build", "allure-report-url.txt");

    private final AtomicBoolean isRunAllTests = new AtomicBoolean(false);

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        for (TestIdentifier root : testPlan.getRoots()) {
            if (containsSuite(testPlan, root)) {
                isRunAllTests.set(true);
                break;
            }
        }

        AllureReportSender.resetMailLock();
        resetUrlFile();

        if (isRunAllTests.get()) {
            log.info("[AllureAutoPublishListener] RunAllTests detected — Allure will be published to Netlify at the end.");
        } else {
            log.info("[AllureAutoPublishListener] Individual execution — Allure will still be published to Netlify.");
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            log.info("[AllureAutoPublishListener] Generating and publishing Allure report...");
            String url = AllureAutoPublisher.generateAndPublish();
            log.info("[AllureAutoPublishListener] Allure report published at: {}", url);
        } catch (Exception e) {
            log.error("[AllureAutoPublishListener] Failed to publish Allure report: {}", e.getMessage(), e);
        }
    }

    private static void resetUrlFile() {
        try {
            if (Files.deleteIfExists(URL_FILE)) {
                log.debug("[AllureAutoPublishListener] Previous Allure URL cleared: {}", URL_FILE.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("[AllureAutoPublishListener] Could not delete URL file: {}", e.getMessage());
        }
    }

    private boolean containsSuite(TestPlan plan, TestIdentifier node) {
        if (node.getDisplayName() != null && node.getDisplayName().contains("RunAllTests")) {
            return true;
        }

        Optional<TestSource> src = node.getSource();
        if (src.isPresent() && src.get() instanceof ClassSource cs) {
            if (SUITE_FQCN.equals(cs.getClassName())) return true;
        }

        for (TestIdentifier child : plan.getChildren(node)) {
            if (containsSuite(plan, child)) return true;
        }
        return false;
    }
}
