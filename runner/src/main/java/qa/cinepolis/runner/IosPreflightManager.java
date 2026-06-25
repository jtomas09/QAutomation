package qa.cinepolis.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * iOS-specific pre-flight checks and WDA (WebDriverAgent) cache management.
 *
 * Responsibilities:
 *  1. Validate Xcode, Apple Developer Team, iOS device, Developer Mode
 *  2. Detect real iOS version via xcrun/devicectl
 *  3. Detect Apple Team ID via security keychain
 *  4. Manage per-device WDA cache so recompilation is skipped on subsequent runs
 *
 * The cache file lives at ~/.qautomation/wda/{udid}.properties.
 * DriverFactory (test process) writes to this file after a successful WDA build.
 * This class (Runner process) reads it before launching Gradle to pass -DwdaPrebuilt=true.
 *
 * Does NOT touch Android logic.
 */
public class IosPreflightManager {

    static final String CACHE_DIR =
            System.getProperty("user.home") + "/.qautomation/wda";

    // ── Result ────────────────────────────────────────────────────────────────

    public static class IosPreflightResult {
        public final String  teamId;
        public final String  iosVersion;
        public final String  wdaBundleId;
        public final boolean wdaCached;

        IosPreflightResult(String teamId, String iosVersion,
                           String wdaBundleId, boolean wdaCached) {
            this.teamId      = teamId;
            this.iosVersion  = iosVersion;
            this.wdaBundleId = wdaBundleId;
            this.wdaCached   = wdaCached;
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static IosPreflightResult runPreflight(
            BackendClient client, String executionId, String udid) {

        client.sendLog(executionId, "INFO",
                "🍎 ══════════════ iOS Pre-flight ══════════════");

        // 1. Xcode
        checkXcode(client, executionId);

        // 2. Apple Developer Team
        String teamId = detectAppleTeamId(client, executionId);

        // 3. iOS version (from device, not from Xcode)
        String iosVersion = detectIosVersion(client, executionId, udid);

        // 4. Developer Mode (warn only, non-blocking)
        checkDeveloperMode(client, executionId, udid);

        // 5. WDA cache — invalidated if iOS version changed
        Properties cache  = loadWdaCache(udid, iosVersion, client, executionId);
        String  wdaBundleId;
        boolean wdaCached;
        if (cache != null) {
            wdaBundleId = cache.getProperty("bundleId", generateWdaBundleId(udid));
            wdaCached   = true;
            client.sendLog(executionId, "INFO",
                    "✅ WebDriverAgent precompilado detectado — saltando compilación."
                    + "\n   bundle: " + wdaBundleId
                    + "\n   iOS:    " + cache.getProperty("iosVersion", "?")
                    + "\n   built:  " + cache.getProperty("builtAt", "?"));
        } else {
            wdaBundleId = generateWdaBundleId(udid);
            wdaCached   = false;
            client.sendLog(executionId, "INFO",
                    "🔨 WebDriverAgent se compilará e instalará automáticamente."
                    + "\n   bundle: " + wdaBundleId
                    + "\n   Appium firmará con Team ID: "
                    + (teamId.isBlank() ? "⚠️  no detectado" : teamId));
        }

        client.sendLog(executionId, "INFO",
                "🍎 ════════════ iOS Pre-flight completo ════════════");

        return new IosPreflightResult(teamId, iosVersion, wdaBundleId, wdaCached);
    }

    // ── 1. Xcode ──────────────────────────────────────────────────────────────

    private static void checkXcode(BackendClient client, String executionId) {
        try {
            Process p = new ProcessBuilder("xcodebuild", "-version")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(15, TimeUnit.SECONDS);
            if (out.startsWith("Xcode")) {
                client.sendLog(executionId, "INFO",
                        "✅ " + out.replace("\n", " | "));
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️  Xcode no encontrado. Instala desde App Store y ejecuta:\n"
                        + "   sudo xcode-select --switch /Applications/Xcode.app\n"
                        + "   xcodebuild -runFirstLaunch");
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  No se pudo verificar Xcode: " + e.getMessage());
        }
    }

    // ── 2. Apple Developer Team ───────────────────────────────────────────────

    public static String detectAppleTeamId(BackendClient client, String executionId) {
        try {
            Process p = new ProcessBuilder("security", "find-identity",
                    "-v", "-p", "codesigning")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);

            // "Apple Development: Full Name (TEAMID10)" — Team ID = 10 uppercase alphanum
            Pattern primary = Pattern.compile(
                    "\"Apple Development:[^(]+\\(([A-Z0-9]{10})\\)\"");
            Matcher m = primary.matcher(out);
            if (m.find()) {
                String id = m.group(1);
                client.sendLog(executionId, "INFO",
                        "✅ Apple Developer Team: " + id);
                return id;
            }

            // Older Xcode: "iPhone Developer: Name (TEAMID)"
            Pattern legacy = Pattern.compile(
                    "\"iPhone Developer:[^(]+\\(([A-Z0-9]{10})\\)\"");
            Matcher ml = legacy.matcher(out);
            if (ml.find()) {
                String id = ml.group(1);
                client.sendLog(executionId, "INFO",
                        "✅ Apple Developer Team (legacy): " + id);
                return id;
            }

            client.sendLog(executionId, "WARN",
                    "⚠️  Apple Developer Team no encontrado en el keychain.\n"
                    + "   Sin Team ID, WebDriverAgent no podrá firmarse.\n"
                    + "   Solución: Xcode → Settings → Accounts → agrega tu Apple ID\n"
                    + "   y acepta la licencia de desarrollador.");
            return "";
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  No se pudo detectar Team ID: " + e.getMessage());
            return "";
        }
    }

