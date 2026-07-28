package qa.cinepolis.runner.events;

/**
 * Cómo se presenta un evento en el Dashboard — ver ExecutionEvent/EventType.
 * BUSINESS es lo único visible por defecto en "Actividad en Tiempo Real"; el resto
 * vive en "Log Técnico".
 */
public enum EventCategory {
    BUSINESS,
    TECHNICAL,
    DEBUG,
    TRACE
}
