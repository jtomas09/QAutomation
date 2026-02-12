package config;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Properties;

public class DriverFactory {

    private static volatile AndroidDriver driver;
    private static Properties props;

    public static AndroidDriver getDriver() {
        if (driver == null) {
            synchronized (DriverFactory.class) {
                if (driver == null) {
                    driver = createDriver();
                }
            }
        }
        return driver;
    }

    private static AndroidDriver createDriver() {
        try {
            props = loadProps("src/test/resources/appium.properties");
            UiAutomator2Options options = new UiAutomator2Options();

            options.setPlatformName(get("platformName", "Android"));

            String deviceName = System.getProperty("deviceName", get("deviceName", "Android Device"));
            options.setDeviceName(deviceName);

            String platformVersion = System.getProperty("platformVersion", get("platformVersion", ""));
            if (!platformVersion.isBlank()) options.setPlatformVersion(platformVersion);

            String udid = System.getProperty("udid", get("udid", ""));
            if (!udid.isBlank()) options.setUdid(udid);

            String appPackage = get("appPackage", "");
            String appActivity = get("appActivity", "");
            String apkPath = get("apkPath", "");

            if (!apkPath.isBlank()) {
                options.setApp(apkPath);
                options.setFullReset(false);
            }

            if (!appPackage.isBlank()) options.setAppPackage(appPackage);
            if (!appActivity.isBlank()) options.setAppActivity(appActivity);

            options.setAutomationName(get("automationName", "UiAutomator2"));
            options.setNoReset(Boolean.parseBoolean(get("noReset", "true")));

            options.setNewCommandTimeout(Duration.ofSeconds(
                    Long.parseLong(get("newCommandTimeout", "100"))
            ));

            options.setAutoGrantPermissions(Boolean.parseBoolean(get("autoGrantPermissions", "true")));

            options.setUiautomator2ServerInstallTimeout(Duration.ofSeconds(
                    Long.parseLong(get("uia2InstallTimeout", "40"))
            ));

            // 🔥 PERFORMANCE FLAGS
            options.setCapability("disableWindowAnimation", true);
            options.setCapability("ignoreUnimportantViews", true);
            options.setCapability("skipDeviceInitialization", true);
            options.setCapability("skipServerInstallation", true);
            options.setCapability("forceAppLaunch", true); // <- importante

            URL hub = URI.create(
                    System.getProperty("appium.hub",
                            get("appium.hub", "http://127.0.0.1:4723/wd/hub"))
            ).toURL();

            AndroidDriver d = new AndroidDriver(hub, options);

            // 🔥 Evita multiplicador de tiempos
            d.manage().timeouts().implicitlyWait(Duration.ZERO);

            return d;

        } catch (Exception e) {
            throw new RuntimeException("Error creando el AndroidDriver", e);
        }
    }

    // 🔥 NUEVO: Fuerza relanzamiento limpio de la app
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

    // 🔥 NUEVO: Garantiza que la app esté activa
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
        }
    }

    private static Properties loadProps(String path) throws IOException {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            p.load(fis);
        }
        return p;
    }

    private static String get(String key, String def) {
        if (props == null) return def;
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
