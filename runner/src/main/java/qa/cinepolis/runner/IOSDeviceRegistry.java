package qa.cinepolis.runner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fuente de verdad única para "¿este UDID iOS está físicamente presente en el
 * Runner ahora mismo?" — cualquier componente que necesite responder esa
 * pregunta debe consultar esta clase en vez de ejecutar devicectl/xctrace por
 * su cuenta o inferirlo de WdaManager (WDA corriendo es un concepto distinto:
 * ver IOSMirrorProvider.isDeviceConnected).
 *
 * Se alimenta exclusivamente de IOSDeviceScanner.scan() — el mismo escaneo que
 * ya gobierna DeviceReadinessEvaluator.readyForExecution — así que no dispara
 * ningún proceso xcrun adicional ni introduce un timer/hilo nuevo. isPresent()
 * es una lectura en memoria O(1), segura para rutas de alta frecuencia (p.ej.
 * el polling HTTP de /api/device/status).
 *
 * TTL: si IOSDeviceScanner deja de ejecutarse (Appium caído, watchdog detenido,
 * etc.) un snapshot "presente" no debe quedar válido para siempre — pasado el
 * TTL se considera expirado y se reporta como no presente.
 */
public final class IOSDeviceRegistry {

    /** Tiempo máximo que un snapshot "presente" se considera vigente sin un nuevo scan. */
    private static final long TTL_MS = 45_000L;

    public record Snapshot(boolean present, long updatedAtMs, String platformVersion) {}

    private static final ConcurrentHashMap<String, Snapshot> LAST_SEEN = new ConcurrentHashMap<>();

    private IOSDeviceRegistry() {}

    /**
     * Actualiza el registro con el resultado de un escaneo de IOSDeviceScanner.scan().
     * Marca como ausentes los UDIDs previamente vistos que ya no aparecen en este scan,
     * y registra como presentes los que sí aparecen — logueando únicamente en las
     * transiciones reales de estado, nunca en cada scan. También cachea platformVersion
     * (ya viene en el mismo Map de cada escaneo — IOSDeviceScanner.scanInternal — así que
     * esto no dispara ningún proceso xcrun adicional) para que IOSMirrorProviderResolver
     * pueda decidir WDA vs CoreDevice por versión sin una consulta O(n) aparte.
     */
    static void update(List<Map<String, String>> scannedDevices) {
        long now = System.currentTimeMillis();

        java.util.Set<String> seenNow = new java.util.HashSet<>();
        for (Map<String, String> device : scannedDevices) {
            String udid = device.get("udid");
            if (udid == null || udid.isBlank()) continue;
            seenNow.add(udid);
            markPresentInternal(udid, now, device.get("platformVersion"));
        }

        // UDIDs que estaban presentes/registrados pero no aparecieron en este scan.
        for (String udid : LAST_SEEN.keySet()) {
            if (seenNow.contains(udid)) continue;
            Snapshot prev = LAST_SEEN.get(udid);
            if (prev != null && prev.present()) {
                LAST_SEEN.put(udid, new Snapshot(false, now, prev.platformVersion()));
                System.out.println("📱 iOS Device desconectado — UDID: " + udid);
            }
        }
    }

    private static void markPresentInternal(String udid, long now, String platformVersion) {
        Snapshot prev = LAST_SEEN.get(udid);
        String resolvedVersion = (platformVersion != null && !platformVersion.isBlank())
                ? platformVersion
                : (prev != null ? prev.platformVersion() : null); // conserva la última versión conocida si este scan no la trae
        LAST_SEEN.put(udid, new Snapshot(true, now, resolvedVersion));
        if (prev == null || !prev.present()) {
            System.out.println("📱 iOS Device conectado — UDID: " + udid);
        }
    }

    /**
     * ¿Este UDID está físicamente presente según el último escaneo, dentro del TTL?
     * Consulta en memoria, O(1), sin procesos ni bloqueo.
     */
    public static boolean isPresent(String udid) {
        if (udid == null || udid.isBlank()) return false;
        Snapshot snap = LAST_SEEN.get(udid);
        if (snap == null) return false;
        if (!snap.present()) return false;

        long age = System.currentTimeMillis() - snap.updatedAtMs();
        if (age > TTL_MS) {
            System.out.println("📱 iOS Device expiró por TTL (" + age + "ms sin scan) — UDID: " + udid);
            LAST_SEEN.put(udid, new Snapshot(false, snap.updatedAtMs(), snap.platformVersion()));
            return false;
        }
        return true;
    }

    /**
     * Versión de iOS (p.ej. "26.6") del último escaneo que vio este UDID, o null si
     * nunca se vio o el escaneo no la reportó. Lectura en memoria O(1) — usada por
     * IOSMirrorProviderResolver para decidir WDA vs CoreDevice sin ejecutar devicectl.
     */
    public static String getPlatformVersion(String udid) {
        if (udid == null || udid.isBlank()) return null;
        Snapshot snap = LAST_SEEN.get(udid);
        return snap != null ? snap.platformVersion() : null;
    }
}
