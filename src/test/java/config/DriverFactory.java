package config;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;

/**
 * Cross-platform AppiumDriver factory (Android + iOS).
 *
 * Features:
 *  - Pre-flight validation: Appium server, ADB device (Android) / udid (iOS)
 *  - Automatic retry (up to MAX_RETRIES attempts)
 *  - Supports: local USB, BrowserStack, Sauce Labs
 *  - Set platformName=Android or platformName=iOS in appium.properties
 */
public class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

    private static final int  MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5_000L;

    private static volatile AppiumDriver driver;
    private static volatile Properties   props;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the current AppiumDriver (Android or iOS), creating one if needed. Thread-safe. */
    public static AppiumDriver getDriver() {
        if ("true".equals(System.getProperty("cinepolis.abort.requested")))
            throw new RuntimeException("Ejecucion abortada por el usuario");

        if (driver != null && !isSessionAlive(driver)) {
            synchronized (DriverFactory.class) {
                if (driver != null && !isSessionAlive(driver)) {
                    log.warn("[DriverFactory] Stale driver session detected — recreating.");
                    driver = null;
                }
            }
        }

        if (driver == null) {
            synchronized (DriverFactory.class) {
                if (driver == null) {
                    driver = createDriverWithRetries();
                }
            }
        }
        return driver;
    }

    /** Closes the current Appium session and clears the singleton. */
    public static void quitDriver() {
        AppiumDriver d = driver;
        if (d != null) {
            try { d.quit(); }
            catch (Exception e) { log.warn("[DriverFactory] quit() error: {}", e.getMessage()); }
            driver = null;
            log.info("[DriverFactory] AppiumDriver session closed.");
        }
    }

    /** Terminates and relaunches the app under test. */
    public static void relaunchApp() {
        if (driver == null) return;
        String appId = isIOS() ? prop("bundleId", "") : prop("appPackage", "");
        if (appId.isBlank()) return;
        terminateApp(driver, appId);
        activateApp(driver, appId);
    }

    /** Brings the app to the foreground; relaunches if activation fails. */
    public static void ensureAppRunning() {
        if (driver == null) return;
        String appId = isIOS() ? prop("bundleId", "") : prop("appPackage", "");
        if (appId.isBlank()) return;
        try {
            activateApp(driver, appId);
        } catch (Exception e) {
            log.warn("[DriverFactory] activateApp failed, attempting relaunch: {}", e.getMessage());
            relaunchApp();
        }
    }

    /** Returns true if the current platform is iOS. */
    public static boolean isIOS() {
        return "iOS".equalsIgnoreCase(prop("platformName", "Android"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Platform-aware app control helpers (AppiumDriver base lacks these methods)
    // ──────────────────────────────────────────────────────────────────────────

    public static void terminateApp(AppiumDriver d, String appId) {
        if (d == null || appId == null || appId.isBlank()) return;
        try {
            if (isIOS()) ((IOSDriver) d).terminateApp(appId);
            else         ((AndroidDriver) d).terminateApp(appId);
        } catch (Exception ignored) {}
    }

    public static void activateApp(AppiumDriver d, String appId) {
        if (d == null || appId == null || appId.isBlank()) return;
        try {
            if (isIOS()) ((IOSDriver) d).activateApp(appId);
            else         ((AndroidDriver) d).activateApp(appId);
        } catch (Exception ignored) {}
    }

    public static void hideKeyboard(AppiumDriver d) {
        if (d == null) return;
        try { d.executeScript("mobile: hideKeyboard"); } catch (Exception ignored) {}
    }

    public static void setClipboardText(AppiumDriver d, String text) {
        if (d == null || text == null) return;
        try {
            if (!isIOS()) ((AndroidDriver) d).setClipboardText(text);
        } catch (Exception ignored) {}
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Driver creation — retry loop
    // ──────────────────────────────────────────────────────────────────────────

    private static AppiumDriver createDriverWithRetries() {
        initProps();
        String mode     = prop("appium.mode",   "local");
        String platform = prop("platformName",  "Android");

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.info("");
            log.info("[DriverFactory] ══════════════════════════════════════════");
            log.info("[DriverFactory]  Intento {}/{} — mode={} platform={}", attempt, MAX_RETRIES, mode, platform);
            log.info("[DriverFactory] ══════════════════════════════════════════");

            try {
                AppiumDriver d = attemptCreate(mode, platform);
                if (attempt > 1)
                    log.info("[DriverFactory] Driver creado en intento {}.", attempt);
                return d;
            } catch (Exception e) {
                lastException = e;
                log.error("[DriverFactory] Intento {} FALLIDO: {}", attempt, e.getMessage());
                log.error("[DriverFactory] Causa raiz: {}", rootCause(e).getMessage());
                e.printStackTrace();

                diagnose(e, platform);

                if (attempt < MAX_RETRIES) {
                    log.info("[DriverFactory] Esperando {} ms antes de reintentar...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            }
        }

        String summary = String.format(
            "[DriverFactory] Failed to create AppiumDriver after %d attempts.%n" +
            "  platform   = %s%n" +
            "  mode       = %s%n" +
            "  deviceName = %s%n" +
            "  udid       = %s%n" +
            "  hub        = %s%n" +
            "  rootCause  = %s",
            MAX_RETRIES,
            platform,
            mode,
            prop("deviceName",  "?"),
            prop("udid",        "?"),
            prop("appium.hub",  "?"),
            rootCause(lastException).getMessage()
        );
        log.error(summary);
        throw new RuntimeException(summary, lastException);
    }

    private static AppiumDriver attemptCreate(String mode, String platform) throws Exception {
        boolean ios = "iOS".equalsIgnoreCase(platform);

        if (ios) {
            XCUITestOptions options = new XCUITestOptions();
            URL hub;
            switch (mode) {
                case "browserstack" -> hub = buildBrowserStackIOS(options);
                case "saucelabs"    -> hub = buildSauceLabsIOS(options);
                default             -> hub = buildLocalIOS(options);
            }
            log.info("[DriverFactory] Hub URL : {}", hub);
            log.info("[DriverFactory] Capabilities:\n{}", options.toJson());
            IOSDriver d = new IOSDriver(hub, options);
            d.manage().timeouts().implicitlyWait(Duration.ZERO);
            log.info("[DriverFactory] IOSDriver OK — sessionId={}", d.getSessionId());
            return d;
        } else {
            UiAutomator2Options options = new UiAutomator2Options();
            URL hub;
            switch (mode) {
                case "browserstack" -> hub = buildBrowserStack(options);
                case "saucelabs"    -> hub = buildSauceLabs(options);
                default             -> hub = buildLocal(options);
            }
            log.info("[DriverFactory] Hub URL : {}", hub);
            log.info("[DriverFactory] Capabilities:\n{}", options.toJson());
            AndroidDriver d = new AndroidDriver(hub, options);
            d.manage().timeouts().implicitlyWait(Duration.ZERO);
            log.info("[DriverFactory] AndroidDriver OK — sessionId={}", d.getSessionId());
            return d;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOCAL ANDROID
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildLocal(UiAutomator2Options o) throws Exception {
        String hubUrl  = prop("appium.hub",      "http://127.0.0.1:4723/wd/hub");
        String udid    = prop("udid",            "");
        String pkg     = prop("appPackage",      "");
        String act     = prop("appActivity",     "");
        String apkPath = prop("apkPath",         "");

        if ((act.isBlank() || "auto".equalsIgnoreCase(act)) && !udid.isBlank() && !pkg.isBlank()) {
            act = resolveAppActivity(udid, pkg);
        }

        validateAppiumServer(hubUrl);
        if (!udid.isBlank()) {
            validateAdbDevice(udid);
            if (!pkg.isBlank()) validatePackageInstalled(udid, pkg);
            cleanupUiAutomator2Session(udid, Integer.parseInt(prop("systemPort", "8200")));
        }

        o.setPlatformName("Android");
        o.setDeviceName(prop("deviceName", "Android Device"));

        String platformVersion = prop("platformVersion", "");
        if (!platformVersion.isBlank()) o.setPlatformVersion(platformVersion);
        if (!udid.isBlank())            o.setUdid(udid);

        if (!apkPath.isBlank()) { o.setApp(apkPath); o.setFullReset(false); }
        if (!pkg.isBlank()) o.setAppPackage(pkg);
        if (!act.isBlank()) o.setAppActivity(act);

        o.setAutomationName(prop("automationName", "UiAutomator2"));
        o.setNoReset(Boolean.parseBoolean(prop("noReset", "true")));
        o.setAutoGrantPermissions(true);

        long cmdTimeout = Long.parseLong(prop("newCommandTimeout", "300"));
        o.setNewCommandTimeout(Duration.ofSeconds(cmdTimeout));

        long uia2Launch  = Long.parseLong(prop("uia2LaunchTimeout",  "120"));
        long uia2Install = Long.parseLong(prop("uia2InstallTimeout", "120"));
        int  adbExec     = Integer.parseInt(prop("adbExecTimeout",   "90000"));
        int  apkInstall  = Integer.parseInt(prop("androidInstallTimeout", "90000"));

        o.setUiautomator2ServerLaunchTimeout(Duration.ofSeconds(uia2Launch));
        o.setUiautomator2ServerInstallTimeout(Duration.ofSeconds(uia2Install));
        o.setCapability("adbExecTimeout",        adbExec);
        o.setCapability("androidInstallTimeout", apkInstall);

        o.setCapability("disableWindowAnimation",   true);
        o.setCapability("ignoreUnimportantViews",   true);
        o.setCapability("forceAppLaunch",           true);
        o.setCapability("skipServerInstallation",   false);
        o.setCapability("skipDeviceInitialization", false);
        o.setCapability("ensureWebviewsHavePages",  false);
        o.setCapability("nativeWebScreenshot",      true);

        int systemPort = Integer.parseInt(prop("systemPort", "8200"));
        o.setCapability("systemPort", systemPort);

        String envHub = System.getenv("APPIUM_SERVER_URL");
        String finalHub = (envHub != null && !envHub.isBlank()) ? envHub : hubUrl;

        log.info("[DriverFactory] local Android → device={} udid={} pkg={} activity={} hub={}",
            prop("deviceName","?"), udid, pkg, act, finalHub);

        return URI.create(finalHub).toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOCAL iOS
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildLocalIOS(XCUITestOptions o) throws Exception {
        String hubUrl   = prop("appium.hub",     "http://127.0.0.1:4723/wd/hub");
        String udid     = prop("udid",           "");
        String bundleId = prop("bundleId",       "");
        String ipaPath  = prop("ipaPath",        "");

        validateAppiumServer(hubUrl);

        o.setPlatformName("iOS");
        o.setDeviceName(prop("deviceName", "iPhone"));

        String platformVersion = prop("platformVersion", "");
        if (!platformVersion.isBlank()) o.setPlatformVersion(platformVersion);
        if (!udid.isBlank())     o.setUdid(udid);
        if (!bundleId.isBlank()) o.setBundleId(bundleId);
        if (!ipaPath.isBlank())  o.setApp(ipaPath);

        o.setAutomationName("XCUITest");
        o.setNoReset(Boolean.parseBoolean(prop("noReset", "true")));

        long cmdTimeout = Long.parseLong(prop("newCommandTimeout", "300"));
        o.setNewCommandTimeout(Duration.ofSeconds(cmdTimeout));

        o.setCapability("autoAcceptAlerts",   true);
        o.setCapability("nativeWebScreenshot", true);

        String envHub = System.getenv("APPIUM_SERVER_URL");
        String finalHub = (envHub != null && !envHub.isBlank()) ? envHub : hubUrl;

        log.info("[DriverFactory] local iOS → device={} udid={} bundleId={} hub={}",
            prop("deviceName","?"), udid, bundleId, finalHub);

        return URI.create(finalHub).toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pre-flight validations
    // ──────────────────────────────────────────────────────────────────────────

    private static void validateAppiumServer(String hubUrl) {
        String base = hubUrl.replaceAll("/wd/hub$", "");
        String[] paths = {"/status", "/wd/hub/status"};

        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        for (String path : paths) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + path))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
                int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code == 200) {
                    log.info("[Preflight] Appium server OK: {}", base);
                    return;
                }
            } catch (Exception ignored) {}
        }

        throw new IllegalStateException(
            "[Preflight] Appium server NOT reachable at: " + base + "\n" +
            "  Solucion: appium --port 4723\n" +
            "  Verifica drivers instalados:\n" +
            "    Android: appium driver install uiautomator2\n" +
            "    iOS:     appium driver install xcuitest"
        );
    }

    private static void validateAdbDevice(String udid) {
        try {
            Process p = new ProcessBuilder("adb", "-s", udid, "get-state")
                .redirectErrorStream(true).start();
            String out  = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int    exit = p.waitFor();

            if ("device".equalsIgnoreCase(out)) {
                log.info("[Preflight] ADB device OK: {} (authorized)", udid);
                return;
            }
            if ("unauthorized".equalsIgnoreCase(out)) {
                throw new IllegalStateException(
                    "[Preflight] Device " + udid + " UNAUTHORIZED.\n" +
                    "  Solucion: Acepta el dialogo 'Permitir depuracion USB' en el dispositivo."
                );
            }
            if ("offline".equalsIgnoreCase(out)) {
                throw new IllegalStateException(
                    "[Preflight] Device " + udid + " is OFFLINE. Desconecta y vuelve a conectar.");
            }
            throw new IllegalStateException(
                "[Preflight] Device " + udid + " NOT found (exit=" + exit + ", state='" + out + "').");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Preflight] adb check skipped ({}) — verifica que adb este en el PATH.", e.getMessage());
        }
    }

    private static void validatePackageInstalled(String udid, String appPackage) {
        try {
            Process p = new ProcessBuilder(
                "adb", "-s", udid, "shell", "pm", "list", "packages", appPackage)
                .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            if (out.contains("package:" + appPackage)) {
                log.info("[Preflight] Package OK: {} instalado en {}.", appPackage, udid);
            } else {
                log.warn("[Preflight] Package {} NO encontrado en {}.", appPackage, udid);
            }
        } catch (Exception e) {
            log.warn("[Preflight] Package check skipped: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BROWSERSTACK — Android
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildBrowserStack(UiAutomator2Options o) throws Exception {
        String user   = prop("browserstack.user",       "");
        String key    = prop("browserstack.key",        "");
        String device = prop("browserstack.device",     "Samsung Galaxy A55");
        String osVer  = prop("browserstack.os.version", "14.0");
        String appId  = prop("browserstack.app.id",     "");
        String pkg    = prop("appPackage",  "");
        String act    = prop("appActivity", "");

        if (user.isBlank() || key.isBlank())
            throw new IllegalStateException("[BrowserStack] Configura browserstack.user y browserstack.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException("[BrowserStack] Configura browserstack.app.id con el ID del APK subido");

        o.setCapability("bstack:options", Map.of(
            "userName",    user,
            "accessKey",   key,
            "deviceName",  device,
            "osVersion",   osVer,
            "projectName", "Cinepolis Automation",
            "buildName",   "Build-" + LocalDate.now(),
            "debug",       "true"
        ));
        o.setApp(appId);
        if (!pkg.isBlank()) o.setAppPackage(pkg);
        if (!act.isBlank()) o.setAppActivity(act);
        o.setAutomationName("UiAutomator2");
        o.setNoReset(true);
        o.setAutoGrantPermissions(true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(prop("newCommandTimeout", "300"))));

        log.info("[DriverFactory] BrowserStack Android → device={} osVersion={}", device, osVer);
        return URI.create("https://hub-cloud.browserstack.com/wd/hub").toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BROWSERSTACK — iOS
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildBrowserStackIOS(XCUITestOptions o) throws Exception {
        String user     = prop("browserstack.user",       "");
        String key      = prop("browserstack.key",        "");
        String device   = prop("browserstack.ios.device", "iPhone 15");
        String osVer    = prop("browserstack.ios.version","17");
        String appId    = prop("browserstack.app.id",     "");
        String bundleId = prop("bundleId",                "");

        if (user.isBlank() || key.isBlank())
            throw new IllegalStateException("[BrowserStack] Configura browserstack.user y browserstack.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException("[BrowserStack] Configura browserstack.app.id con el ID del IPA subido");

        o.setCapability("bstack:options", Map.of(
            "userName",    user,
            "accessKey",   key,
            "deviceName",  device,
            "osVersion",   osVer,
            "projectName", "Cinepolis Automation iOS",
            "buildName",   "Build-iOS-" + LocalDate.now(),
            "debug",       "true"
        ));
        o.setApp(appId);
        if (!bundleId.isBlank()) o.setBundleId(bundleId);
        o.setAutomationName("XCUITest");
        o.setNoReset(true);
        o.setCapability("autoAcceptAlerts", true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(prop("newCommandTimeout", "300"))));

        log.info("[DriverFactory] BrowserStack iOS → device={} osVersion={}", device, osVer);
        return URI.create("https://hub-cloud.browserstack.com/wd/hub").toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SAUCE LABS — Android
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildSauceLabs(UiAutomator2Options o) throws Exception {
        String user   = prop("saucelabs.user",       "");
        String key    = prop("saucelabs.key",        "");
        String region = prop("saucelabs.region",     "us-west-1");
        String device = prop("saucelabs.device",     "Samsung Galaxy A54 5G");
        String osVer  = prop("saucelabs.os.version", "13");
        String appId  = prop("saucelabs.app.id",     "");
        String pkg    = prop("appPackage",  "");
        String act    = prop("appActivity", "");

        if (user.isBlank() || key.isBlank())
            throw new IllegalStateException("[SauceLabs] Configura saucelabs.user y saucelabs.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException("[SauceLabs] Configura saucelabs.app.id con el ID del APK subido");

        o.setCapability("sauce:options", Map.of(
            "username",        user,
            "accessKey",       key,
            "deviceName",      device,
            "platformVersion", osVer,
            "name",            "Cinepolis-Android-" + LocalDate.now()
        ));
        o.setApp(appId);
        if (!pkg.isBlank()) o.setAppPackage(pkg);
        if (!act.isBlank()) o.setAppActivity(act);
        o.setAutomationName("UiAutomator2");
        o.setNoReset(true);
        o.setAutoGrantPermissions(true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(prop("newCommandTimeout", "300"))));

        log.info("[DriverFactory] SauceLabs Android → device={} region={}", device, region);
        return URI.create("https://ondemand." + region + ".saucelabs.com/wd/hub").toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SAUCE LABS — iOS
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildSauceLabsIOS(XCUITestOptions o) throws Exception {
        String user     = prop("saucelabs.user",            "");
        String key      = prop("saucelabs.key",             "");
        String region   = prop("saucelabs.region",          "us-west-1");
        String device   = prop("saucelabs.ios.device",      "iPhone 15 Simulator");
        String osVer    = prop("saucelabs.ios.version",     "17.0");
        String appId    = prop("saucelabs.app.id",          "");
        String bundleId = prop("bundleId",                  "");

        if (user.isBlank() || key.isBlank())
            throw new IllegalStateException("[SauceLabs] Configura saucelabs.user y saucelabs.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException("[SauceLabs] Configura saucelabs.app.id con el ID del IPA subido");

        o.setCapability("sauce:options", Map.of(
            "username",        user,
            "accessKey",       key,
            "deviceName",      device,
            "platformVersion", osVer,
            "name",            "Cinepolis-iOS-" + LocalDate.now()
        ));
        o.setApp(appId);
        if (!bundleId.isBlank()) o.setBundleId(bundleId);
        o.setAutomationName("XCUITest");
        o.setNoReset(true);
        o.setCapability("autoAcceptAlerts", true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(prop("newCommandTimeout", "300"))));

        log.info("[DriverFactory] SauceLabs iOS → device={} region={}", device, region);
        return URI.create("https://ondemand." + region + ".saucelabs.com/wd/hub").toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Diagnostics
    // ──────────────────────────────────────────────────────────────────────────

    private static void diagnose(Exception e, String platform) {
        String msg  = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String root = rootCause(e).getMessage() != null ? rootCause(e).getMessage().toLowerCase() : "";

        if ("iOS".equalsIgnoreCase(platform)) {
            if (msg.contains("xcuitest") && (msg.contains("not found") || msg.contains("not installed"))) {
                log.error("[Diagnose] XCUITest driver NOT installed in Appium.");
                log.error("  Solucion: appium driver install xcuitest");
            } else if (msg.contains("connection refused") || root.contains("connection refused")) {
                log.error("[Diagnose] Appium server NO esta corriendo. Solucion: appium --port 4723");
            } else if (msg.contains("xcode") || msg.contains("instruments")) {
                log.error("[Diagnose] Error de Xcode/Instruments. Verifica que Xcode Command Line Tools esten instalados.");
            }
        } else {
            if (msg.contains("uiautomator2") && (msg.contains("not found") || msg.contains("not installed"))) {
                log.error("[Diagnose] UiAutomator2 driver NOT installed. Solucion: appium driver install uiautomator2");
            } else if (msg.contains("could not start activity") || root.contains("could not start activity")) {
                log.error("[Diagnose] Appium no pudo iniciar la actividad. Verifica appActivity en appium.properties.");
            } else if (msg.contains("connection refused") || root.contains("connection refused")) {
                log.error("[Diagnose] Appium server NO esta corriendo. Solucion: appium --port 4723");
            } else if (msg.contains("device") && msg.contains("not found")) {
                log.error("[Diagnose] Dispositivo no encontrado. udid={}", prop("udid", "?"));
            } else if (msg.contains("timeout") || root.contains("timeout")) {
                log.error("[Diagnose] Timeout. Considera aumentar uia2LaunchTimeout en appium.properties.");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auto-detect launcher activity (Android only)
    // ──────────────────────────────────────────────────────────────────────────

    private static String resolveAppActivity(String udid, String pkg) {
        try {
            String[] cmd = {"adb", "-s", udid, "shell", "cmd", "package",
                            "resolve-activity", "--brief",
                            "-a", "android.intent.action.MAIN",
                            "-c", "android.intent.category.LAUNCHER", pkg};
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();

            for (String line : out.split("\\n")) {
                line = line.trim();
                if (line.startsWith(pkg + "/")) {
                    String activity = line.substring(line.indexOf('/') + 1);
                    if (!activity.isBlank()) {
                        log.info("[DriverFactory] appActivity auto-detectada via ADB: {}", activity);
                        return activity;
                    }
                }
            }
            log.warn("[DriverFactory] No se pudo auto-detectar appActivity. Output ADB: {}", out);
        } catch (Exception e) {
            log.warn("[DriverFactory] Error en resolveAppActivity: {}", e.getMessage());
        }
        return "";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────────────

    private static boolean isSessionAlive(AppiumDriver d) {
        try {
            if (d == null || d.getSessionId() == null) return false;
            d.manage().window().getSize();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Properties loading
    // ──────────────────────────────────────────────────────────────────────────

    private static void initProps() {
        if (props == null) {
            synchronized (DriverFactory.class) {
                if (props == null) {
                    try {
                        props = loadProps();
                    } catch (Exception e) {
                        log.warn("[DriverFactory] appium.properties no encontrado: {}. Usando valores por defecto.", e.getMessage());
                        props = new Properties();
                    }
                }
            }
        }
    }

    private static Properties loadProps() throws IOException {
        Properties p = new Properties();

        File local = new File("appium.properties");
        if (local.exists()) {
            try (FileInputStream fis = new FileInputStream(local)) {
                p.load(fis);
                log.info("[DriverFactory] appium.properties cargado de: {}", local.getAbsolutePath());
                return p;
            }
        }

        File project = new File("src/test/resources/appium.properties");
        if (project.exists()) {
            try (FileInputStream fis = new FileInputStream(project)) {
                p.load(fis);
                log.info("[DriverFactory] appium.properties cargado de: {}", project.getAbsolutePath());
                return p;
            }
        }

        try (InputStream is = DriverFactory.class.getClassLoader()
                .getResourceAsStream("appium.properties")) {
            if (is != null) {
                p.load(is);
                log.info("[DriverFactory] appium.properties cargado desde classpath.");
                return p;
            }
        }

        throw new IOException(
            "appium.properties no encontrado en ninguna ubicacion:\n" +
            "  1. Junto al JAR:             " + local.getAbsolutePath()   + "\n" +
            "  2. Recursos del proyecto:    " + project.getAbsolutePath() + "\n" +
            "  3. Classpath (JAR embebido)"
        );
    }

    /**
     * Elimina sesiones huérfanas de UiAutomator2 antes de crear una nueva.
     * Resuelve el error "systemPort X busy" (ej: 8200) en ejecuciones con REUSE_DRIVER=false.
     * Mata tanto el servidor como el proceso de test para liberar recursos y el puerto TCP.
     */
    private static void cleanupUiAutomator2Session(String udid, int systemPort) {
        if (udid == null || udid.isBlank()) return;
        log.info("[DriverFactory] Limpiando sesión UiAutomator2 (udid={}, port={})...", udid, systemPort);

        String[][] killCmds = {
            {"adb", "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server"},
            {"adb", "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server.test"},
        };
        for (String[] cmd : killCmds) {
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }

        // Liberar el systemPort si está ocupado (fuser -k es silencioso si no está en uso)
        try {
            String[] fuserKill = {"adb", "-s", udid, "shell", "fuser", "-k", systemPort + "/tcp"};
            Process p = new ProcessBuilder(fuserKill).redirectErrorStream(true).start();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        log.info("[DriverFactory] Limpieza UiAutomator2 completada.");
        sleep(500L);
    }

    /**
     * Resolves a property value.
     * Priority: JVM system property (-D) > appium.properties file > default.
     */
    static String prop(String key, String def) {
        initProps();
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys.trim();
        if (props == null) return def;
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
