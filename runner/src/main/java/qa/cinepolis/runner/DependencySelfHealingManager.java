package qa.cinepolis.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * Monitors all critical dependencies every 5 minutes and fires a callback
 * when the overall health changes (DEGRADED ↔ ONLINE).
 *
 * Replaces SelfHealingManager (which only monitored ADB).
 *
 * Monitored components:
 *   JRE    — embedded bin exists (always true when running on system Java)
 *   Node   — NODE_BIN prop or system `node` in PATH
 *   Appium — HTTP /status ready:true; self-heals via driver reinstall if FAIL
 *   ADB    — PlatformToolsManager.isAdbFunctional(); auto-heals via reset()+resolveAdb()
 *   Xcode  — macOS only; XcodeValidator; non-macOS: not required for ONLINE
 *
 * ONLINE criteria: JRE + Node + Appium + ADB  (Xcode is optional — affects iosReady only)
 */
public class DependencySelfHealingManager {

    private static final int INTERVAL_MINUTES = 5;

    // ── Health snapshot ─────────────────────────────────────────────────────

    public static class HealthReport {
        public final boolean jreOk;
        public final boolean nodeOk;
        public final boolean appiumOk;
        public final boolean adbOk;
        public final boolean xcodeOk;

        public HealthReport(boolean jre, boolean node, boolean appium, boolean adb, boolean xcode) {
            this.jreOk    = jre;
            this.nodeOk   = node;
            this.appiumOk = appium;
            this.adbOk    = adb;
            this.xcodeOk  = xcode;
        }

        /** ONLINE = JRE + Node + Appium + ADB. Xcode is not required — it only affects iosReady. */
        public boolean isFullyOperational() {
            return jreOk && nodeOk && appiumOk && adbOk;
        }

        public String summary() {
            return String.format("JRE=%s Node=%s Appium=%s ADB=%s Xcode=%s",
                    flag(jreOk), flag(nodeOk), flag(appiumOk), flag(adbOk), flag(xcodeOk));
        }

        private static String flag(boolean v) { return v ? "OK" : "FAIL"; }
    }

    @FunctionalInterface
    public interface HealCallback {
        void onHealthChange(HealthReport report);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final PlatformToolsManager     platformTools;
    private final AppiumManager            appiumMgr;
    private final String                   os;
    private final HealCallback             callback;
    private final ScheduledExecutorService scheduler;
    private volatile HealthReport          lastReport;

