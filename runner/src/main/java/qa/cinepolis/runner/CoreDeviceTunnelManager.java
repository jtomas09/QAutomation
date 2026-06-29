package qa.cinepolis.runner;

import java.util.concurrent.TimeUnit;

/**
 * Validates and establishes the CoreDevice tunnel before Appium session creation.
 *
 * Xcode 16+ uses a CoreDevice tunnel (via RemoteDeviceDiscoveryD) to communicate
 * with physical iOS devices. When tunnelState=disconnected, Appium's XCUITest driver
 * will reject the device UDID with "Unknown device or simulator UDID" even though
 * the device is physically connected.
 *
 * All devicectl JSON parsing is delegated to {@link DevicectlParser}. No indexOf,
 * substring, or regex is used to interpret devicectl JSON output.
 *
 * Recovery strategy is transport-type-aware:
 *   WIRED        — safe to restart remotedeviced daemon (killall -9)
 *   LOCAL_NETWORK — never kill remotedeviced (destroys Bonjour/WiFi session);
 *                   only devicectl device connection connect is attempted
 *   UNKNOWN       — passive polling only (no destructive actions)
 */
public final class CoreDeviceTunnelManager {

    static final int TUNNEL_TIMEOUT_SECONDS = 60;
    static final int POLL_INTERVAL_SECONDS  = 3;

    private CoreDeviceTunnelManager() {}

    // ── Result type ────────────────────────────────────────────────────────────

    public static class DeviceConnectionState {
        public final String                       coreDeviceId;
        public final String                       physicalUdid;
        public final String                       tunnelState;
        public final String                       pairingState;
        public final boolean                      xctraceVisible;
        public final DevicectlParser.TransportType transportType;

        DeviceConnectionState(String coreDeviceId, String physicalUdid,
                               String tunnelState, String pairingState,
                               boolean xctraceVisible,
                               DevicectlParser.TransportType transportType) {
            this.coreDeviceId  = coreDeviceId  != null ? coreDeviceId  : "";
            this.physicalUdid  = physicalUdid  != null ? physicalUdid  : "";
            this.tunnelState   = tunnelState   != null ? tunnelState   : "unknown";
            this.pairingState  = pairingState  != null ? pairingState  : "unknown";
            this.xctraceVisible = xctraceVisible;
            this.transportType = transportType != null ? transportType : DevicectlParser.TransportType.UNKNOWN;
        }

        DeviceConnectionState(String coreDeviceId, String physicalUdid,
                               String tunnelState, String pairingState,
                               boolean xctraceVisible) {
            this(coreDeviceId, physicalUdid, tunnelState, pairingState, xctraceVisible,
                 DevicectlParser.TransportType.UNKNOWN);
        }

