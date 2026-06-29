package config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson-based parser for {@code xcrun devicectl list devices --json-output -}.
 *
 * Centralizes all devicectl JSON parsing for the test (Gradle) JVM. Replaces indexOf/substring/regex
 * extraction in IOSDeviceStateService and IOSDeviceSynchronizationManager.
 *
 * Device lookup is always keyed on {@code hardwareProperties.udid} (physical UDID) to avoid
 * the Xcode 26 regression where the UDID appears first in {@code connectionProperties.potentialHostnames},
 * causing window-based extraction to miss the {@code identifier} field.
 *
 * Thread-safe: the shared ObjectMapper is stateless after construction.
 */
public final class DevicectlParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DevicectlParser() {}

    // ── Transport type ─────────────────────────────────────────────────────────

    public enum TransportType {
        /** USB cable — daemon restarts are safe. */
        WIRED,
        /** WiFi / Bonjour — killing remotedeviced destroys the Bonjour session. */
        LOCAL_NETWORK,
        /** Transport not reported or not recognized. */
        UNKNOWN;

        static TransportType from(String raw) {
            if (raw == null || raw.isBlank()) return UNKNOWN;
            String normalized = raw.toLowerCase().replace("_", "").replace("-", "");
            if ("wired".equals(normalized))        return WIRED;
            if ("localnetwork".equals(normalized)) return LOCAL_NETWORK;
            return UNKNOWN;
        }
    }

    // ── Device info ────────────────────────────────────────────────────────────

    public static final class DeviceInfo {
        /** {@code identifier} — CoreDevice UUID (RFC 4122, 36 chars). */
        public final String        coreDeviceId;
        /** {@code hardwareProperties.udid} — physical UDID sent to Appium. */
        public final String        physicalUdid;
        /** {@code connectionProperties.tunnelState} */
        public final String        tunnelState;
        /** {@code connectionProperties.pairingState} or Xcode 26 {@code localHostEnrollmentState}. */
        public final String        pairingState;
        /** {@code connectionProperties.transportType} */
        public final TransportType transportType;
        /** iOS version string, e.g. {@code "26.5"}. */
        public final String        osVersion;
        /** {@code deviceProperties.developerModeEnabled} or {@code developerModeStatus == "enabled"}. */
        public final boolean       developerModeEnabled;
        /** True when any lock-related property in {@code deviceProperties} is set. */
        public final boolean       screenLocked;

        DeviceInfo(String coreDeviceId, String physicalUdid,
                   String tunnelState, String pairingState,
                   TransportType transportType, String osVersion,
                   boolean developerModeEnabled, boolean screenLocked) {
            this.coreDeviceId         = nn(coreDeviceId);
            this.physicalUdid         = nn(physicalUdid);
            this.tunnelState          = nnOr(tunnelState,  "unknown");
            this.pairingState         = nnOr(pairingState, "unknown");
            this.transportType        = transportType != null ? transportType : TransportType.UNKNOWN;
            this.osVersion            = nn(osVersion);
            this.developerModeEnabled = developerModeEnabled;
            this.screenLocked         = screenLocked;
        }

        public boolean isTunnelConnected() {
            return "connected".equalsIgnoreCase(tunnelState);
        }

        public boolean isPaired() {
            return !pairingState.isEmpty()
                && !"unpaired".equalsIgnoreCase(pairingState)
                && !"unknown".equalsIgnoreCase(pairingState);
        }

        @Override
        public String toString() {
            return "[DeviceInfo udid=" + physicalUdid
                 + " coreId=" + (coreDeviceId.isEmpty() ? "(none)" : coreDeviceId)
                 + " transport=" + transportType
                 + " tunnel=" + tunnelState
                 + " pairing=" + pairingState
                 + " devMode=" + developerModeEnabled + "]";
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Finds the device whose {@code hardwareProperties.udid} equals {@code physicalUdid}.
     * Returns {@code null} when not found, JSON is invalid, or input is blank.
     */
    public static DeviceInfo findByUdid(String json, String physicalUdid) {
        if (blank(json) || blank(physicalUdid)) return null;
        try {
            JsonNode root    = MAPPER.readTree(json);
            JsonNode devices = root.path("result").path("devices");
            if (!devices.isArray()) return null;
            for (JsonNode d : devices) {
                if (physicalUdid.equals(d.path("hardwareProperties").path("udid").asText(""))) {
                    return fromNode(d);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Returns all physical devices ({@code hardwareProperties.reality = "physical"}).
     */
    public static List<DeviceInfo> findAllPhysical(String json) {
        List<DeviceInfo> result = new ArrayList<>();
        if (blank(json)) return result;
        try {
            JsonNode root    = MAPPER.readTree(json);
            JsonNode devices = root.path("result").path("devices");
            if (devices.isArray()) {
                for (JsonNode d : devices) {
                    if ("physical".equalsIgnoreCase(
                            d.path("hardwareProperties").path("reality").asText(""))) {
                        result.add(fromNode(d));
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    // ── Internal parsing ───────────────────────────────────────────────────────

    private static DeviceInfo fromNode(JsonNode device) {
        String coreDeviceId = device.path("identifier").asText("");
        String physicalUdid = device.path("hardwareProperties").path("udid").asText("");

        JsonNode conn       = device.path("connectionProperties");
        String tunnelState  = conn.path("tunnelState").asText("unknown");
        String transportRaw = conn.path("transportType").asText("");

        String pairingState = conn.path("pairingState").asText(null);
        if (blank(pairingState)) {
            pairingState = device.path("deviceProperties").path("pairingState").asText(null);
        }
        if (blank(pairingState)) {
            String enrolled = conn.path("localHostEnrollmentState").asText(null);
            pairingState = "enrolled".equalsIgnoreCase(enrolled) ? "paired" : null;
        }

        String osVersion = device.path("deviceProperties").path("osVersionNumber").asText(null);
        if (blank(osVersion)) {
            for (String f : new String[]{"osVersionNumber", "operatingSystemVersion",
                                          "softwareVersion", "osVersion"}) {
                String v = device.path("hardwareProperties").path(f).asText(null);
                if (!blank(v)) { osVersion = v; break; }
            }
        }

        JsonNode devProps = device.path("deviceProperties");

        boolean devMode;
        JsonNode dmBool = devProps.path("developerModeEnabled");
        if (dmBool.isBoolean()) {
            devMode = dmBool.asBoolean();
        } else {
            devMode = "enabled".equalsIgnoreCase(devProps.path("developerModeStatus").asText(null));
        }

        boolean locked = devProps.path("screenViewingRequiresPasscode").asBoolean(false)
                      || devProps.path("isPasscodeLocked").asBoolean(false)
                      || devProps.path("screenLocked").asBoolean(false);

        return new DeviceInfo(
                coreDeviceId, physicalUdid,
                tunnelState, pairingState,
                TransportType.from(transportRaw),
                osVersion != null ? osVersion : "",
                devMode, locked);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String nn(String s)     { return s != null ? s : ""; }
    private static String nnOr(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
