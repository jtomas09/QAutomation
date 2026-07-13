package qa.cinepolis.runner.mirror;

import java.util.regex.Pattern;

/**
 * Decide qué DeviceMirrorProvider debe atender un UDID dado, según la forma
 * del identificador — Android e iOS usan formatos de UDID mutuamente
 * excluyentes, el mismo criterio que ya usa IOSDeviceScanner para distinguir
 * UDIDs físicos de iOS del resto. Es una clasificación en memoria (sin
 * subprocesos), apta para el hot path del Mirror.
 */
public final class MirrorProviderRegistry {

    // UDID físico de iPhone/iPad (Xcode 15+): 8 hex - 16 hex.
    private static final Pattern MODERN_IOS_UDID = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{16}$");
    // UDID legado (dispositivos pre-2018): 40 caracteres hex sin separador.
    private static final Pattern LEGACY_IOS_UDID = Pattern.compile("(?i)^[0-9a-f]{40}$");

    private final DeviceMirrorProvider androidProvider;
    private final DeviceMirrorProvider iosProvider;

    public MirrorProviderRegistry(String adbPath) {
        this.androidProvider = new AndroidMirrorProvider(adbPath);
        this.iosProvider     = new IOSMirrorProvider();
    }

    /**
     * @return el provider correspondiente a la plataforma del UDID, o null si
     *         ese provider no está soportado en este host (p.ej. iOS pedido
     *         en un Runner Windows/Linux).
     */
    public DeviceMirrorProvider resolve(String udid) {
        DeviceMirrorProvider candidate = isIosUdid(udid) ? iosProvider : androidProvider;
        return candidate.isSupported() ? candidate : null;
    }

    static boolean isIosUdid(String udid) {
        return udid != null
                && (MODERN_IOS_UDID.matcher(udid).matches() || LEGACY_IOS_UDID.matcher(udid).matches());
    }
}
