package qa.cinepolis.runner.mirror;

/**
 * Mirror de iOS vía CoreDevice/RSD (RemoteServiceDiscovery) — el mecanismo
 * moderno que reemplaza a AVFoundation/WDA para dispositivos suficientemente
 * nuevos, sin depender de WebDriverAgent.
 *
 * ── Estado actual: ESQUELETO, NO FUNCIONAL — documentado a propósito ───────
 * Investigación real contra un iPhone físico (iOS 26.6, macOS 26.5.1),
 * usando pymobiledevice3 instalado en un venv de prueba:
 *
 *   - Túnel userspace RSD: SÍ se establece sin sudo/root
 *     ("userspace RSD established (no root)").
 *   - 73 servicios reales enumerados vía RSD para este dispositivo.
 *   - "com.apple.coredevice.displayservice" (el servicio de video) NO está
 *     entre ellos → InvalidServiceError: No such service.
 *   - Causa confirmada: el servicio de streaming de pantalla de CoreDevice
 *     requiere iOS 27+ (Xcode 27 Device Hub no soporta screen sharing con
 *     iOS anterior a 27 — confirmado con fuentes públicas de junio 2026).
 *
 * Este Runner es Java, no Python — no existe hoy una implementación nativa
 * del túnel RSD/RemoteXPC ni del protocolo CoreDevice en este código base
 * (pymobiledevice3 es la única implementación probada, y es Python). Construir
 * esa pieza es un trabajo separado y significativo, no un ajuste menor — por
 * eso esta clase existe ya con el contrato completo de DeviceMirrorProvider
 * (para que IOSMirrorProviderResolver pueda usarla en cuanto se implemente),
 * pero start()/captureFrame() devuelven "no disponible" de forma honesta en
 * vez de simular una implementación a medias.
 *
 * isDisplayServiceAvailable() SÍ hace la verificación real de versión (no
 * solo "version >= 27" ciego) — deja explícito en el log exactamente por qué
 * se decidió NOT_AVAILABLE, para que IOSMirrorProviderResolver nunca calle
 * en silencio.
 */
final class CoreDeviceMirrorProvider implements DeviceMirrorProvider {

    private static final int MIN_MAJOR_VERSION = 27;

    @Override
    public String name() { return "CoreDevice"; }

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        return qa.cinepolis.runner.IOSDeviceRegistry.isPresent(udid);
    }

    /**
     * ¿Este UDID puede usar el mirror CoreDevice? Verifica la versión real
     * (iOS 27+, mínimo confirmado empíricamente) — pero incluso cumpliéndola,
     * hoy siempre devuelve false porque la implementación del túnel RSD/
     * protocolo CoreDevice todavía no existe en este Runner (ver javadoc de
     * la clase). Cuando se implemente, este método deberá además abrir el
     * túnel y confirmar la presencia real de "com.apple.coredevice.displayservice"
     * en la lista de servicios del dispositivo — no basta con el número de versión.
     */
    boolean isDisplayServiceAvailable(String udid) {
        String version = qa.cinepolis.runner.IOSDeviceRegistry.getPlatformVersion(udid);
        if (version == null || version.isBlank()) {
            System.out.println("[CoreDeviceMirrorProvider] Versión de iOS desconocida para udid=" + udid + " — se asume NO disponible.");
            return false;
        }
        if (!meetsMinimumVersion(version)) {
            System.out.println("[CoreDeviceMirrorProvider] iOS " + version + " < " + MIN_MAJOR_VERSION
                    + " — el servicio de video CoreDevice no existe en este firmware (confirmado empíricamente) — udid=" + udid);
            return false;
        }
        System.out.println("[CoreDeviceMirrorProvider] iOS " + version + " cumple la versión mínima, PERO la implementación "
                + "del túnel RSD/protocolo CoreDevice aún no existe en este Runner — udid=" + udid + " (pendiente, no un fallo del dispositivo)");
        return false;
    }

    static boolean meetsMinimumVersion(String platformVersion) {
        try {
            String major = platformVersion.split("\\.")[0].trim();
            return Integer.parseInt(major) >= MIN_MAJOR_VERSION;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean start(String udid) {
        return false; // ver isDisplayServiceAvailable() — nunca disponible todavía
    }

    @Override
    public void stop(String udid) { /* nada que detener — start() nunca arranca sesión */ }

    @Override
    public byte[] captureFrame(String udid) { return null; }
}
