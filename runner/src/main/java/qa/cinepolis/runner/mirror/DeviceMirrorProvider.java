package qa.cinepolis.runner.mirror;

/**
 * Abstracción de captura de pantalla para el Device Mirror — permite que
 * DeviceStreamServer sirva /api/device-mirror/{udid} para cualquier
 * plataforma sin saber CÓMO se obtiene cada frame.
 *
 * Contrato:
 *  - isSupported()       : ¿esta herramienta existe en este host, en general?
 *                          (no depende de un dispositivo concreto — p.ej. ¿hay
 *                          binario adb?, ¿estamos en macOS para WDA/xcrun?).
 *  - isDeviceConnected() : ¿ESTE udid específico está listo para capturar
 *                          ahora mismo? Puede ser costoso (shells out /
 *                          hace una llamada HTTP) — se llama solo cuando una
 *                          captura ya falló, no en cada frame.
 *  - start()/stop()      : ciclo de vida por sesión de mirror (se llaman una
 *                          vez al abrir/cerrar el stream, nunca por frame).
 *                          Pensado para providers que necesiten preparar o
 *                          liberar recursos de sesión; los providers actuales
 *                          no necesitan estado pesado, así que pueden ser
 *                          prácticamente no-op.
 *  - captureFrame()      : UN frame como PNG — mismo formato para todos los
 *                          providers, así el resto del pipeline (conversión a
 *                          JPEG, multipart MJPEG) es agnóstico a la plataforma.
 *                          null si la captura falló.
 *
 * Todas las implementaciones deben ser thread-safe para múltiples UDIDs
 * concurrentes (un mismo provider atiende todos los dispositivos de su
 * plataforma), y ningún método debe lanzar — los errores se reportan como
 * false/null, nunca como excepción, para no tumbar el loop MJPEG.
 */
public interface DeviceMirrorProvider {

    /** Nombre corto para logs y diagnóstico, p.ej. "ADB" o "WDA". */
    String name();

    /** ¿Está disponible esta herramienta de captura en este host, en general? */
    boolean isSupported();

    /** ¿Este UDID específico está conectado y listo para producir frames? */
    boolean isDeviceConnected(String udid);

    /**
     * Prepara una sesión de mirror para este UDID. Se llama una vez al abrir
     * el stream. Debe devolver rápido (no debe lanzar procesos largos como
     * compilar/instalar un agente de automatización).
     *
     * @return true si el provider quedó listo para producir frames.
     */
    boolean start(String udid);

    /** Captura un único frame en formato PNG, o null si la captura falló. */
    byte[] captureFrame(String udid);

    /** Libera lo reservado en start(). Idempotente, nunca lanza. */
    void stop(String udid);
}
