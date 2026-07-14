package qa.cinepolis.runner.mirror;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado observable del ciclo de vida de WebDriverAgent para el Mirror de iOS.
 *
 * Antes, el Mirror solo tenía dos estados implícitos: "conectado" (derivado de
 * si el Runner responde a /health, sin relación con si WDA realmente produce
 * frames) y "pantalla negra" (cuando captureFrame() falla, sin explicación
 * visible para el usuario). Esta clase desacopla lo que el usuario VE de lo
 * que WDA está haciendo internamente, para que el panel nunca quede negro sin
 * decir por qué.
 *
 * No aplica a Android: AndroidMirrorProvider no tiene una fase de
 * inicialización equivalente — un dispositivo Android conectado siempre puede
 * capturar frames de inmediato vía adb screencap, sin un paso de "compilar e
 * instalar un agente" de por medio.
 */
public final class IOSMirrorStateTracker {

    public enum Phase {
        /** No hay dispositivo iOS conectado. */
        DEVICE_DISCONNECTED,
        /** Dispositivo conectado y listo, pero ninguna sesión WDA se ha intentado todavía. */
        DEVICE_DETECTED,
        /** Preflight/xcodebuild en curso — bajo demanda (Mirror) o disparado por una ejecución real. */
        INITIALIZING_WDA,
        /** WDA responde y ya se capturó al menos un frame real. */
        MIRROR_ACTIVE,
        /** El último intento de levantar WDA falló — reason contiene el motivo real. */
        ERROR
    }

    public record Snapshot(Phase phase, String reason, long updatedAtMs) {}

    private static final ConcurrentHashMap<String, Snapshot> STATES = new ConcurrentHashMap<>();

    private IOSMirrorStateTracker() {}

    public static void markInitializing(String udid) {
        STATES.put(udid, new Snapshot(Phase.INITIALIZING_WDA, null, System.currentTimeMillis()));
    }

    /** Idempotente — evita escrituras redundantes una vez que ya está activo. */
    public static void markActive(String udid) {
        Snapshot current = STATES.get(udid);
        if (current != null && current.phase() == Phase.MIRROR_ACTIVE) return;
        STATES.put(udid, new Snapshot(Phase.MIRROR_ACTIVE, null, System.currentTimeMillis()));
    }

    public static void markError(String udid, String reason) {
        STATES.put(udid, new Snapshot(Phase.ERROR, reason, System.currentTimeMillis()));
    }

    /** Limpia el estado rastreado (p.ej. cuando el dispositivo se desconecta). */
    public static void clear(String udid) {
        STATES.remove(udid);
    }

    /**
     * @param udid      UDID del dispositivo consultado.
     * @param connected estado de conexión real, ya calculado por el llamador
     *                  (vía DeviceMirrorProvider.isDeviceConnected).
     * @param iosDevice si este UDID corresponde a un dispositivo iOS — Android
     *                  no tiene fases WDA, siempre se reporta MIRROR_ACTIVE si conectado.
     */
    public static Snapshot get(String udid, boolean connected, boolean iosDevice) {
        if (!connected) {
            // Desconexión física real (connected ya viene de IOSDeviceRegistry, no de
            // WdaManager) — cualquier fase de WDA/Mirror rastreada queda obsoleta;
            // se limpia para que una futura reconexión arranque en DEVICE_DETECTED
            // en vez de heredar un ERROR/INITIALIZING_WDA de la sesión anterior.
            clear(udid);
            return new Snapshot(Phase.DEVICE_DISCONNECTED, null, System.currentTimeMillis());
        }
        if (!iosDevice) {
            return new Snapshot(Phase.MIRROR_ACTIVE, null, System.currentTimeMillis());
        }
        Snapshot tracked = STATES.get(udid);
        return tracked != null ? tracked : new Snapshot(Phase.DEVICE_DETECTED, null, System.currentTimeMillis());
    }
}
