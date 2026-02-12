package utils;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class TestSteps {

    // Carpeta donde se guardan las evidencias (imágenes) para el PDF
    private static final Path EVIDENCE_DIR = Paths.get("build", "evidencias");

    // Lista de pasos por hilo/test
    private static final ThreadLocal<List<StepResult>> steps =
            ThreadLocal.withInitial(ArrayList::new);

    // Nombre del test actual (para nombrar archivos)
    private static final ThreadLocal<String> currentTestName = new ThreadLocal<>();

    public static void startScenario(String testName) {
        currentTestName.set(sanitize(testName));
        steps.get().clear();
    }

    public static List<StepResult> finishScenario() {
        List<StepResult> copy = new ArrayList<>(steps.get());
        steps.remove();              // 🔥 libera ThreadLocal
        currentTestName.remove();
        return copy;
    }

    public static void run(String name, Runnable action, AndroidDriver driver) {
        try {
            // Ejecutar el paso como step de Allure
            Allure.step(name, action::run);

            // Screenshot de paso OK
            String screenshotPath = captureEvidence(driver, name, name + " - OK");

            // Guardar resultado del paso para el PDF (OK)
            steps.get().add(new StepResult(name, "OK", screenshotPath));

        } catch (Throwable t) { // ✅ atrapa también AssertionError y cualquier fallo real

            // Screenshot en error
            String screenshotPath = captureEvidence(driver, name + "_ERROR", "Screenshot on error");

            // Stacktrace completo a Allure
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            Allure.addAttachment("Stacktrace completo", "text/plain", sw.toString());

            // Guardar resultado del paso (ERROR) para el PDF
            steps.get().add(new StepResult(name, "ERROR", screenshotPath));

            // ✅ Falla el test conservando la causa
            Assertions.fail("❌ " + name + " falló.", t);
        }
    }

    // ======================================================
    // MÉTODOS USADOS TAMBIÉN POR PdfReportExtension
    // ======================================================

    public static String captureEvidence(AndroidDriver driver, String stepName, String allureName) {
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

    // ================== MÉTODOS PRIVADOS ==================

    private static String takeScreenshot(AndroidDriver driver, String stepName) throws IOException {
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

