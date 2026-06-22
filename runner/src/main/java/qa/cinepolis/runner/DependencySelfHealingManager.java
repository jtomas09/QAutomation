package qa.cinepolis.runner;

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
 *   Appium — process alive or binary resolvable via APPIUM_BIN
 *   ADB    — PlatformToolsManager.isAdbFunctional(); auto-heals via reset()+resolveAdb()
 *   Xcode  — macOS only; XcodeValidator; non-macOS always true
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

        public boolean isFullyOperational() {
            return jreOk && nodeOk && appiumOk && adbOk && xcodeOk;
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
        boolean appiumOk = checkAppium();
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
            boolean ok = Files.isExecutable(
                    Path.of(agentDataDir, "runtime", "jre17", "bin", "java"));
            System.setProperty("JRE_EMBEDDED_OK", String.valueOf(ok));
            return ok;
        }
        return true; // running on system JRE — still functional
    }

    private boolean checkNode() {
        String nodeBin = System.getProperty("NODE_BIN", "");
        if (!nodeBin.isBlank()) {
            boolean ok = Files.isExecutable(Path.of(nodeBin));
            System.setProperty("NODE_OK", String.valueOf(ok));
            if (!ok) System.err.println("[DependencyHealer] Node embebido no ejecutable: " + nodeBin);
            return ok;
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

    private boolean checkAppium() {
        boolean alive    = appiumMgr.isAlive();
        boolean canStart = appiumMgr.canStart();
        boolean drivers  = checkDriversInstalled();
        boolean ok       = (alive || canStart) && drivers;
        System.setProperty("APPIUM_OK", String.valueOf(ok));
        if (!alive && !canStart)
            System.err.println("[DependencyHealer] Appium no disponible (proceso y binario).");
        return ok;
    }

    private boolean checkDriversInstalled() {
        // APPIUM_HOME is set by installer or defaults to ~/.appium
        String appiumHome = System.getProperty("APPIUM_HOME",
                System.getProperty("user.home") + "/.appium");
        Path modules = Path.of(appiumHome, "node_modules");

        // uiautomator2 required for Android
        boolean ua2 = Files.isDirectory(modules.resolve("appium-uiautomator2-driver"))
                   || Files.isDirectory(modules.resolve("@appium/uiautomator2-driver"));

        // xcuitest required for iOS — only on macOS
        boolean xcui = !"MACOS".equals(os)
                    || Files.isDirectory(modules.resolve("appium-xcuitest-driver"))
                    || Files.isDirectory(modules.resolve("@appium/xcuitest-driver"));

        boolean ok = ua2 && xcui;
        System.setProperty("DRIVERS_OK", String.valueOf(ok));
        if (!ok) System.err.printf("[DependencyHealer] Drivers incompletos: ua2=%s xcui=%s (APPIUM_HOME=%s)%n",
                ua2, xcui, appiumHome);
        return ok;
    }

    private boolean checkAndHealAdb() {
        if (platformTools.isAdbFunctional()) return true;

        System.out.println("[DependencyHealer] ADB no funcional — reparando...");
        platformTools.reset();
        platformTools.resolveAdb();
        boolean ok = platformTools.isAdbFunctional();

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

    private boolean checkXcode() {
        XcodeValidator.invalidateCache();
        XcodeValidator.XcodeInfo info = XcodeValidator.validate();
        System.setProperty("XCODE_OK",      String.valueOf(info.installed));
        System.setProperty("XCODE_VERSION",
                info.xcodeVersion != null ? info.xcodeVersion : "unavailable");
        return info.installed;
    }
}
