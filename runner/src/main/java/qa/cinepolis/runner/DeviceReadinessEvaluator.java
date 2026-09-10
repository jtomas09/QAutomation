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
 *
 * ── TAREA 26C — última puerta: WdaLifecycleOwner.TERMINAL_ERRORS ────────────────
 * Esta clase alimenta /api/devices/sync → Dashboard "Dispositivos Conectados"
 * (Pipeline A) — completamente separado de IosPreflightManager/IOSRunnerReadinessEngine
 * (Pipeline B, el que decide si una ejecución real procede). TAREA 26B demostró con
 * evidencia que Pipeline A nunca consultaba WdaLifecycleOwner: un dispositivo con un
 * error terminal real conocido (p.ej. IOS_ACCOUNT_SESSION_REQUIRED) podía seguir
 * apareciendo como "listo" en el Dashboard aunque el flujo de ejecución real ya
 * supiera que no podía usarlo — la regla operativa que motiva esta tarea.
 *
 * Corrección mínima: después de que TODOS los chequeos de conectividad de este
 * archivo (transporte/túnel/pairing/Appium/Xcode) ya habrían declarado el
 * dispositivo listo, se hace UNA última consulta de solo lectura a
 * {@link WdaLifecycleOwner#isTerminalError} — la MISMA fuente que ya usa
 * {@link IOSRunnerReadinessEngine} — y, si existe, se reclasifica su texto con
 * {@link IOSWdaErrorClassifier} (el clasificador YA existente, sin duplicar ningún
 * patrón) únicamente para producir el mensaje humano sugerido cuando el código es
 * {@code IOS_ACCOUNT_SESSION_REQUIRED}; para cualquier otro código se conserva el
 * texto original de {@code terminalErrorReason()} sin reformatear, igual que ya
 * hacen IosPreflightManager/IOSRunnerReadinessEngine. Esta clase NO reimplementa
 * signing/provisioning/trust/account-session/recovery — solo lee un veredicto ya
 * calculado por la autoridad existente.
 *
 * Nunca convierte un dispositivo genuinamente listo en "no listo" por error: la
 * consulta ocurre DESPUÉS de los chequeos de conectividad, así que un dispositivo
 * físicamente desconectado o sin túnel siempre reporta esa razón (más inmediata),
 * nunca queda enmascarado por un terminalError potencialmente más antiguo.
 *
 * Por qué es seguro ante stale state (nunca bloquea permanentemente un dispositivo
 * que ya volvió a estar sano):
 *   - {@code TERMINAL_ERRORS} vive en memoria del proceso del Runner — un reinicio
 *     del Runner lo vacía por completo (Escenario C de la tarea), nunca sobrevive
 *     entre reinicios.
 *   - Cualquier intento real que encuentre WDA ya sano (una ejecución de Job vía
 *     {@code WdaLifecycleOwner.acquire()}, o el propio Mirror vía
 *     {@code requestForMirror()}) ya limpia este mismo estado automáticamente
 *     ({@code resetForRetry()}, ver el comentario "evidencia real (Fase 16)" en
 *     {@code WdaLifecycleOwner.java}) — esta clase nunca necesita su propio
 *     mecanismo de limpieza porque nunca es la única vía de escritura del estado;
 *     solo lo LEE.
 *   - Esta clase JAMÁS llama a {@code markTerminalError}/{@code resetForRetry} —
 *     de solo lectura, cero cambios al ciclo de vida real de {@code WdaLifecycleOwner}.
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
     *
     * @param udid UDID físico del dispositivo — TAREA 26C, usado únicamente para la
     *             última consulta de solo lectura a WdaLifecycleOwner (ver Javadoc
     *             de clase). Puede ser {@code null}/vacío (degrada de forma segura:
     *             simplemente omite esa consulta, comportamiento idéntico al anterior).
     */
    public static Readiness evaluate(DevicectlParser.DeviceInfo info, String udid) {
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

        // 4. TAREA 26C — última puerta: ¿el flujo de ejecución real ya sabe que este
        //    UDID no puede usarse ahora mismo? (ver Javadoc de clase)
        Readiness terminal = terminalErrorOverride(presence, tunnel, udid);
        if (terminal != null) return terminal;

        return new Readiness(presence, tunnel, true, null);
    }

    /**
     * Evaluates readiness for xctrace-discovered devices (no DeviceInfo available).
     *
     * xcrun xctrace '== Devices ==' guarantees the device is physically accessible.
     * Presence and tunnel are UNKNOWN because xctrace does not expose transport details.
     *
     * @param udid ver {@link #evaluate(DevicectlParser.DeviceInfo, String)}.
     */
    public static Readiness evaluateXctrace(String udid) {
        if (systemCheckFailed("APPIUM_OK")) {
            return fail(Presence.UNKNOWN, TunnelStatus.UNKNOWN, "Appium no disponible (APPIUM_OK=false)");
        }
        if (isMacOs() && systemCheckFailed("XCODE_OK")) {
            return fail(Presence.UNKNOWN, TunnelStatus.UNKNOWN, "Xcode no disponible o no instalado (XCODE_OK=false)");
        }
        Readiness terminal = terminalErrorOverride(Presence.UNKNOWN, TunnelStatus.UNKNOWN, udid);
        if (terminal != null) return terminal;

        return new Readiness(Presence.UNKNOWN, TunnelStatus.UNKNOWN, true, null);
    }

    /**
     * TAREA 26C — ver Javadoc de clase para la justificación completa (autoridad
     * reutilizada, por qué es seguro ante stale state). Devuelve {@code null} cuando
     * no hay nada que reportar (sin UDID, o sin error terminal vigente) — el llamador
     * continúa con su propio veredicto {@code readyForExecution=true} sin cambios.
     */
    private static Readiness terminalErrorOverride(Presence presence, TunnelStatus tunnel, String udid) {
        if (udid == null || udid.isBlank()) return null;
        if (!WdaLifecycleOwner.isTerminalError(udid)) return null;

        String reason = WdaLifecycleOwner.terminalErrorReason(udid);
        IOSWdaErrorCode code = reason != null ? IOSWdaErrorClassifier.classify(reason) : IOSWdaErrorCode.UNKNOWN;
        String humanReason = code == IOSWdaErrorCode.IOS_ACCOUNT_SESSION_REQUIRED
                ? "Acción requerida: inicia sesión o vuelve a autenticar tu cuenta Apple en Xcode "
                  + "antes de ejecutar la suite."
                : reason;
        return fail(presence, tunnel, humanReason);
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
