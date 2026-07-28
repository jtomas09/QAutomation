package qa.cinepolis.runner.events;

/**
 * API central para publicar eventos de dominio — reemplaza gradualmente a
 * BackendClient.sendLog() como canal preferido para narración de negocio nueva
 * (ver arquitectura de eventos). sendLog() sigue siendo el canal para todo lo
 * TECHNICAL/DEBUG/TRACE — no se elimina ni se toca.
 */
public interface ExecutionEventPublisher {

    void publish(ExecutionEvent event);

    /** Azúcar sintáctica para narración simple (sin progreso/suite/test/device). */
    default void business(String executionId, EventType type, String message) {
        publish(new ExecutionEvent(executionId, java.time.Instant.now().toString(), EventSeverity.INFO,
                EventCategory.BUSINESS, "runner", type, message, null, null, null, null, null));
    }

    default void business(String executionId, EventType type, EventSeverity severity, String message) {
        publish(new ExecutionEvent(executionId, java.time.Instant.now().toString(), severity,
                EventCategory.BUSINESS, "runner", type, message, null, null, null, null, null));
    }

    /** Narración con suite (inicio/fin de suite). */
    default void suite(String executionId, EventType type, EventSeverity severity, String suite, String message) {
        publish(new ExecutionEvent(executionId, java.time.Instant.now().toString(), severity,
                EventCategory.BUSINESS, "gradle", type, message, null, null, suite, null, null));
    }

    /** Progreso de caso — alimenta la barra de progreso y el pill "N/total" en el Timeline. */
    default void caseProgress(String executionId, EventType type, EventSeverity severity,
                               String test, int current, int total, String message) {
        publish(new ExecutionEvent(executionId, java.time.Instant.now().toString(), severity, EventCategory.BUSINESS,
                "gradle", type, message, null, new ExecutionEvent.Progress(current, total), null, test, null));
    }
}
