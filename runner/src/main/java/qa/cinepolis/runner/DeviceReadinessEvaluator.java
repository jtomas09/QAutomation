package qa.cinepolis.runner;

/**
 * Computes device readiness for Appium test execution independently of DeviceStatus.
 *
 * Separates two orthogonal concerns:
 *
 *   DeviceStatus (AVAILABLE / DISCOVERED / OFFLINE)
 *     → "Is the device in the Runner's inventory?"
 *
 *   DeviceReadinessEvaluator.Readiness.readyForExecution
 *     → "Can Appium start a session on this device right now?"
 *
 * A device can be AVAILABLE but NOT ready (e.g. Appium down, Xcode missing).
 * A device can be DISCOVERED and NOT ready (WiFi detected but no active tunnel).
 *
 * readyForExecution = true requires ALL of:
 *   1. TransportType is WIRED or (LOCAL_NETWORK + tunnel connected)
 *   2. PairingState is not "unpaired"
 *   3. Appium responding — APPIUM_OK system prop (written by DependencySelfHealingManager)
 *   4. Xcode installed on macOS — XCODE_OK system prop (same source)
 *
 * System properties are treated as "not yet checked" (optimistic) when null,
 * and only block readiness when explicitly "false".  This avoids false negatives
 * on first startup before DependencySelfHealingManager has run its first cycle.
 */
public final class DeviceReadinessEvaluator {

    private DeviceReadinessEvaluator() {}

    // ── Enums ─────────────────────────────────────────────────────────────────

    /** Where the device was discovered. */
    public enum Presence {
        /** Cable USB — xcrun devicectl transportType=wired. */
        USB,
        /** Wi-Fi / Bonjour — xcrun devicectl transportType=localNetwork. */
        LOCAL_NETWORK,
        /** Transport not reported by devicectl (xctrace-only path, older Xcode). */
        UNKNOWN
    }

    /** Normalized CoreDevice tunnel state. */
    public enum TunnelStatus {
        /** Tunnel active — Appium can communicate over CoreDevice. */
        CONNECTED,
        /** Tunnel broken — usually requires USB reconnect or `devicectl connection connect`. */
        DISCONNECTED,
        /** Not available from this discovery path (xctrace) or not reported. */
        UNKNOWN
    }

    // ── Readiness result ──────────────────────────────────────────────────────

    public static final class Readiness {
        public final Presence     presence;
        public final TunnelStatus tunnel;
        /** True only when all conditions for Appium session creation are met. */
        public final boolean      readyForExecution;
        /** Human-readable explanation when readyForExecution=false; null when ready. */
        public final String       notReadyReason;

        Readiness(Presence presence, TunnelStatus tunnel,
                  boolean ready, String notReadyReason) {
            this.presence          = presence;
            this.tunnel            = tunnel;
            this.readyForExecution = ready;
            this.notReadyReason    = notReadyReason;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluates readiness from DeviceInfo (devicectl JSON path — Xcode 15+/26).
     * Called by IOSDeviceScanner.applyDeviceInfo().
     */
    public static Readiness evaluate(DevicectlParser.DeviceInfo info) {
        Presence     presence = presenceFrom(info.transportType);
        TunnelStatus tunnel   = tunnelFrom(info.tunnelState);

        // 1. Transport/tunnel — the primary gate for Appium connectivity
        if (info.transportType == DevicectlParser.TransportType.UNKNOWN) {
            return fail(presence, tunnel, "Tipo de transporte no identificado");
        }
        if (info.transportType == DevicectlParser.TransportType.LOCAL_NETWORK
                && tunnel != TunnelStatus.CONNECTED) {
            return fail(presence, tunnel,
                "Wi-Fi / Bonjour detectado — túnel CoreDevice "
                + info.tunnelState
                + " (conecta USB o usa: xcrun devicectl device connection connect)");
        }

        // 2. Pairing — device must have trusted this Mac
        if ("unpaired".equalsIgnoreCase(info.pairingState)) {
            return fail(presence, tunnel,
                "Dispositivo no emparejado — desbloquea el iPhone y acepta «Confiar en este Mac»");
        }

        // 3. System health — read from JVM props set by DependencySelfHealingManager
        //    null means "not yet checked on first startup" → optimistically OK
        if (systemCheckFailed("APPIUM_OK")) {
            return fail(presence, tunnel, "Appium no disponible (APPIUM_OK=false)");
        }
        if (isMacOs() && systemCheckFailed("XCODE_OK")) {
            return fail(presence, tunnel, "Xcode no disponible o no instalado (XCODE_OK=false)");
        }

        return new Readiness(presence, tunnel, true, null);
    }

    /**
     * Evaluates readiness for xctrace-discovered devices (no DeviceInfo available).
     *
     * xcrun xctrace '== Devices ==' guarantees the device is physically accessible.
     * Presence and tunnel are UNKNOWN because xctrace does not expose transport details.
     */
    public static Readiness evaluateXctrace() {
        if (systemCheckFailed("APPIUM_OK")) {
            return fail(Presence.UNKNOWN, TunnelStatus.UNKNOWN, "Appium no disponible (APPIUM_OK=false)");
        }
        if (isMacOs() && systemCheckFailed("XCODE_OK")) {
            return fail(Presence.UNKNOWN, TunnelStatus.UNKNOWN, "Xcode no disponible o no instalado (XCODE_OK=false)");
        }
        return new Readiness(Presence.UNKNOWN, TunnelStatus.UNKNOWN, true, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Readiness fail(Presence p, TunnelStatus t, String reason) {
        return new Readiness(p, t, false, reason);
    }

    /**
     * Returns true only when the JVM property is explicitly "false".
     * null or "true" → healthy (unknown = optimistic on first startup).
     */
    private static boolean systemCheckFailed(String property) {
        return "false".equals(System.getProperty(property));
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static Presence presenceFrom(DevicectlParser.TransportType type) {
        if (type == DevicectlParser.TransportType.WIRED)         return Presence.USB;
        if (type == DevicectlParser.TransportType.LOCAL_NETWORK) return Presence.LOCAL_NETWORK;
        return Presence.UNKNOWN;
    }

    private static TunnelStatus tunnelFrom(String raw) {
        if (raw == null)                            return TunnelStatus.UNKNOWN;
        if ("connected".equalsIgnoreCase(raw))      return TunnelStatus.CONNECTED;
        if ("disconnected".equalsIgnoreCase(raw))   return TunnelStatus.DISCONNECTED;
        return TunnelStatus.UNKNOWN;
    }
}