    public DependencySelfHealingManager(
            PlatformToolsManager platformTools,
            AppiumManager appiumMgr,
            String os,
            HealthReport initialReport,
            HealCallback callback) {
        this.platformTools = platformTools;
        this.appiumMgr     = appiumMgr;
        this.os            = os;
        this.lastReport    = initialReport;
        this.callback      = callback;
        this.scheduler     = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dep-selfheal");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        System.out.println("[DependencyHealer] Iniciado. Verificacion cada " + INTERVAL_MINUTES + " min.");
        scheduler.scheduleAtFixedRate(this::checkAll,
                INTERVAL_MINUTES, INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    public HealthReport getLastReport() { return lastReport; }

    // ── Periodic check ──────────────────────────────────────────────────────

    private void checkAll() { evaluate(true); }

    private HealthReport evaluate(boolean fireCallback) {
        boolean wasOk = lastReport != null && lastReport.isFullyOperational();

        boolean jreOk    = checkJre();
        boolean nodeOk   = checkNode();
        boolean appiumOk = checkAndHealAppium();
        boolean adbOk    = checkAndHealAdb();
        boolean xcodeOk  = "MACOS".equals(os) ? checkXcode() : true;

        HealthReport report = new HealthReport(jreOk, nodeOk, appiumOk, adbOk, xcodeOk);
        lastReport = report;
        System.out.println("[DependencyHealer] " + report.summary());

        if (fireCallback && wasOk != report.isFullyOperational()) {
            try { callback.onHealthChange(report); }
            catch (Exception e) {
                System.err.println("[DependencyHealer] Callback error: " + e.getMessage());
            }
        }
        return report;
    }

    // ── Component checks ──────────────────────────────────────────────────────

    private boolean checkJre() {
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path javaBin = Path.of(agentDataDir, "runtime", "jre17", "bin", "java");
            if (!Files.isExecutable(javaBin)) {
                System.err.println("[DependencyHealer] JRE embebido no ejecutable: " + javaBin);
                System.setProperty("JRE_EMBEDDED_OK", "false");
                return false;
            }
            if (!checksumOk(javaBin, "JRE")) {
                System.setProperty("JRE_EMBEDDED_OK", "false");
                return false;
            }
            System.setProperty("JRE_EMBEDDED_OK", "true");
            return true;
        }
        return true; // running on system JRE — still functional
    }

    private boolean checkNode() {
        String nodeBin = System.getProperty("NODE_BIN", "");
        if (!nodeBin.isBlank()) {
            Path bin = Path.of(nodeBin);
            if (!Files.isExecutable(bin)) {
                System.err.println("[DependencyHealer] Node embebido no ejecutable: " + nodeBin);
                System.setProperty("NODE_OK", "false");
                return false;
            }
            if (!checksumOk(bin, "Node")) {
                System.setProperty("NODE_OK", "false");
                return false;
            }
            System.setProperty("NODE_OK", "true");
            return true;
        }
        try {
            Process p = new ProcessBuilder("node", "--version")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            boolean ok   = done && p.exitValue() == 0;
            System.setProperty("NODE_OK", String.valueOf(ok));
            return ok;
        } catch (Exception e) {
            System.setProperty("NODE_OK", "false");
            return false;
        }
    }

    /**
     * AppiumValidator check + self-heal cycle:
     *   1. GET /status → ready:true  →  OK
     *   2. Restart (binary exists)   →  if OK, done
     *   3. healDrivers() — reinstall version-compatible drivers → restart
     *   4. Re-check /status          →  final verdict
     */
    private boolean checkAndHealAppium() {
        // Phase 1: already responding?
        if (appiumMgr.isAlive()) {
            String version = appiumMgr.getAppiumVersion();
            System.out.printf("[AppiumValidator] StatusEndpoint=OK Version=%s%n", version);
            System.setProperty("APPIUM_OK",      "true");
            System.setProperty("APPIUM_VERSION",  version);
            return true;
        }

        System.out.println("[AppiumValidator] StatusEndpoint=FAIL — Appium no responde en /status");

        // Phase 2: binary available?
        if (!appiumMgr.canStart()) {
            System.err.println("[AppiumValidator] Binario no encontrado — Appium=FAIL");
            System.setProperty("APPIUM_OK", "false");
            return false;
        }

        // Phase 3: try plain restart (handles process crash)
        System.out.println("[AppiumValidator] Intentando reinicio de proceso...");
        try {
            appiumMgr.ensureRunning();
        } catch (Exception e) {
            System.err.println("[AppiumValidator] Reinicio fallido: " + e.getMessage());
        }

        if (appiumMgr.isAlive()) {
            String version = appiumMgr.getAppiumVersion();
            System.out.printf("[AppiumValidator] Reinicio exitoso — StatusEndpoint=OK Version=%s%n", version);
            System.setProperty("APPIUM_OK",      "true");
            System.setProperty("APPIUM_VERSION",  version);
            return true;
        }

        // Phase 4: heal drivers (handles Server 2.x + Driver 3.x mismatch)
        System.out.println("[AppiumValidator] Reinicio fallido — detectada posible incompatibilidad de drivers.");
        System.out.println("[AppiumValidator] Drivers instalados:");
        String driverList = appiumMgr.getInstalledDriverList();
        driverList.lines().forEach(l -> System.out.println("[AppiumValidator]   " + l));

        System.out.println("[AppiumValidator] Iniciando reparacion de drivers...");
        try {
            appiumMgr.healDrivers();
        } catch (Exception e) {
            System.err.println("[AppiumValidator] Driver healing error: " + e.getMessage());
        }

        boolean ok      = appiumMgr.isAlive();
        String  version = ok ? appiumMgr.getAppiumVersion() : "unavailable";
        System.out.printf("[AppiumValidator] Post-healing StatusEndpoint=%s Version=%s%n",
                ok ? "OK" : "FAIL", version);
        System.setProperty("APPIUM_OK",     String.valueOf(ok));
        if (ok) System.setProperty("APPIUM_VERSION", version);
        return ok;
    }

    private boolean checkAndHealAdb() {
        if (platformTools.isAdbFunctional()) {
            Path adbBin = platformTools.getToolsDir().resolve("adb");
            if (!checksumOk(adbBin, "ADB")) {
                System.out.println("[DependencyHealer] ADB checksum invalido — re-descargando...");
                platformTools.reset();
                platformTools.resolveAdb();
            }
            return platformTools.isAdbFunctional();
        }

        System.out.println("[DependencyHealer] ADB no funcional — reparando (kill-server → start-server)...");
        boolean ok = platformTools.healAdbServer();

        if (ok) {
            System.setProperty("ADB_PATH",    platformTools.resolveAdb());
            System.setProperty("ADB_VERSION", platformTools.getAdbVersion());
            System.setProperty("ADB_OK",      "true");
            System.out.println("[DependencyHealer] ADB reparado.");
        } else {
            System.setProperty("ADB_OK", "false");
            System.err.println("[DependencyHealer] ADB no pudo repararse.");
        }
        return ok;
    }

    // Returns false ONLY when a baseline exists AND the hash doesn't match.
    private boolean checksumOk(Path file, String label) {
        if (!Files.exists(file)) return true;
        try {
            boolean ok = ChecksumValidator.matchesBaseline(file);
            if (!ok) System.err.printf("[DependencyHealer] %s checksum mismatch: %s%n", label, file);
            return ok;
        } catch (IOException e) {
            System.err.printf("[DependencyHealer] %s checksum error: %s%n", label, e.getMessage());
            return true; // IO error reading sidecar → don't false-alarm
        }
    }

    private boolean checkXcode() {
        XcodeValidator.invalidateCache();
        XcodeValidator.XcodeInfo info = XcodeValidator.validate();
        System.setProperty("XCODE_OK",      String.valueOf(info.installed));
        System.setProperty("XCODE_VERSION",
                info.xcodeVersion != null ? info.xcodeVersion : "unavailable");
        return info.installed;
    }
}
