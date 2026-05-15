package config;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;

public class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

    private static volatile AndroidDriver driver;
    private static Properties props;

    public static AndroidDriver getDriver() {
        if ("true".equals(System.getProperty("cinepolis.abort.requested")))
            throw new RuntimeException("Ejecución abortada por el usuario");

        if (driver != null && !isSessionAlive(driver)) {
            synchronized (DriverFactory.class) {
                if (driver != null && !isSessionAlive(driver)) {
                    log.warn("[DriverFactory] Stale driver session detected, creating new one.");
                    driver = null;
                }
            }
        }
        if (driver == null) {
            synchronized (DriverFactory.class) {
                if (driver == null) {
                    driver = createDriver();
                }
            }
        }
        return driver;
    }

    private static boolean isSessionAlive(AndroidDriver d) {
        try {
            if (d == null || d.getSessionId() == null) return false;
            // Real server round-trip: if the session is stale, this throws
            d.manage().window().getSize();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static AndroidDriver createDriver() {
        try {
            props = loadProps("src/test/resources/appium.properties");
            String mode = System.getProperty("appium.mode", get("appium.mode", "local"));

            UiAutomator2Options options = new UiAutomator2Options();
            URL hub;

            switch (mode) {
                case "browserstack" -> hub = buildBrowserStack(options);
                case "saucelabs"    -> hub = buildSauceLabs(options);
                default             -> hub = buildLocal(options);
            }

            AndroidDriver d = new AndroidDriver(hub, options);
            d.manage().timeouts().implicitlyWait(Duration.ZERO);
            log.info("[DriverFactory] AndroidDriver created. mode={}", mode);
            return d;

        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            log.error("[DriverFactory] CAUSA RAÍZ: {}", root.getMessage());
            log.error("[DriverFactory] mode={} deviceName={} udid={} appPackage={} hub={}",
                    System.getProperty("appium.mode",  props != null ? props.getProperty("appium.mode", "local") : "local"),
                    System.getProperty("deviceName",   props != null ? props.getProperty("deviceName",  "?")     : "?"),
                    System.getProperty("udid",         props != null ? props.getProperty("udid",        "?")     : "?"),
                    props != null ? props.getProperty("appPackage", "?") : "?",
                    System.getProperty("appium.hub",   props != null ? props.getProperty("appium.hub",  "?")     : "?"));
            throw new RuntimeException("Failed to create AndroidDriver", e);
        }
    }

    // ── LOCAL ─────────────────────────────────────────────────────────────────
    private static URL buildLocal(UiAutomator2Options o) throws Exception {
        o.setPlatformName(get("platformName", "Android"));

        String deviceName = System.getProperty("deviceName", get("deviceName", "Android Device"));
        o.setDeviceName(deviceName);

        String platformVersion = System.getProperty("platformVersion", get("platformVersion", ""));
        if (!platformVersion.isBlank()) o.setPlatformVersion(platformVersion);

        String udid = System.getProperty("udid", get("udid", ""));
        if (!udid.isBlank()) o.setUdid(udid);

        String appPackage  = get("appPackage",  "");
        String appActivity = get("appActivity", "");
        String apkPath     = get("apkPath",     "");

        if (!apkPath.isBlank())     { o.setApp(apkPath); o.setFullReset(false); }
        if (!appPackage.isBlank())  o.setAppPackage(appPackage);
        if (!appActivity.isBlank()) o.setAppActivity(appActivity);

        o.setAutomationName(get("automationName", "UiAutomator2"));
        o.setNoReset(Boolean.parseBoolean(get("noReset", "true")));
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(get("newCommandTimeout", "100"))));
        o.setAutoGrantPermissions(Boolean.parseBoolean(get("autoGrantPermissions", "true")));
        o.setUiautomator2ServerInstallTimeout(Duration.ofSeconds(Long.parseLong(get("uia2InstallTimeout", "40"))));
        o.setCapability("disableWindowAnimation", true);
        o.setCapability("ignoreUnimportantViews", true);
        o.setCapability("forceAppLaunch",         true);
        o.setCapability("enforceXPath1",          true);

        // AWS Device Farm inyecta APPIUM_SERVER_URL automáticamente
        String envHub = System.getenv("APPIUM_SERVER_URL");
        return URI.create(
                envHub != null && !envHub.isBlank() ? envHub :
                System.getProperty("appium.hub", get("appium.hub", "http://127.0.0.1:4723/wd/hub"))
        ).toURL();
    }

    // ── BROWSERSTACK ──────────────────────────────────────────────────────────
    private static URL buildBrowserStack(UiAutomator2Options o) throws Exception {
        String user    = get("browserstack.user",       "");
        String key     = get("browserstack.key",        "");
        String device  = get("browserstack.device",     "Samsung Galaxy A55");
        String osVer   = get("browserstack.os.version", "14.0");
        String appId   = get("browserstack.app.id",     "");
        String appPkg  = get("appPackage",  "");
        String appAct  = get("appActivity", "");

        if (user.isBlank() || key.isBlank())
            throw new IllegalStateException(
                "[BrowserStack] Completa browserstack.user y browserstack.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException(
                "[BrowserStack] Sube el APK en browserstack.com/app-automate y pon el app_url en browserstack.app.id");

        o.setCapability("bstack:options", Map.of(
            "userName",    user,
            "accessKey",   key,
            "deviceName",  device,
            "osVersion",   osVer,
            "projectName", "Cinepolis Automation",
            "buildName",   "Build " + LocalDate.now(),
            "debug",       "true"
        ));
        o.setApp(appId);
        if (!appPkg.isBlank())  o.setAppPackage(appPkg);
        if (!appAct.isBlank())  o.setAppActivity(appAct);
        o.setAutomationName("UiAutomator2");
        o.setNoReset(true);
        o.setAutoGrantPermissions(true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(get("newCommandTimeout", "180"))));

        log.info("[DriverFactory] BrowserStack → device={} osVersion={}", device, osVer);
        return URI.create("https://hub-cloud.browserstack.com/wd/hub").toURL();
    }

    // ── SAUCE LABS ────────────────────────────────────────────────────────────
    private static URL buildSauceLabs(UiAutomator2Options o) throws Exception {
        String user    = get("saucelabs.user",       "");
        String key     = get("saucelabs.key",        "");
        String region  = get("saucelabs.region",     "us-west-1");
        String device  = get("saucelabs.device",     "Samsung Galaxy A54 5G");
        String osVer   = get("saucelabs.os.version", "13");
        String appId   = get("saucelabs.app.id",     "");
        String appPkg  = get("appPackage",  "");
        String appAct  = get("appActivity", "");

        if (user.isBlank() || key.isBlank())
            throw new IllegalStateException(
                "[SauceLabs] Completa saucelabs.user y saucelabs.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException(
                "[SauceLabs] Sube el APK en app.saucelabs.com y pon el storage ID en saucelabs.app.id");

        o.setCapability("sauce:options", Map.of(
            "username",        user,
            "accessKey",       key,
            "deviceName",      device,
            "platformVersion", osVer,
            "name",            "Cinepolis Automation - " + LocalDate.now()
        ));
        o.setApp(appId);
        if (!appPkg.isBlank())  o.setAppPackage(appPkg);
        if (!appAct.isBlank())  o.setAppActivity(appAct);
        o.setAutomationName("UiAutomator2");
        o.setNoReset(true);
        o.setAutoGrantPermissions(true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(get("newCommandTimeout", "180"))));

        log.info("[DriverFactory] SauceLabs → device={} region={}", device, region);
        return URI.create("https://ondemand." + region + ".saucelabs.com/wd/hub").toURL();
    }

    /**
     * Terminates and relaunches the app under test.
     */
    public static void relaunchApp() {
        if (driver == null) return;

        String appPackage = get("appPackage", "");
        if (appPackage.isBlank()) return;

        try {
            driver.terminateApp(appPackage);
        } catch (Exception ignored) {}

        try {
            driver.activateApp(appPackage);
        } catch (Exception ignored) {}
    }

    /**
     * Ensures the app under test is in the foreground, relaunching it if necessary.
     */
    public static void ensureAppRunning() {
        if (driver == null) return;

        String appPackage = get("appPackage", "");
        if (appPackage.isBlank()) return;

        try {
            driver.activateApp(appPackage);
        } catch (Exception e) {
            relaunchApp();
        }
    }

    public static void quitDriver() {
        AndroidDriver d = driver;
        if (d != null) {
            d.quit();
            driver = null;
            log.info("[DriverFactory] AndroidDriver session closed.");
        }
    }

    private static Properties loadProps(String path) throws IOException {
        Properties p = new Properties();

        // 1. Current directory — used when running as a JAR with appium.properties alongside it.
        File localFile = new File("appium.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                p.load(fis);
                log.info("[DriverFactory] appium.properties loaded from: {}", localFile.getAbsolutePath());
                return p;
            }
        }

        // 2. Project path — used when running from IDE or Gradle.
        File projectFile = new File(path);
        if (projectFile.exists()) {
            try (FileInputStream fis = new FileInputStream(projectFile)) {
                p.load(fis);
                log.info("[DriverFactory] appium.properties loaded from: {}", projectFile.getAbsolutePath());
                return p;
            }
        }

        // 3. Classpath — used when the file is embedded in the JAR.
        try (InputStream is = DriverFactory.class.getClassLoader()
                .getResourceAsStream("appium.properties")) {
            if (is != null) {
                p.load(is);
                log.info("[DriverFactory] appium.properties loaded from classpath (embedded JAR).");
                return p;
            }
        }

        throw new IOException(
                "appium.properties not found in any of the expected locations:\n" +
                "  1. Current directory: " + localFile.getAbsolutePath() + "\n" +
                "  2. Project path:       " + projectFile.getAbsolutePath() + "\n" +
                "  3. Classpath (JAR)\n" +
                "Place appium.properties in the same directory as the JAR."
        );
    }

    private static String get(String key, String def) {
        if (props == null) return def;
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
