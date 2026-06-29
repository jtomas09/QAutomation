package config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth for iOS device hardware/sync state within the test (Gradle) JVM.
 *
 * Problem solved:
 *   IosPreflightManager (Runner JVM), DriverFactory and IOSDeviceSynchronizationManager
 *   all independently ran 'xcrun xctrace list devices' + 'xcrun devicectl' at different
 *   moments. Because xctrace visibility is transient (CoreDevice daemon state changes
 *   between queries), three independent queries returned contradictory results —
 *   the sync manager started a 6-attempt recovery loop for a device the Runner had
 *   already confirmed ready 10 seconds earlier.
 *
 * Solution:
 *   1. All xctrace / devicectl queries in the test JVM go through this service.
 *   2. Results are cached in-process: every subsequent caller in the same Gradle run
 *      receives the identical DeviceState object — no independent subprocess.
 *   3. When the Runner confirms the device is ready via -DiosState.* JVM properties
 *      (set by JobExecutor after IosPreflightManager.runPreflight() succeeds), the service
 *      trusts that result for up to STATE_MAX_AGE_MS without running any subprocess.
 *   4. Each individual command is timed and its latency logged at INFO level.
 *
 * Usage:
 *   DeviceState state = IOSDeviceStateService.getState(udid, log);   // cached on first call
 *   DeviceState fresh = IOSDeviceStateService.refresh(udid, log);    // forces new subprocess
 */
public final class IOSDeviceStateService {

    private static final Logger log = LoggerFactory.getLogger(IOSDeviceStateService.class);

    /** JVM property prefix — written by JobExecutor after Runner-side preflight confirms ready. */
    static final String PROP_PREFIX = "iosState.";

    /**
     * Maximum age for Runner-confirmed state when no explicit Runner property is present.
     * Not used when {@code -DiosState.xctraceVisible} is explicitly set — in that case
     * the Runner state is trusted unconditionally (see {@link #loadOrQuery}).
     * Kept for backwards-compatibility with any code that reads this constant.
     */
    static final long STATE_MAX_AGE_MS = 120_000L;

    /** Per-command timeouts — bounds each individual subprocess call. */
    static final int XCTRACE_TIMEOUT_SEC   = 15;
    static final int DEVICECTL_TIMEOUT_SEC = 12;

    private IOSDeviceStateService() {}

    // ── DeviceState ───────────────────────────────────────────────────────────

    public static class DeviceState {
        public final boolean xctraceVisible;
        public final boolean coreDeviceVisible;
        public final String  tunnelState;
        public final String  pairingState;
        public final String  coreDeviceId;
        /** True when state was loaded from Runner JVM properties (no subprocess executed). */
        public final boolean fromRunner;
        /** Wall-clock time (ms) when this snapshot was captured. */
        public final long    capturedAtMs;

        DeviceState(boolean xctraceVisible, boolean coreDeviceVisible,
                    String tunnelState, String pairingState, String coreDeviceId,
                    boolean fromRunner, long capturedAtMs) {
            this.xctraceVisible    = xctraceVisible;
            this.coreDeviceVisible = coreDeviceVisible;
            this.tunnelState       = tunnelState  != null ? tunnelState  : "unknown";
            this.pairingState      = pairingState != null ? pairingState : "unknown";
            this.coreDeviceId      = coreDeviceId != null ? coreDeviceId : "";
            this.fromRunner        = fromRunner;
            this.capturedAtMs      = capturedAtMs;
        }

        /** True when Appium's XCUITest driver should be able to see this device. */
        public boolean isReadyForAppium() {
            return xctraceVisible && !"unpaired".equalsIgnoreCase(pairingState);
        }

        /** True when CoreDevice sees the device but xctrace does not (desync condition). */
        public boolean hasDesync() {
            return coreDeviceVisible && !xctraceVisible;
        }

