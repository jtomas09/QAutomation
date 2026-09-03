package qa.cinepolis.runner;

/**
 * TAREA 8 — resultado conceptual de {@link IOSRecoveryManager#recover}, exactamente
 * los 6 valores diseñados en TAREA 7 (sección 6). No se agregó ninguno nuevo.
 *
 * Ninguno de estos valores significa "el dispositivo está READY" por sí mismo —
 * esa certificación es exclusiva de {@code IOSRunnerReadinessResult.Status.READY},
 * producida únicamente por {@link IOSRunnerReadinessEngine}. {@code RECOVERY_SUCCEEDED}
 * significa "un nuevo diagnóstico del Engine, tomado después de la recuperación,
 * confirmó READY" — nunca lo declara este manager por su cuenta.
 */
public enum IOSRecoveryOutcome {
    /** El diagnóstico no requería ninguna acción de este manager (ya resuelto, o fuera de su alcance). */
    NO_ACTION_REQUIRED,
    /** Se acaba de disparar una operación real de recuperación (uso interno/logging — ver Javadoc de {@link IOSRecoveryManager}). */
    RECOVERY_STARTED,
    /** Ya existía una operación real en curso (WdaLifecycleOwner.isBuildInFlight) — no se inició una segunda. */
    RECOVERY_IN_PROGRESS,
    /** La operación real terminó y un NUEVO diagnóstico del Engine confirmó READY. */
    RECOVERY_SUCCEEDED,
    /** La operación real terminó (o lanzó una excepción) y el dispositivo sigue sin estar READY. */
    RECOVERY_FAILED,
    /** El diagnóstico (antes o después de intentar recuperar) requiere intervención manual del usuario. */
    ACTION_REQUIRED
}
