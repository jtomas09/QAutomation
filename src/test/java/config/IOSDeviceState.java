package config;

/**
 * Immutable snapshot of all iOS device and app-signing state confirmed by the Runner
 * (JobExecutor / IosPreflightManager) before the Gradle test process started.
 *
 * All fields are populated from -D JVM flags injected by the Runner.
 * This class NEVER executes subprocesses.
 *
 * Design intent:
 *   The Runner is the single source of truth for iOS device state.
 *   DriverFactory and IOSDeviceSynchronizationManager consume this object
 *   and skip redundant subprocess calls when {@link #ready} is true.
 *
 * Transport mechanism:
 *   Runner → Gradle subprocess via -D flags → IOSDeviceState.fromRunnerProps()
 *
 * Android: not referenced.
 */
public final class IOSDeviceState {

    // ── Hardware / sync state (from -DiosState.* flags) ──────────────────────

    /** True when xctrace confirmed this UDID in the Runner JVM. */
    public final boolean xctraceVisible;
    /** True when CoreDevice (devicectl) confirmed this UDID in the Runner JVM. */
    public final boolean coreDeviceVisible;
    /** True when the CoreDevice tunnel was 'connected' at preflight time. */
    public final boolean tunnelConnected;
    /** True when the device pairing state was 'paired' at preflight time. */
    public final boolean paired;

    // ── Device identity ───────────────────────────────────────────────────────

    public final String physicalUdid;
    public final String coreDeviceId;
    public final String platformVersion;

    // ── App / signing config (from regular -D flags) ──────────────────────────

    public final String teamId;
    public final String bundleId;
    public final String updatedWDABundleId;
    public final String webDriverAgentUrl;

    // ── Driver / WDA state ────────────────────────────────────────────────────

    /** True when the Runner confirmed Appium's XCUITest driver is installed. */
    public final boolean xcuitestInstalled;
    /** True when the Runner confirmed a pre-built WDA exists in the cache. */
    public final boolean wdaPrebuilt;

    // ── Composite readiness ───────────────────────────────────────────────────

    /**
     * True when the Runner confirmed this device is fully ready for an Appium session:
     * xctrace visible + device paired + XCUITest driver available.
     *
     * When true, DriverFactory skips all redundant pre-session validation.
     */
    public final boolean ready;

    /** Epoch-ms timestamp when the Runner confirmed the device state. */
    public final long confirmedAt;

    // ── Runner DeviceAvailability fields (from -DiosState.transportType/readyForExecution/notReadyReason) ──

    /**
     * Transport type as reported by devicectl at preflight time — WIRED, LOCAL_NETWORK, or UNKNOWN.
     * Set by IosPreflightManager via JobExecutor. Empty string when Runner did not provide this.
     */
    public final String  transportType;

    /**
     * Runner's definitive readiness decision for Appium session creation.
     * False when transport/tunnel/pairing/system-health block execution.
     * IOSDeviceSynchronizationManager must not attempt recovery when this is false.
     */
    public final boolean runnerReadyForExecution;

    /**
     * Human-readable explanation when runnerReadyForExecution=false.
     * Empty string when ready. Use this instead of attempting recovery.
     */
    public final String  runnerNotReadyReason;

    // ── Private constructor ───────────────────────────────────────────────────

