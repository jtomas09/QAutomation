package qa.cinepolis.runner.mirror;

/**
 * Único punto de propagación de eventos del ciclo de vida de WebDriverAgent
 * hacia el Mirror. El origen del evento no importa — tanto el lanzamiento
 * on-demand del Mirror (IOSMirrorProvider) como una ejecución real de test
 * (IosPreflightManager, IOSExecutionCleanupManager) publican aquí en vez de
 * llamar directamente a IOSMirrorStateTracker.
 *
 * IOSMirrorStateTracker expone sus mutadores como package-private: esta es
 * la única clase en el paquete autorizada a invocarlos, así que un fallo de
 * WDA durante una ejecución real produce exactamente el mismo evento — y el
 * mismo efecto en el Mirror — que uno ocurrido durante un lanzamiento
 * on-demand. No hay dos caminos de estado, solo dos productores del mismo.
 */
public final class WdaEventBus {

    public enum WdaEvent {
        /** Preflight/xcodebuild en curso — bajo demanda o disparado por una ejecución real. */
        INITIALIZING,
        /** WDA respondió y produjo al menos un frame real. */
        ACTIVE,
        /** El intento de levantar WDA falló — reason contiene la causa real. */
        ERROR,
        /** WDA dejó de ejecutarse sin error — vuelve al estado neutral (dispositivo detectado). */
        STOPPED
    }

    private WdaEventBus() {}

    /**
     * @param udid   dispositivo afectado.
     * @param event  tipo de evento — ver {@link WdaEvent}.
     * @param reason motivo real del fallo; solo relevante para {@code ERROR}, ignorado en el resto.
     */
    public static void publish(String udid, WdaEvent event, String reason) {
        switch (event) {
            case INITIALIZING -> IOSMirrorStateTracker.markInitializing(udid);
            case ACTIVE       -> IOSMirrorStateTracker.markActive(udid);
            case ERROR        -> IOSMirrorStateTracker.markError(udid, reason);
            case STOPPED      -> IOSMirrorStateTracker.clear(udid);
        }
    }

    /** Conveniencia para eventos sin motivo (todos salvo ERROR). */
    public static void publish(String udid, WdaEvent event) {
        publish(udid, event, null);
    }
}
