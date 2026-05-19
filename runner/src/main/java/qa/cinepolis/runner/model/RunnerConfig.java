package qa.cinepolis.runner.model;

public class RunnerConfig {

    public String backendUrl;
    public String runnerToken;
    public long   pollIntervalMs;
    public String testCommand;
    public String workDir;

    public static RunnerConfig fromEnv() {
        RunnerConfig c = new RunnerConfig();
        c.backendUrl     = env("BACKEND_URL",      "https://qautomation-production.up.railway.app");
        c.runnerToken    = env("RUNNER_TOKEN",     "runner-local-token");
        c.pollIntervalMs = Long.parseLong(env("POLL_INTERVAL_MS", "5000"));
        c.testCommand    = env("TEST_COMMAND",     "./gradlew test");
        c.workDir        = env("WORK_DIR",         ".");
        return c;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