    private IOSDeviceState(
            boolean xctraceVisible,    boolean coreDeviceVisible,
            boolean tunnelConnected,   boolean paired,
            String  physicalUdid,      String coreDeviceId,    String platformVersion,
            String  teamId,            String bundleId,
            String  updatedWDABundleId, String webDriverAgentUrl,
            boolean xcuitestInstalled, boolean wdaPrebuilt,
            long    confirmedAt,
            String  transportType,     boolean runnerReadyForExecution, String runnerNotReadyReason) {

        this.xctraceVisible          = xctraceVisible;
        this.coreDeviceVisible       = coreDeviceVisible;
        this.tunnelConnected         = tunnelConnected;
        this.paired                  = paired;
        this.physicalUdid            = safe(physicalUdid);
        this.coreDeviceId            = safe(coreDeviceId);
        this.platformVersion         = safe(platformVersion);
        this.teamId                  = safe(teamId);
        this.bundleId                = safe(bundleId);
        this.updatedWDABundleId      = safe(updatedWDABundleId);
        this.webDriverAgentUrl       = safe(webDriverAgentUrl);
        this.xcuitestInstalled       = xcuitestInstalled;
        this.wdaPrebuilt             = wdaPrebuilt;
        // All critical conditions must hold. notReadyReason() explains the first failure.
        this.ready                   = xctraceVisible
                                    && coreDeviceVisible
                                    && paired
                                    && xcuitestInstalled
                                    && !safe(teamId).isEmpty()
                                    && !safe(bundleId).isEmpty();
        this.confirmedAt             = confirmedAt;
        this.transportType           = safe(transportType);
        this.runnerReadyForExecution = runnerReadyForExecution;
        this.runnerNotReadyReason    = safe(runnerNotReadyReason);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates an IOSDeviceState from Runner-injected JVM system properties.
     * Never executes subprocesses.
     *
     * Returns an instance with {@code ready=false} if the Runner did not inject
     * the required {@code -DiosState.xctraceVisible} property.
     */
    public static IOSDeviceState fromRunnerProps() {
        String xctraceStr = System.getProperty("iosState.xctraceVisible");
        if (xctraceStr == null) {
            return empty();
        }

        long confirmedAt = parseLong(System.getProperty("iosState.confirmedAtMs"), 0L);
        if (confirmedAt == 0L) confirmedAt = System.currentTimeMillis();

        boolean xctraceVisible    = "true".equalsIgnoreCase(xctraceStr.trim());
        boolean coreDeviceVisible = "true".equalsIgnoreCase(
                System.getProperty("iosState.coreDeviceVisible", "true").trim());
        boolean tunnelConnected   = "connected".equalsIgnoreCase(
                System.getProperty("iosState.tunnelState", "unknown").trim());
        boolean paired            = !"unpaired".equalsIgnoreCase(
                System.getProperty("iosState.pairingState", "paired").trim());
        boolean xcuitestInstalled      = "true".equalsIgnoreCase(
                System.getProperty("appiumXcuitestInstalled", "false").trim());
        boolean wdaPrebuilt            = "true".equalsIgnoreCase(
                System.getProperty("wdaPrebuilt", "false").trim());
        String  transportType          = System.getProperty("iosState.transportType", "");
        boolean runnerReadyForExecution = "true".equalsIgnoreCase(
                System.getProperty("iosState.readyForExecution", "true").trim());
        String  runnerNotReadyReason   = System.getProperty("iosState.notReadyReason", "");

        return new IOSDeviceState(
                xctraceVisible,    coreDeviceVisible,
                tunnelConnected,   paired,
                System.getProperty("udid",                  ""),
                System.getProperty("iosState.coreDeviceId", ""),
                System.getProperty("platformVersion",       ""),
                System.getProperty("xcodeOrgId",            ""),
                System.getProperty("bundleId",              ""),
                System.getProperty("updatedWDABundleId",    ""),
                System.getProperty("webDriverAgentUrl",     ""),
                xcuitestInstalled, wdaPrebuilt,
                confirmedAt,
                transportType, runnerReadyForExecution, runnerNotReadyReason
        );
    }

    /**
     * Returns an empty (not-ready) state for use when Runner properties are absent.
     * {@code ready} is always false; all String fields are empty.
     */
    public static IOSDeviceState empty() {
        return new IOSDeviceState(
                false, false, false, false,
                "", "", "", "", "", "", "",
                false, false,
                System.currentTimeMillis(),
                "", false, ""
        );
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** True when this state was populated from Runner properties (not the empty default). */
    public boolean fromRunner() {
        return System.getProperty("iosState.xctraceVisible") != null;
    }

    /** Age of this state in whole seconds since the Runner confirmed it. */
    public long ageSeconds() {
        return (System.currentTimeMillis() - confirmedAt) / 1000L;
    }

    /**
     * True when the minimum conditions for attempting an Appium/XCUITest session
     * are met, even if {@code xctraceVisible} or {@code tunnelState} are not confirmed.
     *
     * Conditions required:
     *   coreDeviceVisible — device present in CoreDevice (USB acknowledged by macOS)
     *   paired            — device has trusted this Mac (accepted "Trust" prompt)
     *   xcuitestInstalled — Appium's XCUITest driver is installed and usable
     *   teamId            — non-empty: WDA cannot be code-signed without a Team ID
     *   bundleId          — non-empty: Appium needs the app bundle to launch it
     *   physicalUdid      — non-empty: Appium needs a UDID to target the device
     *
     * Intentionally excluded:
     *   xctraceVisible — non-fatal: WDA can operate via USB even when absent
     *   tunnelState    — non-fatal: Xcode 16+/26 may report disconnected while WDA works
     *   appiumAvailable — guaranteed by buildLocalIOS() via validateAppiumServer()
     *
     * Decision matrix in DriverFactory:
     *   ready=true              -> fast path, no diagnostics
     *   canAttemptSession=true  -> soft diagnostic (non-blocking), proceed to IOSDriver
     *   canAttemptSession=false -> hard abort on truly fatal missing conditions
     */
    public boolean canAttemptSession() {
        return coreDeviceVisible
            && paired
            && xcuitestInstalled
            && !teamId.isBlank()
            && !bundleId.isBlank()
            && !physicalUdid.isBlank();
    }

    /**
     * Returns the first condition that prevents {@link #canAttemptSession()} from
     * being true. Returns "todas las condiciones de intento cumplidas" when it is true.
     */
    public String canAttemptSessionReason() {
        if (!coreDeviceVisible)     return "CoreDevice no detecta el dispositivo (coreDeviceVisible=false)";
        if (!paired)                return "dispositivo no emparejado (pairingState != paired)";
        if (!xcuitestInstalled)     return "XCUITest driver no instalado en Appium";
        if (teamId.isBlank())       return "Team ID ausente — WDA no puede firmarse sin xcodeOrgId";
        if (bundleId.isBlank())     return "Bundle ID ausente — Appium no puede identificar la app";
        if (physicalUdid.isBlank()) return "UDID fisico no configurado — Appium no puede apuntar al dispositivo";
        return "todas las condiciones de intento cumplidas";
    }

    /**
     * Returns a human-readable explanation of why {@link #ready} is false.
     * Returns "todas las condiciones cumplidas" when ready is true.
     * Use this to log a clear reason when fast-pathing is not possible.
     */
    public String notReadyReason() {
        if (!xctraceVisible)      return "xctrace no detecta el dispositivo";
        if (!coreDeviceVisible)   return "CoreDevice no detecta el dispositivo";
        if (!paired)              return "dispositivo no emparejado (pairingState != paired)";
        if (!xcuitestInstalled)   return "XCUITest driver no instalado en Appium";
        if (teamId.isEmpty())     return "xcodeOrgId (Team ID) no configurado — WDA no puede firmarse";
        if (bundleId.isEmpty())   return "bundleId de la aplicación no configurado";
        return "todas las condiciones cumplidas";
    }

    @Override
    public String toString() {
        String readyStr        = ready ? "✅" : ("❌ (" + notReadyReason() + ")");
        String runnerReadyStr  = runnerReadyForExecution ? "✅"
                : ("❌ (" + (runnerNotReadyReason.isEmpty() ? "?" : runnerNotReadyReason) + ")");
        return String.format(
                "[IOSDeviceState udid=%s transport=%s xctrace=%s coreDevice=%s tunnel=%s paired=%s " +
                "xcuitest=%s teamId=%s bundleId=%s wdaPrebuilt=%s ready=%s runnerReady=%s age=%ds]",
                physicalUdid.isEmpty() ? "(none)" : physicalUdid,
                transportType.isEmpty() ? "?" : transportType,
                xctraceVisible    ? "✅" : "❌",
                coreDeviceVisible ? "✅" : "❌",
                tunnelConnected   ? "connected ✅" : "disconnected ⚠️",
                paired            ? "✅" : "❌",
                xcuitestInstalled ? "✅" : "❌",
                teamId.isEmpty()  ? "❌" : "✅",
                bundleId.isEmpty() ? "❌" : "✅",
                wdaPrebuilt       ? "✅" : "❌",
                readyStr,
                runnerReadyStr,
                ageSeconds()
        );
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String safe(String s) { return s != null ? s : ""; }

    private static long parseLong(String s, long def) {
        try   { return s != null ? Long.parseLong(s.trim()) : def; }
        catch (NumberFormatException e) { return def; }
    }
}
