package qa.cinepolis.runner.mirror;

import java.util.List;

/**
 * Selecciona automáticamente el provider de mirror para iOS — el usuario
 * nunca elige, y la elección nunca es solo "version >= 27":
 *
 *   iOS 27+ Y el dispositivo expone realmente com.apple.coredevice.displayservice
 *       → CoreDeviceMirrorProvider (sin WDA, video real vía CoreDevice/RSD)
 *   cualquier otro caso (iOS < 27, o 27+ sin el servicio expuesto)
 *       → IOSMirrorProvider (WDA) — el MISMO WebDriverAgent que ya usa la
 *         automatización XCUITest/Appium, reutilizado únicamente como fuente
 *         de captura (ver WdaLifecycleOwner.Consumer.MIRROR): no se crea una
 *         segunda instancia, no interfiere con una ejecución real en curso.
 *
 * Hoy CoreDeviceMirrorProvider.isDisplayServiceAvailable() siempre devuelve
 * false (ver su javadoc — investigación real contra un iPhone físico, no
 * teórica), así que en la práctica todo dispositivo cae a WDA — pero la
 * decisión se toma aquí, en un único lugar, con logs explícitos, para que
 * el día que CoreDeviceMirrorProvider tenga una implementación real, el
 * cambio de comportamiento sea automático y visible en el log, sin tocar
 * MirrorProviderRegistry ni el resto del pipeline.
 *
 * Delega el arranque/parada/captura a FallbackChainProvider — el mismo
 * mecanismo ya probado (ganador cacheado por UDID) que usa Android — esta
 * clase solo agrega el log de decisión pedido explícitamente por el equipo.
 */
final class IOSMirrorProviderResolver implements DeviceMirrorProvider {

    private final CoreDeviceMirrorProvider coreDevice;
    private final DeviceMirrorProvider wda;
    private final FallbackChainProvider chain;

    IOSMirrorProviderResolver(CoreDeviceMirrorProvider coreDevice, DeviceMirrorProvider wda) {
        this.coreDevice = coreDevice;
        this.wda = wda;
        this.chain = new FallbackChainProvider("iOS", List.of(coreDevice, wda));
    }

    @Override
    public String name() {
        // Delega al ganador real (FallbackChainProvider ya expone "WDA"/"CoreDevice"
        // una vez resuelto, y el nombre genérico "iOS" solo antes de que haya uno) —
        // DeviceStreamServer depende de comparar esto contra "WDA" exactamente para
        // reactivar la tolerancia a huecos de captura durante una compilación de WDA
        // en curso (ver DeviceStreamServer.MirrorHandler, rama isWdaProvider).
        return chain.name();
    }

    @Override
    public boolean isSupported() {
        return coreDevice.isSupported() || wda.isSupported();
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        return qa.cinepolis.runner.IOSDeviceRegistry.isPresent(udid);
    }

    @Override
    public boolean start(String udid) {
        String iosVersion = qa.cinepolis.runner.IOSDeviceRegistry.getPlatformVersion(udid);
        System.out.println("[IOSMirrorResolver] Device: udid=" + udid);
        System.out.println("[IOSMirrorResolver] iOS: " + (iosVersion != null ? iosVersion : "desconocida"));

        boolean available = coreDevice.isSupported() && coreDevice.isDisplayServiceAvailable(udid);
        System.out.println("[IOSMirrorResolver] CoreDevice display service: " + (available ? "AVAILABLE" : "NOT_AVAILABLE"));
        System.out.println("[IOSMirrorResolver] Selected provider: " + (available ? "CoreDevice" : "WDA"));

        return chain.start(udid);
    }

    @Override
    public void stop(String udid) { chain.stop(udid); }

    @Override
    public byte[] captureFrame(String udid) { return chain.captureFrame(udid); }
}
