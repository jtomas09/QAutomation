package base;

import config.DriverFactory;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.appium.java_client.ios.IOSStartScreenRecordingOptions;
import io.qameta.allure.Allure;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.interactions.Pause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pages.common.CinemasHelper;
import utils.*;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(PdfReportExtension.class)
public class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    protected AppiumDriver driver;
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

    private static final AtomicBoolean RUN_INIT_DONE         = new AtomicBoolean(false);
    private static final AtomicBoolean MEXICO_CINEMA_CHECKED = new AtomicBoolean(false);
    private static volatile String     lastAlimentosCinema   = null;

    @BeforeAll
    public static void beforeAllSuite() {
        if (RUN_INIT_DONE.compareAndSet(false, true)) {
            suiteStart = System.currentTimeMillis();

            totalTests = 0;
            passedTests = 0;
            failedTests = 0;
            executedTests.clear();
            executedStories.clear();

            envWritten = false;
            driverCreatedOnce = false;
            MEXICO_CINEMA_CHECKED.set(false);
            lastAlimentosCinema = null;

            try { clearDirectory(Paths.get("build", "reportes-pdf")); } catch (Exception ignored) {}
            try { clearDirectory(Paths.get("build", "reports", "allure-report")); } catch (Exception ignored) {}
            try { clearDirectory(Paths.get("build", "allure-results")); } catch (Exception ignored) {}
            try { clearDirectory(Paths.get("build", "evidencias")); } catch (Exception ignored) {}

            log.info("[BaseTest] Run initialized; output directories cleared.");
        } else {
            log.debug("[BaseTest] Run already initialized; skipping directory cleanup.");
        }
    }

    protected static void resetSuiteFlags() {
        MEXICO_CINEMA_CHECKED.set(false);
        lastAlimentosCinema = null;
        log.info("[BaseTest] Suite flags reset (country changed by non-Mexico class).");
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

    private String getAppPackageSafe() {
        boolean ios = DriverFactory.isIOS();
        // iOS uses bundleId; Android uses appPackage
        String sysProp = ios ? System.getProperty("bundleId") : System.getProperty("appPackage");
        try {
            if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        } catch (Exception ignored) {}

        try {
            if (driver != null && driver.getCapabilities() != null) {
                String capKey = ios ? "bundleId" : "appPackage";
                Object cap = driver.getCapabilities().getCapability(capKey);
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

            DriverFactory.terminateApp(driver, appPackage);
            DriverFactory.activateApp(driver, appPackage);

            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        } catch (Exception ignored) {}
    }

    private void ensureAppRunning() {
        try {
            if (driver == null) return;
            String appPackage = getAppPackageSafe();
            if (appPackage == null || appPackage.isBlank()) return;

            try {
                DriverFactory.activateApp(driver, appPackage);
            } catch (Exception e) {
                relaunchAppSafe();
            }
        } catch (Exception ignored) {}
    }

    private void quickSwipeUp(AppiumDriver driver) {
        Dimension size = driver.manage().window().getSize();
        int width  = size.getWidth();
        int height = size.getHeight();

        int x      = width / 2;
        int startY = (int) (height * 0.78);
        int endY   = (int) (height * 0.32);

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        swipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, startY));
        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        swipe.addAction(new Pause(finger, Duration.ofMillis(180)));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(300), PointerInput.Origin.viewport(), x, endY));
        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(swipe));

        try { Thread.sleep(60); } catch (InterruptedException ignored) {}
    }

    private boolean isAlimentosScreenVisible(AppiumDriver driver) {
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

    private void autoScrollOnAppOpen(AppiumDriver driver) {
        if (!AUTO_SCROLL_ON_OPEN || driver == null) return;

        try {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}

            if (!isAlimentosScreenVisible(driver)) {
                log.debug("[BaseTest] Auto-scroll skipped: Alimentos screen not detected.");
                return;
            }

            for (int i = 0; i < AUTO_SCROLL_SWIPES; i++) {
                quickSwipeUp(driver);
            }

            log.info("[BaseTest] Auto-scroll performed on Alimentos screen. swipes={}", AUTO_SCROLL_SWIPES);

        } catch (Exception e) {
            log.warn("[BaseTest] Auto-scroll failed: {}", e.getMessage());
        }
    }

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        if (!REUSE_DRIVER) {
            try {
                driver = DriverFactory.getDriver();
            } catch (Exception e) {
                log.error("[BaseTest] Driver creation FAILED — verifica que Appium esté corriendo y el dispositivo conectado. Causa: {}", e.getMessage(), e);
                throw e;
            }
            log.info("[BaseTest] Driver created: {}", driver);
            autoScrollOnAppOpen(driver);
            log.debug("[BaseTest] Invoking PromosGuard after auto-scroll...");
            new CinemasHelper(driver).dismissTransientPromosGuard("BaseTest@BeforeEach");
            log.debug("[BaseTest] PromosGuard finished.");
            ensureAppRunning();
            log.debug("[BaseTest] ensureAppRunning after PromosGuard (REUSE_DRIVER=false).");
            new CinemasHelper(driver).dismissTransientPromosGuard("BaseTest@BeforeEach:reactivate");
            log.debug("[BaseTest] Second PromosGuard finished.");

        } else {
            if (!driverCreatedOnce || driver == null) {
                try {
                    driver = DriverFactory.getDriver();
                } catch (Exception e) {
                    log.error("[BaseTest] Driver creation FAILED — verifica que Appium esté corriendo y el dispositivo conectado. Causa: {}", e.getMessage(), e);
                    throw e;
                }
                driverCreatedOnce = true;
                log.info("[BaseTest] Driver created (REUSE_DRIVER=true): {}", driver);
            } else {
                log.debug("[BaseTest] Driver reused (REUSE_DRIVER=true): {}", driver);
            }

            ensureAppRunning();

            autoScrollOnAppOpen(driver);
            log.debug("[BaseTest] Invoking PromosGuard after auto-scroll...");
            new CinemasHelper(driver).dismissTransientPromosGuard("BaseTest@BeforeEach");
            log.debug("[BaseTest] PromosGuard finished.");
        }

        // ── Escenario México: selecciona cine si no hay uno pre-seleccionado (solo 1 vez por suite)
        // Skipped for alimentos menus — they manage their own cinema via ensureCinemaSelectedFromAlimentos
        String simpleClass = getClass().getSimpleName();
        String testClass = testInfo.getTestClass().map(Class::getName).orElse("");
        if ((testClass.contains("México") || testClass.contains("Mexico"))
                && !MenuCinemaResolver.isAlimentosMenu(simpleClass)
                && MEXICO_CINEMA_CHECKED.compareAndSet(false, true)) {
            log.info("[BaseTest] Test México detectado -> verificando selección de cine...");
            try {
                new CinemasHelper(driver).ensureMexicoCinemaSelected();
            } catch (Exception e) {
                log.warn("[BaseTest] ensureMexicoCinemaSelected falló (no bloquea): {}", e.getMessage());
                try { driver.navigate().back(); Thread.sleep(800); } catch (Exception ignored) {}
            }
        }

        // ── Alimentos menus: auto-selecciona el cine correcto antes de CADA test
        if (MenuCinemaResolver.isAlimentosMenu(simpleClass)) {
            String targetCinema = null;
            // Priority 1: @Cinema annotation on the method (per-test override, e.g. MenuAtmosfera)
            try {
                final String[] annotCinema = {null};
                testInfo.getTestMethod().ifPresent(m -> {
                    Cinema c = m.getAnnotation(Cinema.class);
                    if (c != null && c.value() != null && !c.value().isBlank()) {
                        annotCinema[0] = c.value().trim();
                    }
                });
                targetCinema = annotCinema[0];
            } catch (Exception ignored) {}
            // Priority 2: class-level mapping (MenuCoffeTree, MenuMiCine, MenuVIP, MenuTradicional)
            if (targetCinema == null) {
                targetCinema = MenuCinemaResolver.resolve(simpleClass);
            }
            if (targetCinema != null && !targetCinema.equals(lastAlimentosCinema)) {
                log.info("[BaseTest] Alimentos cinema: {} → {}", lastAlimentosCinema, targetCinema);
                try {
                    new CinemasHelper(driver).ensureCinemaSelectedFromAlimentos(targetCinema);
                    lastAlimentosCinema = targetCinema;
                } catch (Exception e) {
                    log.warn("[BaseTest] ensureCinemaSelectedFromAlimentos({}) falló: {}", targetCinema, e.getMessage());
                    // Dismiss cinema selector overlay so it doesn't block test navigation
                    try { driver.navigate().back(); Thread.sleep(800); } catch (Exception ignored) {}
                }
            }
        }

        startVideoRecording();

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
                        log.info("[BaseTest] environment.properties created.");
                    }
                }
            }
        } catch (Exception e) {
            log.error("[BaseTest] Failed to create environment.properties: {}", e.getMessage());
        }
    }

    @AfterEach
    public void tearDown(TestInfo testInfo) {
        stopVideoRecording(testInfo);
        String testKey = testInfo.getDisplayName();

        try {
            if (testInfo.getDisplayName() != null && !testInfo.getDisplayName().isBlank()) {
                if (!executedTests.contains(testInfo.getDisplayName())) {
                    executedTests.add(testInfo.getDisplayName());
                }
            }

            totalTests++;

            // @AfterEach corre ANTES de TestWatcher.testFailed (orden JUnit 5).
            // Por eso isFailed() aún es false aunque el test haya fallado por assert.
            // El resultado real lo reporta JUnit → BaseTestStatusRegistry (vía testFailed/testSuccessful).
            // Aquí solo detectamos fallos de setup (driver no creado).
            boolean setupFailed = (driver == null && !REUSE_DRIVER)
                    || (REUSE_DRIVER && !driverCreatedOnce);

            if (setupFailed) {
                failedTests++;
                log.error("[BaseTest] TEST FAILED (setUp — driver no creado): {}", testInfo.getDisplayName());
                BaseTestStatusRegistry.markFailed(testKey, new RuntimeException("Driver not created"));
            } else {
                // No podemos saber el resultado aquí (TestWatcher aún no corrió).
                // El conteo definitivo lo hace PdfReportExtension.SuiteMailer desde BaseTestStatusRegistry.
                log.info("[BaseTest] TEST ENDING: {}", testInfo.getDisplayName());
            }

            if (REUSE_DRIVER) {
                try {
                    if (driver != null) {
                        String pkg = getAppPackageSafe();
                        if (pkg != null && !pkg.isBlank()) {
                            DriverFactory.terminateApp(driver, pkg);
                            log.info("[BaseTest] App terminada tras test; próximo setUp la relanzará.");
                        }
                    }
                } catch (Exception ignored) {}
            }

            BaseTestStatusRegistry.clear(testKey);

        } catch (Exception e) {
            failedTests++;
            log.error("[BaseTest] Error in tearDown: {}", e.getMessage(), e);

            if (REUSE_DRIVER) {
                try {
                    log.info("[BaseTest] Relaunching app after tearDown exception...");
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
            if (suiteName == null || suiteName.isBlank()) suiteName = "Cinépolis";

            String executed = executedTests.isEmpty() ? "" : String.join(" | ", executedTests);

            String reportDir = "build/reportes-pdf";
            String mergedPdfName = "Reporte_" + sanitizeLocal(suiteName) + ".pdf";

            try {
                PdfSuiteMerger.mergeReports(reportDir, mergedPdfName);
            } catch (Exception e) {
                log.info("[BaseTest] PDF merge skipped: {}", e.getMessage());
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
                    props.store(out, "Suite execution metrics");
                }

            } catch (Exception e) {
                log.error("[BaseTest] Failed to write suite-metrics.properties", e);
            }

        } finally {
            if (REUSE_DRIVER) {
                try { DriverFactory.quitDriver(); } catch (Exception ignored) {}
                driver = null;
            }
        }
    }

    // ── Video recording ────────────────────────────────────────────────────────

    private static boolean isVideoEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("video.enabled",
                System.getenv().getOrDefault("VIDEO_ENABLED", "false")));
    }

    private void startVideoRecording() {
        if (!isVideoEnabled() || driver == null) return;
        try {
            if (DriverFactory.isIOS()) {
                ((io.appium.java_client.ios.IOSDriver) driver).startRecordingScreen(
                    new IOSStartScreenRecordingOptions()
                        .withVideoQuality(IOSStartScreenRecordingOptions.VideoQuality.MEDIUM)
                        .withTimeLimit(Duration.ofMinutes(15))
                );
            } else {
                ((AndroidDriver) driver).startRecordingScreen(
                    new AndroidStartScreenRecordingOptions()
                        .withBitRate(2_000_000)
                        .withTimeLimit(Duration.ofMinutes(15))
                );
            }
            log.info("[Video] Grabacion iniciada");
        } catch (Exception e) {
            log.warn("[Video] No se pudo iniciar grabacion: {}", e.getMessage());
        }
    }

    private void stopVideoRecording(TestInfo testInfo) {
        if (!isVideoEnabled() || driver == null) return;
        try {
            String base64;
            if (DriverFactory.isIOS()) {
                base64 = ((io.appium.java_client.ios.IOSDriver) driver).stopRecordingScreen();
            } else {
                base64 = ((AndroidDriver) driver).stopRecordingScreen();
            }
            if (base64 == null || base64.isBlank()) return;

            byte[] videoBytes = Base64.getDecoder().decode(base64);
            String className  = getClass().getSimpleName();
            String testName   = testInfo.getDisplayName()
                    .replaceAll("[^a-zA-Z0-9_\\-]", "_");

            Path dir  = Paths.get("build", "videos", className);
            Files.createDirectories(dir);
            Path file = dir.resolve(testName + ".mp4");
            Files.write(file, videoBytes);
            log.info("[Video] Guardado ({} KB): {}", videoBytes.length / 1024, file.toAbsolutePath());

            Allure.addAttachment(
                "Video — " + testInfo.getDisplayName(),
                "video/mp4",
                new ByteArrayInputStream(videoBytes),
                ".mp4"
            );
        } catch (Exception e) {
            log.warn("[Video] No se pudo guardar grabacion: {}", e.getMessage());
        }
    }

    private static String sanitizeLocal(String s) {
        if (s == null || s.isBlank()) return "reporte";
        return s.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
