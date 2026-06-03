package qa.cinepolis.backend.model;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    ABORTING,  // solicitud recibida, esperando que el runner detenga el proceso
    PASSED,
    FAILED,
    SKIPPED,
    ABORTED,
    COMPLETED  // alias interno para PASSED (compatibilidad runner)
}