        /**
         * True when the device is accessible to Appium's XCUITest driver.
         *
         * xctraceVisible is the definitive gate. tunnelState is NOT a gate — Xcode 16+/26
         * may report disconnected while WDA can still respond via USB.
         */
        public boolean isReadyForAppium() {
            if (!xctraceVisible) return false;
            return !"unpaired".equalsIgnoreCase(pairingState);
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public static DeviceConnectionState ensureTunnelConnected(
            BackendClient client, String executionId, String physicalUdid) {

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

        // Log DevicectlParser findings to main activity log for validation evidence.
        // This confirms the parser found the device via hardwareProperties.udid (not potentialHostnames).
        client.sendLog(executionId, "INFO",
                "🔍 [DevicectlParser] Dispositivo identificado:\n"
                + "   Physical UDID   : " + state.physicalUdid + "  ← appium:udid\n"
                + "   CoreDevice UUID : " + (state.coreDeviceId.isBlank()
                                             ? "(no detectado)" : state.coreDeviceId) + "\n"
                + "   Transport Type  : " + state.transportType + "\n"
                + "   Tunnel State    : " + state.tunnelState + "\n"
                + "   Pairing State   : " + state.pairingState);

        if (state.isReadyForAppium()) {
            client.sendLog(executionId, "INFO",
                    "✅ [CoreDevice] Tunnel activo — dispositivo listo para Appium.");
            return state;
        }

        return awaitReady(client, executionId, physicalUdid, state);
    }

    /**
     * Reads current CoreDevice state via {@link DevicectlParser#findByUdid}.
     * No indexOf/substring/regex is used to interpret the JSON output.
     */
    public static DeviceConnectionState readConnectionState(String physicalUdid) {
        if (physicalUdid == null || physicalUdid.isBlank()) return null;
        String json = runDevicectlJson();
        if (json == null) return null;

        DevicectlParser.DeviceInfo info = DevicectlParser.findByUdid(json, physicalUdid);
        if (info == null) return null;

        boolean xctraceVisible = isVisibleInXctrace(physicalUdid);
        return new DeviceConnectionState(
                info.coreDeviceId, physicalUdid,
                info.tunnelState, info.pairingState,
                xctraceVisible, info.transportType);
    }

    // ── Recovery loop ──────────────────────────────────────────────────────────

    private static DeviceConnectionState awaitReady(
            BackendClient client, String executionId,
            String physicalUdid, DeviceConnectionState initial) {

        DevicectlParser.TransportType transport = initial.transportType;

        client.sendLog(executionId, "INFO",
                "══════════════ CoreDevice Recovery ══════════════\n"
                + "   TransportType  : " + transport + "\n"
                + "   Physical UDID  : " + physicalUdid + "\n"
                + "   CoreDevice UUID: " + (initial.coreDeviceId.isBlank()
                                            ? "(no detectado)" : initial.coreDeviceId) + "\n"
                + "   Tunnel         : " + initial.tunnelState + "\n"
                + "   Tiempo máximo  : " + TUNNEL_TIMEOUT_SECONDS + "s");

        DeviceConnectionState last = initial;

        if (transport == DevicectlParser.TransportType.WIRED) {
            // USB: killall -9 remotedeviced restarts the daemon cleanly
            client.sendLog(executionId, "INFO",
                    "   Recovery Strategy: WIRED → reiniciando daemon remotedeviced (killall -9)...");
            runSilent("killall", "-9", "remotedeviced");
            sleep(4_000);
            DeviceConnectionState afterRestart = readConnectionState(physicalUdid);
            if (afterRestart != null) last = afterRestart;
            if (last.isReadyForAppium()) {
                client.sendLog(executionId, "INFO",
                        "   Resultado: CONNECTED ✅ (daemon reiniciado, 4s)\n"
                        + "═════════════════════════════════════════════════");
                logState(client, executionId, last);
                return last;
            }
            client.sendLog(executionId, "INFO",
                    "   Daemon reiniciado — dispositivo aún offline. Iniciando polling...");

        } else if (transport == DevicectlParser.TransportType.LOCAL_NETWORK) {
            // WiFi: NEVER kill remotedeviced — it destroys the Bonjour/mDNS session
            // Only action: devicectl device connection connect
            client.sendLog(executionId, "INFO",
                    "   Recovery Strategy: LOCAL_NETWORK → devicectl connection connect\n"
                    + "   (remotedeviced NO reiniciado — destruiría la sesión Bonjour/WiFi)");
            if (!initial.coreDeviceId.isBlank()) {
                // Capture stdout/stderr/exitCode for diagnostic evidence
                String[] cr = runCapture("xcrun", "devicectl", "device", "connection", "connect",
                        "--device", initial.coreDeviceId);
                client.sendLog(executionId, "INFO",
                        "   Comando ejecutado:\n"
                        + "   $ xcrun devicectl device connection connect --device "
                        + initial.coreDeviceId + "\n"
                        + "   Exit code : " + cr[0] + "\n"
                        + "   Stdout    : " + (cr[1].isEmpty() ? "(vacío)" : cr[1]) + "\n"
                        + "   Stderr    : " + (cr[2].isEmpty() ? "(vacío)" : cr[2]));
                runSilent("xcrun", "devicectl", "device", "info", "--device", initial.coreDeviceId);
                sleep(3_000);
                DeviceConnectionState afterConnect = readConnectionState(physicalUdid);
                if (afterConnect != null) last = afterConnect;
                // Log explicit after-state regardless of outcome
                client.sendLog(executionId, "INFO",
                        "   Estado después de connection connect:\n"
                        + "   CoreDevice UUID : " + (last.coreDeviceId.isBlank()
                                                      ? "(no detectado)" : last.coreDeviceId) + "\n"
                        + "   xctrace         : " + (last.xctraceVisible ? "visible ✅" : "NO visible ❌") + "\n"
                        + "   tunnelState     : " + last.tunnelState + "\n"
                        + "   pairingState    : " + last.pairingState);
                if (last.isReadyForAppium()) {
                    client.sendLog(executionId, "INFO",
                            "   Resultado: CONNECTED ✅ (connection connect, 3s)\n"
                            + "═════════════════════════════════════════════════");
                    logState(client, executionId, last);
                    return last;
                }
                client.sendLog(executionId, "INFO",
                        "   Dispositivo aún offline después de connection connect. Iniciando polling...");
            } else {
                client.sendLog(executionId, "WARN",
                        "   CoreDevice UUID no disponible — no se puede enviar connection connect.\n"
                        + "   Iniciando polling pasivo...");
            }

        } else {
            // UNKNOWN: no destructive actions
            client.sendLog(executionId, "INFO",
                    "   Recovery Strategy: UNKNOWN transport — polling pasivo (sin acciones destructivas)");
        }

        // ── Polling loop ───────────────────────────────────────────────────────
        String coreDeviceId = last.coreDeviceId.isBlank() ? initial.coreDeviceId : last.coreDeviceId;
        if (!coreDeviceId.isBlank() && transport != DevicectlParser.TransportType.WIRED) {
            tryTriggerConnection(coreDeviceId);
        }

        long deadline = System.currentTimeMillis() + (TUNNEL_TIMEOUT_SECONDS * 1_000L);
        int  attempt  = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            sleep(POLL_INTERVAL_SECONDS * 1_000L);

            DeviceConnectionState current = readConnectionState(physicalUdid);
            if (current != null) last = current;

            if (last.isReadyForAppium()) {
                client.sendLog(executionId, "INFO",
                        "   Resultado: CONNECTED ✅ (~" + (attempt * POLL_INTERVAL_SECONDS) + "s)\n"
                        + "═════════════════════════════════════════════════");
                logState(client, executionId, last);
                return last;
            }

            if (attempt % 5 == 0) {
                long remaining = (deadline - System.currentTimeMillis()) / 1_000;
                client.sendLog(executionId, "INFO",
                        "   ⏳ Esperando... (" + remaining + "s restantes)"
                        + "  pairing=" + last.pairingState
                        + "  xctrace=" + (last.xctraceVisible ? "visible" : "no visible"));
                coreDeviceId = last.coreDeviceId.isBlank() ? coreDeviceId : last.coreDeviceId;
                if (!coreDeviceId.isBlank() && transport != DevicectlParser.TransportType.WIRED) {
                    tryTriggerConnection(coreDeviceId);
                }
            }
        }

        // ── Timeout: structured diagnosis ──────────────────────────────────────
        String rootCause = diagnoseFinalState(last, physicalUdid, transport);
        client.sendLog(executionId, "WARN",
                "   Resultado: FAILED ⚠️\n"
                + "   Motivo: " + rootCause
                + stateDetail(last)
                + "\n═════════════════════════════════════════════════"
                + "\n   Acciones manuales si el problema persiste:"
                + "\n   1. Desbloquea el iPhone → acepta «Confiar en este Mac»"
                + "\n   2. Ajustes → Privacidad y seguridad → Modo desarrollador → activar"
                + "\n   3. Desconecta y vuelve a conectar el cable USB"
                + "\n   4. Abre Xcode → Window → Devices and Simulators");
        return last;
    }

