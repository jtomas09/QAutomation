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
    // Nombre amigable real del equipo (el configurado por el usuario en el SO) —
    // distinto de `hostname`, que en redes sin PTR/mDNS confiable puede resolver
    // a una IP cruda (evidencia real: hostname="192.168.1.11" en producción).
    // Se envía como campo ADICIONAL al backend (ver BackendClient.sendHeartbeat) —
    // nunca reemplaza a `hostname`, que sigue disponible tal cual para diagnóstico.
    public String  computerName;
    public boolean androidSupported;
    public boolean iosSupported;

    // ── Agent data & Appium ────────────────────────────────────────────────
    public String  agentDataDir;   // root dir for downloaded tools, logs, updates
    public int     appiumPort;     // default 4723
    public int     streamPort;     // Device Stream Service port (default 8082)

    // ── Auto-managed workspace ────────────────────────────────────────────
    // repoUrl / repoBranch / projectName are NOT stored locally.
    // They are fetched from GET /api/runner/config before every job.
    public String  repoUrl;        // set at startup for banner display only — not persisted
    public String  repoBranch;     // set at startup for banner display only — not persisted
    public String  projectName;    // set at startup for banner display only — not persisted
    public String  workspaceDir;   // {agentDataDir}/workspace — workspace root (auto-set)

    public static RunnerConfig fromEnv() {
        RunnerConfig c = new RunnerConfig();

        // Core
        c.backendUrl     = env("BACKEND_URL",      "https://qautomation-production.up.railway.app");
        c.runnerToken    = env("RUNNER_TOKEN",      "runner-local-token");
        c.pollIntervalMs = Long.parseLong(env("POLL_INTERVAL_MS", "5000"));
        c.workDir        = env("WORK_DIR",          ".");
        c.appiumHub      = env("APPIUM_HUB",        "http://127.0.0.1:4723");
        c.allureBaseUrl  = env("ALLURE_BASE_URL",   "");
        c.version        = env("RUNNER_VERSION",    "2.3.0");
        c.appiumPort     = Integer.parseInt(env("APPIUM_PORT",  "4723"));
        c.streamPort     = Integer.parseInt(env("STREAM_PORT", "8082"));

        // Agent data directory (user-level, no admin required)
        String home = System.getProperty("user.home", ".");
        String defaultDataDir = isWindows()
                ? System.getenv().getOrDefault("LOCALAPPDATA", home + "\\AppData\\Local") + "\\AutomationQA"
                : home + "/.automationqa";
        c.agentDataDir   = env("AGENT_DATA_DIR", defaultDataDir);
        c.workspaceDir   = c.agentDataDir + File.separator + "workspace";
        // repoUrl / repoBranch / projectName are populated at runtime from GET /api/runner/config

        // Auto-detect OS and hostname
        c.os           = detectOs();
        c.hostname     = detectHostname();
        c.computerName = detectComputerName(c.os);
        // Consumido por BackendClient.sendHeartbeat() — mismo patrón ya usado por
        // ADB_PATH/ADB_OK (ver DependencySelfHealingManager) para campos opcionales
        // del heartbeat sin cambiar la firma de sendHeartbeat() en sus 5 llamadores.
        System.setProperty("COMPUTER_NAME", c.computerName);

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

    /**
     * Nombre real del equipo tal como lo ve el sistema operativo (Ajustes del
     * sistema en macOS, "Nombre de PC" en Windows) — nunca una IP. Si el comando
     * nativo falla por cualquier motivo, cae a detectHostname() (mismo
     * comportamiento que existía antes de este campo, nunca empeora nada).
     */
    private static String detectComputerName(String os) {
        try {
            if ("MACOS".equals(os)) {
                String name = runShort("scutil", "--get", "ComputerName");
                if (name != null && !name.isBlank()) return name;
            } else if ("WINDOWS".equals(os)) {
                String env = System.getenv("COMPUTERNAME");
                if (env != null && !env.isBlank()) return env;
            } else {
                String name = runShort("hostnamectl", "--static");
                if (name != null && !name.isBlank()) return name;
            }
        } catch (Exception ignored) {}
        return detectHostname();
    }

    /** Ejecuta un comando corto y de solo lectura, devolviendo su primera línea de salida (o null). */
    private static String runShort(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                out = reader.readLine();
            }
            p.waitFor(3, TimeUnit.SECONDS);
            return out == null ? null : out.trim();
        } catch (Exception e) {
            return null;
        }
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
     * Probes for the embedded ADB binary only.
     * The Agent never depends on Android Studio, ANDROID_HOME, or PATH.
     * If the embedded path doesn't exist yet, PlatformToolsManager will
     * download platform-tools at startup and override androidSupported=true.
     */
    static boolean probeAndroid() {
        for (String candidate : buildAdbCandidates()) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                Process p = new ProcessBuilder(candidate, "version")
                        .redirectErrorStream(true).start();
                boolean done = p.waitFor(3, TimeUnit.SECONDS);
                p.getInputStream().readAllBytes();
                if (done && p.exitValue() == 0) {
                    System.out.println("[Runner] ADB embebido disponible: " + candidate);
                    return true;
                }
                p.destroyForcibly();
            } catch (Exception ignored) {}
        }
        System.out.println("[Runner] ADB embebido no encontrado — se descargara al iniciar.");
        return false;
    }

    private static List<String> buildAdbCandidates() {
        // Only the embedded location — never PATH, ANDROID_HOME, or Android Studio
        List<String> list = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        if (isWindows()) {
            String localAppData = System.getenv().getOrDefault("LOCALAPPDATA",
                    home + "\\AppData\\Local");
            list.add(localAppData + "\\AutomationQA\\runner\\platform-tools\\adb.exe");
        } else {
            list.add(home + "/.automationqa/platform-tools/adb");
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
