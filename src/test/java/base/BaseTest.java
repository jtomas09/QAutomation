package base;

import config.DriverFactory;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;

import pages.alimentos.SelectorPage;
import pages.common.BasePage;
import utils.*;

import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.appium.java_client.touch.WaitOptions.waitOptions;
import static io.appium.java_client.touch.offset.PointOption.point;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(PdfReportExtension.class)
public class BaseTest {

    protected AndroidDriver driver;
    private static volatile boolean envWritten = false;

    private static long suiteStart;
    private static long suiteEnd;
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    private static final List<String> executedTests = new CopyOnWriteArrayList<>();
    private static final List<String> executedStories = new CopyOnWriteArrayList<>();

    private static final boolean REUSE_DRIVER =
            "true".equalsIgnoreCase(System.getProperty("REUSE_DRIVER",
                    System.getenv().getOrDefault("REUSE_DRIVER", "false")));

    private static volatile boolean driverCreatedOnce = false;

    private static final boolean AUTO_SCROLL_ON_OPEN =
            "true".equalsIgnoreCase(System.getProperty("AUTO_SCROLL_ON_OPEN",
                    System.getenv().getOrDefault("AUTO_SCROLL_ON_OPEN", "false")));

    private static final int AUTO_SCROLL_SWIPES = Integer.parseInt(
            System.getProperty("AUTO_SCROLL_SWIPES",
                    System.getenv().getOrDefault("AUTO_SCROLL_SWIPES", "1"))
    );

    private static final String AUTO_SCROLL_ALIMENTOS_ANCHOR_XPATH =
            System.getProperty("AUTO_SCROLL_ALIMENTOS_ANCHOR_XPATH",
                    System.getenv().getOrDefault("AUTO_SCROLL_ALIMENTOS_ANCHOR_XPATH",
                            "//*[contains(@text,'Alimentos') or contains(@content-desc,'Alimentos') or contains(@resource-id,'alimentos')]"
                    ));

    private static final int AUTO_SCROLL_ALIMENTOS_WAIT_SECONDS = Integer.parseInt(
            System.getProperty("AUTO_SCROLL_ALIMENTOS_WAIT_SECONDS",
                    System.getenv().getOrDefault("AUTO_SCROLL_ALIMENTOS_WAIT_SECONDS", "3"))
    );

    // ✅ Se limpia una sola vez por ejecución completa
    private static final AtomicBoolean RUN_INIT_DONE = new AtomicBoolean(false);

    @BeforeAll
    public static void beforeAllSuite() {

        // ✅ Este bloque corre SOLO 1 vez por toda la ejecución (aunque haya varias clases)
        if (RUN_INIT_DONE.compareAndSet(false, true)) {

            suiteStart = System.currentTimeMillis();

            // reset contadores/listas por corrida real
            totalTests = 0;
            passedTests = 0;
            failedTests = 0;
            executedTests.clear();
            executedStories.clear();

            envWritten = false;
            driverCreatedOnce = false;

            // ✅ OJO: esto ya NO se repetirá por cada clase
            try { clearDirectory(Paths.get("build", "reportes-pdf")); } catch (Exception ignored) {}
            try { clearDirectory(Paths.get("build", "reports", "allure-report")); } catch (Exception ignored) {}
            try { clearDirectory(Paths.get("build", "allure-results")); } catch (Exception ignored) {}
            try { clearDirectory(Paths.get("build", "evidencias")); } catch (Exception ignored) {}

            System.out.println("[BaseTest] (INIT) Limpieza de carpetas ejecutada SOLO una vez.");
        } else {
            System.out.println("[BaseTest] (INIT) Limpieza ya realizada en esta ejecución. (OK)");
        }
    }

    private static void clearDirectory(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) return;