        /** True when this snapshot is younger than {@code maxAgeMs} milliseconds. */
        public boolean isFresh(long maxAgeMs) {
            return (System.currentTimeMillis() - capturedAtMs) < maxAgeMs;
        }

        /** Age of this snapshot in whole seconds. */
        public long ageSeconds() {
            return (System.currentTimeMillis() - capturedAtMs) / 1000L;
        }

        @Override
        public String toString() {
            return String.format(
                "[xctrace=%s coreDevice=%s tunnel=%s pairing=%s age=%ds src=%s]",
                xctraceVisible    ? "✅" : "❌",
                coreDeviceVisible ? "✅" : "❌",
                tunnelState, pairingState,
                ageSeconds(),
                fromRunner ? "runner" : "query");
        }
    }

    // ── In-process cache (per-UDID, per Gradle run) ───────────────────────────

    private static final ConcurrentHashMap<String, DeviceState> CACHE =
            new ConcurrentHashMap<>();

    /**
     * Returns the device state for {@code udid} using the following priority:
     * <ol>
     *   <li>In-process cache — same JVM, zero subprocess overhead for repeated callers</li>
     *   <li>Runner-confirmed JVM properties — fresh (age &lt; STATE_MAX_AGE_MS)</li>
     *   <li>Fresh subprocess query (xcrun xctrace + xcrun devicectl)</li>
     * </ol>
     *
     * @param udid   physical device UDID
     * @param caller caller's SLF4J logger (may be null — falls back to this class's logger)
     */
    public static DeviceState getState(String udid, Logger caller) {
        if (udid == null || udid.isBlank()) return emptyState();
        DeviceState cached = CACHE.get(udid);
        if (cached != null) {
            Logger l = caller != null ? caller : log;
            l.debug("[DeviceState] Cache hit — {}", cached);
            return cached;
        }
        return loadOrQuery(udid, caller != null ? caller : log);
    }

    /**
     * Forces a fresh subprocess query, updates the in-process cache, and returns
     * the new state.  Call this during recovery polling — the cached value must not
     * be reused when we need to know whether the device became visible.
     */
    public static DeviceState refresh(String udid, Logger caller) {
        if (udid == null || udid.isBlank()) return emptyState();
        CACHE.remove(udid);
        DeviceState fresh = queryFresh(udid, caller != null ? caller : log);
        CACHE.put(udid, fresh);
        return fresh;
    }

    /** Evicts the cache entry for {@code udid}. */
    public static void invalidate(String udid) {
        if (udid != null) CACHE.remove(udid);
    }

    // ── Load from JVM props or run fresh query ────────────────────────────────

    private static DeviceState loadOrQuery(String udid, Logger logger) {
        String xctraceStr = System.getProperty(PROP_PREFIX + "xctraceVisible");

        // When the Runner explicitly injected -DiosState.xctraceVisible, trust it
        // unconditionally — no age check, no subprocess. The Runner is the authority:
        // it ran every validation (xctrace, devicectl, pairing) before Gradle started.
        // If the device went offline after the Runner confirmed, Appium reports it
        // clearly during session creation — no silent false-negative here.
        if (xctraceStr != null) {
            String confirmedAtStr = System.getProperty(PROP_PREFIX + "confirmedAtMs");
            long confirmedAt = parseLong(confirmedAtStr, 0L);
            long ageMs = System.currentTimeMillis() - confirmedAt;
            DeviceState fromProps = new DeviceState(
                "true".equalsIgnoreCase(xctraceStr.trim()),
                "true".equalsIgnoreCase(
                        System.getProperty(PROP_PREFIX + "coreDeviceVisible", "true").trim()),
                System.getProperty(PROP_PREFIX + "tunnelState",  "unknown"),
                System.getProperty(PROP_PREFIX + "pairingState", "unknown"),
                System.getProperty(PROP_PREFIX + "coreDeviceId", ""),
                true,
                confirmedAt > 0 ? confirmedAt : System.currentTimeMillis()
            );
            CACHE.put(udid, fromProps);
            logger.info("[DeviceState] ✅ Estado Runner ({} s) — sin consulta: {}", ageMs / 1000, fromProps);
            return fromProps;
        }

        DeviceState fresh = queryFresh(udid, logger);
        CACHE.put(udid, fresh);
        return fresh;
    }

