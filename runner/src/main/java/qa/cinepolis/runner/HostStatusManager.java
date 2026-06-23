package qa.cinepolis.runner;

/**
 * Derives the canonical host operational status from component health flags.
 *
 * ONLINE   — JRE, Node, Appium, ADB all healthy. Xcode is optional (macOS/iOS).
 * DEGRADED — Any required component (JRE/Node/Appium/ADB) missing or non-functional.
 * OFFLINE  — Determined externally by the backend staleness detector.
 *
 * iosReady: Xcode installed — signals iOS capability without requiring a device connected.
 */
public class HostStatusManager {

    public enum HostStatus { ONLINE, DEGRADED, OFFLINE }

    // ── Report ──────────────────────────────────────────────────────────────

    public static class HostReport {
        public final HostStatus hostStatus;
        public final boolean    jreInstalled;
        public final boolean    nodeInstalled;
        public final boolean    appiumInstalled;
        public final boolean    adbInstalled;
        public final boolean    xcodeInstalled;
        public final boolean    iosReady;

        HostReport(boolean jre, boolean node, boolean appium,
                   boolean adb, boolean xcode, boolean iosReady) {
            this.jreInstalled    = jre;
            this.nodeInstalled   = node;
            this.appiumInstalled = appium;
            this.adbInstalled    = adb;
            this.xcodeInstalled  = xcode;
            this.iosReady        = iosReady;
            // ONLINE requires JRE + Node + Appium + ADB; Xcode is optional (macOS-only)
            this.hostStatus      = (jre && node && appium && adb)
                                   ? HostStatus.ONLINE
                                   : HostStatus.DEGRADED;
        }
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Computes a HostReport from individual component flags.
     * On non-macOS, xcodeInstalled should be set to false (not applicable).
     */
    public static HostReport evaluate(boolean jreOk, boolean nodeOk, boolean appiumOk,
                                      boolean adbOk, boolean xcodeOk, boolean iosSupported) {
        boolean iosReady = xcodeOk && iosSupported;
        return new HostReport(jreOk, nodeOk, appiumOk, adbOk, xcodeOk, iosReady);
    }

    /**
     * Derives a HostReport from a DependencySelfHealingManager.HealthReport.
     * Convenient for the periodic health-change callback.
     */
    public static HostReport fromHealthReport(
            DependencySelfHealingManager.HealthReport health, boolean iosSupported) {
        return evaluate(health.jreOk, health.nodeOk, health.appiumOk,
                        health.adbOk, health.xcodeOk, iosSupported);
    }

    // ── System-property bridge ────────────────────────────────────────────────

    /**
     * Publishes the HostReport to JVM system properties consumed by BackendClient.sendHeartbeat().
     *
     *   HOST_STATUS  →  "ONLINE" | "DEGRADED"
     *   IOS_READY    →  "true"   | "false"
     */
    public static void apply(HostReport report) {
        System.setProperty("HOST_STATUS", report.hostStatus.name());
        System.setProperty("IOS_READY",   String.valueOf(report.iosReady));
        System.out.println("[HostStatus]");
        System.out.printf("[HostStatus] JRE=%s Node=%s Appium=%s ADB=%s Xcode=%s%n",
                flag(report.jreInstalled),    flag(report.nodeInstalled),
                flag(report.appiumInstalled), flag(report.adbInstalled),
                flag(report.xcodeInstalled));
        System.out.printf("[HostStatus] Estado=%s iosReady=%s%n",
                report.hostStatus, report.iosReady);
    }

    private static String flag(boolean v) { return v ? "OK" : "FAIL"; }
}
