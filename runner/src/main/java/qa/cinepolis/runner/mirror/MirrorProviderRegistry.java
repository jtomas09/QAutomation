package qa.cinepolis.runner.mirror;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decide qué DeviceMirrorProvider debe atender un UDID dado, según la forma
 * del identificador — Android e iOS usan formatos de UDID mutuamente
 * excluyentes, el mismo criterio que ya usa IOSDeviceScanner para distinguir
 * UDIDs físicos de iOS del resto. Es una clasificación en memoria (sin
 * subprocesos), apta para el hot path del Mirror.
 *
 * ── El Mirror nunca depende de WDA ────────────────────────────────────────
 * Cada plataforma resuelve a una FallbackChainProvider con varios candidatos
 * ordenados por calidad — el primero soportado en este host que logre
 * arrancar para un UDID dado "gana" esa sesión de mirror:
 *
 *   Android: ScrcpyMirrorProvider (video real vía scrcpy) → AndroidMirrorProvider
 *            (adb screencap — fallback universal, incluye Windows).
 *   iOS:     AVFoundationMirrorProvider (video real vía USB, solo macOS+ffmpeg)
 *            → LibimobiledeviceMirrorProvider (idevicescreenshot, USB directo).
 *
 * IOSMirrorProvider (WDA) NO participa de esta cadena — WebDriverAgent sigue
 * existiendo únicamente para automatización/inspección real (ver
 * WdaLifecycleOwner), nunca como fuente del Mirror.
 */
public final class MirrorProviderRegistry {

    // UDID físico de iPhone/iPad (Xcode 15+): 8 hex - 16 hex.
    private static final Pattern MODERN_IOS_UDID = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{16}$");
    // UDID legado (dispositivos pre-2018): 40 caracteres hex sin separador.
    private static final Pattern LEGACY_IOS_UDID = Pattern.compile("(?i)^[0-9a-f]{40}$");

    private final DeviceMirrorProvider androidChain;
    private final DeviceMirrorProvider iosChain;

    public MirrorProviderRegistry(String adbPath) {
        this.androidChain = new FallbackChainProvider("Android", List.of(
                new ScrcpyMirrorProvider(adbPath),
                new AndroidMirrorProvider(adbPath)
        ));
        this.iosChain = new FallbackChainProvider("iOS", List.of(
                new AVFoundationMirrorProvider(),
                new LibimobiledeviceMirrorProvider()
        ));
    }

    /**
     * @return el provider (cadena de fallback) correspondiente a la plataforma
     *         del UDID, o null si ningún candidato de esa plataforma está
     *         soportado en este host.
     */
    public DeviceMirrorProvider resolve(String udid) {
        DeviceMirrorProvider candidate = isIosUdid(udid) ? iosChain : androidChain;
        return candidate.isSupported() ? candidate : null;
    }

    static boolean isIosUdid(String udid) {
        return udid != null
                && (MODERN_IOS_UDID.matcher(udid).matches() || LEGACY_IOS_UDID.matcher(udid).matches());
    }
}