        Files.walk(dir)
                .filter(p -> !p.equals(dir))
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
    }

    // ============================================================
    // ✅ RECOVERY: asegurar app viva entre tests cuando REUSE_DRIVER=true
    // ============================================================
    private String getAppPackageSafe() {
        try {
            String p = System.getProperty("appPackage");
            if (p != null && !p.isBlank()) return p.trim();
        } catch (Exception ignored) {}

        try {
            if (driver != null && driver.getCapabilities() != null) {
                Object cap = driver.getCapabilities().getCapability("appPackage");
                if (cap != null) {
                    String p = String.valueOf(cap).trim();
                    if (!p.isBlank()) return p;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private void relaunchAppSafe() {
        try {
            if (driver == null) return;
            String appPackage = getAppPackageSafe();
            if (appPackage == null || appPackage.isBlank()) return;

            try { driver.terminateApp(appPackage); } catch (Exception ignored) {}
            try { driver.activateApp(appPackage); } catch (Exception ignored) {}

            // mini-pausa para tomar foreground estable
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        } catch (Exception ignored) {}
    }

    private void ensureAppRunning() {
        try {
            if (driver == null) return;
            String appPackage = getAppPackageSafe();
            if (appPackage == null || appPackage.isBlank()) return;

            try {
                driver.activateApp(appPackage);
            } catch (Exception e) {
                relaunchAppSafe();
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // Swipe / auto-scroll (misma lógica)
    // ============================================================
    private void quickSwipeUp(AndroidDriver driver) {
        Dimension size = driver.manage().window().getSize();
        int width = size.getWidth();
        int height = size.getHeight();

        int x = width / 2;
        int startY = (int) (height * 0.78);
        int endY   = (int) (height * 0.32);

        new TouchAction(driver)
                .press(point(x, startY))
                .waitAction(waitOptions(Duration.ofMillis(180)))
                .moveTo(point(x, endY))
                .release()
                .perform();

        try { Thread.sleep(60); } catch (InterruptedException ignored) {}
    }

    private boolean isAlimentosScreenVisible(AndroidDriver driver) {
        if (driver == null) return false;

        By anchor = By.xpath(AUTO_SCROLL_ALIMENTOS_ANCHOR_XPATH);
        long end = System.currentTimeMillis() + (AUTO_SCROLL_ALIMENTOS_WAIT_SECONDS * 1000L);

        try {
            while (System.currentTimeMillis() < end) {
                List<WebElement> els = driver.findElements(anchor);
                if (!els.isEmpty()) {
                    try { if (els.get(0).isDisplayed()) return true; }
                    catch (Exception ignored) { return true; }
                }
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            }
        } catch (Exception ignored) {}

        return false;
    }

    private void autoScrollOnAppOpen(AndroidDriver driver) {
        if (!AUTO_SCROLL_ON_OPEN || driver == null) return;

        try {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}

            if (!isAlimentosScreenVisible(driver)) {
                System.out.println("[BaseTest] Auto-scroll NO ejecutado: no se detectó pantalla Alimentos.");
                return;
            }

            for (int i = 0; i < AUTO_SCROLL_SWIPES; i++) {
                quickSwipeUp(driver);
            }

            System.out.println("[BaseTest] Auto-scroll ejecutado en pantalla Alimentos. Swipes=" + AUTO_SCROLL_SWIPES);

        } catch (Exception e) {
            System.err.println("[BaseTest] No se pudo auto-scroll al abrir: " + e.getMessage());
        }
    }

    @BeforeEach
    public void setUp(TestInfo testInfo) {
//        BasePage.resetPerTestGuards();          // reset anti-loop por test
//        new BasePage(driver).dismissClubLoginIfPresent();  // intenta cerrarla al inicio
        if (!REUSE_DRIVER) {
            driver = DriverFactory.getDriver();
            System.out.println("[BaseTest] Driver creado: " + driver);
            autoScrollOnAppOpen(driver);

        } else {
            if (!driverCreatedOnce || driver == null) {
                driver = DriverFactory.getDriver();
                driverCreatedOnce = true;
                System.out.println("[BaseTest] Driver creado (REUSE_DRIVER): " + driver);
            } else {
                System.out.println("[BaseTest] Driver reutilizado (REUSE_DRIVER): " + driver);
            }

            // ✅ FIX: garantiza que la app esté viva al inicio de cada test (sin reinstalar)
            ensureAppRunning();

            autoScrollOnAppOpen(driver);
        }

        TestSteps.startScenario(testInfo.getDisplayName());

        if (testInfo.getDisplayName() != null && !testInfo.getDisplayName().isBlank()) {
            if (!executedTests.contains(testInfo.getDisplayName())) {
                executedTests.add(testInfo.getDisplayName());
            }
        }

        try {
            testInfo.getTestMethod().ifPresent(m -> {
                Story st = m.getAnnotation(Story.class);
                if (st != null && st.value() != null && !st.value().isBlank()) {
                    executedStories.add(st.value().trim());
                }
            });
        } catch (Exception ignored) {}

        try {
            if (!envWritten) {
                synchronized (BaseTest.class) {
                    if (!envWritten) {
                        AllureEnvironmentWriter.crearEnvironmentProperties(driver);
                        envWritten = true;
                        System.out.println("[BaseTest] environment.properties creado.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[BaseTest] Error creando environment.properties: " + e.getMessage());
        }
    }

    @AfterEach
    public void tearDown(TestInfo testInfo) {
        String testKey = testInfo.getDisplayName();

        try {
            if (testInfo.getDisplayName() != null && !testInfo.getDisplayName().isBlank()) {
                if (!executedTests.contains(testInfo.getDisplayName())) {
                    executedTests.add(testInfo.getDisplayName());
                }
            }

            try {
                String path = TestSteps.captureEvidence(driver, "TEST_FINAL", "TEST_FINAL");
                if (path != null) {
                    TestSteps.getStepsInternal()
                            .add(new StepResult("Evidencia final (auto)", "OK", path));
                }
            } catch (Exception ignored) {}

            List<StepResult> results = TestSteps.finishScenario();
            PdfReportGenerator.generate(testInfo.getDisplayName(), results);

            totalTests++;

            boolean junitFailed = BaseTestStatusRegistry.isFailed(testKey);
            boolean stepsFailed = results.stream()
                    .anyMatch(r -> "FAIL".equalsIgnoreCase(r.getStatus())
                            || "ERROR".equalsIgnoreCase(r.getStatus()));

            boolean finalFailed = junitFailed || stepsFailed;

            if (finalFailed) {
                failedTests++;
                System.out.println("[BaseTest] TEST FAILED: " + testInfo.getDisplayName());

                // ✅ FIX: si el test falló y estás reusando driver, relanza app para que el siguiente test arranque bien
                if (REUSE_DRIVER) {
                    System.out.println("[BaseTest] (RECOVERY) Relanzando app por fallo del test...");
                    relaunchAppSafe();
                }

            } else {
                passedTests++;
                System.out.println("[BaseTest] TEST PASSED: " + testInfo.getDisplayName());
            }

            BaseTestStatusRegistry.clear(testKey);

        } catch (Exception e) {
            failedTests++;
            System.err.println("[BaseTest] Error en tearDown: " + e.getMessage());
            e.printStackTrace();

            // ✅ Si truena el teardown y REUSE_DRIVER, igual recupera para el siguiente test
            if (REUSE_DRIVER) {
                try {
                    System.out.println("[BaseTest] (RECOVERY) Relanzando app por excepción en tearDown...");
                    relaunchAppSafe();
                } catch (Exception ignored) {}
            }

        } finally {
            if (!REUSE_DRIVER) {
                try { DriverFactory.quitDriver(); } catch (Exception ignored) {}
                driver = null;
            }
        }
    }

    @AfterAll
    public void afterAllSuiteAndCloseDriverIfNeeded() {
        try {
            suiteEnd = System.currentTimeMillis();
            long duration = suiteEnd - suiteStart;

            String suiteName = System.getProperty("executionName");
            if (suiteName == null || suiteName.isBlank()) suiteName = System.getenv("EXECUTION_NAME");
            if (suiteName == null || suiteName.isBlank()) suiteName = "Cinépolis Alimentos";

            String executed = executedTests.isEmpty() ? "" : String.join(" | ", executedTests);

            String reportDir = "build/reportes-pdf";
            String mergedPdfName = "Reporte_" + sanitizeLocal(suiteName) + ".pdf";

            try {
                PdfSuiteMerger.mergeReports(reportDir, mergedPdfName);
            } catch (Exception e) {
                System.out.println("[BaseTest] (Info) No se pudo hacer merge de evidencias (ok): " + e.getMessage());
            }

            try {
                Path metricsFile = Paths.get(reportDir, "suite-metrics.properties");
                Files.createDirectories(metricsFile.getParent());

                Properties props = new Properties();
                props.setProperty("suiteName", suiteName);
                props.setProperty("totalTests", String.valueOf(totalTests));
                props.setProperty("passedTests", String.valueOf(passedTests));
                props.setProperty("failedTests", String.valueOf(failedTests));
                props.setProperty("durationMillis", String.valueOf(duration));
                props.setProperty("executedTests", executed);
                props.setProperty("mergedPdfName", mergedPdfName);

                try (OutputStream out = Files.newOutputStream(
                        metricsFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                )) {
                    props.store(out, "Metrics de ejecución de la suite");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        } finally {
            if (REUSE_DRIVER) {
                try { DriverFactory.quitDriver(); } catch (Exception ignored) {}
                driver = null;
            }
        }
    }

    private static String sanitizeLocal(String s) {
        if (s == null || s.isBlank()) return "reporte";
        return s.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
