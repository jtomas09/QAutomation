package qa.cinepolis.runner.model;

public class RunnerConfig {

    public String backendUrl;
    public String runnerToken;
    public long   pollIntervalMs;
    public String workDir;        // cwd for the test Maven project
    public String appiumHub;      // Appium server URL
    public String allureBaseUrl;  // base URL for published Allure reports

    public static RunnerConfig fromEnv() {
        RunnerConfig c = new RunnerConfig();
        c.backendUrl     = env("BACKEND_URL",      "https://qautomation-production.up.railway.app");
        c.runnerToken    = env("RUNNER_TOKEN",      "runner-local-token");
        c.pollIntervalMs = Long.parseLong(env("POLL_INTERVAL_MS", "5000"));
        c.workDir        = env("WORK_DIR",          ".");
        c.appiumHub      = env("APPIUM_HUB",        "http://127.0.0.1:4723");
        c.allureBaseUrl  = env("ALLURE_BASE_URL",   "");
        return c;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
