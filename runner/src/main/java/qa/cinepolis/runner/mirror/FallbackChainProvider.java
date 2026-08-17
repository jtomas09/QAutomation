package qa.cinepolis.runner.mirror;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Envuelve una lista de DeviceMirrorProvider ordenada por prioridad para una
 * misma plataforma (p.ej. [AVFoundationMirrorProvider, LibimobiledeviceMirrorProvider]
 * para iOS) y expone la MISMA interfaz DeviceMirrorProvider — DeviceStreamServer
 * nunca sabe que hay más de un candidato detrás; cero cambios ahí.
 *
 * start(udid) prueba cada candidato SOPORTADO (isSupported()) en orden hasta
 * que uno logre iniciar; ese candidato "gana" la sesión para este UDID y
 * atiende captureFrame()/stop()/isDeviceConnected() hasta que la conexión
 * termine. Si ninguno arranca, start() devuelve false (mismo contrato que
 * cualquier provider individual).
 */
final class FallbackChainProvider implements DeviceMirrorProvider {

    private final String displayName;
    private final List<DeviceMirrorProvider> candidates;
    private final ConcurrentHashMap<String, DeviceMirrorProvider> active = new ConcurrentHashMap<>();

    FallbackChainProvider(String displayName, List<DeviceMirrorProvider> candidates) {
        this.displayName = displayName;
        this.candidates  = candidates;
    }

    @Override
    public String name() {
        DeviceMirrorProvider winner = active.values().stream().findFirst().orElse(null);
        return winner != null ? winner.name() : displayName;
    }

    @Override
    public boolean isSupported() {
        for (DeviceMirrorProvider c : candidates) {
            if (c.isSupported()) return true;
        }
        return false;
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        DeviceMirrorProvider winner = active.get(udid);
        if (winner != null) return winner.isDeviceConnected(udid);
        for (DeviceMirrorProvider c : candidates) {
            if (c.isSupported()) return c.isDeviceConnected(udid);
        }
        return false;
    }

    @Override
    public boolean start(String udid) {
        DeviceMirrorProvider existing = active.get(udid);
        if (existing != null) return true; // ya hay un ganador activo para este UDID

        for (DeviceMirrorProvider c : candidates) {
            if (!c.isSupported()) continue;
            if (c.start(udid)) {
                active.put(udid, c);
                System.out.println("[FallbackChainProvider][" + displayName + "] " + c.name()
                        + " ganó la sesión de mirror — udid=" + udid);
                return true;
            }
        }
        return false;
    }

    @Override
    public void stop(String udid) {
        DeviceMirrorProvider winner = active.remove(udid);
        if (winner != null) winner.stop(udid);
    }

    @Override
    public byte[] captureFrame(String udid) {
        DeviceMirrorProvider winner = active.get(udid);
        return winner != null ? winner.captureFrame(udid) : null;
    }
}
