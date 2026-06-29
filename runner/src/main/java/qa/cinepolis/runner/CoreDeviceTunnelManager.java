package qa.cinepolis.runner;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and establishes the CoreDevice tunnel before Appium session creation.
 *
 * Xcode 16+ uses a CoreDevice tunnel (via RemoteDeviceDiscoveryD) to communicate
 * with physical iOS devices. When tunnelState=disconnected, Appium's XCUITest driver
 * will reject the device UDID with "Unknown device or simulator UDID" even though
 * the device is physically connected via USB.
 *
 * This class:
 *  1. Reads tunnel/pairing state from `xcrun devicectl list devices --json-output -`
 *  2. Verifies the device appears in `xcrun xctrace list devices` (== Devices == section)
 *  3. If disconnected: triggers reconnection attempt and polls until connected or timeout
 *  4. Logs CoreDevice ID, Physical UDID, Tunnel State, Pairing State, xctrace visibility
 *
 * Must be called from IosPreflightManager.runPreflight() BEFORE WDA operations and
 * BEFORE Gradle (DriverFactory) is spawned. Never touches Android logic.
 */
public final class CoreDeviceTunnelManager {

    static final int TUNNEL_TIMEOUT_SECONDS = 60;
    static final int POLL_INTERVAL_SECONDS  = 3;

    private static final Pattern COREDEVICE_ID_PAT = Pattern.compile(
            "\"identifier\"\\s*:\\s*\"" +
            "([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\"");

    private CoreDeviceTunnelManager() {}

    // ── Result type ────────────────────────────────────────────────────────────

    public static class DeviceConnectionState {
        public final String  coreDeviceId;
        public final String  physicalUdid;
        public final String  tunnelState;
        public final String  pairingState;
        public final boolean xctraceVisible;

        DeviceConnectionState(String coreDeviceId, String physicalUdid,
                               String tunnelState, String pairingState,
                               boolean xctraceVisible) {
            this.coreDeviceId   = coreDeviceId  != null ? coreDeviceId  : "";
            this.physicalUdid   = physicalUdid  != null ? physicalUdid  : "";
            this.tunnelState    = tunnelState   != null ? tunnelState   : "unknown";
            this.pairingState   = pairingState  != null ? pairingState  : "unknown";
            this.xctraceVisible = xctraceVisible;
        }

