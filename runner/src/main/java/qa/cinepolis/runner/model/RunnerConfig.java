package qa.cinepolis.runner.model;

import java.util.UUID;

public class RunnerConfig {

    public String backendUrl;
    public String runnerToken;
    public long   pollIntervalMs;
    public String workDir;
    public String appiumHub;
    public String allureBaseUrl;
    public String runnerId;
    public String platform;   // android | ios | auto (auto-detected)
    public String version;

    public static RunnerConfig fromEnv() {
        RunnerConfig c = new RunnerConfig();
        c.backendUrl     = env("BACKEND_URL",      "https://qautomation-production.up.railway.app");
        c.runnerToken    = env("RUNNER_TOKEN",      "runner-local-token");
        c.pollIntervalMs = Long.parseLong(env("POLL_INTERVAL_MS", "5000"));
        c.workDir        = env("WORK_DIR",          ".");
        c.appiumHub      = env("APPIUM_HUB",        "http://127.0.0.1:4723");
        c.allureBaseUrl  = env("ALLURE_BASE_URL",   "");
        c.runnerId       = env("RUNNER_ID",         generateDefaultId());
        c.platform       = env("RUNNER_PLATFORM",   detectPlatform());
        c.version        = env("RUNNER_VERSION",    "2.2.0");
        return c;
    }

    private static String generateDefaultId() {
        String os = System.getProperty("os.name", "unknown").toLowerCase();
        String hostname = "unknown";
        try { hostname = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) {}
        String prefix = os.contains("win") ? "win" : os.contains("mac") ? "mac" : "linux";
        return prefix + "-" + hostname.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        // iOS runners must be on macOS; Windows runners are Android-only
        return os.contains("mac") ? "android" : "android";
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
