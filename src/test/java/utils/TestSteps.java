package utils;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestSteps {

    private static final Logger log = LoggerFactory.getLogger(TestSteps.class);

    /** Set to false via -DcaptureEvidence=false to skip screenshot capture and speed up execution. */
    private static final boolean CAPTURE_EVIDENCE_ENABLED =
            Boolean.parseBoolean(System.getProperty("captureEvidence", "true"));

    private static final Path EVIDENCE_DIR = Paths.get("build", "evidencias");

    private static final ThreadLocal<List<StepResult>> steps =
            ThreadLocal.withInitial(ArrayList::new);

    private static final ThreadLocal<String> currentTestName = new ThreadLocal<>();
    private static final ThreadLocal<String> currentCinema = new ThreadLocal<>();

    public static void startScenario(String testName) {
        currentTestName.set(sanitize(testName));
        steps.get().clear();
        currentCinema.remove();
    }

    public static void setCinema(String cinema) {
        currentCinema.set(cinema);
    }

    public static String getCinema() {
        return currentCinema.get();
    }

    public static List<StepResult> finishScenario() {
        List<StepResult> copy = new ArrayList<>(steps.get());
        steps.remove();
        currentTestName.remove();
        currentCinema.remove();
        return copy;
    }

    public static void run(String name, Runnable action, AppiumDriver driver) {
        final String[] ref = {null};
        try {
            Allure.step(name, () -> {
                action.run();
                if (CAPTURE_EVIDENCE_ENABLED) {
                    ref[0] = captureEvidence(driver, name, name + " - OK");
                }
            });
            steps.get().add(new StepResult(name, "OK", ref[0]));

        } catch (TestAbortedException aborted) {
            String screenshotPath = null;
            if (CAPTURE_EVIDENCE_ENABLED) {
                screenshotPath = captureEvidence(driver, name + "_SKIPPED", "Screenshot on skipped");
            }

            try {
                Allure.addAttachment("Motivo SKIPPED", "text/plain",
                        String.valueOf(aborted.getMessage()));
            } catch (Exception ignored) {}

            steps.get().add(new StepResult(name, "SKIPPED", screenshotPath));
            throw aborted;

        } catch (Throwable t) {
            String screenshotPath = captureEvidence(driver, name + "_ERROR", "Screenshot on error");

            // Guardamos el stacktrace en Allure; si el UUID del test ya no existe
            // (e.g. lifecycle fuera de orden) el catch evita un error secundario.
            try {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                Allure.addAttachment("Stacktrace completo", "text/plain", sw.toString());
            } catch (Exception allureEx) {
                log.debug("[TestSteps] Allure UUID no disponible al adjuntar stacktrace: {}", allureEx.getMessage());
            }

            steps.get().add(new StepResult(name, "ERROR", screenshotPath));

            log.error("[TestSteps] Step failed: {}", name, t);
            Assertions.fail(name + " failed.", t);
        }
    }

    public static String captureEvidence(AppiumDriver driver, String stepName, String allureName) {
        try {
            if (driver == null) return null;

            String screenshotPath = takeScreenshot(driver, stepName);
            byte[] bytes = Files.readAllBytes(Paths.get(screenshotPath));

            Allure.addAttachment(
                    allureName,
                    "image/png",
                    new ByteArrayInputStream(bytes),
                    ".png"
            );

            return screenshotPath;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<StepResult> getStepsInternal() {
        return steps.get();
    }

    private static String takeScreenshot(AppiumDriver driver, String stepName) throws IOException {
        byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

        Files.createDirectories(EVIDENCE_DIR);

        String testName = currentTestName.get() != null ? currentTestName.get() : "test";
        String fileName = testName + "_" + sanitize(stepName) + "_" + System.currentTimeMillis() + ".png";
        Path file = EVIDENCE_DIR.resolve(fileName);

        Files.write(file, bytes);
        return file.toString();
    }

    private static String sanitize(String text) {
        return text.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
