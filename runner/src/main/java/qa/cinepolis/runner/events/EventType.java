package qa.cinepolis.runner.events;

/**
 * Vocabulario cerrado de eventos de negocio — cada valor corresponde a un momento
 * que el usuario reconoce en el panel "Actividad en Tiempo Real". El frontend mapea
 * ícono/color a partir de ESTO, nunca del contenido de {@code message}.
 *
 * RAW_LOG es el puente de compatibilidad hacia BackendClient.sendLog() legacy — nunca
 * se usa para narración nueva (ver ExecutionEventPublisher).
 */
public enum EventType {
    REPO_CLONE_START, REPO_CLONE_DONE,
    DEVICE_PREPARE_START, DEVICE_PREPARE_DONE,
    APPIUM_START, APPIUM_READY,
    DRIVER_CREATE_START, DRIVER_CREATE_DONE,
    SUITE_START, SUITE_DONE,
    CASE_START, CASE_PASSED, CASE_FAILED, CASE_SKIPPED, CASE_RETRY,
    REPORT_GENERATING, REPORT_READY,
    MAIL_SENDING, MAIL_SENT,
    EXECUTION_FINISHED,
    RAW_LOG
}
