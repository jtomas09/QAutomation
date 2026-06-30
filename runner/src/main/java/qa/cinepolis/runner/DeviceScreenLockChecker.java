package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/**
 * Checks whether an iOS device screen is currently locked using
 * {@code xcrun devicectl device info details}.
 *
 * Called at three points in the execution lifecycle:
 *  1. IosPreflightManager.runPreflight() — initial check before any preflight step.
 *  2. IosPreflightManager.runPreflight() — stability check after all preflight steps.
 *  3. JobExecutor — immediately before launching the Gradle subprocess.
 *
 * The Framework side tracks elapsed time since the last confirmed unlock
 * (iosState.confirmedUnlockedAtMs) and warns when the gap exceeds 5 seconds.
 *
 * Never throws — returns optimistic (unlocked=true) when the query fails or the
 * lock-state field is absent from the JSON. This prevents a missing field from
 * blocking executions on setups where the device is always unlocked.
 */
public final class DeviceScreenLockChecker {

    private static final ObjectMapper MAPPER      = new ObjectMapper();
    private static final int          TIMEOUT_SEC = 8;

    private DeviceScreenLockChecker() {}

    // ── Result ────────────────────────────────────────────────────────────────

    public static final class LockState {
        /** True when confirmed unlocked — or state is unknown (optimistic). */
        public final boolean unlocked;
        /** How the state was determined: "devicectl", "optimistic", "optimistic-no-udid". */
        public final String  method;
        /** System.currentTimeMillis() when this check ran. */
        public final long    checkedAtMs;

        LockState(boolean unlocked, String method) {
            this.unlocked    = unlocked;
            this.method      = method;
            this.checkedAtMs = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return (unlocked ? "UNLOCKED ✅" : "LOCKED ❌") + " (via " + method + ")";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current lock state for the given physical UDID.
     *
     * Queries {@code xcrun devicectl device info details --device <udid> --json-output -}
     * and parses {@code deviceProperties} for known lock-state field names, which vary
     * across Xcode versions:
     * <ul>
     *   <li>{@code screenLocked}</li>
     *   <li>{@code isPasscodeLocked}</li>
     *   <li>{@code screenViewingRequiresPasscode}</li>
     * </ul>
     *
     * Returns {@code unlocked=true, method="optimistic"} when:
     * <ul>
     *   <li>UDID is blank.</li>
     *   <li>The command times out or fails.</li>
     *   <li>None of the known lock-state fields is present in the JSON.</li>
     * </ul>
     */
    public static LockState check(String udid) {
        if (udid == null || udid.isBlank()) return new LockState(true, "optimistic-no-udid");
        Boolean locked = queryDevicectl(udid);
        if (locked != null) return new LockState(!locked, "devicectl");
        return new LockState(true, "optimistic");
    }

    // ── Private implementation ────────────────────────────────────────────────

    private static Boolean queryDevicectl(String udid) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "info", "details",
                    "--device", udid, "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return null; }

            JsonNode root = MAPPER.readTree(json);

            // Xcode 15–25: result.device (singular object)
            JsonNode deviceNode = root.path("result").path("device");
            // Xcode 26+: may use result.devices (array) even for single-device details
            if (deviceNode.isMissingNode() || deviceNode.isNull()) {
                JsonNode arr = root.path("result").path("devices");
                if (arr.isArray() && !arr.isEmpty()) deviceNode = arr.get(0);
            }
            if (deviceNode == null || deviceNode.isMissingNode() || deviceNode.isNull()) return null;

            JsonNode devProps = deviceNode.path("deviceProperties");
            if (devProps.isMissingNode() || devProps.isNull()) return null;

            for (String field : new String[]{
                    "screenLocked", "isPasscodeLocked", "screenViewingRequiresPasscode"}) {
                JsonNode n = devProps.path(field);
                if (n.isBoolean()) return n.asBoolean();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
