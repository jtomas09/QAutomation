package config;

import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ensures a physical iOS device is fully synchronized across CoreDevice (devicectl)
 * and xctrace before an Appium session is created.
 *
 * Root cause of "Unknown device or simulator UDID":
 *   After Xcode 15+/CoreDevice migration, a device can be visible in devicectl JSON
 *   yet absent from 'xcrun xctrace list devices == Devices =='. Appium's XCUITest driver
 *   uses xctrace (or equivalent) to validate the UDID before creating a session, so
 *   CoreDevice visibility alone is not sufficient.
 *
 * This class:
 *  1. Reads sync state from CoreDevice (devicectl JSON) and xctrace
 *  2. Classifies any desync condition (COREDEVICE_DESYNC, TUNNEL_DISCONNECTED, etc.)
 *  3. Attempts auto-recovery by triggering CoreDevice tunnel reconnection
 *  4. Throws SyncException with category + action if recovery fails
 *
 * Called from DriverFactory.runIosPreSessionDiagnostic() right before new IOSDriver() —
 * never called on Android.
 */
public final class IOSDeviceSynchronizationManager {

    static final int MAX_RECOVERY_ATTEMPTS = 6;
    static final int RECOVERY_POLL_SECONDS = 5;

    private static final Pattern COREDEVICE_ID_PAT = Pattern.compile(
            "\"identifier\"\\s*:\\s*\"" +
            "([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\"");

    private IOSDeviceSynchronizationManager() {}

    // ── Failure categories ────────────────────────────────────────────────────

    public enum SyncCategory {
        DEVICE_NOT_PAIRED,       // device not trusted by this Mac
        DEVICE_LOCKED,           // screen locked (warn-only, non-blocking)
        COREDEVICE_DESYNC,       // CoreDevice sees it, xctrace does not
        XCTRACE_NOT_VISIBLE,     // neither tool can see the device
        TUNNEL_DISCONNECTED,     // CoreDevice tunnel disconnected + xctrace invisible
        WDA_BUILD_FAILED,        // xcodebuild failed to compile WebDriverAgent
        WDA_SIGNING_FAILED,      // code-signing error during WDA build
        APPIUM_DRIVER_NOT_FOUND, // XCUITest driver not installed
        SESSION_CREATION_FAILED  // device visible but Appium rejected the session
    }

    public static class SyncException extends IllegalStateException {
        public final SyncCategory category;
        public final String       actionSuggested;

        SyncException(SyncCategory category, String message, String actionSuggested) {
            super(message);
            this.category        = category;
            this.actionSuggested = actionSuggested;
        }
    }

    // ── State snapshot ────────────────────────────────────────────────────────

    public static class SyncState {
        public final boolean coreDeviceVisible;
        public final boolean xctraceVisible;
        public final String  tunnelState;
        public final String  pairingState;
        public final String  coreDeviceId;

        SyncState(boolean coreDeviceVisible, boolean xctraceVisible,
                   String tunnelState, String pairingState, String coreDeviceId) {
            this.coreDeviceVisible = coreDeviceVisible;
            this.xctraceVisible    = xctraceVisible;
            this.tunnelState       = tunnelState   != null ? tunnelState   : "unknown";
            this.pairingState      = pairingState  != null ? pairingState  : "unknown";
            this.coreDeviceId      = coreDeviceId  != null ? coreDeviceId  : "";
        }

        /**
         * xctrace visibility is the authoritative gate for Appium.
         * tunnelState is intentionally not a gate: in Xcode 26, "disconnected" tunnel
         * may coexist with a usable device (observed WDA starting while tunnel=disconnected).
         */
        public boolean isReadyForAppium() {
            return xctraceVisible && !"unpaired".equalsIgnoreCase(pairingState);
        }

