package config;

import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

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
 * Single source of truth:
 *   All device-state queries are delegated to IOSDeviceStateService. Results are cached
 *   in-process so that IosPreflightManager (Runner JVM), DriverFactory and this class
 *   never produce contradictory answers about the same device at the same point in time.
 *
 * This class:
 *  1. Reads sync state via IOSDeviceStateService (Runner-confirmed or fresh query)
 *  2. Classifies any desync condition
 *  3. Attempts auto-recovery — time-bounded by MAX_RECOVERY_TOTAL_SECONDS
 *  4. Throws SyncException with category + actionSuggested if recovery fails
 *
 * Called from DriverFactory.runIosPreSessionDiagnostic() right before new IOSDriver() —
 * never called on Android.
 */
public final class IOSDeviceSynchronizationManager {

    static final int  MAX_RECOVERY_ATTEMPTS       = 2;
    static final int  RECOVERY_POLL_SECONDS        = 4;
    /**
     * Hard wall-clock limit for the entire recovery cycle.
     * Recovery runs at most twice (daemon restart + one fresh query per attempt).
     * Fast-fail keeps the total under 30 s even on slow hardware.
     */
    static final int  MAX_RECOVERY_TOTAL_SECONDS   = 30;

    private IOSDeviceSynchronizationManager() {}

    // ── Failure categories ────────────────────────────────────────────────────

    public enum SyncCategory {
        DEVICE_NOT_PAIRED,            // device not trusted by this Mac
        DEVICE_LOCKED,                // screen locked (warn-only, non-blocking)
        COREDEVICE_DESYNC,            // CoreDevice sees it, xctrace does not (pre-recovery)
        XCTRACE_NOT_VISIBLE,          // neither tool can see the device
        XCTRACE_DEVICE_NOT_VISIBLE,   // CoreDevice visible, xctrace never sees device after recovery
        TUNNEL_DISCONNECTED,          // confirmed tunnel=disconnected AND xctrace never became visible
        WDA_BUILD_FAILED,             // xcodebuild failed to compile WebDriverAgent
        WDA_SIGNING_FAILED,           // code-signing error during WDA build
        APPIUM_DRIVER_NOT_FOUND,      // XCUITest driver not installed (generic)
        XCUITEST_DRIVER_NOT_INSTALLED,// XCUITest driver missing from 'appium driver list'
        INVALID_PLATFORM_NAME,        // platformName was not exactly "iOS"
        DEVICE_NOT_FOUND_BY_APPIUM,   // device visible in xctrace but Appium rejected UDID
        SESSION_CREATION_FAILED       // device visible but Appium rejected the session
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

    // ── Legacy state snapshot (used by DriverFactory.classifyIosSessionFailure) ──

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
            this.tunnelState       = tunnelState  != null ? tunnelState  : "unknown";
            this.pairingState      = pairingState != null ? pairingState : "unknown";
            this.coreDeviceId      = coreDeviceId != null ? coreDeviceId : "";
        }

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
     * State is obtained from IOSDeviceStateService — which may return Runner-confirmed
     * data without running any subprocess, or a fresh query if no Runner state is present.
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

        // Single authoritative state query — cached in IOSDeviceStateService for this Gradle run.
        // When the Runner passed -DiosState.xctraceVisible, this returns instantly (no subprocess).
        IOSDeviceStateService.DeviceState state = IOSDeviceStateService.getState(udid, log);

        // ── Happy path FIRST — no subprocess calls when Runner already confirmed ready ──
        if (state.xctraceVisible) {
            if (state.fromRunner) {
                log.info("[DeviceSync] ✅ Estado Runner confirmado — xctrace ✅ CoreDevice {} (edad: {} s)",
                        state.coreDeviceVisible ? "✅" : "N/A", state.ageSeconds());
            } else {
                log.info("[DeviceSync] ✅ Dispositivo sincronizado — xctrace ✅ CoreDevice {} ({} ms)",
                        state.coreDeviceVisible ? "✅" : "N/A",
                        System.currentTimeMillis() - startMs);
            }
            return;
        }

        // ── Device not visible in xctrace — run full diagnostic ──
        log.info("[DeviceSync] ══ Validando sincronización del dispositivo ══");
        log.info("[DeviceSync] UDID: {}", udid);
        logState(log, state, 0, 0);

        // Hard fail: device not paired
        if ("unpaired".equalsIgnoreCase(state.pairingState)) {
            throw new SyncException(
                SyncCategory.DEVICE_NOT_PAIRED,
                "[DeviceSync] Dispositivo " + udid + " no emparejado con este Mac.",
                "Desbloquea el iPhone → acepta «Confiar en este Mac».\n"
                + "  Luego: Ajustes → Privacidad y seguridad → Modo desarrollador → ON");
        }

