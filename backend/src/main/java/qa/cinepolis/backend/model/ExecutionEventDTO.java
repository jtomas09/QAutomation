package qa.cinepolis.backend.model;

/**
 * Evento de NEGOCIO para el panel "Actividad en Tiempo Real" — canal completamente
 * independiente de {@link LogEvent} (Developer Log: TODO stdout/stderr/Gradle/JUnit,
 * sin excepción, sin cambios). No existe ninguna traducción automática de un log a
 * un ExecutionEventDTO — la única forma de que algo llegue aquí es que un emisor lo
 * publique explícitamente (ver qa.cinepolis.runner.events.ExecutionEventPublisher).
 * Si esta clase alguna vez tienta a alguien a "derivar" un evento a partir de una
 * línea de log, es la señal de que ese caso debería ser, en cambio, un nuevo
 * EventType publicado explícitamente en su origen real.
 *
 * {@code category}/{@code severity}/{@code type} son Strings libres (no enums del lado
 * backend) a propósito: el Runner es quien define el vocabulario real (ver
 * qa.cinepolis.runner.events.EventType/EventCategory/EventSeverity) y el backend solo
 * transporta y reenvía.
 *
 * {@code timestamp} es String (ISO-8601), no java.time.Instant — el Runner lo serializa
 * con un ObjectMapper plano sin JavaTimeModule; mismo criterio que LogEvent.time.
 */
public record ExecutionEventDTO(
        String executionId,
        String timestamp,
        String severity,   // INFO · SUCCESS · WARN · ERROR
        String category,   // BUSINESS · TECHNICAL · DEBUG · TRACE
        String source,     // "git" · "device" · "appium" · "gradle" · "mail" · "report"
        String type,       // ver EventType del Runner — p.ej. "CASE_PASSED"
        String message,
        String details,
        ProgressDTO progress,
        String suite,
        String test,
        String device
) {
    public record ProgressDTO(int current, int total) {}
}
