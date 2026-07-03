package qa.cinepolis.backend.model;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    FINALIZING, // post-procesamiento activo: limpieza dispositivo, videos, Allure
    ABORTING,   // solicitud recibida, esperando que el runner detenga el proceso
    PASSED,
    FAILED,
    SKIPPED,
    ABORTED,
    COMPLETED   // ejecución terminada — todos los tests pasaron; FAILED si alguno falló
}
