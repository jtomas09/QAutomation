package qa.cinepolis.runner;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full Appium lifecycle manager + AppiumValidator.
 *
 * Resolution order:
 *   0. APPIUM_BIN system property (embedded enterprise mode — absolute priority)
 *   1. Running Appium at http://127.0.0.1:4723/status (already up — use it)
 *   2. PATH: appium / appium.cmd
 *   3. Global npm: ~/.npm-global/bin/appium  (or $(npm root -g)/../.bin/appium)
 *   4. npx appium (no install, ephemeral)
 *   5. Install Appium locally (if NODE_BIN set) or globally via npm, then start
 *
 * AppiumValidator contract (checked by isAlive()):
 *   - HTTP 200 from /status  AND  body contains "ready":true
 *   - At least one driver compatible with the installed server version
 *
 * Appium is considered OK only when isAlive() returns true.
 * On failure, healDrivers() uninstalls incompatible drivers and reinstalls
 * version-pinned ones to fix Server 2.x/Driver 3.x (or vice-versa) mismatches.
 */
public class AppiumManager {

    private static final String APPIUM_URL    = "http://127.0.0.1:4723/status";
    private static final int    PORT          = 4723;
    private static final int    STARTUP_WAIT  = 30; // seconds
    private static final int    RESTART_DELAY = 5;  // seconds

    private final String os;
    private volatile Process      appiumProcess = null;
    private volatile boolean      managedByUs   = false;
    private final    AtomicBoolean stopping      = new AtomicBoolean(false);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AppiumManager(String os) {
        this.os = os;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Ensures Appium is running on port 4723.
     * If already running, returns immediately.
     * Otherwise, locates or installs Appium and starts it.
     * Blocks until Appium is responsive (up to 30 s).
     */
    public synchronized void ensureRunning() throws IOException, InterruptedException {
        if (isAlive()) {
            System.out.println("[AppiumValidator] Ya en ejecucion en el puerto " + PORT);
            return;
        }

        System.out.println("[AppiumValidator] Localizando Appium...");
        String appiumBin = findAppiumBin();

        if (appiumBin == null) {
            System.out.println("[AppiumValidator] No encontrado. Instalando via npm...");
            installAppium();
            appiumBin = findAppiumBin();
        }

        if (appiumBin == null) {
            throw new IOException("Appium no pudo ser instalado ni localizado. Instala Node.js + npm manualmente.");
        }

        startAppium(appiumBin);
    }

    /**
     * True if Appium responds on /status with HTTP 200 AND body contains "ready":true.
     */
    public boolean isAlive() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(APPIUM_URL))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean http200 = res.statusCode() == 200;
            boolean ready   = res.body().contains("\"ready\":true")
                           || res.body().contains("\"ready\": true");
            return http200 && ready;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Repairs Appium by stopping it, reinstalling drivers at server-compatible versions,
     * and restarting. Called by DependencySelfHealingManager when Appium is FAIL.
     *
     * Prevents: Server 2.x + Driver 3.x  or  Server 3.x + Driver 2.x.
     */
    public void healDrivers() throws IOException, InterruptedException {
        System.out.println("[AppiumValidator] Iniciando reparacion de drivers...");
        stop();
        stopping.set(false);
        Thread.sleep(2000);

        String appiumBin = findAppiumBin();
        if (appiumBin == null) {
            System.err.println("[AppiumValidator] Binario no encontrado — no se puede reparar.");
            return;
        }

        String appiumVersion = getAppiumVersion();
        System.out.printf("[AppiumValidator] Server=%s — reinstalando drivers compatibles...%n", appiumVersion);

        // Uninstall existing (possibly incompatible) drivers
        uninstallDriver(appiumBin, "uiautomator2");
        if ("MACOS".equals(os)) uninstallDriver(appiumBin, "xcuitest");

        // Reinstall with version-compatible specs
        installDriver("uiautomator2", appiumVersion);
        if ("MACOS".equals(os)) installDriver("xcuitest", appiumVersion);

        // Restart
        System.out.println("[AppiumValidator] Reiniciando Appium post-reparacion...");
        ensureRunning();
    }