        public boolean hasDesync() {
            return coreDeviceVisible && !xctraceVisible;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ensures the physical iOS device is visible in xctrace before Appium session creation.
     *
     * <ul>
     *   <li>DEVICE_NOT_PAIRED → throws immediately (user action required)</li>
     *   <li>COREDEVICE_DESYNC → attempts auto-recovery (MAX_RECOVERY_ATTEMPTS × RECOVERY_POLL_SECONDS)</li>
     *   <li>XCTRACE_NOT_VISIBLE → throws immediately</li>
     *   <li>Device ready → returns normally</li>
     * </ul>
     *
     * @param udid physical device UDID (00008110-... format)
     * @param log  caller's SLF4J logger
     * @throws SyncException with category + actionSuggested if device cannot be synchronized
     */
    public static void ensureReady(String udid, Logger log) {
        if (udid == null || udid.isBlank()) {
            log.info("[DeviceSync] No UDID configurado — omitiendo validación de sincronización.");
            return;
        }

        long startMs = System.currentTimeMillis();
        log.info("[DeviceSync] ══ Validando sincronización del dispositivo ══");
        log.info("[DeviceSync] UDID: {}", udid);

        SyncState state = readState(udid);
        logState(log, state, 0, 0);

        // Hard fail: device not paired — needs user interaction on the device
        if ("unpaired".equalsIgnoreCase(state.pairingState)) {
            throw new SyncException(
                SyncCategory.DEVICE_NOT_PAIRED,
                "[DeviceSync] Dispositivo " + udid + " no emparejado con este Mac.",
                "Desbloquea el iPhone → acepta «Confiar en este Mac».\n"
                + "  Luego: Ajustes → Privacidad y seguridad → Modo desarrollador → ON");
        }

        // Advisory: warn if screen may be locked (WDA can still connect, don't block)
        if (state.coreDeviceVisible && isDeviceLocked(udid)) {
            log.warn("[DeviceSync] ⚠️  El iPhone puede estar bloqueado — desbloquéalo si la sesión falla.");
        }

        // Happy path: xctrace sees the device
        if (state.xctraceVisible) {
            long ms = System.currentTimeMillis() - startMs;
            log.info("[DeviceSync] ✅ Dispositivo sincronizado — xctrace ✅ CoreDevice {} ({}ms)",
                     state.coreDeviceVisible ? "✅" : "N/A", ms);
            return;
        }

        // CoreDevice visible, xctrace not → COREDEVICE_DESYNC → attempt auto-recovery
        if (state.coreDeviceVisible) {
            SyncCategory cat = "disconnected".equalsIgnoreCase(state.tunnelState)
                    ? SyncCategory.TUNNEL_DISCONNECTED
                    : SyncCategory.COREDEVICE_DESYNC;
            log.warn("[DeviceSync] ⚠️  {} detectado — CoreDevice ✅ | xctrace ❌", cat);
            log.info("[DeviceSync] Iniciando recuperación automática ({} intentos × {}s = {}s máx)...",
                     MAX_RECOVERY_ATTEMPTS, RECOVERY_POLL_SECONDS,
                     MAX_RECOVERY_ATTEMPTS * RECOVERY_POLL_SECONDS);
            recoverDesync(udid, state, log, startMs, cat);
            return;
        }

        // Neither CoreDevice nor xctrace can see the device
        throw new SyncException(
            SyncCategory.XCTRACE_NOT_VISIBLE,
            "[DeviceSync] Dispositivo " + udid + " NO visible en CoreDevice ni xctrace.",
            "Verifica: cable USB conectado, iPhone desbloqueado,\n"
            + "  «Confiar en este Mac» aceptado, Developer Mode activo\n"
            + "  (Ajustes → Privacidad y seguridad → Modo desarrollador).");
    }

    // ── Recovery loop ─────────────────────────────────────────────────────────

    private static void recoverDesync(String udid, SyncState initial,
                                       Logger log, long startMs, SyncCategory category) {
        String coreDeviceId = initial.coreDeviceId;

        for (int attempt = 1; attempt <= MAX_RECOVERY_ATTEMPTS; attempt++) {
            if (!coreDeviceId.isBlank()) {
                log.info("[DeviceSync] [{}] Reconectando CoreDevice id={}...", attempt, coreDeviceId);
                triggerConnection(coreDeviceId);
            } else {
                log.info("[DeviceSync] [{}] CoreDevice ID no disponible — esperando daemon...", attempt);
            }

            try { Thread.sleep(RECOVERY_POLL_SECONDS * 1_000L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }

            SyncState current = readState(udid);
            // Update coreDeviceId if we found it this time
            if (!current.coreDeviceId.isBlank() && coreDeviceId.isBlank()) {
                coreDeviceId = current.coreDeviceId;
            }

            long elapsed = (System.currentTimeMillis() - startMs) / 1_000L;
            logState(log, current, attempt, elapsed);

            if (current.xctraceVisible) {
                log.info("[DeviceSync] ✅ Recuperación exitosa — xctrace visible (intento {}, {}s)",
                         attempt, elapsed);
                return;
            }

            if (attempt < MAX_RECOVERY_ATTEMPTS) {
                log.warn("[DeviceSync] [{}] xctrace aún sin ver el dispositivo — {}s restantes...",
                         attempt, (MAX_RECOVERY_ATTEMPTS - attempt) * RECOVERY_POLL_SECONDS);
            }
        }

        long elapsed = (System.currentTimeMillis() - startMs) / 1_000L;
        throw new SyncException(
            category,
            "[DeviceSync] No se pudo sincronizar " + udid + " tras "
            + MAX_RECOVERY_ATTEMPTS + " intentos (" + elapsed + "s). Categoría: " + category,
            "CoreDevice detecta el dispositivo pero xctrace no responde. Acciones:\n"
            + "  1. Desconecta y vuelve a conectar el cable USB\n"
            + "  2. Desbloquea el iPhone → acepta «Confiar en este Mac»\n"
            + "  3. sudo killall -9 remotedeviced   (reinicia el daemon CoreDevice)\n"
            + "  4. sudo killall -9 usbmuxd          (reinicia el daemon USB multiplexer)\n"
            + "  5. Abre Xcode → Window → Devices and Simulators — verifica que el dispositivo aparezca sin advertencia");
    }

    private static void triggerConnection(String coreDeviceId) {
        // Explicit connect (Xcode 15+; silently ignored on older Xcode)
        runSilent("xcrun", "devicectl", "device", "connection", "connect",
                  "--device", coreDeviceId);
        // Info probe refreshes daemon awareness of the device
        runSilent("xcrun", "devicectl", "device", "info", "--device", coreDeviceId);
    }

    // ── State reading (package-private: used by DriverFactory.classifyIosSessionFailure) ──

    static SyncState readState(String udid) {
        boolean coreDeviceVisible = isUdidInDevicectl(udid);
        boolean xctraceVisible    = isUdidInXctrace(udid);
        String  tunnelState       = "unknown";
        String  pairingState      = "unknown";
        String  coreDeviceId      = "";

        if (coreDeviceVisible) {
            String[] details = readDevicectlDetails(udid);
            tunnelState  = details[0];
            pairingState = details[1];
            coreDeviceId = details[2];
        }

        return new SyncState(coreDeviceVisible, xctraceVisible, tunnelState, pairingState, coreDeviceId);
    }

    private static String[] readDevicectlDetails(String udid) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(12, TimeUnit.SECONDS);
            if (!json.contains(udid)) return new String[]{"unknown", "unknown", ""};

            int idx = json.indexOf(udid);
            // 3000 chars backward covers connectionProperties / deviceProperties;
            // 1000 chars forward covers the CoreDevice identifier UUID.
            String region = json.substring(Math.max(0, idx - 3000),
                                           Math.min(json.length(), idx + 1000));

            String tunnel  = extractJson(region, "tunnelState");
            String pairing = extractJson(region, "pairingState");
            if (pairing == null) {
                // Xcode 26 uses localHostEnrollmentState instead of pairingState
                String enrolled = extractJson(region, "localHostEnrollmentState");
                if ("enrolled".equalsIgnoreCase(enrolled)) pairing = "paired";
            }
            Matcher m = COREDEVICE_ID_PAT.matcher(region);
            String coreId = m.find() ? m.group(1) : "";

            return new String[]{
                tunnel  != null ? tunnel  : "unknown",
                pairing != null ? pairing : "unknown",
                coreId
            };
        } catch (Exception ignored) {
            return new String[]{"unknown", "unknown", ""};
        }
    }

