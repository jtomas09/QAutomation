package qa.cinepolis.backend.model;

public enum ExecutionStatus {
    QUEUED,
    STARTING,           // Runner recibió el job — preflight en progreso (device, Appium, etc.)
    RUNNING,            // Gradle ejecutando tests activamente
    FINALIZING,         // Todos los tests terminaron — post-procesamiento (cleanup, Allure, etc.)
    ABORTING,           // Solicitud de aborto recibida, esperando que el runner detenga el proceso
    PASSED,
    FAILED,
    FAILED_FINALIZATION, // Post-procesamiento falló — la ejecución no pudo completarse limpiamente
    SKIPPED,
    ABORTED,
    COMPLETED,          // Ejecución terminada correctamente; FAILED si algún test falló
    INCOMPLETE          // Terminó con menos casos ejecutados que los planificados (expectedCount) —
                         // independientemente de si los que sí corrieron pasaron o fallaron
}
