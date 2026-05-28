package config;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
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
 * Enterprise-grade AndroidDriver factory.
 *
 * Features:
 *  - Pre-flight validation: Appium server, ADB device, package installed
 *  - Samsung / Android 14-15 specific timeouts
 *  - Automatic retry (up to MAX_RETRIES attempts)
 *  - Full root-cause logging and stack traces
 *  - Supports: local USB, BrowserStack, Sauce Labs
 *  - Capabilities use UiAutomator2Options (Appium 2 / W3C compliant)
 */
public class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

    private static final int  MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5_000L;

    private static volatile AndroidDriver driver;
    private static volatile Properties    props;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the current AndroidDriver, creating one if needed. Thread-safe. */
    public static AndroidDriver getDriver() {
        if ("true".equals(System.getProperty("cinepolis.abort.requested")))
            throw new RuntimeException("Ejecucion abortada por el usuario");

        // Detect and clear stale session
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
        AndroidDriver d = driver;
        if (d != null) {
            try { d.quit(); }
            catch (Exception e) { log.warn("[DriverFactory] quit() error: {}", e.getMessage()); }
            driver = null;
            log.info("[DriverFactory] AndroidDriver session closed.");
        }
    }

    /** Terminates and relaunches the app under test. */
    public static void relaunchApp() {
        if (driver == null) return;
        String pkg = prop("appPackage", "");
        if (pkg.isBlank()) return;
        try { driver.terminateApp(pkg); } catch (Exception ignored) {}
        try { driver.activateApp(pkg);  } catch (Exception ignored) {}
    }

    /** Brings the app to the foreground; relaunches if activation fails. */
    public static void ensureAppRunning() {
        if (driver == null) return;
        String pkg = prop("appPackage", "");
        if (pkg.isBlank()) return;
        try {
            driver.activateApp(pkg);
        } catch (Exception e) {
            log.warn("[DriverFactory] activateApp failed, attempting relaunch: {}", e.getMessage());
            relaunchApp();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Driver creation — retry loop
    // ──────────────────────────────────────────────────────────────────────────

    private static AndroidDriver createDriverWithRetries() {
        initProps();
        String mode = prop("appium.mode", "local");

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.info("");
            log.info("[DriverFactory] ══════════════════════════════════════════");
            log.info("[DriverFactory]  Intento {}/{} — mode={}", attempt, MAX_RETRIES, mode);
            log.info("[DriverFactory] ══════════════════════════════════════════");

            try {
                AndroidDriver d = attemptCreate(mode);
                if (attempt > 1)
                    log.info("[DriverFactory] Driver creado en intento {}.", attempt);
                return d;
            } catch (Exception e) {
                lastException = e;
                log.error("[DriverFactory] Intento {} FALLIDO: {}", attempt, e.getMessage());
                log.error("[DriverFactory] Causa raiz: {}", rootCause(e).getMessage());
                e.printStackTrace();   // full stack trace as requested

                diagnose(e);           // hint for the most common failures

                if (attempt < MAX_RETRIES) {
                    log.info("[DriverFactory] Esperando {} ms antes de reintentar...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            }
        }

        // All retries exhausted
        String summary = String.format(
            "[DriverFactory] Failed to create AndroidDriver after %d attempts.%n" +
            "  mode       = %s%n" +
            "  deviceName = %s%n" +
            "  udid       = %s%n" +
            "  appPackage = %s%n" +
            "  appActivity= %s%n" +
            "  hub        = %s%n" +
            "  rootCause  = %s",
            MAX_RETRIES,
            mode,
            prop("deviceName",  "?"),
            prop("udid",        "?"),
            prop("appPackage",  "?"),
            prop("appActivity", "?"),
            prop("appium.hub",  "?"),
            rootCause(lastException).getMessage()
        );
        log.error(summary);
        throw new RuntimeException(summary, lastException);
    }

    private static AndroidDriver attemptCreate(String mode) throws Exception {
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

    // ──────────────────────────────────────────────────────────────────────────
    // LOCAL — enterprise capabilities for Samsung / Android 14-15
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildLocal(UiAutomator2Options o) throws Exception {
        String hubUrl  = prop("appium.hub",      "http://127.0.0.1:4723/wd/hub");
        String udid    = prop("udid",            "");
        String pkg     = prop("appPackage",      "");
        String act     = prop("appActivity",     "");
        String apkPath = prop("apkPath",         "");

        // ── Pre-flight validations ───────────────────────────────
        validateAppiumServer(hubUrl);
        if (!udid.isBlank()) {
            validateAdbDevice(udid);
            if (!pkg.isBlank()) validatePackageInstalled(udid, pkg);
            // Kill any leftover UIA2 server from a previous session to free systemPort
            try {
                String[] killCmd = {"adb", "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server"};
                Process p = Runtime.getRuntime().exec(killCmd);
                p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                log.debug("[DriverFactory] UIA2 server stopped on device {} to free systemPort.", udid);
            } catch (Exception ignored) {}
        }

        // ── Platform ─────────────────────────────────────────────
        o.setPlatformName("Android");
        o.setDeviceName(prop("deviceName", "Android Device"));

        String platformVersion = prop("platformVersion", "");
        if (!platformVersion.isBlank()) o.setPlatformVersion(platformVersion);
        if (!udid.isBlank())            o.setUdid(udid);

        // ── App ───────────────────────────────────────────────────
        if (!apkPath.isBlank()) {
            o.setApp(apkPath);
            o.setFullReset(false);
        }
        if (!pkg.isBlank()) o.setAppPackage(pkg);
        if (!act.isBlank()) o.setAppActivity(act);

        // ── Automation ────────────────────────────────────────────
        o.setAutomationName(prop("automationName", "UiAutomator2"));
        o.setNoReset(Boolean.parseBoolean(prop("noReset", "true")));
        o.setAutoGrantPermissions(true);

        // ── Session timeout ───────────────────────────────────────
        long cmdTimeout = Long.parseLong(prop("newCommandTimeout", "300"));
        o.setNewCommandTimeout(Duration.ofSeconds(cmdTimeout));

        // ── Samsung / Android 14-15 extended timeouts ─────────────
        // UiAutomator2 server can take 60-90 s to install on Samsung
        long uia2Launch  = Long.parseLong(prop("uia2LaunchTimeout",  "120"));  // seconds
        long uia2Install = Long.parseLong(prop("uia2InstallTimeout", "120"));  // seconds
        int  adbExec     = Integer.parseInt(prop("adbExecTimeout",   "90000")); // ms
        int  apkInstall  = Integer.parseInt(prop("androidInstallTimeout", "90000")); // ms

        o.setUiautomator2ServerLaunchTimeout(Duration.ofSeconds(uia2Launch));
        o.setUiautomator2ServerInstallTimeout(Duration.ofSeconds(uia2Install));
        o.setCapability("adbExecTimeout",        adbExec);
        o.setCapability("androidInstallTimeout", apkInstall);

        // ── Stability / performance ───────────────────────────────
        o.setCapability("disableWindowAnimation",   true);   // faster transitions
        o.setCapability("ignoreUnimportantViews",   true);   // faster element lookup
        o.setCapability("forceAppLaunch",           true);   // always cold-start
        o.setCapability("skipServerInstallation",   false);  // reinstall UIA2 server
        o.setCapability("skipDeviceInitialization", false);  // full device init
        o.setCapability("ensureWebviewsHavePages",  false);  // native app only
        o.setCapability("nativeWebScreenshot",      true);   // reliable screenshots

        // systemPort: must be unique per concurrent session
        int systemPort = Integer.parseInt(prop("systemPort", "8200"));
        o.setCapability("systemPort", systemPort);

        // ── Hub URL (supports AWS Device Farm env override) ───────
        String envHub = System.getenv("APPIUM_SERVER_URL");
        String finalHub = (envHub != null && !envHub.isBlank()) ? envHub : hubUrl;

        log.info("[DriverFactory] local → device={} udid={} pkg={} activity={} hub={}",
            prop("deviceName","?"), udid, pkg, act, finalHub);

        return URI.create(finalHub).toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pre-flight validations
    // ──────────────────────────────────────────────────────────────────────────

    /** Verifies Appium server is reachable (tries /status and /wd/hub/status). */
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
            "  Verifica drivers: appium driver list --installed\n" +
            "  Instalar UiAutomator2: appium driver install uiautomator2"
        );
    }

    /**
     * Verifies the device is connected via ADB and USB debugging is authorized.
     * Distinguishes between: not found, unauthorized, offline.
     */
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
                    "  Solucion:\n" +
                    "    1. Ajustes > Opciones de desarrollador > Depuracion USB (activar)\n" +
                    "    2. Acepta el dialogo 'Permitir depuracion USB' en el dispositivo\n" +
                    "    3. Si no aparece el dialogo: adb kill-server && adb start-server"
                );
            }

            if ("offline".equalsIgnoreCase(out)) {
                throw new IllegalStateException(
                    "[Preflight] Device " + udid + " is OFFLINE.\n" +
                    "  Solucion: desconecta y vuelve a conectar el cable USB."
                );
            }

            // Unexpected output
            throw new IllegalStateException(
                "[Preflight] Device " + udid + " NOT found (exit=" + exit + ", state='" + out + "').\n" +
                "  Dispositivos disponibles: adb devices\n" +
                "  UDID configurado en appium.properties: " + udid
            );

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // adb not in PATH — warn but don't block (will fail later with clearer Appium error)
            log.warn("[Preflight] adb check skipped ({}) — verifica que adb este en el PATH.", e.getMessage());
        }
    }

    /** Checks whether the app package is installed on the device. */
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
                log.warn("[Preflight] Package {} NO encontrado en {}.\n" +
                    "  Instala la app manualmente: adb -s {} install app.apk",
                    appPackage, udid, udid);
            }
        } catch (Exception e) {
            log.warn("[Preflight] Package check skipped: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BROWSERSTACK
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
            throw new IllegalStateException(
                "[BrowserStack] Configura browserstack.user y browserstack.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException(
                "[BrowserStack] Sube el APK en browserstack.com/app-automate y configura browserstack.app.id");

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

        log.info("[DriverFactory] BrowserStack → device={} osVersion={}", device, osVer);
        return URI.create("https://hub-cloud.browserstack.com/wd/hub").toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SAUCE LABS
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
            throw new IllegalStateException(
                "[SauceLabs] Configura saucelabs.user y saucelabs.key en appium.properties");
        if (appId.isBlank())
            throw new IllegalStateException(
                "[SauceLabs] Sube el APK en app.saucelabs.com y configura saucelabs.app.id");

        o.setCapability("sauce:options", Map.of(
            "username",        user,
            "accessKey",       key,
            "deviceName",      device,
            "platformVersion", osVer,
            "name",            "Cinepolis-" + LocalDate.now()
        ));
        o.setApp(appId);
        if (!pkg.isBlank()) o.setAppPackage(pkg);
        if (!act.isBlank()) o.setAppActivity(act);
        o.setAutomationName("UiAutomator2");
        o.setNoReset(true);
        o.setAutoGrantPermissions(true);
        o.setNewCommandTimeout(Duration.ofSeconds(Long.parseLong(prop("newCommandTimeout", "300"))));

        log.info("[DriverFactory] SauceLabs → device={} region={}", device, region);
        return URI.create("https://ondemand." + region + ".saucelabs.com/wd/hub").toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Diagnostics — maps common Appium errors to actionable messages
    // ──────────────────────────────────────────────────────────────────────────

    private static void diagnose(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String root = rootCause(e).getMessage() != null
            ? rootCause(e).getMessage().toLowerCase() : "";

        if (msg.contains("uiautomator2") && (msg.contains("not found") || msg.contains("not installed"))) {
            log.error("[Diagnose] UiAutomator2 driver NOT installed in Appium.");
            log.error("  Solucion: appium driver install uiautomator2");
        } else if (msg.contains("could not start activity") || root.contains("could not start activity")) {
            log.error("[Diagnose] Appium no pudo iniciar la actividad.");
            log.error("  appActivity configurado: {}", prop("appActivity", "?"));
            log.error("  Verifica con: adb shell dumpsys window | findstr mCurrentFocus");
            log.error("  El valor correcto es la clase despues del '/': com.pkg/com.pkg.ACTIVITY -> ACTIVITY");
        } else if (msg.contains("connection refused") || root.contains("connection refused")) {
            log.error("[Diagnose] Appium server NO esta corriendo.");
            log.error("  Solucion: appium --port 4723");
        } else if (msg.contains("device") && msg.contains("not found")) {
            log.error("[Diagnose] Dispositivo no encontrado.");
            log.error("  udid configurado: {}", prop("udid", "?"));
            log.error("  Dispositivos disponibles: adb devices");
        } else if (msg.contains("unauthorized")) {
            log.error("[Diagnose] Dispositivo no autorizado para depuracion USB.");
            log.error("  Acepta el dialogo en el dispositivo y vuelve a intentar.");
        } else if (msg.contains("timeout") || root.contains("timeout")) {
            log.error("[Diagnose] Timeout al crear sesion. Samsung/Android 15 puede necesitar mas tiempo.");
            log.error("  Considera aumentar uia2LaunchTimeout y uia2InstallTimeout en appium.properties.");
        } else if (msg.contains("package") && msg.contains("not found")) {
            log.error("[Diagnose] La app {} no esta instalada.", prop("appPackage", "?"));
            log.error("  Instala con: adb install app.apk");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────────────

    private static boolean isSessionAlive(AndroidDriver d) {
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
    // Properties loading (thread-safe, lazy)
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

        // 1. Alongside the JAR (deployment scenario)
        File local = new File("appium.properties");
        if (local.exists()) {
            try (FileInputStream fis = new FileInputStream(local)) {
                p.load(fis);
                log.info("[DriverFactory] appium.properties cargado de: {}", local.getAbsolutePath());
                return p;
            }
        }

        // 2. Project resources (IDE / Gradle test run)
        File project = new File("src/test/resources/appium.properties");
        if (project.exists()) {
            try (FileInputStream fis = new FileInputStream(project)) {
                p.load(fis);
                log.info("[DriverFactory] appium.properties cargado de: {}", project.getAbsolutePath());
                return p;
            }
        }

        // 3. Classpath (embedded in JAR)
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
            "  1. Junto al JAR:       " + local.getAbsolutePath()   + "\n" +
            "  2. Recursos del proyecto: " + project.getAbsolutePath() + "\n" +
            "  3. Classpath (JAR embebido)"
        );
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