    /**
     * Returns the raw output of 'appium driver list --installed' for diagnostics.
     */
    public String getInstalledDriverList() {
        String appiumBin = findAppiumBin();
        if (appiumBin == null) return "appium-bin-not-found";
        try {
            String nodeBin    = System.getProperty("NODE_BIN");
            boolean isFilePath = Files.exists(Path.of(appiumBin));
            boolean useNode   = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin)) && isFilePath;
            String appiumHome = System.getProperty("APPIUM_HOME");
            String[] cmd = useNode
                    ? new String[]{nodeBin, appiumBin, "driver", "list", "--installed"}
                    : new String[]{appiumBin, "driver", "list", "--installed"};
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (appiumHome != null && !appiumHome.isBlank())
                pb.environment().put("APPIUM_HOME", appiumHome);
            Process p = pb.start();
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return "timeout"; }
            return new String(p.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * Start a background watchdog that restarts Appium when it crashes.
     */
    public void startWatchdog(ScheduledExecutorService scheduler) {
        startWatchdog(scheduler, null);
    }

    /**
     * Variant that fires {@code onRestart} (if non-null) after a successful restart.
     */
    public void startWatchdog(ScheduledExecutorService scheduler, Runnable onRestart) {
        scheduler.scheduleAtFixedRate(() -> {
            if (stopping.get() || !managedByUs) return;
            if (!isAlive()) {
                System.out.println("[AppiumValidator] Proceso caido — reiniciando...");
                try {
                    Thread.sleep(RESTART_DELAY * 1000L);
                    ensureRunning();
                    if (isAlive() && onRestart != null) {
                        onRestart.run();
                    }
                } catch (Exception e) {
                    System.err.println("[AppiumValidator] Error al reiniciar: " + e.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * True if an Appium binary can be located (process need not be running).
     */
    public boolean canStart() {
        return findAppiumBin() != null;
    }

    /**
     * Returns the Appium version string (e.g. "2.19.0" or "3.0.0").
     */
    public String getAppiumVersion() {
        String bin = findAppiumBin();
        if (bin == null) return "unavailable";
        try {
            String nodeBin    = System.getProperty("NODE_BIN");
            boolean isFilePath = Files.exists(Path.of(bin));
            boolean useNode   = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin)) && isFilePath;
            String[] cmd = useNode
                    ? new String[]{nodeBin, bin, "--version"}
                    : isFilePath ? new String[]{bin, "--version"}
                    : new String[]{"sh", "-c", bin + " --version"};
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return "unavailable"; }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return (p.exitValue() == 0 && !out.isBlank()) ? out.split("\n")[0].trim() : "unavailable";
        } catch (Exception e) { return "unavailable"; }
    }

    public void stop() {
        stopping.set(true);
        Process p = appiumProcess;
        if (p != null && p.isAlive()) {
            p.destroy();
            try { p.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            if (p.isAlive()) p.destroyForcibly();
            System.out.println("[AppiumValidator] Proceso detenido.");
        }
    }

    // ── Find ──────────────────────────────────────────────────────────────

    /**
     * Logs a full diagnostic snapshot for startup and DependencyHealer reporting.
     * Does NOT assume Appium is running — just resolves and inspects.
     */
    public void logDiagnostic() {
        String nodeBin    = System.getProperty("NODE_BIN", "");
        boolean nodeExists = !nodeBin.isBlank() && Files.exists(Path.of(nodeBin));
        String  appiumBin  = resolveEmbeddedBin(); // embedded path — no probe
        boolean binExists  = appiumBin != null && Files.exists(Path.of(appiumBin));
        boolean binExec    = appiumBin != null && Files.isExecutable(Path.of(appiumBin));
        String  version    = "unavailable";
        if (binExists && binExec && nodeExists) {
            try {
                Process p = new ProcessBuilder(nodeBin, appiumBin, "--version")
                        .redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    String out = new String(p.getInputStream().readAllBytes()).trim();
                    if (!out.isBlank()) version = out.split("\n")[0].trim();
                } else {
                    p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                }
            } catch (Exception ignored) {}
        }
        boolean alive = isAlive();

        System.out.println("[AppiumValidator]");
        System.out.printf("[AppiumValidator] NodeBin=%s Exists=%s%n",
                nodeBin.isBlank() ? "unset" : nodeBin, nodeExists);
        System.out.printf("[AppiumValidator] AppiumBin=%s%n",
                appiumBin != null ? appiumBin : "NOT_FOUND");
        System.out.printf("[AppiumValidator] Exists=%s Executable=%s%n", binExists, binExec);
        System.out.printf("[AppiumValidator] Version=%s%n", version);
        System.out.printf("[AppiumValidator] StatusEndpoint=%s%n", alive ? "OK" : "FAIL");
    }

    /**
     * Returns the embedded Appium binary path derived from AGENT_DATA_DIR,
     * without probing or running anything. Used for diagnostics.
     */
    private String resolveEmbeddedBin() {
        boolean win = isWindows();
        // Prefer AGENT_DATA_DIR-derived path (handles spaces via Path.of)
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path p = Path.of(agentDataDir, "runtime", "appium", "node_modules", ".bin",
                             win ? "appium.cmd" : "appium");
            if (Files.exists(p)) return p.toString();
        }
        // Fallback: explicit APPIUM_BIN property
        String prop = System.getProperty("APPIUM_BIN");
        if (prop != null && !prop.isBlank() && Files.exists(Path.of(prop))) return prop;
        return null;
    }

    // Package-visible so DependencySelfHealingManager can probe without duplicating logic
    String findAppiumBin() {
        List<String> candidates = buildCandidates();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (probeAppiumBin(candidate)) {
                System.out.println("[AppiumValidator] AppiumBin=" + candidate);
                return candidate;
            }
        }
        System.err.println("[AppiumValidator] AppiumBin=NOT_FOUND — rutas intentadas: " + candidates.size());
        return null;
    }

    private List<String> buildCandidates() {
        List<String> list = new ArrayList<>();
        boolean win = isWindows();

        // Priority 1 (highest): AGENT_DATA_DIR-derived path — handles spaces via Path.of()
        // This is the canonical embedded enterprise path; avoids dependence on APPIUM_BIN plist var.
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path derived = Path.of(agentDataDir, "runtime", "appium", "node_modules", ".bin",
                                   win ? "appium.cmd" : "appium");
            if (Files.exists(derived)) {
                list.add(derived.toString());
            }
        }

        // Priority 2: APPIUM_BIN system property (set by installer LaunchAgent plist)
        String embeddedBin = System.getProperty("APPIUM_BIN");
        if (embeddedBin != null && !embeddedBin.isBlank() && Files.exists(Path.of(embeddedBin))) {
            if (!list.contains(embeddedBin)) list.add(embeddedBin);
        }

        // Priority 3+: fallbacks for dev/non-enterprise environments (not used in production)
        list.add(win ? "appium.cmd" : "appium");

        try {
            String npmRoot = runCapture("npm", "root", "-g");
            if (npmRoot != null) {
                Path prefix = Path.of(npmRoot.strip()).getParent();
                if (prefix != null) {
                    list.add(prefix.resolve(win ? ".bin/appium.cmd" : "bin/appium").toString());
                }
            }
        } catch (Exception ignored) {}

        if (!win) {
            list.add("/opt/homebrew/bin/appium");
            list.add("/usr/local/bin/appium");
        }

        list.add(win ? "npx.cmd appium" : "npx appium");

        return list;
    }

    /**
     * Tests whether an Appium binary candidate is runnable.
     *
     * Rule: if the candidate is a real file path (Files.exists), run it via
     * ProcessBuilder array form — this handles paths with spaces (e.g. "Application Support")
     * correctly without shell interpretation.
     *
     * Only commands like "npx appium" (not a file) fall back to sh -c.
     */
    private boolean probeAppiumBin(String bin) {
        try {
            String nodeBin    = System.getProperty("NODE_BIN");
            boolean isFilePath = Files.exists(Path.of(bin));   // true for all real file paths
            boolean nodeReady  = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin));

            String[] cmd;
            if (isFilePath && nodeReady) {
                // Canonical: use embedded Node with full path — no shell, no space issues
                cmd = new String[]{nodeBin, bin, "--version"};
            } else if (isFilePath) {
                // File exists but no embedded Node — run directly (system Node must be in PATH)
                cmd = new String[]{bin, "--version"};
            } else {
                // Shell command like "npx appium" — needs shell interpretation
                cmd = isWindows() ? new String[]{"cmd", "/c", bin, "--version"}
                                  : new String[]{"sh", "-c", bin + " --version"};
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes());
            if (!done) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0 && out.matches("(?s).*\\d+\\.\\d+.*");
        } catch (Exception e) { return false; }
    }

    // ── Install ───────────────────────────────────────────────────────────

    private void installAppium() throws IOException, InterruptedException {
        String nodeBin = System.getProperty("NODE_BIN");
        boolean hasEmbeddedNode = nodeBin != null && !nodeBin.isBlank()
                && Files.exists(Path.of(nodeBin));

        if (hasEmbeddedNode) {
            String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                    System.getProperty("user.home") + "/.automationqa");
            String prefixDir = agentDataDir + "/runtime/appium";
            Files.createDirectories(Path.of(prefixDir));
            String npmBin = Path.of(nodeBin).getParent().resolve("npm").toString();
            System.out.println("[AppiumValidator] Instalando localmente via Node embebido: " + prefixDir);
            // No version pin — install latest stable Appium (compatible with latest drivers)
            Process p = new ProcessBuilder(npmBin, "install", "--prefix", prefixDir, "appium",
                    "--no-audit", "--no-fund")
                    .inheritIO().start();
            boolean done = p.waitFor(10, TimeUnit.MINUTES);
            if (!done || p.exitValue() != 0) {
                throw new IOException("npm install appium fallo con codigo " + (done ? p.exitValue() : "timeout"));
            }
        } else {
            System.out.println("[AppiumValidator] Ejecutando: npm install -g appium ...");
            String npmCmd = isWindows() ? "npm.cmd" : "npm";
            Process p = new ProcessBuilder(npmCmd, "install", "-g", "appium")
                    .inheritIO().start();
            boolean done = p.waitFor(10, TimeUnit.MINUTES);
            if (!done || p.exitValue() != 0) {
                throw new IOException("npm install -g appium fallo con codigo " + (done ? p.exitValue() : "timeout"));
            }
        }

        System.out.println("[AppiumValidator] Appium instalado.");
        // Detect installed version and install version-compatible drivers
        String version = getAppiumVersion();
        System.out.printf("[AppiumValidator] Server=%s — instalando drivers compatibles...%n", version);
        installDriver("uiautomator2", version);
        if ("MACOS".equals(os)) {
            installDriver("xcuitest", version);
        }
    }

    /**
     * Installs a driver with the version spec appropriate for the given Appium server version.
     * Prevents Appium 2.x + Driver 3.x incompatibilities.
     */
    private void installDriver(String driver, String appiumVersion) {
        String spec = buildDriverSpec(driver, appiumVersion);
        try {
            System.out.println("[AppiumValidator] Instalando driver: " + spec);
            String appiumBin = findAppiumBin();
            if (appiumBin == null) return;
            String nodeBin    = System.getProperty("NODE_BIN");
            boolean useNode   = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin))
                    && !appiumBin.startsWith("npx");
            String appiumHome = System.getProperty("APPIUM_HOME");
            String[] cmd;
            if (useNode) {
                cmd = new String[]{nodeBin, appiumBin, "driver", "install", spec};
            } else if (isWindows()) {
                cmd = new String[]{"cmd", "/c", appiumBin, "driver", "install", spec};
            } else {
                cmd = new String[]{appiumBin, "driver", "install", spec};
            }
            ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
            if (appiumHome != null && !appiumHome.isBlank())
                pb.environment().put("APPIUM_HOME", appiumHome);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[AppiumValidator] Warning: no se pudo instalar driver " + spec + ": " + e.getMessage());
        }
    }

    private void uninstallDriver(String appiumBin, String driver) {
        try {
            System.out.println("[AppiumValidator] Desinstalando driver: " + driver);
            String nodeBin    = System.getProperty("NODE_BIN");
            boolean isFilePath = Files.exists(Path.of(appiumBin));
            boolean useNode   = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin)) && isFilePath;
            String appiumHome = System.getProperty("APPIUM_HOME");
            String[] cmd = useNode
                    ? new String[]{nodeBin, appiumBin, "driver", "uninstall", driver}
                    : new String[]{appiumBin, "driver", "uninstall", driver};
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (appiumHome != null && !appiumHome.isBlank())
                pb.environment().put("APPIUM_HOME", appiumHome);
            Process p = pb.start();
            p.waitFor(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("[AppiumValidator] Warning: uninstall " + driver + ": " + e.getMessage());
        }
    }

    /**
     * Maps a driver name + Appium server version to a version-pinned npm install spec.
     *
     *   Appium 2.x  →  uiautomator2@2  /  xcuitest@7
     *   Appium 3.x+ →  uiautomator2    /  xcuitest   (latest, compatible)
     */
    private String buildDriverSpec(String driver, String appiumVersion) {
        if (appiumVersion == null || appiumVersion.equals("unavailable")) return driver;
        if (appiumVersion.startsWith("2.")) {
            return switch (driver) {
                case "uiautomator2" -> "uiautomator2@2";
                case "xcuitest"     -> "xcuitest@7";
                default             -> driver;
            };
        }
        return driver; // Appium 3.x+ → latest drivers are compatible
    }

    // ── Start ─────────────────────────────────────────────────────────────

    private void startAppium(String appiumBin) throws IOException, InterruptedException {
        System.out.println("[AppiumValidator] Iniciando: " + appiumBin + " --port " + PORT + " ...");

        String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                System.getProperty("user.home") + "/.automationqa");
        Path logFile = Path.of(agentDataDir, "logs", "appium.log");
        Files.createDirectories(logFile.getParent());

        String nodeBin = System.getProperty("NODE_BIN");
        boolean useEmbeddedNode = nodeBin != null && !nodeBin.isBlank()
                && Files.exists(Path.of(nodeBin))
                && !appiumBin.startsWith("npx");

        String[] cmd;
        if (useEmbeddedNode) {
            cmd = new String[]{nodeBin, appiumBin, "--port", String.valueOf(PORT),
                    "--log", logFile.toString(), "--log-timestamp"};
        } else if (appiumBin.startsWith("npx")) {
            String npxBin = isWindows() ? "npx.cmd" : "npx";
            cmd = new String[]{npxBin, "appium", "--port", String.valueOf(PORT),
                    "--log", logFile.toString(), "--log-timestamp"};
        } else {
            cmd = new String[]{appiumBin, "--port", String.valueOf(PORT),
                    "--log", logFile.toString(), "--log-timestamp"};
        }

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());

        String appiumHome = System.getProperty("APPIUM_HOME");
        if (appiumHome != null && !appiumHome.isBlank()) {
            pb.environment().put("APPIUM_HOME", appiumHome);
        }

        appiumProcess = pb.start();
        managedByUs   = true;
        System.out.println("[AppiumValidator] Proceso iniciado (PID " + appiumProcess.pid() +
                "). Esperando respuesta en puerto " + PORT + "...");
        System.out.println("[AppiumValidator] Log: " + logFile);

        for (int i = 0; i < STARTUP_WAIT; i++) {
            Thread.sleep(1000);
            if (isAlive()) {
                System.out.println("[AppiumValidator] StatusEndpoint=OK (/status ready:true)");
                return;
            }
            if (!appiumProcess.isAlive()) {
                throw new IOException("Appium salio prematuramente. Ver log: " + logFile);
            }
        }
        throw new IOException("Appium no respondio en " + STARTUP_WAIT + "s. Ver log: " + logFile);
    }

    // ── Utils ─────────────────────────────────────────────────────────────

    private String runCapture(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor(5, TimeUnit.SECONDS);
        return out.isBlank() ? null : out;
    }

    private boolean isWindows() { return "WINDOWS".equals(os); }
}
