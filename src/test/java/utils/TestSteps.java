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
        long t0 = System.currentTimeMillis();
        boolean ios = config.DriverFactory.isIOS();
        log.info("[TRACE] Ejecutando TestSteps.run(\"{}\") | hilo={} plataforma={} hora={}",
                name, Thread.currentThread().getName(), ios ? "iOS" : "Android", t0);
        // Arquitectura de eventos — este es el único punto por el que pasa CADA paso
        // funcional real de cualquier flujo (SeleccionAsientos, FlujosCompraNoLogin,
        // alimentos, etc.), así que instrumentarlo aquí cubre todos los flujos
        // existentes de una sola vez, sin tocar cada Page Object por separado.
        String suite = System.getProperty("executionName", "");
        String test  = currentTestName.get();
        TestFlowEventPublisher.stepStarted(suite, test, name);
        final String[] ref = {null};
        try {
            Allure.step(name, () -> {
                action.run();
                if (CAPTURE_EVIDENCE_ENABLED) {
                    ref[0] = captureEvidence(driver, name, name + " - OK");
                }
            });
            steps.get().add(new StepResult(name, "OK", ref[0]));
            log.info("[TRACE] TestSteps.run(\"{}\") OK | duracionMs={}", name, System.currentTimeMillis() - t0);
            TestFlowEventPublisher.stepCompleted(suite, test, name);

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
            TestFlowEventPublisher.stepSkipped(suite, test, name, aborted.getMessage());
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
            TestFlowEventPublisher.stepFailed(suite, test, name, t.getMessage());

            log.error("[TestSteps] Step failed: {}", name, t);
            log.error("[TRACE] TestSteps.run(\"{}\") ERROR | plataforma={} duracionMs={} causa={}",
                    name, ios ? "iOS" : "Android", System.currentTimeMillis() - t0, t.getMessage());
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

    // FIX real (causa raíz confirmada de pérdida de acentos/Unicode): este sanitize()
    // alimenta currentTestName, que a su vez viaja SIN RESANITIZAR a
    // TestFlowEventPublisher.stepStarted/Completed/Failed/Skipped() — es decir, el nombre
    // de prueba mostrado en vivo en el Dashboard durante la ejecución venía de aquí. El
    // regex anterior era una whitelist ASCII (solo a-z A-Z 0-9 _ -), así que "Selección de
    // Múltiples Asientos" llegaba al Dashboard como "Selecci_n_de_M_ltiples_Asientos" desde
    // el primer evento. Se reemplaza por una blacklist que solo sustituye caracteres
    // realmente inválidos en un nombre de archivo (este mismo valor también se usa para el
    // nombre del PNG de evidencia en takeScreenshot()), preservando acentos/ñ/¿/¡/Unicode.
    private static final java.util.regex.Pattern FILENAME_UNSAFE_CHARS =
            java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    private static String sanitize(String text) {
        if (text == null) return "";
        return FILENAME_UNSAFE_CHARS.matcher(text).replaceAll("_");
    }
}
