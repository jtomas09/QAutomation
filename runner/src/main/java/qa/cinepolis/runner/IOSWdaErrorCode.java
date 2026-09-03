package qa.cinepolis.runner;

/**
 * TAREA 3.2 — categorías técnicas para un mensaje real de fallo de xcodebuild/WDA
 * (ya preservado por {@code WdaManager.BuildOutcome.captureError}, ver TAREA 3.1).
 *
 * Cada categoría está respaldada por evidencia real (código o logs) — ver
 * {@link IOSWdaErrorClassifier} para el detalle exacto de qué patrón la activa
 * y de dónde sale la evidencia. No se agregó ninguna categoría sin evidencia
 * (p.ej. no existe IOS_CERTIFICATE_EXPIRED, IOS_DEVICE_NOT_PROVISIONED, etc. —
 * no hay mensajes reales que las justifiquen todavía).
 *
 * Esta clasificación NO ejecuta ninguna acción, NO decide readyForExecution, y
 * NO es todavía consumida por IOSRunnerReadinessEngine ni por el flujo real de
 * ejecución (JobExecutor/IosPreflightManager/WdaLifecycleOwner/WdaManager) —
 * queda preparada para una tarea posterior.
 */
public enum IOSWdaErrorCode {
    /** No hubo error — uso reservado para llamadores, el clasificador nunca lo devuelve. */
    NONE,
    IOS_DEVELOPER_TRUST_REQUIRED,
    IOS_SIGNING_REQUIRED,
    IOS_PROVISIONING_REQUIRED,
    IOS_WDA_BUILD_FAILED,
    IOS_WDA_STARTUP_FAILED,
    UNKNOWN
}
