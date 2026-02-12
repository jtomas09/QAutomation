package utils;

import org.junit.jupiter.api.extension.*;

public class SuiteWatcherExtension implements
        BeforeAllCallback,
        BeforeTestExecutionCallback,
        TestWatcher {

    @Override
    public void beforeAll(ExtensionContext context) {
        String executionName = System.getProperty("executionName");
        if (executionName == null || executionName.isBlank()) {
            executionName = System.getenv("EXECUTION_NAME");
        }
        if (executionName == null || executionName.isBlank()) {
            executionName = context.getRoot().getDisplayName();
        }

        BaseTestStatusRegistry.resetForRun(executionName);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        BaseTestStatusRegistry.onTestStart(context.getUniqueId());
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        BaseTestStatusRegistry.markPassed(context.getUniqueId());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        BaseTestStatusRegistry.markFailed(context.getUniqueId(), cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        BaseTestStatusRegistry.markFailed(context.getUniqueId(), cause);
    }
}