    private static boolean isDeviceLocked(String udid) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(12, TimeUnit.SECONDS);
            if (!json.contains(udid)) return false;
            int idx = json.indexOf(udid);
            String region = json.substring(Math.max(0, idx - 800), Math.min(json.length(), idx + 800));
            return region.contains("\"screenViewingRequiresPasscode\":true")
                || region.contains("\"isPasscodeLocked\":true")
                || region.contains("\"screenLocked\":true");
        } catch (Exception ignored) {}
        return false;
    }

    // ── Visibility checks ─────────────────────────────────────────────────────

    private static boolean isUdidInXctrace(String udid) {
        if (udid == null || udid.isBlank()) return false;
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(12, TimeUnit.SECONDS);
            boolean inDevices = false;
            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("== Devices ==")) { inDevices = true;  continue; }
                if (t.startsWith("=="))             { inDevices = false; continue; }
                if (inDevices && t.contains(udid))  return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isUdidInDevicectl(String udid) {
        if (udid == null || udid.isBlank()) return false;
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(12, TimeUnit.SECONDS);
            return json.contains(udid);
        } catch (Exception ignored) {}
        return false;
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private static void logState(Logger log, SyncState s, int attempt, long elapsedSec) {
        String pfx  = attempt == 0 ? "[DeviceSync]" : "[DeviceSync][" + attempt + "]";
        String time = attempt == 0 ? "" : " (" + elapsedSec + "s)";
        log.info("{} Estado de sincronización{}:", pfx, time);
        log.info("{}   CoreDevice   : {}", pfx, s.coreDeviceVisible ? "✅ visible"  : "❌ NO visible");
        log.info("{}   xctrace      : {}", pfx, s.xctraceVisible    ? "✅ visible"  : "❌ NO visible");
        log.info("{}   Tunnel       : {}", pfx, fmtState(s.tunnelState,  "connected"));
        log.info("{}   Pairing      : {}", pfx, fmtState(s.pairingState, "paired"));
        if (!s.coreDeviceId.isBlank())
            log.info("{}   CoreDevice ID: {}", pfx, s.coreDeviceId);
    }

    private static String fmtState(String val, String good) {
        if (val == null || "unknown".equalsIgnoreCase(val)) return "desconocido";
        return val + (val.equalsIgnoreCase(good) ? " ✅" : " ⚠️");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractJson(String region, String key) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(region);
        return m.find() ? m.group(1) : null;
    }

    private static void runSilent(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            p.waitFor(8, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }
}