    private static String diagnoseFinalState(DeviceConnectionState s, String physicalUdid,
                                              DevicectlParser.TransportType transport) {
        if ("unpaired".equalsIgnoreCase(s.pairingState)) {
            return "dispositivo no ha aceptado confianza en este Mac (pairingState=unpaired)";
        }
        if (s.coreDeviceId.isBlank()) {
            return "CoreDevice no detecta UDID=" + physicalUdid
                 + " — posible cable USB no reconocido o UDID incorrecto";
        }
        if (transport == DevicectlParser.TransportType.LOCAL_NETWORK) {
            return "túnel WiFi (LOCAL_NETWORK) desconectado — dispositivo y Mac "
                 + "deben estar en la misma red con Bonjour activo";
        }
        if ("disconnected".equalsIgnoreCase(s.tunnelState)) {
            return "tunnelState=disconnected tras recovery WIRED — "
                 + "intenta desconectar y reconectar el cable USB";
        }
        return "tunnelState=" + s.tunnelState + " xctraceVisible=false transport=" + transport;
    }

    // ── Tunnel trigger ─────────────────────────────────────────────────────────

    private static void tryTriggerConnection(String coreDeviceId) {
        runSilent("xcrun", "devicectl", "device", "connection", "connect",
                  "--device", coreDeviceId);
        runSilent("xcrun", "devicectl", "device", "info", "--device", coreDeviceId);
    }

    // ── xctrace check ──────────────────────────────────────────────────────────

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
        client.sendTechLog(executionId,
                "🔌 [CoreDevice] Estado del dispositivo:" + stateDetail(s));
        if (s.isReadyForAppium()) {
            client.sendLog(executionId, "INFO",
                    "✅ [CoreDevice] Dispositivo conectado y listo para Appium.");
        }
    }

    private static String stateDetail(DeviceConnectionState s) {
        String tunnelIcon  = "connected".equalsIgnoreCase(s.tunnelState)  ? " ✅" : " ⚠️";
        String pairingIcon = "paired".equalsIgnoreCase(s.pairingState)    ? " ✅" : " ⚠️";
        String xtraceIcon  = s.xctraceVisible                             ? " ✅" : " ⚠️";
        return "\n   CoreDevice UUID : " + (s.coreDeviceId.isBlank() ? "(no detectado)" : s.coreDeviceId)
             + "\n   Physical UDID   : " + s.physicalUdid + "  ← appium:udid"
             + "\n   Transport Type  : " + s.transportType
             + "\n   Tunnel State    : " + s.tunnelState  + tunnelIcon
             + "\n   Pairing State   : " + s.pairingState + pairingIcon
             + "\n   xctrace         : " + (s.xctraceVisible ? "visible" : "NO visible") + xtraceIcon;
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

    /** Runs a command and returns [exitCode, stdout, stderr] as strings. */
    private static String[] runCapture(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            String stdout = new String(p.getInputStream().readAllBytes()).trim();
            String stderr = new String(p.getErrorStream().readAllBytes()).trim();
            boolean done = p.waitFor(12, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return new String[]{"-1", "", "timeout (12s)"}; }
            return new String[]{String.valueOf(p.exitValue()), stdout, stderr};
        } catch (Exception e) {
            return new String[]{"-1", "", e.getMessage() != null ? e.getMessage() : "exception"};
        }
    }

    private static void runSilent(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            p.waitFor(8, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