        /**
         * True when the device is accessible to Appium's XCUITest driver.
         *
         * xctraceVisible is the definitive gate: a device in 'xcrun xctrace list devices
         * == Devices ==' is reachable for testing regardless of what devicectl reports.
         *
         * tunnelState is intentionally NOT a gate. In Xcode 16+/Xcode 26, devicectl may
         * report tunnelState=disconnected even when the device is physically connected via
         * USB and WDA can start and respond. This was confirmed: WDA emitted
         * ServerURLHere->http://192.168.x.x:8100 while tunnelState showed "disconnected".
         *
         * "unpaired" is an explicit failure — device has not trusted the Mac.
         */
        public boolean isReadyForAppium() {
            if (!xctraceVisible) return false;
            return !"unpaired".equalsIgnoreCase(pairingState);
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Validates the CoreDevice tunnel for the given physical UDID, waiting up to
     * TUNNEL_TIMEOUT_SECONDS for it to become ready.
     *
     * If devicectl is unavailable (older Xcode) or the device is not found,
     * falls back to xctrace visibility as the readiness signal.
     *
     * @param client      for sending preflight log entries to the backend
     * @param executionId current execution/session identifier
     * @param physicalUdid device physical UDID (00008110-... format) obtained by IOSDeviceScanner
     * @return final observed state (may not be ready if timeout was reached)
     */
    public static DeviceConnectionState ensureTunnelConnected(
            BackendClient client, String executionId, String physicalUdid) {

        // Fast path: if WDA is already responding, the device is definitely usable —
        // skip the tunnel check entirely. This avoids a 60s wait when WDA was kept
        // alive from a previous run.
        if (WdaManager.isWdaRunning()) {
            client.sendLog(executionId, "INFO",
                    "✅ [CoreDevice] WebDriverAgent ya activo — omitiendo verificación de tunnel.");
            DeviceConnectionState s = readConnectionState(physicalUdid);
            if (s != null) logState(client, executionId, s);
            return s != null ? s
                    : new DeviceConnectionState("", physicalUdid, "active/wda-running", "paired", true);
        }

        client.sendLog(executionId, "INFO",
                "🔌 [CoreDevice] Verificando estado de conexión del dispositivo...");

        DeviceConnectionState state = readConnectionState(physicalUdid);

        // devicectl unavailable or device absent from its output → optimistic fallback
        if (state == null) {
            boolean visible = isVisibleInXctrace(physicalUdid);
            DeviceConnectionState fallback = new DeviceConnectionState(
                    "", physicalUdid, "unknown", "unknown", visible);
            client.sendLog(executionId, visible ? "INFO" : "WARN",
                    (visible ? "✅" : "⚠️") + " [CoreDevice] devicectl sin datos para " + physicalUdid
                    + (visible
                       ? " — dispositivo visible en xctrace, continuando."
                       : " — dispositivo NO visible en xctrace. Appium puede fallar."));
            return fallback;
        }

        logState(client, executionId, state);

        if (state.isReadyForAppium()) {
            client.sendLog(executionId, "INFO",
                    "✅ [CoreDevice] Tunnel activo — dispositivo listo para Appium.");
            return state;
        }

        // Tunnel not connected or device not in xctrace → attempt reconnection and poll
        return awaitReady(client, executionId, physicalUdid, state);
    }

    /**
     * Reads the current CoreDevice connection state for the device with the given
     * physical UDID by parsing `xcrun devicectl list devices --json-output -`.
     *
     * Physical UDID lives in hardwareProperties.udid.
     * tunnelState / pairingState are in connectionProperties / deviceProperties
     * (typically 500-2500 chars before hardwareProperties in the JSON object).
     * CoreDevice identifier UUID follows hardwareProperties.
     *
     * Returns null when devicectl is unavailable or the device is not found.
     */
    public static DeviceConnectionState readConnectionState(String physicalUdid) {
        if (physicalUdid == null || physicalUdid.isBlank()) return null;
        String json = runDevicectlJson();
        if (json == null || !json.contains(physicalUdid)) return null;

        int    idx    = json.indexOf(physicalUdid);
        // 3000 chars backward covers connectionProperties and deviceProperties;
        // 1000 chars forward covers the identifier UUID that follows hardwareProperties.
        String region = json.substring(Math.max(0, idx - 3000), Math.min(json.length(), idx + 1000));

        String coreDeviceId = extractCoreDeviceId(region);
        String tunnelState  = extractJsonString(region, "tunnelState");
        String pairingState = extractJsonString(region, "pairingState");
        // localHostEnrollmentState is an Xcode 26 alias for pairingState
        if (pairingState == null) {
            String enrolled = extractJsonString(region, "localHostEnrollmentState");
            if ("enrolled".equalsIgnoreCase(enrolled)) pairingState = "paired";
        }
        boolean xctraceVisible = isVisibleInXctrace(physicalUdid);

        return new DeviceConnectionState(coreDeviceId, physicalUdid,
                tunnelState, pairingState, xctraceVisible);
    }

    // ── Polling loop ───────────────────────────────────────────────────────────

    private static DeviceConnectionState awaitReady(
            BackendClient client, String executionId,
            String physicalUdid, DeviceConnectionState initial) {

        client.sendLog(executionId, "INFO",
                "🔄 [CoreDevice] Tunnel desconectado — ejecutando recuperación automática...\n"
                + "   Tiempo máximo de espera: " + TUNNEL_TIMEOUT_SECONDS + "s");

        DeviceConnectionState last = initial;

        // ── Paso 1: reiniciar daemon remotedeviced ─────────────────────────────
        // El daemon puede quedar bloqueado sin sincronizarse con xctrace. killall -9
        // lo reinicia sin requerir sudo cuando el proceso Runner corre como el mismo UID
        // que inició remotedeviced (instalación estándar de desarrollador).
        client.sendLog(executionId, "INFO",
                "   [CoreDevice] Reiniciando daemon remotedeviced (killall -9)...");
        runSilent("killall", "-9", "remotedeviced");
        try { Thread.sleep(4_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        DeviceConnectionState afterRestart = readConnectionState(physicalUdid);
        if (afterRestart != null) {
            last = afterRestart;
            if (last.isReadyForAppium()) {
                client.sendLog(executionId, "INFO",
                        "✅ [CoreDevice] Dispositivo disponible tras reinicio del daemon (4s).");
                logState(client, executionId, last);
                return last;
            }
        }
        client.sendLog(executionId, "INFO",
                "   [CoreDevice] Daemon reiniciado — dispositivo aún no visible en xctrace. Iniciando polling...");

        // ── Paso 2: trigger de reconexión + polling ────────────────────────────
        String coreDeviceId = last.coreDeviceId.isBlank() ? initial.coreDeviceId : last.coreDeviceId;
        if (!coreDeviceId.isBlank()) tryTriggerConnection(coreDeviceId);

        long deadline = System.currentTimeMillis() + (TUNNEL_TIMEOUT_SECONDS * 1_000L);
        int  attempt  = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try { Thread.sleep(POLL_INTERVAL_SECONDS * 1_000L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }

            DeviceConnectionState current = readConnectionState(physicalUdid);
            if (current != null) last = current;

            if (last.isReadyForAppium()) {
                client.sendLog(executionId, "INFO",
                        "✅ [CoreDevice] Dispositivo disponible después de ~"
                        + (attempt * POLL_INTERVAL_SECONDS) + "s.");
                logState(client, executionId, last);
                return last;
            }

            // Progress update every ~15 s (every 5 polls at 3s interval)
            if (attempt % 5 == 0) {
                long remaining = (deadline - System.currentTimeMillis()) / 1_000;
                client.sendLog(executionId, "INFO",
                        "   ⏳ [CoreDevice] Esperando que dispositivo aparezca en xctrace... ("
                        + remaining + "s restantes)"
                        + "  pairingState=" + last.pairingState
                        + "  xctrace=" + (last.xctraceVisible ? "visible" : "no visible"));
                coreDeviceId = last.coreDeviceId.isBlank() ? coreDeviceId : last.coreDeviceId;
                if (!coreDeviceId.isBlank()) tryTriggerConnection(coreDeviceId);
            }
        }

        // ── Timeout: diagnóstico de causa raíz preciso ─────────────────────────
        String rootCause = diagnoseFinalState(last, physicalUdid);
        client.sendLog(executionId, "WARN",
                "⚠️  [CoreDevice] Tiempo agotado (" + TUNNEL_TIMEOUT_SECONDS + "s).\n"
                + "   Causa raíz diagnosticada: " + rootCause
                + stateDetail(last)
                + "\n   Acciones manuales si el problema persiste:"
                + "\n   1. Desbloquea el iPhone → acepta «Confiar en este Mac»"
                + "\n   2. Ajustes → Privacidad y seguridad → Modo desarrollador → activar"
                + "\n   3. Desconecta y vuelve a conectar el cable USB"
                + "\n   4. Abre Xcode → Window → Devices and Simulators");
        return last;
    }

    /**
     * Diagnoses the likely root cause when the device fails to become visible in xctrace.
     * Returns a human-readable string suitable for a WARN log line.
     */
    private static String diagnoseFinalState(DeviceConnectionState s, String physicalUdid) {
        if ("unpaired".equalsIgnoreCase(s.pairingState)) {
            return "dispositivo no ha aceptado confianza en este Mac "
                 + "(pairingState=unpaired) — desbloquea el iPhone y acepta «Confiar en este Mac»";
        }
        if (s.coreDeviceId.isBlank()) {
            return "CoreDevice no detecta el dispositivo UDID=" + physicalUdid
                 + " — posible cable USB no reconocido, daemon aún iniciándose "
                 + "o UDID incorrecto en la configuración";
        }
        if (s.xctraceVisible) {
            // isReadyForAppium() returned false but xctraceVisible=true — only other cause is unpaired
            return "pairingState=" + s.pairingState
                 + " — dispositivo no emparejado con este Mac";
        }
        if ("connected".equalsIgnoreCase(s.tunnelState)) {
            return "tunnelState=connected pero xctrace aún no ve el dispositivo — "
                 + "desync persistente entre CoreDevice y xctrace; "
                 + "intenta desconectar y reconectar el cable USB";
        }
        if ("disconnected".equalsIgnoreCase(s.tunnelState)) {
            return "tunnelState=disconnected tras reiniciar daemon remotedeviced — "
                 + "el daemon no logró establecer el túnel CoreDevice; "
                 + "posible problema USB, pantalla del iPhone bloqueada o confianza pendiente";
        }
        return "tunnelState=" + s.tunnelState + " xctraceVisible=false "
             + "coreDeviceId=" + (s.coreDeviceId.isBlank() ? "(no detectado)" : s.coreDeviceId)
             + " — dispositivo encontrado en CoreDevice pero no visible para Appium/xctrace";
    }

    // ── Tunnel trigger ─────────────────────────────────────────────────────────

    /**
     * Sends lightweight devicectl commands that may prompt RemoteDeviceDiscoveryD
     * to re-establish the CoreDevice tunnel. Uses the CoreDevice UUID (identifier),
     * NOT the physical UDID.
     */
    private static void tryTriggerConnection(String coreDeviceId) {
        // Attempt explicit connection (Xcode 16+; silently ignored on older Xcode)
        runSilent("xcrun", "devicectl", "device", "connection", "connect",
                "--device", coreDeviceId);
        // Info probe — may refresh daemon awareness of the device
        runSilent("xcrun", "devicectl", "device", "info", "--device", coreDeviceId);
    }

    // ── xctrace check ──────────────────────────────────────────────────────────

    /**
     * Returns true when the physical UDID appears in the '== Devices ==' section of
     * `xcrun xctrace list devices`. Devices with a disconnected CoreDevice tunnel,
     * untrusted devices, or offline devices are absent from this section.
     */
    static boolean isVisibleInXctrace(String physicalUdid) {
        if (physicalUdid == null || physicalUdid.isBlank()) return false;
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(12, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return false; }

            boolean inDevices = false;
            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("== Devices =="))  { inDevices = true;  continue; }
                if (t.startsWith("=="))              { inDevices = false; continue; }
                if (inDevices && t.contains(physicalUdid)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ── Logging ────────────────────────────────────────────────────────────────

    private static void logState(BackendClient client, String executionId,
                                  DeviceConnectionState s) {
        // Technical detail (tunnel/pairing/xctrace state) goes to the Logs Técnicos tab only
        client.sendTechLog(executionId,
                "🔌 [CoreDevice] Estado del dispositivo:" + stateDetail(s));
        // Functional summary visible in Actividad en Tiempo Real only when device is ready
        if (s.isReadyForAppium()) {
            client.sendLog(executionId, "INFO",
                    "✅ [CoreDevice] Dispositivo conectado y listo para Appium.");
        }
    }

    private static String stateDetail(DeviceConnectionState s) {
        String tunnelIcon  = "connected".equalsIgnoreCase(s.tunnelState)  ? " ✅" : " ⚠️";
        String pairingIcon = "paired".equalsIgnoreCase(s.pairingState)    ? " ✅" : " ⚠️";
        String xtraceIcon  = s.xctraceVisible                             ? " ✅" : " ⚠️";
        return "\n   CoreDevice ID  : " + (s.coreDeviceId.isBlank() ? "(no detectado)" : s.coreDeviceId)
             + "\n   Physical UDID  : " + s.physicalUdid + "  ← appium:udid"
             + "\n   Tunnel State   : " + s.tunnelState  + tunnelIcon
             + "\n   Pairing State  : " + s.pairingState + pairingIcon
             + "\n   xctrace        : " + (s.xctraceVisible ? "visible" : "NO visible") + xtraceIcon;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private static String runDevicectlJson() {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(12, TimeUnit.SECONDS);
            return json;
        } catch (Exception e) {
            System.err.println("[CoreDevice] devicectl --json-output error: " + e.getMessage());
            return null;
        }
    }

    private static String extractCoreDeviceId(String region) {
        Matcher m = COREDEVICE_ID_PAT.matcher(region);
        return m.find() ? m.group(1) : null;
    }

    private static String extractJsonString(String region, String key) {
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