        // Advisory: screen may be locked — only check when there is already a problem
        // (avoids a devicectl subprocess on the happy path)
        if (state.coreDeviceVisible && isDeviceLocked(udid)) {
            log.warn("[DeviceSync] ⚠️  El iPhone puede estar bloqueado — desbloquéalo si la sesión falla.");
        }

        // CoreDevice visible, xctrace not → desync → attempt auto-recovery
        if (state.coreDeviceVisible) {
            log.warn("[DeviceSync] ⚠️  Desync detectado — CoreDevice ✅ | xctrace ❌{}",
                    "disconnected".equalsIgnoreCase(state.tunnelState) ? " (tunnel=disconnected)" : "");
            log.info("[DeviceSync] Iniciando recuperación automática (máx {} s)...",
                    MAX_RECOVERY_TOTAL_SECONDS);
            recoverDesync(udid, state, log, startMs);
            return;
        }

        // Neither CoreDevice nor xctrace
        throw new SyncException(
            SyncCategory.XCTRACE_NOT_VISIBLE,
            "[DeviceSync] Dispositivo " + udid + " NO visible en CoreDevice ni xctrace.",
            "Verifica: cable USB conectado, iPhone desbloqueado,\n"
            + "  «Confiar en este Mac» aceptado, Developer Mode activo\n"
            + "  (Ajustes → Privacidad y seguridad → Modo desarrollador).");
    }

    // ── Recovery loop ─────────────────────────────────────────────────────────

    private static void recoverDesync(String udid, IOSDeviceStateService.DeviceState initial,
                                       Logger log, long startMs) {
        String coreDeviceId = initial.coreDeviceId;
        long   deadline     = startMs + (MAX_RECOVERY_TOTAL_SECONDS * 1_000L);

        // ── When tunnel is explicitly disconnected, restart the CoreDevice daemon first ──
        // This is the only real repair action available without sudo. 'killall' works when
        // the test runner and remotedeviced share the same UID (standard developer setup).
        if ("disconnected".equalsIgnoreCase(initial.tunnelState)) {
            log.info("[DeviceSync] Tunnel desconectado — reiniciando daemon remotedeviced...");
            runSilent("killall", "-9", "remotedeviced");
            try { Thread.sleep(3_000); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            IOSDeviceStateService.DeviceState after = IOSDeviceStateService.refresh(udid, log);
            long elapsed = (System.currentTimeMillis() - startMs) / 1000L;
            if (after.xctraceVisible) {
                log.info("[DeviceSync] ✅ Daemon reiniciado — xctrace visible ({} s)", elapsed);
                return;
            }
            log.warn("[DeviceSync] Daemon reiniciado pero xctrace aún no ve el dispositivo ({} s)", elapsed);
            // Fall through to limited polling loop for any remaining time
        }

        log.info("[DeviceSync] Límite de tiempo: {} s", MAX_RECOVERY_TOTAL_SECONDS);

        int attempt = 0;
        IOSDeviceStateService.DeviceState last = initial;

        for (int i = 1; i <= MAX_RECOVERY_ATTEMPTS; i++) {
            if (System.currentTimeMillis() >= deadline) {
                log.warn("[DeviceSync] Tiempo máximo de recuperación agotado antes del intento {}.", i);
                break;
            }
            attempt = i;

            if (!coreDeviceId.isBlank()) {
                log.info("[DeviceSync] [{}] Reconectando CoreDevice id={}...", i, coreDeviceId);
                triggerConnection(coreDeviceId);
            } else {
                log.info("[DeviceSync] [{}] CoreDevice ID no disponible — esperando daemon...", i);
            }

            try { Thread.sleep(RECOVERY_POLL_SECONDS * 1_000L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }

            if (System.currentTimeMillis() >= deadline) {
                log.warn("[DeviceSync] Tiempo máximo agotado tras espera del intento {}.", i);
                break;
            }

            last = IOSDeviceStateService.refresh(udid, log);
            if (!last.coreDeviceId.isBlank()) coreDeviceId = last.coreDeviceId;

            long elapsed = (System.currentTimeMillis() - startMs) / 1000L;
            logState(log, last, i, elapsed);

            if (last.xctraceVisible) {
                log.info("[DeviceSync] ✅ Recuperación exitosa — xctrace visible (intento {}, {} s)",
                        i, elapsed);
                return;
            }

            long remaining = (deadline - System.currentTimeMillis()) / 1000L;
            if (i < MAX_RECOVERY_ATTEMPTS && remaining > 0) {
                log.warn("[DeviceSync] [{}] xctrace aún sin ver el dispositivo — ~{} s restantes",
                        i, remaining);
            }
        }

        long elapsed = (System.currentTimeMillis() - startMs) / 1000L;

        SyncCategory finalCategory = "disconnected".equalsIgnoreCase(last.tunnelState)
                ? SyncCategory.TUNNEL_DISCONNECTED
                : SyncCategory.XCTRACE_DEVICE_NOT_VISIBLE;

        throw new SyncException(
            finalCategory,
            "[DeviceSync] No se pudo sincronizar " + udid + " tras "
            + attempt + " intentos (" + elapsed + " s). Categoría: " + finalCategory,
            finalCategory == SyncCategory.TUNNEL_DISCONNECTED
                ? "Tunnel CoreDevice desconectado — pasos manuales:\n"
                  + "  1. sudo killall -9 remotedeviced\n"
                  + "  2. Desconecta y reconecta el cable USB\n"
                  + "  3. Abre Xcode → Window → Devices and Simulators"
                : "CoreDevice detecta el dispositivo pero xctrace nunca lo ve:\n"
                  + "  1. Desconecta y reconecta el cable USB\n"
                  + "  2. sudo killall -9 remotedeviced   (reinicia daemon CoreDevice)\n"
                  + "  3. sudo killall -9 usbmuxd          (reinicia daemon USB multiplexer)\n"
                  + "  4. Abre Xcode → Window → Devices and Simulators"
        );
    }

    private static void triggerConnection(String coreDeviceId) {
        runSilent("xcrun", "devicectl", "device", "connection", "connect",
                  "--device", coreDeviceId);
        runSilent("xcrun", "devicectl", "device", "info", "--device", coreDeviceId);
    }

    // ── Package-private: readState — used by DriverFactory.classifyIosSessionFailure ──

    /**
     * Returns the current device state as a SyncState snapshot.
     *
     * When called post-session-failure, triggers a fresh query (bypasses any cached
     * "device is ready" state from before the failure) so classification reflects
     * the actual device state at the moment the session was rejected.
     */
    static SyncState readState(String udid) {
        // Post-failure: always refresh — the cached "ready" state is no longer valid
        IOSDeviceStateService.DeviceState ds = IOSDeviceStateService.refresh(udid, null);
        return new SyncState(
            ds.coreDeviceVisible,
            ds.xctraceVisible,
            ds.tunnelState,
            ds.pairingState,
            ds.coreDeviceId
        );
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private static void logState(Logger log, IOSDeviceStateService.DeviceState s,
                                  int attempt, long elapsedSec) {
        String pfx  = attempt == 0 ? "[DeviceSync]" : "[DeviceSync][" + attempt + "]";
        String time = attempt == 0 ? "" : " (" + elapsedSec + " s)";
        log.info("{} Estado de sincronización{}:", pfx, time);
        log.info("{}   CoreDevice     : {}", pfx, s.coreDeviceVisible ? "✅ visible"  : "❌ NO visible");
        log.info("{}   xctrace        : {}", pfx, s.xctraceVisible    ? "✅ visible"  : "❌ NO visible");
        log.info("{}   Tunnel         : {}", pfx, fmtState(s.tunnelState,  "connected"));
        log.info("{}   Pairing        : {}", pfx, fmtState(s.pairingState, "paired"));
        log.info("{}   Fuente         : {}", pfx, s.fromRunner
                ? "Runner (confirmado hace " + s.ageSeconds() + " s)"
                : "consulta directa");
        if (!s.coreDeviceId.isBlank())
            log.info("{}   CoreDevice ID  : {}", pfx, s.coreDeviceId);
    }

    private static String fmtState(String val, String good) {
        if (val == null || "unknown".equalsIgnoreCase(val)) return "desconocido";
        return val + (val.equalsIgnoreCase(good) ? " ✅" : " ⚠️");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isDeviceLocked(String udid) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(IOSDeviceStateService.DEVICECTL_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!json.contains(udid)) return false;
            int idx = json.indexOf(udid);
            String region = json.substring(Math.max(0, idx - 800), Math.min(json.length(), idx + 800));
            return region.contains("\"screenViewingRequiresPasscode\":true")
                || region.contains("\"isPasscodeLocked\":true")
                || region.contains("\"screenLocked\":true");
        } catch (Exception ignored) {}
        return false;
    }

    private static void runSilent(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            p.waitFor(8, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }
}
