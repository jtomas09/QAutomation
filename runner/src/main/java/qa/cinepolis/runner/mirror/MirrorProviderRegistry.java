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
 * ── Android — sin cambios, ver historial ──────────────────────────────────
 * ScrcpyMirrorProvider (video real vía scrcpy) → AndroidMirrorProvider
 * (adb screencap — fallback universal, incluye Windows).
 *
 * ── iOS — selección automática por capacidad real del dispositivo ─────────
 * Investigación real (no teórica) contra un iPhone físico descartó
 * AVFoundation/CoreMediaIO y libimobiledevice para este dispositivo: ni
 * AVFoundation expone el iPhone como fuente de video (macOS nunca lista el
 * dispositivo salvo el enlace de confianza vía QuickTime, que NO es
 * automatizable — se investigó a fondo el mecanismo CoreMediaIO subyacente,
 * ver historial de AVFoundationMirrorProvider), ni libimobiledevice funciona
 * contra iOS 17+ (protocolo lockdownd clásico reemplazado por RemoteXPC,
 * confirmado en el issue tracker del proyecto). Investigación adicional (real,
 * con pymobiledevice3 contra el mismo iPhone) confirmó que el sucesor moderno
 * — CoreDevice/RSD — tampoco expone su servicio de video en este dispositivo
 * porque requiere iOS 27+ (este iPhone corre iOS 26.6).
 *
 * IOSMirrorProviderResolver decide automáticamente, por versión real del
 * dispositivo Y disponibilidad real del servicio (nunca "version >= 27" a
 * ciegas): CoreDeviceMirrorProvider si el dispositivo lo soporta, si no
 * IOSMirrorProvider (WDA) — reutilizando el MISMO WebDriverAgent que ya usa
 * la automatización XCUITest/Appium (ver WdaLifecycleOwner.Consumer.MIRROR),
 * nunca una segunda instancia, nunca interfiriendo con una ejecución real.
 */
public final class MirrorProviderRegistry {

    // UDID físico de iPhone/iPad (Xcode 15+): 8 hex - 16 hex.
    private static final Pattern MODERN_IOS_UDID = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{16}$");
    // UDID legado (dispositivos pre-2018): 40 caracteres hex sin separador.
    private static final Pattern LEGACY_IOS_UDID = Pattern.compile("(?i)^[0-9a-f]{40}$");

    private final DeviceMirrorProvider androidChain;
    private final DeviceMirrorProvider iosChain;

    /** Sin auto-descarga de ffmpeg/scrcpy (compatibilidad/pruebas) — solo Homebrew/PATH. */
    public MirrorProviderRegistry(String adbPath) {
        this(adbPath, null);
    }

    /**
     * @param agentDataDir si no es null, AVFoundationMirrorProvider y
     *                     ScrcpyMirrorProvider intentan descargar ffmpeg/scrcpy
     *                     automáticamente (ver MirrorDependencyManager) — el
     *                     usuario nunca instala nada manualmente.
     */
    public MirrorProviderRegistry(String adbPath, String agentDataDir) {
        this.androidChain = new FallbackChainProvider("Android", List.of(
                new ScrcpyMirrorProvider(adbPath, agentDataDir),
                new AndroidMirrorProvider(adbPath)
        ));
        this.iosChain = new IOSMirrorProviderResolver(
                new CoreDeviceMirrorProvider(),
                new IOSMirrorProvider()
        );
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