    // ── 3. iOS Version ────────────────────────────────────────────────────────

    public static String detectIosVersion(
            BackendClient client, String executionId, String udid) {
        if (udid == null || udid.isBlank()) return "";

        // xcrun devicectl (Xcode 14+ / macOS 14+) — most accurate
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            if (out.contains(udid)) {
                int idx = out.indexOf(udid);
                String region = out.substring(
                        Math.max(0, idx - 300), Math.min(out.length(), idx + 600));
                Pattern vp = Pattern.compile(
                        "\"osVersionNumber\"\\s*:\\s*\"([\\d.]+)\"");
                Matcher vm = vp.matcher(region);
                if (vm.find()) {
                    String v = vm.group(1);
                    client.sendLog(executionId, "INFO",
                            "📱 iOS " + v + " (vía devicectl)");
                    return v;
                }
            }
        } catch (Exception ignored) {}

        // Fallback: xcrun xctrace list devices
        // Output line: "iPhone 15 Pro (17.5.1) (00008130-XXXX)"
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            Pattern lp = Pattern.compile(
                    "\\(([\\d]+\\.[\\d.]+)\\)\\s*\\(\\s*"
                    + Pattern.quote(udid) + "\\s*\\)");
            Matcher lm = lp.matcher(out);
            if (lm.find()) {
                String v = lm.group(1);
                client.sendLog(executionId, "INFO",
                        "📱 iOS " + v + " (vía xctrace)");
                return v;
            }
            client.sendLog(executionId, "WARN",
                    "⚠️  No se pudo extraer la versión iOS del dispositivo "
                    + udid + " — se usará la configurada en appium.properties.");
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  xcrun no respondió: " + e.getMessage());
        }
        return "";
    }

    // ── 4. Developer Mode ─────────────────────────────────────────────────────

    private static void checkDeveloperMode(
            BackendClient client, String executionId, String udid) {
        if (udid == null || udid.isBlank()) return;
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            if (!out.contains(udid)) return;

            int idx = out.indexOf(udid);
            String region = out.substring(
                    Math.max(0, idx - 600), Math.min(out.length(), idx + 600));

            if (region.contains("\"developerModeEnabled\":true")
                    || region.contains("\"developerModeEnabled\": true")) {
                client.sendLog(executionId, "INFO",
                        "✅ Developer Mode activo en el dispositivo");
            } else if (region.contains("\"developerModeEnabled\":false")
                    || region.contains("\"developerModeEnabled\": false")) {
                client.sendLog(executionId, "WARN",
                        "⚠️  Developer Mode INACTIVO.\n"
                        + "   Actívalo: Ajustes → Privacidad y seguridad → Modo desarrollador\n"
                        + "   Sin él, xcodebuild no puede instalar WebDriverAgent.");
            }
        } catch (Exception ignored) {
            // devicectl not available (Xcode < 14) — skip silently
        }
    }

    // ── WDA bundle ID ─────────────────────────────────────────────────────────

    public static String generateWdaBundleId(String udid) {
        String suffix = udid.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (suffix.length() > 10) suffix = suffix.substring(0, 10);
        return "io.qautomation.wda." + suffix;
    }

    // ── WDA cache ─────────────────────────────────────────────────────────────

    /**
     * Loads the WDA cache for a device.
     * Returns null if:
     *  - Cache file does not exist (WDA never built)
     *  - iOS version changed since last build (WDA must be recompiled)
     */
    static Properties loadWdaCache(String udid, String currentIosVersion,
                                    BackendClient client, String executionId) {
        if (udid == null || udid.isBlank()) return null;
        File f = cacheFile(udid);
        if (!f.exists()) return null;
        try {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            }
            String cachedVersion = p.getProperty("iosVersion", "");
            if (!currentIosVersion.isBlank() && !cachedVersion.isBlank()
                    && !currentIosVersion.equals(cachedVersion)) {
                f.delete();
                client.sendLog(executionId, "INFO",
                        "♻️  iOS actualizado (" + cachedVersion + " → " + currentIosVersion
                        + ") — WDA se recompilará para esta versión.");
                return null;
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Saves WDA build record. Called by DriverFactory (Gradle process) after a
     * successful IOSDriver session creation to indicate WDA is installed on device.
     */
    public static void saveWdaCache(
            String udid, String bundleId, String teamId, String iosVersion) {
        if (udid == null || udid.isBlank()) return;
        try {
            new File(CACHE_DIR).mkdirs();
            Properties p = new Properties();
            p.setProperty("udid",       udid);
            p.setProperty("bundleId",   bundleId);
            p.setProperty("teamId",     teamId);
            p.setProperty("iosVersion", iosVersion);
            p.setProperty("builtAt",    String.valueOf(System.currentTimeMillis()));
            try (FileOutputStream out = new FileOutputStream(cacheFile(udid))) {
                p.store(out, "QAutomation WDA cache — do not edit manually");
            }
        } catch (Exception e) {
            System.err.println("[WdaCache] Save failed for " + udid + ": " + e.getMessage());
        }
    }

    private static File cacheFile(String udid) {
        String safe = udid.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(CACHE_DIR, safe + ".properties");
    }
}
