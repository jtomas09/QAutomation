package qa.cinepolis.backend.model;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED,
    ABORTED,
    COMPLETED  // alias interno para PASSED (compatibilidad runner)
}
