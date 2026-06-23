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
 * Resolution strategy:
 *   Node:   AGENT_DATA_DIR/runtime/node/bin/node  →  NODE_BIN prop  →  system node
 *   Appium: AGENT_DATA_DIR/runtime/appium/node_modules/appium/index.js  →  APPIUM_BIN prop
 *
 * All Appium commands run as:  node  appium/index.js  [args...]
 * Never uses PATH, which appium, npm global, or .bin/appium shell wrappers.
 */
public class AppiumManager {

    private static final String APPIUM_URL    = "http://127.0.0.1:4723/status";
    private static final int    PORT          = 4723;
    private static final int    STARTUP_WAIT  = 30; // seconds
    private static final int    RESTART_DELAY = 5;  // seconds

    private final String os;
    private volatile Process       appiumProcess = null;
    private volatile boolean       managedByUs   = false;
    private final    AtomicBoolean stopping      = new AtomicBoolean(false);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AppiumManager(String os) { this.os = os; }

    // ── Public API ────────────────────────────────────────────────────────

    public synchronized void ensureRunning() throws IOException, InterruptedException {
        if (isAlive()) {
            System.out.println("[AppiumValidator] Ya en ejecucion en el puerto " + PORT);
            return;
        }

        System.out.println("[AppiumValidator] Localizando Appium...");
        String entryPoint = findAppiumEntryPoint();

        if (entryPoint == null) {
            System.out.println("[AppiumValidator] No encontrado. Instalando via npm...");
            installAppium();
            entryPoint = findAppiumEntryPoint();
        }

        if (entryPoint == null) {
            throw new IOException("Appium no pudo ser instalado ni localizado.");
        }

        startAppium(entryPoint);
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
        } catch (Exception e) { return false; }
    }

    /**
     * Repairs Appium by stopping it, reinstalling drivers at server-compatible versions,
     * and restarting. Called by DependencySelfHealingManager when Appium is FAIL.
     */
    public void healDrivers() throws IOException, InterruptedException {
        System.out.println("[AppiumValidator] Iniciando reparacion de drivers...");
        stop();
        stopping.set(false);
        Thread.sleep(2000);

        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) {
            System.err.println("[AppiumValidator] Entry point no encontrado — no se puede reparar.");
            return;
        }

        String appiumVersion = getAppiumVersion();
        System.out.printf("[AppiumValidator] Server=%s — reinstalando drivers compatibles...%n", appiumVersion);

        uninstallDriver(entryPoint, "uiautomator2");
        if ("MACOS".equals(os)) uninstallDriver(entryPoint, "xcuitest");

        installDriver("uiautomator2", appiumVersion);
        if ("MACOS".equals(os)) installDriver("xcuitest", appiumVersion);

        System.out.println("[AppiumValidator] Reiniciando Appium post-reparacion...");
        ensureRunning();
    }

    /**
     * Returns the raw output of 'appium driver list --installed' for diagnostics.
     */
    public String getInstalledDriverList() {
        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) return "appium-entry-point-not-found";
        try {
            ProcessBuilder pb = withAppiumHome(
                    new ProcessBuilder(nodeCmd(entryPoint, "driver", "list", "--installed"))
                            .redirectErrorStream(true));
            Process p = pb.start();
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return "timeout"; }
            return new String(p.getInputStream().readAllBytes()).trim();
        } catch (Exception e) { return "error: " + e.getMessage(); }
    }

    public void startWatchdog(ScheduledExecutorService scheduler) {
        startWatchdog(scheduler, null);
    }

    public void startWatchdog(ScheduledExecutorService scheduler, Runnable onRestart) {
        scheduler.scheduleAtFixedRate(() -> {
            if (stopping.get() || !managedByUs) return;
            if (!isAlive()) {
                System.out.println("[AppiumValidator] Proceso caido — reiniciando...");
                try {
                    Thread.sleep(RESTART_DELAY * 1000L);
                    ensureRunning();
                    if (isAlive() && onRestart != null) onRestart.run();
                } catch (Exception e) {
                    System.err.println("[AppiumValidator] Error al reiniciar: " + e.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /** True if an Appium entry point can be located (server need not be running). */
    public boolean canStart() { return findAppiumEntryPoint() != null; }

    /** Returns the Appium version string (e.g. "3.5.2") or "unavailable". */
    public String getAppiumVersion() {
        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) return "unavailable";
        try {
            Process p = new ProcessBuilder(nodeCmd(entryPoint, "--version"))
                    .redirectErrorStream(true).start();
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

    // ── Diagnostics ───────────────────────────────────────────────────────

    /**
     * Logs a full diagnostic snapshot. Does NOT assume Appium is running.
     * Logged format:
     *   [AppiumValidator]
     *   [AppiumValidator] NodeBin=...  Exists=true
     *   [AppiumValidator] AppiumEntryPoint=...
     *   [AppiumValidator] Exists=true  Readable=true
     *   [AppiumValidator] Version=3.5.2
     *   [AppiumValidator] StatusEndpoint=OK
     */
    public void logDiagnostic() {
        String  nodeBin    = resolveNodeBin();
        boolean nodeExists = Files.isExecutable(Path.of(nodeBin));
        String  entryPoint = resolveEmbeddedEntryPoint();
        boolean epExists   = entryPoint != null && Files.exists(Path.of(entryPoint));
        boolean epReadable = epExists && Files.isReadable(Path.of(entryPoint));
        String  version    = "unavailable";
        if (epExists && epReadable && nodeExists) {
            try {
                Process p = new ProcessBuilder(nodeBin, entryPoint, "--version")
                        .redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    String out = new String(p.getInputStream().readAllBytes()).trim();
                    if (!out.isBlank()) version = out.split("\n")[0].trim();
                } else {
                    p.getInputStream().transferTo(OutputStream.nullOutputStream());
                }
            } catch (Exception ignored) {}
        }
        boolean alive = isAlive();

        System.out.println("[AppiumValidator]");
        System.out.printf("[AppiumValidator] NodeBin=%s Exists=%s%n", nodeBin, nodeExists);
        System.out.printf("[AppiumValidator] AppiumEntryPoint=%s%n",
                entryPoint != null ? entryPoint : "NOT_FOUND");
        System.out.printf("[AppiumValidator] Exists=%s Readable=%s%n", epExists, epReadable);
        System.out.printf("[AppiumValidator] Version=%s%n", version);
        System.out.printf("[AppiumValidator] StatusEndpoint=%s%n", alive ? "OK" : "FAIL");
    }

    // ── Resolution ────────────────────────────────────────────────────────

    /**
     * Resolves the Node.js binary:
     *   1. AGENT_DATA_DIR/runtime/node/bin/node  (embedded enterprise — primary)
     *   2. NODE_BIN system property
     *   3. System "node" (dev/fallback — not used in enterprise runtime)
     */
    private String resolveNodeBin() {
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path embedded = Path.of(agentDataDir, "runtime", "node", "bin",
                                    isWindows() ? "node.exe" : "node");
            if (Files.isExecutable(embedded)) return embedded.toString();
        }
        String prop = System.getProperty("NODE_BIN", "");
        if (!prop.isBlank() && Files.isExecutable(Path.of(prop))) return prop;
        return isWindows() ? "node.exe" : "node";
    }

    /**
     * Resolves the Appium entry point without running any process.
     * Used by logDiagnostic().
     *   1. AGENT_DATA_DIR/runtime/appium/node_modules/appium/index.js
     *   2. APPIUM_BIN system property
     */
    private String resolveEmbeddedEntryPoint() {
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path p = Path.of(agentDataDir, "runtime", "appium",
                             "node_modules", "appium", "index.js");
            if (Files.exists(p)) return p.toString();
        }
        String prop = System.getProperty("APPIUM_BIN", "");
        if (!prop.isBlank() && Files.exists(Path.of(prop))) return prop;
        return null;
    }

    /**
     * Finds a working Appium entry point by probing candidates with --version.
     * Package-visible so DependencySelfHealingManager can call canStart() without duplication.
     */
    String findAppiumEntryPoint() {
        List<String> candidates = buildCandidates();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (probeEntryPoint(candidate)) {
                System.out.println("[AppiumValidator] AppiumEntryPoint=" + candidate);
                return candidate;
            }
        }
        System.err.println("[AppiumValidator] AppiumEntryPoint=NOT_FOUND — intentados: " + candidates.size());
        return null;
    }

    /** Backward-compat alias used internally and by DependencySelfHealingManager. */
    String findAppiumBin() { return findAppiumEntryPoint(); }

    private List<String> buildCandidates() {
        List<String> list = new ArrayList<>();

        // Priority 1: canonical embedded enterprise path — index.js, not shell wrapper
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path indexJs = Path.of(agentDataDir, "runtime", "appium",
                                   "node_modules", "appium", "index.js");
            if (Files.exists(indexJs)) list.add(indexJs.toString());
        }

        // Priority 2: APPIUM_BIN property (may point to index.js or a wrapper)
        String prop = System.getProperty("APPIUM_BIN", "");
        if (!prop.isBlank() && Files.exists(Path.of(prop)) && !list.contains(prop))
            list.add(prop);

        // Priority 3+: dev/non-enterprise only
        if (!isWindows()) {
            list.add("/opt/homebrew/bin/appium");
            list.add("/usr/local/bin/appium");
        }
        list.add(isWindows() ? "npx.cmd appium" : "npx appium");

        return list;
    }

    /**
     * Probes an entry point candidate by running --version via Node.
     * File paths → ProcessBuilder array (no shell, handles spaces correctly).
     * Shell tokens (npx) → sh -c.
     */
    private boolean probeEntryPoint(String candidate) {
        try {
            boolean isFile = Files.exists(Path.of(candidate));
            String[] cmd;
            if (isFile) {
                // index.js or any real file: run via Node — array form handles spaces
                cmd = new String[]{resolveNodeBin(), candidate, "--version"};
            } else {
                // Shell token: "npx appium"
                cmd = isWindows() ? new String[]{"cmd", "/c", candidate, "--version"}
                                  : new String[]{"sh", "-c", candidate + " --version"};
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes());
            if (!done) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0 && out.matches("(?s).*\\d+\\.\\d+.*");
        } catch (Exception e) { return false; }
    }

    // ── Commands ──────────────────────────────────────────────────────────

    /**
     * Builds a [nodeBin, entryPoint, args...] command array.
     * ProcessBuilder handles spaces in all path components correctly.
     */
    private String[] nodeCmd(String entryPoint, String... args) {
        String[] cmd = new String[2 + args.length];
        cmd[0] = resolveNodeBin();
        cmd[1] = entryPoint;
        System.arraycopy(args, 0, cmd, 2, args.length);
        return cmd;
    }

    private ProcessBuilder withAppiumHome(ProcessBuilder pb) {
        String appiumHome = System.getProperty("APPIUM_HOME");
        if (appiumHome != null && !appiumHome.isBlank())
            pb.environment().put("APPIUM_HOME", appiumHome);
        return pb;
    }

    // ── Start ─────────────────────────────────────────────────────────────

    private void startAppium(String entryPoint) throws IOException, InterruptedException {
        System.out.println("[AppiumValidator] Iniciando: node " + entryPoint + " --port " + PORT);

        String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                System.getProperty("user.home") + "/.automationqa");
        Path logFile = Path.of(agentDataDir, "logs", "appium.log");
        Files.createDirectories(logFile.getParent());

        ProcessBuilder pb = withAppiumHome(
                new ProcessBuilder(nodeCmd(entryPoint, "--port", String.valueOf(PORT),
                        "--log", logFile.toString(), "--log-timestamp"))
                        .redirectErrorStream(true)
                        .redirectOutput(logFile.toFile()));

        appiumProcess = pb.start();
        managedByUs   = true;
        System.out.println("[AppiumValidator] Proceso iniciado (PID " + appiumProcess.pid() +
                "). Esperando /status en puerto " + PORT + "...");
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

    // ── Install ───────────────────────────────────────────────────────────

    private void installAppium() throws IOException, InterruptedException {
        String nodeBin = resolveNodeBin();
        String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                System.getProperty("user.home") + "/.automationqa");
        String prefixDir = Path.of(agentDataDir, "runtime", "appium").toString();
        Files.createDirectories(Path.of(prefixDir));

        Path nodeDir = Path.of(nodeBin).getParent();
        String npmBin = nodeDir.resolve(isWindows() ? "npm.cmd" : "npm").toString();
        System.out.println("[AppiumValidator] Instalando Appium en: " + prefixDir);
        Process p = new ProcessBuilder(npmBin, "install", "--prefix", prefixDir, "appium",
                "--no-audit", "--no-fund")
                .inheritIO().start();
        boolean done = p.waitFor(10, TimeUnit.MINUTES);
        if (!done || p.exitValue() != 0)
            throw new IOException("npm install appium fallo con codigo " + (done ? p.exitValue() : "timeout"));

        System.out.println("[AppiumValidator] Appium instalado.");
        String version = getAppiumVersion();
        System.out.printf("[AppiumValidator] Server=%s — instalando drivers compatibles...%n", version);
        installDriver("uiautomator2", version);
        if ("MACOS".equals(os)) installDriver("xcuitest", version);
    }

    private void installDriver(String driver, String appiumVersion) {
        String spec = buildDriverSpec(driver, appiumVersion);
        try {
            System.out.println("[AppiumValidator] Instalando driver: " + spec);
            String entryPoint = findAppiumEntryPoint();
            if (entryPoint == null) return;
            withAppiumHome(new ProcessBuilder(nodeCmd(entryPoint, "driver", "install", spec))
                    .inheritIO())
                    .start().waitFor(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[AppiumValidator] Warning: no se pudo instalar driver " + spec + ": " + e.getMessage());
        }
    }

    private void uninstallDriver(String entryPoint, String driver) {
        try {
            System.out.println("[AppiumValidator] Desinstalando driver: " + driver);
            withAppiumHome(new ProcessBuilder(nodeCmd(entryPoint, "driver", "uninstall", driver))
                    .redirectErrorStream(true))
                    .start().waitFor(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("[AppiumValidator] Warning: uninstall " + driver + ": " + e.getMessage());
        }
    }

    /**
     * Maps driver + Appium server version to a version-pinned npm spec.
     *   Appium 2.x → uiautomator2@2 / xcuitest@7
     *   Appium 3.x → latest (compatible by design)
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
        return driver;
    }

    // ── Utils ─────────────────────────────────────────────────────────────

    private boolean isWindows() { return "WINDOWS".equals(os); }
}
