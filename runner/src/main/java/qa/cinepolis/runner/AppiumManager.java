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
 * Full Appium lifecycle manager.
 *
 * Resolution order:
 *   0. APPIUM_BIN system property (embedded enterprise mode — absolute priority)
 *   1. Running Appium at http://127.0.0.1:4723/status (already up — use it)
 *   2. PATH: appium / appium.cmd
 *   3. Global npm: ~/.npm-global/bin/appium  (or $(npm root -g)/../.bin/appium)
 *   4. npx appium (no install, ephemeral)
 *   5. Install Appium locally (if NODE_BIN set) or globally via npm, then start
 *
 * When NODE_BIN system property is set, Appium is launched as:
 *   $NODE_BIN $APPIUM_BIN --port 4723 ...
 * This avoids any dependency on system Node or PATH.
 *
 * Once started, monitors the process and auto-restarts if it crashes.
 */
public class AppiumManager {

    private static final String APPIUM_URL    = "http://127.0.0.1:4723/status";
    private static final int    PORT          = 4723;
    private static final int    STARTUP_WAIT  = 30; // seconds
    private static final int    RESTART_DELAY = 5;  // seconds

    private final String os;
    private volatile Process     appiumProcess = null;
    private volatile boolean     managedByUs   = false;
    private final    AtomicBoolean stopping     = new AtomicBoolean(false);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AppiumManager(String os) {
        this.os = os;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Ensures Appium is running on port 4723.
     * If it is already running, returns immediately.
     * Otherwise, locates or installs Appium and starts it.
     * Blocks until Appium is responsive (up to 30 s).
     *
     * @throws IOException if Appium cannot be started or installed.
     */
    public synchronized void ensureRunning() throws IOException, InterruptedException {
        if (isAlive()) {
            System.out.println("[Appium] Ya en ejecucion en el puerto " + PORT);
            return;
        }

        System.out.println("[Appium] Localizando Appium...");
        String appiumBin = findAppiumBin();

        if (appiumBin == null) {
            System.out.println("[Appium] No encontrado. Instalando via npm...");
            installAppium();
            appiumBin = findAppiumBin();
        }

        if (appiumBin == null) {
            throw new IOException("Appium no pudo ser instalado ni localizado. Instala Node.js + npm manualmente.");
        }

        startAppium(appiumBin);
    }

    /**
     * Returns true if Appium is responding on its /status endpoint.
     */
    public boolean isAlive() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(APPIUM_URL))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Start a background watchdog that restarts Appium when it crashes.
     * Call once after ensureRunning() succeeds.
     */
    public void startWatchdog(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(() -> {
            if (stopping.get() || !managedByUs) return;
            if (!isAlive()) {
                System.out.println("[Appium] Proceso caido — reiniciando...");
                try {
                    Thread.sleep(RESTART_DELAY * 1000L);
                    ensureRunning();
                } catch (Exception e) {
                    System.err.println("[Appium] Error al reiniciar: " + e.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        stopping.set(true);
        Process p = appiumProcess;
        if (p != null && p.isAlive()) {
            p.destroy();
            try { p.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            if (p.isAlive()) p.destroyForcibly();
            System.out.println("[Appium] Proceso detenido.");
        }
    }

    // ── Find ──────────────────────────────────────────────────────────────

    private String findAppiumBin() {
        for (String candidate : buildCandidates()) {
            if (candidate == null || candidate.isBlank()) continue;
            if (probeAppiumBin(candidate)) {
                System.out.println("[Appium] Encontrado: " + candidate);
                return candidate;
            }
        }
        return null;
    }

    private List<String> buildCandidates() {
        List<String> list = new ArrayList<>();
        boolean win = isWindows();

        // Priority 0: embedded Appium (enterprise mode — APPIUM_BIN set by installer)
        String embeddedBin = System.getProperty("APPIUM_BIN");
        if (embeddedBin != null && !embeddedBin.isBlank() && Files.exists(Path.of(embeddedBin))) {
            list.add(embeddedBin);
        }

        // PATH
        list.add(win ? "appium.cmd" : "appium");

        // npm global prefix
        try {
            String npmRoot = runCapture("npm", "root", "-g");
            if (npmRoot != null) {
                Path prefix = Path.of(npmRoot.strip()).getParent();
                if (prefix != null) {
                    list.add(prefix.resolve(win ? ".bin/appium.cmd" : "bin/appium").toString());
                }
            }
        } catch (Exception ignored) {}

        // Homebrew (macOS)
        if (!win) {
            list.add("/opt/homebrew/bin/appium");
            list.add("/usr/local/bin/appium");
        }

        // npx as last resort (no install needed, but slower first-run)
        list.add(win ? "npx.cmd appium" : "npx appium");

        return list;
    }

    private boolean probeAppiumBin(String bin) {
        try {
            String nodeBin = System.getProperty("NODE_BIN");
            boolean useNode = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin))
                    && !bin.contains(" ");
            String[] cmd;
            if (useNode) {
                cmd = new String[]{nodeBin, bin, "--version"};
            } else if (bin.contains(" ")) {
                cmd = isWindows() ? new String[]{"cmd","/c",bin,"--version"}
                                  : new String[]{"sh","-c",bin+" --version"};
            } else {
                cmd = new String[]{bin, "--version"};
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
            // Local install using embedded Node — no global npm, no PATH dependency
            String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                    System.getProperty("user.home") + "/.automationqa");
            String prefixDir = agentDataDir + "/runtime/appium";
            Files.createDirectories(Path.of(prefixDir));
            String npmBin = Path.of(nodeBin).getParent().resolve("npm").toString();
            System.out.println("[Appium] Instalando localmente via Node embebido: " + prefixDir);
            Process p = new ProcessBuilder(npmBin, "install", "--prefix", prefixDir, "appium@2",
                    "--no-audit", "--no-fund")
                    .inheritIO().start();
            boolean done = p.waitFor(10, TimeUnit.MINUTES);
            if (!done || p.exitValue() != 0) {
                throw new IOException("npm install appium fallo con codigo " + (done ? p.exitValue() : "timeout"));
            }
        } else {
            // Global install (fallback: requires system Node/npm in PATH)
            System.out.println("[Appium] Ejecutando: npm install -g appium ...");
            String npmCmd = isWindows() ? "npm.cmd" : "npm";
            Process p = new ProcessBuilder(npmCmd, "install", "-g", "appium")
                    .inheritIO().start();
            boolean done = p.waitFor(10, TimeUnit.MINUTES);
            if (!done || p.exitValue() != 0) {
                throw new IOException("npm install -g appium fallo con codigo " + (done ? p.exitValue() : "timeout"));
            }
        }

        System.out.println("[Appium] Instalacion completada.");
        installDriver("uiautomator2");
        if ("MACOS".equals(os)) {
            installDriver("xcuitest");
        }
    }

    private void installDriver(String driver) {
        try {
            System.out.println("[Appium] Instalando driver: " + driver);
            String appiumBin = findAppiumBin();
            if (appiumBin == null) return;
            String nodeBin = System.getProperty("NODE_BIN");
            boolean useNode = nodeBin != null && !nodeBin.isBlank()
                    && Files.exists(Path.of(nodeBin))
                    && !appiumBin.startsWith("npx");
            String[] cmd;
            if (useNode) {
                cmd = new String[]{nodeBin, appiumBin, "driver", "install", driver};
            } else if (isWindows()) {
                cmd = new String[]{"cmd", "/c", appiumBin, "driver", "install", driver};
            } else {
                cmd = new String[]{appiumBin, "driver", "install", driver};
            }
            Process p = new ProcessBuilder(cmd).inheritIO().start();
            p.waitFor(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[Appium] Warning: no se pudo instalar driver " + driver + ": " + e.getMessage());
        }
    }

    // ── Start ─────────────────────────────────────────────────────────────

    private void startAppium(String appiumBin) throws IOException, InterruptedException {
        System.out.println("[Appium] Iniciando: " + appiumBin + " --port " + PORT + " ...");

        // Log path uses AGENT_DATA_DIR (set by installer), falls back to ~/.automationqa
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
            // Launch Appium via embedded Node — no PATH dependency
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

        appiumProcess = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

        managedByUs = true;
        System.out.println("[Appium] Proceso iniciado (PID " + appiumProcess.pid() +
                "). Esperando que responda en puerto " + PORT + "...");
        System.out.println("[Appium] Log: " + logFile);

        // Wait up to STARTUP_WAIT seconds
        for (int i = 0; i < STARTUP_WAIT; i++) {
            Thread.sleep(1000);
            if (isAlive()) {
                System.out.println("[Appium] Listo en http://127.0.0.1:" + PORT + "/status");
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
