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
        /** Preflight (Team ID, túnel, caché) en curso — bajo demanda o ejecución real. */
        INITIALIZING,
        /** xcodebuild compilando WDA — ver WdaLifecycleOwner. */
        BUILDING,
        /** Build terminado; esperando a que el proceso anuncie su servidor HTTP. */
        STARTING,
        /** Proceso arriba; esperando a que /status responda. */
        VERIFYING,
        /**
         * WDA confirmado funcional y listo para consumo — publicado por
         * WdaLifecycleOwner (única autoridad del ciclo de vida) en el instante en que
         * acquire() confirma ready=true (WDA ya corriendo y reutilizado, o recién
         * verificado vía /status), y también, de forma redundante y no exclusiva, por
         * IOSMirrorProvider al capturar un frame real — ambos reportan el MISMO hecho
         * a través del mismo evento, nunca dos autoridades distintas.
         */
        ACTIVE,
        /** El intento de levantar WDA falló (TERMINAL) — reason contiene la causa real. */
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
            case BUILDING     -> IOSMirrorStateTracker.markBuilding(udid);
            case STARTING     -> IOSMirrorStateTracker.markStarting(udid);
            case VERIFYING    -> IOSMirrorStateTracker.markVerifying(udid);
            case ACTIVE       -> {
                // TEMP LOG (auditoría Mirror/WDA — remover tras validar Problema 2)
                System.out.println("[WdaEventBus][TEMP] Mirror received ACTIVE — udid=" + udid);
                IOSMirrorStateTracker.markActive(udid);
            }
            case ERROR        -> IOSMirrorStateTracker.markError(udid, reason);
            case STOPPED      -> IOSMirrorStateTracker.clear(udid);
        }
    }

    /** Conveniencia para eventos sin motivo (todos salvo ERROR). */
    public static void publish(String udid, WdaEvent event) {
        publish(udid, event, null);
    }
}