    // ── Fresh subprocess query ────────────────────────────────────────────────

    static DeviceState queryFresh(String udid, Logger logger) {
        logger.info("[DeviceState] Consultando estado del dispositivo {}...", udid);

        long t0 = nanos();
        boolean xctrace = xctraceCheck(udid);
        long xctMs = msElapsed(t0);

        t0 = nanos();
        String[] details = devicectlDetails(udid);
        long dctlMs = msElapsed(t0);

        boolean coreDevice = "true".equals(details[3]);

        logger.info("[DeviceState]   xcrun xctrace list devices     → {} ({} ms)",
                xctrace    ? "VISIBLE ✅" : "no visible ❌", xctMs);
        logger.info("[DeviceState]   xcrun devicectl list devices   → CoreDevice:{} tunnel:{} pairing:{} ({} ms)",
                coreDevice ? "VISIBLE ✅" : "no visible ❌", details[0], details[1], dctlMs);

        return new DeviceState(
                xctrace, coreDevice, details[0], details[1], details[2],
                false, System.currentTimeMillis());
    }

    // ── Subprocess helpers (package-private for IOSDeviceSynchronizationManager) ──

    /**
     * Returns true when the physical UDID appears in the '== Devices ==' section of
     * {@code xcrun xctrace list devices}.
     * Exits after {@link #XCTRACE_TIMEOUT_SEC} seconds and returns false on timeout.
     */
    static boolean xctraceCheck(String udid) {
        if (udid == null || udid.isBlank()) return false;
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(XCTRACE_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                log.warn("[DeviceState] xcrun xctrace timeout ({}s) — device state unknown", XCTRACE_TIMEOUT_SEC);
                return false;
            }
            boolean inDevices = false;
            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("== Devices ==")) { inDevices = true;  continue; }
                if (t.startsWith("=="))             { inDevices = false; continue; }
                if (inDevices && t.contains(udid))  return true;
            }
        } catch (Exception e) {
            log.debug("[DeviceState] xctrace error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Queries {@code xcrun devicectl list devices --json-output -} and returns a 4-element array:
     * <pre>[tunnelState, pairingState, coreDeviceId, coreDeviceVisible]</pre>
     * where {@code coreDeviceVisible} is the string {@code "true"} when the UDID was found.
     *
     * Uses {@link DevicectlParser#findByUdid} for all JSON extraction — no indexOf/substring/regex.
     */
    static String[] devicectlDetails(String udid) {
        String[] none = {"unknown", "unknown", "", "false"};
        if (udid == null || udid.isBlank()) return none;
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(DEVICECTL_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                log.warn("[DeviceState] xcrun devicectl timeout ({}s) — device state unknown", DEVICECTL_TIMEOUT_SEC);
                return none;
            }
            DevicectlParser.DeviceInfo info = DevicectlParser.findByUdid(json, udid);
            if (info == null) return none;
            return new String[]{
                info.tunnelState,
                info.pairingState,
                info.coreDeviceId,
                "true"
            };
        } catch (Exception e) {
            log.debug("[DeviceState] devicectl error: {}", e.getMessage());
        }
        return none;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static DeviceState emptyState() {
        return new DeviceState(false, false, "unknown", "unknown", "",
                false, System.currentTimeMillis());
    }

    private static long nanos()              { return System.nanoTime(); }
    private static long msElapsed(long ns)   { return (System.nanoTime() - ns) / 1_000_000L; }

    private static long parseLong(String s, long def) {
        try   { return s != null ? Long.parseLong(s.trim()) : def; }
        catch (NumberFormatException e) { return def; }
    }

}
