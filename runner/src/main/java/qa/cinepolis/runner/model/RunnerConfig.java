package qa.cinepolis.runner.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Universal Runner configuration — auto-detects OS, capabilities, and hostname.
 * No manual env-var configuration required for device discovery.
 */
public class RunnerConfig {

    // ── Core config ────────────────────────────────────────────────────────
    public String  backendUrl;
    public String  runnerToken;
    public long    pollIntervalMs;
    public String  workDir;
    public String  appiumHub;
    public String  allureBaseUrl;
    public String  runnerId;
    public String  platform;      // "android" | "ios" — legacy routing compat
    public String  version;

    // ── Universal Runner capabilities (auto-detected at startup) ──────────
    public String  os;             // WINDOWS | MACOS | LINUX
    public String  hostname;
    public boolean androidSupported;
    public boolean iosSupported;

    public static RunnerConfig fromEnv() {
        RunnerConfig c = new RunnerConfig();

        // Core
        c.backendUrl     = env("BACKEND_URL",      "https://qautomation-production.up.railway.app");
        c.runnerToken    = env("RUNNER_TOKEN",      "runner-local-token");
        c.pollIntervalMs = Long.parseLong(env("POLL_INTERVAL_MS", "5000"));
        c.workDir        = env("WORK_DIR",          ".");
        c.appiumHub      = env("APPIUM_HUB",        "http://127.0.0.1:4723");
        c.allureBaseUrl  = env("ALLURE_BASE_URL",   "");
        c.version        = env("RUNNER_VERSION",    "2.2.0");

        // Auto-detect OS and hostname
        c.os       = detectOs();
        c.hostname = detectHostname();

        // Auto-detect capabilities (probed at startup)
        c.androidSupported = probeAndroid();
        c.iosSupported     = "MACOS".equals(c.os) && probeIos();

        // runnerId: env override, or auto-generate from OS + hostname
        c.runnerId = env("RUNNER_ID", generateDefaultId(c.os, c.hostname));

        // platform: legacy field for job routing. Auto-set based on capabilities.
        // Can still be overridden via RUNNER_PLATFORM env var.
        String defaultPlatform = c.iosSupported ? "android" : "android";
        c.platform = env("RUNNER_PLATFORM", defaultPlatform);

        return c;
    }

    // ── Capability reporting for dashboard display ─────────────────────────

    public String capabilitySummary() {
        List<String> caps = new ArrayList<>();
        if (androidSupported) caps.add("Android");
        if (iosSupported)     caps.add("iOS");
        return caps.isEmpty() ? "Sin capacidades detectadas" : String.join(" + ", caps);
    }

    // ── Detection helpers ──────────────────────────────────────────────────

    public static String detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win"))  return "WINDOWS";
        if (name.contains("mac"))  return "MACOS";
        return "LINUX";
    }

    private static String detectHostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown-host"; }
    }

    private static String generateDefaultId(String os, String hostname) {
        String prefix = switch (os) {
            case "WINDOWS" -> "win";
            case "MACOS"   -> "mac";
            default        -> "linux";
        };
        String clean = hostname.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return prefix + "-" + clean;
    }

    /**
     * Probes for a working ADB binary.
     * Tries PATH first, then ANDROID_HOME, then common SDK install locations.
     */
    static boolean probeAndroid() {
        for (String candidate : buildAdbCandidates()) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                Process p = new ProcessBuilder(candidate, "version")
                        .redirectErrorStream(true).start();
                boolean done = p.waitFor(3, TimeUnit.SECONDS);
                p.getInputStream().readAllBytes(); // drain to avoid buffer deadlock
                if (done && p.exitValue() == 0) {
                    System.out.println("[Runner] Android soportado (ADB: " + candidate + ")");
                    return true;
                }
                p.destroyForcibly();
            } catch (Exception ignored) {}
        }
        System.out.println("[Runner] Android no detectado (ADB no encontrado en PATH ni en SDK)");
        return false;
    }

    private static List<String> buildAdbCandidates() {
        List<String> list = new ArrayList<>();
        list.add("adb");  // trust PATH first

        String androidHome = env("ANDROID_HOME", null);
        if (androidHome != null) {
            list.add(androidHome + File.separator + "platform-tools" + File.separator
                    + (isWindows() ? "adb.exe" : "adb"));
        }

        String home = System.getProperty("user.home", "");
        if (isWindows()) {
            list.add(home + "\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe");
            list.add("C:\\Android\\platform-tools\\adb.exe");
            list.add("C:\\Program Files\\Android\\platform-tools\\adb.exe");
        } else {
            list.add(home + "/Library/Android/sdk/platform-tools/adb");
            list.add(home + "/Android/Sdk/platform-tools/adb");
            list.add("/usr/local/android-sdk/platform-tools/adb");
        }
        return list;
    }

    /**
     * Probes for Xcode command-line tools (xcrun) — macOS only.
     * Returns true only when xcrun is available and working.
     */
    static boolean probeIos() {
        try {
            Process p = new ProcessBuilder("xcrun", "--version")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            p.getInputStream().readAllBytes();
            if (done && p.exitValue() == 0) {
                System.out.println("[Runner] iOS soportado (Xcode/xcrun disponible)");
                return true;
            }
            p.destroyForcibly();
        } catch (Exception ignored) {}
        System.out.println("[Runner] iOS no detectado (xcrun no disponible — solo macOS con Xcode)");
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Reads from JVM system property (-Dkey=val) first, then OS env var, then default. */
    private static String env(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
