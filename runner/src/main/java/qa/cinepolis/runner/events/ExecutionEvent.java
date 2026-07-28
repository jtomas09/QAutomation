package qa.cinepolis.runner.events;

/**
 * Evento de dominio inmutable — un único tipo para narración de negocio y para el
 * puente de compatibilidad con logs técnicos. Se publica UNA sola vez, en el
 * servicio que sabe lo que ocurrió (ver ExecutionEventPublisher) — nunca se
 * reconstruye adivinando a partir de una línea de texto ya emitida por otro proceso.
 *
 * {@code timestamp} es String (ISO-8601, Instant.now().toString()) y no java.time.Instant
 * a propósito: BackendClient serializa con un ObjectMapper plano (sin JavaTimeModule
 * registrado) — mismo criterio que LogEvent.time ya usa en el backend.
 */
public record ExecutionEvent(
        String executionId,
        String timestamp,
        EventSeverity severity,
        EventCategory category,
        String source,
        EventType type,
        String message,
        String details,
        Progress progress,
        String suite,
        String test,
        String device
) {
    public record Progress(int current, int total) {}
}
