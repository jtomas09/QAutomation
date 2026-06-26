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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

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

                // Req 3+4: Capture logcat and emit user-friendly root cause to Dashboard
                if (!"iOS".equalsIgnoreCase(platform)) {
                    String targetUdid = prop("udid", "");
                    if (!targetUdid.isBlank()) {
                        log.info("[ADB] Capturando logcat de {} para diagnostico...", targetUdid);
                        String logcat = captureLogcat(targetUdid);
                        // Functional: no bracket prefix → visible in Dashboard
                        log.error(classifyFailureCause(logcat, e.getMessage()));
                        // Technical details → [ADB] prefix → filtered to Log Tecnico
                        if (!logcat.isBlank() && !logcat.startsWith("[logcat")) {
                            log.info("[ADB] === Logcat relevante ===");
                            for (String line : logcat.split("\n")) {
                                if (!line.isBlank()) log.info("[ADB] {}", line);
                            }
                            log.info("[ADB] === Fin logcat ===");
                        }
                    }
                }

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
            try {
                IOSDriver d = new IOSDriver(hub, options);
                d.manage().timeouts().implicitlyWait(Duration.ZERO);
                log.info("[DriverFactory] IOSDriver OK — sessionId={}", d.getSessionId());
                // WDA built and installed successfully — persist cache so next run skips compilation
                markWdaBuilt(
                    prop("udid",              ""),
                    prop("updatedWDABundleId", ""),
                    prop("xcodeOrgId",        ""),
                    prop("platformVersion",   "")
                );
                return d;
            } catch (Exception iosEx) {
                log.error("[DriverFactory][iOS] ══════════ APPIUM SESSION CREATION FAILED ══════════");
                log.error("[DriverFactory][iOS] Hub            : {}", hub);
                log.error("[DriverFactory][iOS] Capabilities   :\n{}", options.toJson());
                log.error("[DriverFactory][iOS] Exception class: {}", iosEx.getClass().getName());

                // Structured W3C response fields extracted from the Appium exception message.
                // Appium returns: {"value":{"error":"...","message":"...","stacktrace":"..."}}
                // Selenium deserializes these into the exception message as:
                //   "... Message: <value.message>\nStacktrace:\n<value.stacktrace>"
                extractAndLogAppiumResponse(iosEx, hub);

                // Full cause chain
                int depth = 0;
                Throwable t = iosEx;
                while (t.getCause() != null) {
                    t = t.getCause();
                    depth++;
                    log.error("[DriverFactory][iOS] Cause[{}] {} : {}",
                            depth, t.getClass().getName(), t.getMessage());
                }
                log.error("[DriverFactory][iOS] Full stacktrace:", iosEx);
                log.error("[DriverFactory][iOS] ══════════════════════════════════════════════════");
                throw iosEx;
            }
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
        String hubUrl  = prop("appium.hub",      "http://127.0.0.1:4723");
        String udid    = prop("udid",            "");
        String pkg     = prop("appPackage",      "");
        String act     = prop("appActivity",     "");
        String apkPath = prop("apkPath",         "");

        // Req 5: Fail fast if appPackage is missing — avoids 120s instrumentation timeout
        if (pkg.isBlank()) {
            throw new IllegalStateException(
                "appPackage no definido — ejecucion cancelada.\n" +
                "  Define appPackage en appium.properties o con -DappPackage=com.tu.app\n" +
                "  Sin appPackage Appium espera 120s y falla sin motivo claro.");
        }

        if ((act.isBlank() || "auto".equalsIgnoreCase(act)) && !udid.isBlank()) {
            act = resolveAppActivity(udid, pkg);
        }

        validateAppiumServer(hubUrl);

        boolean isAndroid16  = false;
        boolean isSamsungA16 = false;

        if (!udid.isBlank()) {
            validateAdbDevice(udid);

            // Req 1+2: Verify UiAutomator2 server packages; reinstall if absent/corrupt
            validateAndRepairUiAutomator2(udid);

            // Req 6+7: Detect Android API level and manufacturer
            int    apiLevel     = getAndroidApiLevel(udid);
            String manufacturer = getManufacturer(udid);
            isAndroid16  = apiLevel >= 35; // Android 16 = API 35/36
            isSamsungA16 = isAndroid16 && manufacturer.contains("samsung");

            log.info("[DriverFactory] Dispositivo: {} | API={} | fabricante={}",
                     prop("deviceName", udid), apiLevel, manufacturer);

            // Req 7: Samsung Android 16 — clear stale server data + reinstall before session
            if (isSamsungA16) {
                log.info("[DriverFactory] Samsung Android 16 detectado — reinicializando UiAutomator2...");
                reinitSamsungAndroid16(udid);
            }

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

        // Req 6: Auto-increase launch timeout for Android 16 (instrumentation starts slower)
        long uia2Launch = isAndroid16
                ? 240L
                : Long.parseLong(prop("uia2LaunchTimeout", "120"));
        if (isAndroid16)
            log.info("[DriverFactory] Android 16+: uiautomator2ServerLaunchTimeout = {}s (auto-ajustado)", uia2Launch);
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
        finalHub = finalHub.replaceAll("/wd/hub$", "");  // Appium 2.x/3.x uses bare base URL

        log.info("[DriverFactory] 📡 Appium endpoint: {} | device={} udid={} pkg={} activity={}",
            finalHub, prop("deviceName","?"), udid, pkg, act);

        return URI.create(finalHub).toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOCAL iOS
    // ──────────────────────────────────────────────────────────────────────────

    private static URL buildLocalIOS(XCUITestOptions o) throws Exception {
        String hubUrl   = prop("appium.hub",     "http://127.0.0.1:4723");
        String udid     = prop("udid",           "");
        String bundleId = prop("bundleId",       "");
        String ipaPath  = prop("ipaPath",        "");

        if (!udid.isBlank()) validateIosDevice(udid);
        validateAppiumServer(hubUrl);

        o.setPlatformName("iOS");
        o.setDeviceName(prop("deviceName", "iPhone"));

        String platformVersion = prop("platformVersion", "");
        if (isValidPlatformVersion(platformVersion)) {
            o.setPlatformVersion(platformVersion);
            log.info("[DriverFactory][iOS] platformVersion    : {} (enviada a Appium)", platformVersion);
        } else {
            log.info("[DriverFactory][iOS] platformVersion    : omitida{} — Appium la detectará automáticamente",
                    platformVersion.isBlank() ? "" : " (valor inválido: '" + platformVersion + "')");
        }
        if (!udid.isBlank())     o.setUdid(udid);
        if (!bundleId.isBlank()) o.setBundleId(bundleId);
        if (!ipaPath.isBlank())  o.setApp(ipaPath);

        o.setAutomationName("XCUITest");
        o.setNoReset(Boolean.parseBoolean(prop("noReset", "true")));

        long cmdTimeout = Long.parseLong(prop("newCommandTimeout", "300"));
        o.setNewCommandTimeout(Duration.ofSeconds(cmdTimeout));

        o.setCapability("autoAcceptAlerts",    true);
        o.setCapability("nativeWebScreenshot", true);

        // ── WebDriverAgent signing + caching ─────────────────────────────────
        // xcodeOrgId and xcodeSigningId are required for physical devices.
        // They are auto-detected by IosPreflightManager (Runner) and passed
        // as JVM properties -DxcodeOrgId=XXXX -DxcodeSigningId="Apple Development".
        String teamId = prop("xcodeOrgId", "");
        if (!teamId.isBlank()) {
            o.setCapability("xcodeOrgId",     teamId);
            String signingId = prop("xcodeSigningId", "Apple Development");
            o.setCapability("xcodeSigningId", signingId);
            log.info("[DriverFactory] 🔑 xcodeOrgId={}  xcodeSigningId={}", teamId, signingId);
        } else {
            log.warn("[DriverFactory] ⚠️  xcodeOrgId no configurado — xcodebuild no podrá firmar WDA.");
            log.warn("[DriverFactory]    Solución: Xcode → Settings → Accounts → agrega tu Apple ID.");
        }

        // updatedWDABundleId — unique stable ID per device, prevents conflicts
        // between simultaneous runners. Auto-generated if Runner didn't provide it.
        String wdaBundleId = prop("updatedWDABundleId", "");
        if (wdaBundleId.isBlank() && !udid.isBlank()) {
            String suffix = udid.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (suffix.length() > 10) suffix = suffix.substring(0, 10);
            wdaBundleId = "io.qautomation.wda." + suffix;
            System.setProperty("updatedWDABundleId", wdaBundleId);
        }
        if (!wdaBundleId.isBlank()) {
            o.setCapability("updatedWDABundleId", wdaBundleId);
            log.info("[DriverFactory] 🆔 updatedWDABundleId={}", wdaBundleId);
        }

        // showXcodeLog=true — exposes full xcodebuild output in Appium server log,
        // including the real error when WDA fails to compile (e.g. code 65).
        o.setCapability("showXcodeLog", true);

        // useNewWDA=false — reuse WDA if already running on device (don't restart it).
        o.setCapability("useNewWDA", false);

        // webDriverAgentUrl — URL where WDA is already running, announced by the Runner via
        // "ServerURLHere->URL<-ServerURLHere" from xcodebuild stdout (WdaManager).
        // In Xcode 16+/26 with CoreDevice, this may be a device-specific IP instead of localhost.
        String wdaUrl = prop("webDriverAgentUrl", "");

        // skipServerInstallation — skip xcodebuild if WDA is already installed (prebuilt)
        // or already running (webDriverAgentUrl set). When WDA URL is provided, Appium must
        // connect to the existing WDA directly and not attempt UDID-based device lookup.
        boolean wdaPrebuilt  = Boolean.parseBoolean(prop("wdaPrebuilt", "false"));
        boolean skipInstall  = wdaPrebuilt || !wdaUrl.isBlank();
        if (skipInstall) {
            o.setCapability("skipServerInstallation", true);
            if (wdaPrebuilt) {
                o.setCapability("shouldUseSingletonTestManager", true);
                log.info("[DriverFactory] ⚡ WDA precompilado — skipServerInstallation=true (sin recompilación)");
            }
        } else {
            o.setCapability("skipServerInstallation", false);
            log.info("[DriverFactory] 🔨 Primera ejecución — Appium compilará e instalará WDA automáticamente");
        }

        if (!wdaUrl.isBlank()) {
            o.setCapability("appium:webDriverAgentUrl", wdaUrl);
            // usePrebuiltWDA=true: WDA is confirmed running — Appium must connect directly
            // without performing any UDID-based device validation or WDA install attempt.
            // This is the key fix for "Unknown device or simulator UDID" when using CoreDevice.
            o.setCapability("appium:usePrebuiltWDA",    true);
            log.info("[DriverFactory] 🌐 webDriverAgentUrl={} — usePrebuiltWDA=true, skipInstall=true", wdaUrl);
        }

        String envHub = System.getenv("APPIUM_SERVER_URL");
        String finalHub = (envHub != null && !envHub.isBlank()) ? envHub : hubUrl;
        finalHub = finalHub.replaceAll("/wd/hub$", "");  // Appium 2.x/3.x uses bare base URL

        log.info("[DriverFactory] 📡 Appium endpoint: {} | device={} udid={} bundleId={} teamId={}",
            finalHub, prop("deviceName","?"), udid, bundleId, teamId.isBlank() ? "?" : teamId);

        log.info("[DriverFactory][iOS] ══════ Capabilities → IOSDriver ══════");
        log.info("[DriverFactory][iOS] deviceName        : {}", prop("deviceName", "?"));
        // Physical UDID (8-16 hex) is what Appium's XCUITest driver looks up in
        // hardwareProperties.udid — CoreDevice UUIDs (8-4-4-4-12) are resolved upstream
        // in IOSDeviceScanner.resolvePhysicalUdids() before this point.
        if (!udid.isBlank()) {
            boolean isCoreDevice = udid.length() == 36 && !udid.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}");
            if (isCoreDevice) {
                log.warn("[DriverFactory][iOS] udid (CoreDevice) : {} ⚠  Este es un CoreDevice UUID, no un UDID físico."
                        + " Appium puede rechazarlo.", udid);
            } else {
                log.info("[DriverFactory][iOS] udid (físico)     : {}", udid);
            }
        } else {
            log.info("[DriverFactory][iOS] udid              : (no configurado)");
        }
        log.info("[DriverFactory][iOS] platformVersion   : {}", prop("platformVersion", "(auto-detectada por Appium)"));
        log.info("[DriverFactory][iOS] xcodeOrgId        : {}", teamId.isBlank()      ? "(no configurado)" : teamId);
        log.info("[DriverFactory][iOS] bundleId          : {}", bundleId.isBlank()    ? "(no configurado)" : bundleId);
        log.info("[DriverFactory][iOS] updatedWDABundleId: {}", wdaBundleId.isBlank() ? "(auto)"           : wdaBundleId);
        log.info("[DriverFactory][iOS] wdaPrebuilt       : {}", wdaPrebuilt);
        log.info("[DriverFactory][iOS] webDriverAgentUrl : {}", wdaUrl.isBlank() ? "(Appium administra WDA)" : wdaUrl);
        log.info("[DriverFactory][iOS] ════════════════════════════════════════");

        return URI.create(finalHub).toURL();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pre-flight validations
    // ──────────────────────────────────────────────────────────────────────────

    private static void validateAppiumServer(String hubUrl) {
        String base = hubUrl.replaceAll("/wd/hub$", "");
        // Check Appium 2.x/3.x first (/status), fall back to Appium 1.x (/wd/hub/status)
        String[] paths = {"/status", "/wd/hub/status"};

        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        log.info("[DriverFactory] 📡 Appium endpoint: {}", base);

        for (String path : paths) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + path))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
                int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code == 200) {
                    log.info("[DriverFactory] ✅ Appium disponible: {}", base);
                    return;
                }
            } catch (Exception ignored) {}
        }

        throw new IllegalStateException(
            "❌ Appium no disponible en: " + base + "\n" +
            "  Inicia Appium con: appium --port 4723\n" +
            "  Drivers requeridos:\n" +
            "    Android: appium driver install uiautomator2\n" +
            "    iOS:     appium driver install xcuitest"
        );
    }

    private static void validateAdbDevice(String udid) {
        try {
            Process p = new ProcessBuilder(adbBin(), "-s", udid, "get-state")
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

    private static void validateIosDevice(String udid) {
        final int maxWaitMs = 30_000;
        final int pollMs    =  3_000;
        try {
            for (int attempt = 0; attempt <= maxWaitMs / pollMs; attempt++) {
                // Primary: xcrun xctrace — works for traditional UDIDs (8-16 hex format)
                Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor();
                if (out.contains(udid)) {
                    if (attempt > 0)
                        log.info("[Preflight] iOS device {} visible después de ~{}s de espera CoreDevice.",
                                udid, attempt * pollMs / 1000);
                    else
                        log.info("[Preflight] iOS device OK: {} (visible via xctrace)", udid);
                    return;
                }

                // Xcode 26+ fallback: devicectl --json-output contains physical UDID in
                // hardwareProperties.udid. The text output of devicectl shows CoreDevice UUIDs
                // (8-4-4-4-12 format) — NOT physical UDIDs — so JSON output is required here.
                try {
                    Process p2 = new ProcessBuilder("xcrun", "devicectl", "list", "devices",
                            "--json-output", "-")
                        .redirectErrorStream(false).start();
                    String json = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    p2.waitFor();
                    if (json.contains(udid)) {
                        log.info("[Preflight] iOS device OK: {} (visible via devicectl JSON — hardwareProperties.udid)", udid);
                        return;
                    }
                } catch (Exception ignored) {}

                // Device not visible yet — wait and retry (CoreDevice sync may take a few seconds)
                if (attempt == 0) {
                    log.warn("[Preflight] ⏳ iOS device {} no sincronizado — esperando CoreDevice ({} s máx.)...",
                            udid, maxWaitMs / 1000);
                }
                if (attempt < maxWaitMs / pollMs) {
                    Thread.sleep(pollMs);
                }
            }

            throw new IllegalStateException(
                "[Preflight] iOS device " + udid + " no encontrado via 'xcrun xctrace list devices' "
                + "ni 'xcrun devicectl --json-output' después de " + (maxWaitMs / 1000) + "s.\n"
                + "  Asegúrate de que el iPhone esté conectado, desbloqueado y confíe en este Mac.\n"
                + "  Diagnóstico: xcrun devicectl list devices --json-output -"
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Preflight] xcrun check skipped ({}) — verifica que Xcode esté instalado.", e.getMessage());
        }
    }

    /**
     * Validates a platformVersion string before sending it to Appium.
     * Appium rejects null, blank, "unknown", and non-numeric strings with
     * SessionNotCreatedException — so we omit the capability entirely when invalid,
     * allowing Appium to auto-detect the iOS version from the connected device.
     *
     * Valid examples: "17.7", "18.0", "26.5", "18.0.1"
     * Invalid: null, "", "unknown", "abc", "17"
     */
    // Package-private for unit testing in config.DriverFactoryTest
    static boolean isValidPlatformVersion(String v) {
        if (v == null || v.isBlank()) return false;
        if ("unknown".equalsIgnoreCase(v.trim())) return false;
        return v.trim().matches("\\d+\\.\\d+.*");
    }

    /**
     * Persists a successful WDA build to ~/.qautomation/wda/{udid}.properties
     * so IosPreflightManager (Runner) can detect it on the next run and set
     * -DwdaPrebuilt=true, which causes skipServerInstallation=true in this class.
     *
     * Uses the same file format as IosPreflightManager.saveWdaCache().
     * No dependency on Runner classes — pure Java IO + Properties.
     */
    private static void markWdaBuilt(String udid, String bundleId,
                                     String teamId, String iosVersion) {
        if (udid == null || udid.isBlank()) return;
        try {
            String cacheDir = System.getProperty("user.home") + "/.qautomation/wda";
            new File(cacheDir).mkdirs();
            Properties p = new Properties();
            p.setProperty("udid",       udid);
            p.setProperty("bundleId",   bundleId.isBlank() ? "unknown" : bundleId);
            p.setProperty("teamId",     teamId.isBlank()   ? "unknown" : teamId);
            p.setProperty("iosVersion", iosVersion.isBlank() ? "unknown" : iosVersion);
            p.setProperty("builtAt",    Instant.now().toString());
            String safe = udid.replaceAll("[^a-zA-Z0-9_-]", "_");
            File f = new File(cacheDir, safe + ".properties");
            try (FileOutputStream out = new FileOutputStream(f)) {
                p.store(out, "QAutomation WDA cache — written by DriverFactory");
            }
            log.info("[DriverFactory][iOS] 💾 WDA cache guardado: {} — próxima ejecución omitirá compilación", f);
        } catch (Exception e) {
            log.warn("[DriverFactory][iOS] No se pudo guardar WDA cache: {}", e.getMessage());
        }
    }

    private static void validatePackageInstalled(String udid, String appPackage) {
        try {
            Process p = new ProcessBuilder(
                adbBin(), "-s", udid, "shell", "pm", "list", "packages", appPackage)
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
                log.error("[Diagnose][iOS] XCUITest driver NOT installed in Appium.");
                log.error("[Diagnose][iOS]   Solucion: appium driver install xcuitest");
            } else if (msg.contains("connection refused") || root.contains("connection refused")) {
                log.error("[Diagnose][iOS] Appium server NO esta corriendo. Solucion: appium --port 4723");
            } else if (msg.contains("xcode") || msg.contains("instruments")
                    || msg.contains("wda") || msg.contains("webdriveragent")) {
                // Never replace the Appium error with a generic hint — print the actual cause.
                String rawMsg = e.getMessage() != null ? e.getMessage() : "";
                String origErr = extractSection(rawMsg, "Original error:", "\n");
                if (!origErr.isBlank()) {
                    log.error("[Diagnose][iOS] Original error : {}", origErr.trim());
                }
                String valueMsg = extractSection(rawMsg, "Message:", "\nStacktrace:");
                if (!valueMsg.isBlank()) {
                    log.error("[Diagnose][iOS] value.message  : {}", valueMsg.trim());
                }
                if (origErr.isBlank() && valueMsg.isBlank() && !rawMsg.isBlank()) {
                    log.error("[Diagnose][iOS] Appium error   : {}",
                            rawMsg.lines().limit(15).reduce("", (a, b) -> a + "\n" + b).trim());
                }
                log.error("[Diagnose][iOS] Revisa el Log Tecnico para el stacktrace completo.");
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
    // iOS — Appium W3C response parser
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses and logs the W3C response fields that Appium embeds in the exception message.
     * Appium response format:
     *   {"value":{"error":"session not created","message":"...","stacktrace":"..."}}
     * Selenium 4 maps these to exception message as:
     *   "... Response code 500. Message: <value.message>\nStacktrace:\n<value.stacktrace>"
     *
     * Also queries GET /sessions on the Appium server so the caller can see whether
     * Appium is still alive and which sessions (if any) survived the failure.
     */
    private static void extractAndLogAppiumResponse(Exception ex, URL hub) {
        String raw = ex.getMessage();

        log.error("[DriverFactory][iOS] ── Appium W3C Response ───────────────────────────────");

        // value.error — W3C error code (the exception class name is the clearest proxy)
        log.error("[DriverFactory][iOS] value.error      : {}", ex.getClass().getSimpleName());

        if (raw != null && !raw.isBlank()) {
            // value.message — content between "Message:" and "\nStacktrace:"
            String valueMessage = extractSection(raw, "Message:", "\nStacktrace:");
            if (valueMessage.isBlank()) valueMessage = raw; // fallback: entire message
            log.error("[DriverFactory][iOS] value.message    :\n{}", valueMessage.trim());

            // Original error — innermost cause line; most diagnostic single line
            String origError = extractSection(raw, "Original error:", "\n");
            if (!origError.isBlank())
                log.error("[DriverFactory][iOS] Original error   : {}", origError.trim());

            // value.stacktrace — everything after "Stacktrace:" in the message
            String stacktrace = extractSection(raw, "Stacktrace:", null);
            if (!stacktrace.isBlank())
                log.error("[DriverFactory][iOS] value.stacktrace :\n{}", stacktrace.trim());
        } else {
            log.error("[DriverFactory][iOS] (exception message is null/empty)");
        }

        // Query Appium to confirm it is still alive after the failure
        try {
            String base = hub.toString().replaceAll("/wd/hub$", "");
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/sessions"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.error("[DriverFactory][iOS] GET /sessions → HTTP {} :\n{}",
                    resp.statusCode(), resp.body());
        } catch (Exception httpEx) {
            log.error("[DriverFactory][iOS] GET /sessions → no response ({})", httpEx.getMessage());
        }

        log.error("[DriverFactory][iOS] ─────────────────────────────────────────────────────");
    }

    /**
     * Extracts the text between {@code startMarker} and {@code endMarker} in {@code text}.
     * Case-insensitive start search. If {@code endMarker} is null, returns everything after start.
     * Returns empty string when {@code startMarker} is not found.
     */
    private static String extractSection(String text, String startMarker, String endMarker) {
        if (text == null || text.isBlank()) return "";
        int start = text.indexOf(startMarker);
        if (start < 0) start = text.toLowerCase().indexOf(startMarker.toLowerCase());
        if (start < 0) return "";
        start += startMarker.length();
        if (endMarker == null) return text.substring(start);
        int end = text.indexOf(endMarker, start);
        return end > start ? text.substring(start, end) : text.substring(start);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auto-detect launcher activity (Android only)
    // ──────────────────────────────────────────────────────────────────────────

    private static String resolveAppActivity(String udid, String pkg) {
        try {
            String[] cmd = {adbBin(), "-s", udid, "shell", "cmd", "package",
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

        String adb = adbBin();
        String[][] killCmds = {
            {adb, "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server"},
            {adb, "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server.test"},
        };
        for (String[] cmd : killCmds) {
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }

        // Liberar el systemPort si está ocupado (fuser -k es silencioso si no está en uso)
        try {
            String[] fuserKill = {adbBin(), "-s", udid, "shell", "fuser", "-k", systemPort + "/tcp"};
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

    // ──────────────────────────────────────────────────────────────────────────
    // Android device diagnostics
    // ──────────────────────────────────────────────────────────────────────────

    /** ADB binary — uses ADB_PATH system property (set by PlatformToolsManager), falls back to system adb. */
    private static String adbBin() {
        String path = System.getProperty("ADB_PATH", "");
        return path.isBlank() ? "adb" : path;
    }

    /** Returns the device Android API level, or 0 on error. */
    private static int getAndroidApiLevel(String udid) {
        try {
            Process p = new ProcessBuilder(adbBin(), "-s", udid, "shell",
                    "getprop", "ro.build.version.sdk")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return 0; }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim()
                    .replaceAll("[^0-9]", "");
            return out.isBlank() ? 0 : Integer.parseInt(out);
        } catch (Exception e) {
            log.warn("[DriverFactory] getAndroidApiLevel error: {}", e.getMessage());
            return 0;
        }
    }

    /** Returns the device manufacturer name in lowercase, or empty string on error. */
    private static String getManufacturer(String udid) {
        try {
            Process p = new ProcessBuilder(adbBin(), "-s", udid, "shell",
                    "getprop", "ro.product.manufacturer")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return ""; }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().toLowerCase();
        } catch (Exception e) {
            log.warn("[DriverFactory] getManufacturer error: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Req 1+2: Verifies io.appium.uiautomator2.server and its test package are installed.
     * If either is absent, uninstalls both so Appium performs a clean reinstall.
     */
    private static void validateAndRepairUiAutomator2(String udid) {
        log.info("[ADB] Verificando paquetes UiAutomator2 en {}...", udid);
        try {
            Process p = new ProcessBuilder(adbBin(), "-s", udid, "shell", "pm", "list", "packages")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return; }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean hasServer = out.lines().anyMatch(
                    l -> l.trim().equals("package:io.appium.uiautomator2.server"));
            boolean hasTest = out.lines().anyMatch(
                    l -> l.trim().equals("package:io.appium.uiautomator2.server.test"));

            log.info("[ADB] io.appium.uiautomator2.server      : {}", hasServer ? "OK" : "AUSENTE");
            log.info("[ADB] io.appium.uiautomator2.server.test : {}", hasTest   ? "OK" : "AUSENTE");

            if (!hasServer || !hasTest) {
                log.warn("[ADB] Paquetes UiAutomator2 incompletos — desinstalando para reinstalacion limpia...");
                uninstallUiAutomator2Packages(udid);
                log.info("[ADB] Appium reinstalara UiAutomator2 automaticamente al crear la sesion.");
            }
        } catch (Exception e) {
            log.warn("[ADB] validateAndRepairUiAutomator2 error: {}", e.getMessage());
        }
    }

    /** Force-uninstalls UiAutomator2 server + test APKs from the device. */
    private static void uninstallUiAutomator2Packages(String udid) {
        for (String pkg : new String[]{
                "io.appium.uiautomator2.server",
                "io.appium.uiautomator2.server.test"}) {
            try {
                Process p = new ProcessBuilder(adbBin(), "-s", udid, "uninstall", pkg)
                        .redirectErrorStream(true).start();
                boolean done = p.waitFor(15, TimeUnit.SECONDS);
                String out = done
                        ? new String(p.getInputStream().readAllBytes()).trim()
                        : "timeout";
                log.info("[ADB] uninstall {}: {}", pkg, out);
            } catch (Exception e) {
                log.warn("[ADB] uninstall {} error: {}", pkg, e.getMessage());
            }
        }
    }

    /**
     * Req 7: Samsung Android 16 — clears UiAutomator2 app data + reinstalls before session.
     * Samsung Android 16 rejects stale or mismatched server APKs at instrumentation start.
     */
    private static void reinitSamsungAndroid16(String udid) {
        log.info("[ADB] Samsung Android 16: limpiando datos de UiAutomator2...");
        for (String pkg : new String[]{
                "io.appium.uiautomator2.server",
                "io.appium.uiautomator2.server.test"}) {
            try {
                Process p = new ProcessBuilder(adbBin(), "-s", udid, "shell", "pm", "clear", pkg)
                        .redirectErrorStream(true).start();
                boolean done = p.waitFor(5, TimeUnit.SECONDS);
                String out = done
                        ? new String(p.getInputStream().readAllBytes()).trim()
                        : "timeout";
                log.info("[ADB] pm clear {}: {}", pkg, out);
            } catch (Exception e) {
                log.warn("[ADB] pm clear {} error: {}", pkg, e.getMessage());
            }
        }
        uninstallUiAutomator2Packages(udid);
        log.info("[ADB] Samsung Android 16: reinstalacion pendiente — Appium lo hara al crear la sesion.");
        sleep(1000L);
    }

    /**
     * Req 3: Captures recent logcat entries relevant to session creation failures.
     * Filters for AndroidRuntime errors, Appium server output, and ActivityManager warnings.
     */
    private static String captureLogcat(String udid) {
        try {
            Process p = new ProcessBuilder(
                    adbBin(), "-s", udid, "logcat", "-d", "-t", "200",
                    "AndroidRuntime:E", "AppiumUIA2-Server:D", "UiAutomator:D",
                    "ActivityManager:W", "PackageManager:W", "System.err:W", "*:S")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return "[logcat timeout]"; }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "[logcat error: " + e.getMessage() + "]";
        }
    }

    /**
     * Req 4: Classifies the failure cause from logcat and exception message.
     * Returns a user-readable functional error shown in the Dashboard.
     */
    private static String classifyFailureCause(String logcat, String errorMsg) {
        String s = (logcat + "\n" + (errorMsg != null ? errorMsg : "")).toLowerCase();

        if (s.contains("fatal exception") || (s.contains("process") && s.contains("died")))
            return "Crash de la aplicacion detectado en el dispositivo.\n" +
                   "  Revisa los permisos de la app y que este correctamente instalada.";

        if (s.contains("instrumentation") &&
                (s.contains("crash") || s.contains("failed to run") || s.contains("cannot be initialized")))
            return "Crash del instrumentation (UiAutomator2 server) — APK corrupto o incompatible con Android.";

        if (s.contains("install_failed") || s.contains("no such package") ||
                (s.contains("package") && s.contains("is not installed")))
            return "La app no esta instalada en el dispositivo.\n" +
                   "  Verifica que el APK este instalado o configura apkPath en appium.properties.";

        if (s.contains("permission denied") || s.contains("unauthorized"))
            return "Acceso denegado por el dispositivo.\n" +
                   "  Acepta el dialogo 'Permitir depuracion USB' en la pantalla del dispositivo.";

        if ((s.contains("versioncode") && s.contains("mismatch")) ||
                (s.contains("version") && s.contains("incompatible")))
            return "UiAutomator2 incompatible con la version de Android del dispositivo.\n" +
                   "  Ejecuta desde el Runner: appium driver update uiautomator2";

        if (s.contains("cannot be initialized within") || s.contains("120000ms") ||
                (s.contains("timed out") && s.contains("uiautomator")))
            return "UiAutomator2 no pudo inicializarse a tiempo (timeout).\n" +
                   "  Causa probable: servidor corrupto, permisos USB o Android 16 incompatible.\n" +
                   "  Solucion: desconecta y reconecta el dispositivo e intenta de nuevo.";

        if (s.contains("connection refused") || s.contains("econnrefused"))
            return "Appium no esta corriendo o no acepta conexiones.\n" +
                   "  Verifica que el Runner haya iniciado Appium correctamente.";

        return "Error al iniciar la sesion Appium Android.\n" +
               "  Revisa el Log Tecnico para ver el logcat completo del dispositivo.";
    }
}
