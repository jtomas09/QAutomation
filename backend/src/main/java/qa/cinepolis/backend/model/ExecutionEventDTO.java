package qa.cinepolis.backend.model;

import java.time.Instant;

/**
 * Evento de dominio para el panel "Actividad en Tiempo Real". Reemplaza gradualmente
 * a {@link LogEvent} como forma de comunicar narración de negocio (clonar repo,
 * preparar dispositivo, iniciar suite, caso N/total, PASS/FAIL, reporte, correo,
 * fin de ejecución) — el frontend decide qué mostrar mirando {@code category}/{@code type},
 * nunca parseando texto.
 *
 * {@code category}/{@code severity}/{@code type} son Strings libres (no enums del lado
 * backend) a propósito: el Runner es quien define el vocabulario real (ver
 * qa.cinepolis.runner.events.EventType/EventCategory/EventSeverity) y el backend solo
 * transporta y reenvía — igual que {@code level} en {@link LogEvent} hoy.
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

    /** Traduce un LogEvent legacy a un evento TECHNICAL/RAW_LOG — puente de compatibilidad (ver ExecutionService.addLog). */
    public static ExecutionEventDTO fromLegacyLog(String executionId, String level, String message) {
        String category = switch (level == null ? "" : level.toUpperCase()) {
            case "PASS", "FAIL", "SKIP" -> "BUSINESS";
            case "DEBUG" -> "DEBUG";
            default -> "TECHNICAL";
        };
        return new ExecutionEventDTO(executionId, Instant.now().toString(), level, category,
                "runner", "RAW_LOG", message, null, null, null, null, null);
    }
}
