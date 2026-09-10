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
 * Esta clasificación NO ejecuta ninguna acción, NO decide readyForExecution — salvo
 * {@link #IOS_ACCOUNT_SESSION_REQUIRED} (TAREA 26A), que {@link IOSRunnerReadinessEngine}
 * sí consume explícitamente para producir {@code Status.ACTION_REQUIRED}. El resto de
 * los códigos siguen sin ser consumidos por el flujo real de ejecución
 * (JobExecutor/IosPreflightManager/WdaLifecycleOwner/WdaManager) — queda preparado
 * para una tarea posterior.
 */
public enum IOSWdaErrorCode {
    /** No hubo error — uso reservado para llamadores, el clasificador nunca lo devuelve. */
    NONE,
    IOS_DEVELOPER_TRUST_REQUIRED,
    IOS_SIGNING_REQUIRED,
    IOS_PROVISIONING_REQUIRED,
    IOS_WDA_BUILD_FAILED,
    IOS_WDA_STARTUP_FAILED,
    /**
     * TAREA 25/26A — {@link AppleSigningProbe} determinó, ANTES de intentar compilar
     * WDA, que xcodebuild no puede completar el provisioning automático porque la
     * sesión de cuenta Apple ID que necesita para llamar en vivo al portal de Apple
     * no está disponible ahora mismo — evidencia real: TAREA 24/25, mensaje "No
     * Accounts". Distinto de {@link #IOS_SIGNING_REQUIRED} (que solo describe la
     * MISMA frase cuando aparece en la salida RAW de xcodebuild, sin que el probe la
     * haya evaluado de antemano) para poder distinguir en el futuro "el probe ya lo
     * anticipó" de "se descubrió a mitad de un build real". NO implica que la sesión
     * haya "expirado" — TAREA 24 dejó ese mecanismo explícitamente sin demostrar;
     * solo que no está disponible en este momento.
     */
    IOS_ACCOUNT_SESSION_REQUIRED,
    UNKNOWN
}
