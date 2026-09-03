package qa.cinepolis.runner;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TAREA 8 — primer vertical slice de la autoridad de RECUPERACIÓN diseñada en
 * TAREA 7. Implementa ÚNICAMENTE el caso:
 *
 *   NOT_READY + IOS_WDA_NOT_STARTED  →  reutilizar IosPreflightManager/WdaLifecycleOwner  →  nuevo diagnóstico
 *
 * Ningún otro código de error activa recuperación todavía (Trust, Signing,
 * Provisioning, WDA_BUILD_FAILED, WDA_STARTUP_FAILED, UNKNOWN, tunnel/offline,
 * pairing, Developer Mode) — para esos casos este manager no hace nada y
 * conserva el diagnóstico tal cual (ver {@link #recover}).
 *
 * ── Principio arquitectónico (TAREA 7, sección 2) ───────────────────────────
 * {@link IOSRunnerReadinessEngine} diagnostica ("¿qué está pasando?").
 * Esta clase decide y ejecuta ("¿puedo hacer algo seguro al respecto?").
 * Esta clase NUNCA recalcula pairing/developer mode/trust/signing/provisioning/
 * WDA por su cuenta — toda esa información viene exclusivamente del
 * {@link IOSRunnerReadinessResult} que el Engine ya calculó. Y NUNCA declara
 * {@code Status.READY} por sí misma: el único camino hacia "confirmar READY"
 * es disparar la operación real ya existente y volver a preguntarle al Engine.
 *
 * ── Reutilización, cero duplicación ──────────────────────────────────────
 * La "operación real de recuperación" para WDA_NOT_STARTED es, literalmente,
 * {@link IosPreflightManager#runPreflight}, la MISMA función que ya usan
 * JobExecutor (ejecución real) y WdaLifecycleOwner.requestForMirror (Mirror) —
 * que internamente delega en {@link WdaLifecycleOwner#acquire}, la única
 * autoridad de ciclo de vida de WDA. Esta clase no reimplementa xcodebuild,
 * instalación, polling, retries, ni el mapa INFLIGHT — solo los invoca.
 */
public final class IOSRecoveryManager {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * TAREA 8.2 — guard de coordinación a nivel de SOLICITUD DE RECOVERY, distinto
     * y separado de {@code WdaLifecycleOwner.INFLIGHT} (que sigue coordinando el
     * BUILD de WDA, sin cambios). Auditoría de TAREA 8.1: dos llamadas concurrentes
     * a {@link #recover} para el mismo UDID pueden compartir el mismo Future de
     * INFLIGHT (correcto), pero cada una hace su PROPIO {@code release(RECOVERY)}
     * al terminar — y como {@code Consumer} es una marca de tipo, no un contador de
     * referencias, el primer release() ya vacía el registro y dispara teardown()
     * aunque la segunda llamada de recovery "todavía lo necesitara" — confirmado
     * con test real en TAREA 8.1. Este guard evita que una segunda solicitud de
     * recovery para el mismo UDID llegue siquiera a invocar runPreflight()/acquire().
     *
     * Mismo patrón ya usado en este proyecto para el mismo propósito (evitar
     * solicitudes concurrentes duplicadas), ver {@code WdaLifecycleOwner.MIRROR_REQUEST_PENDING}.
     * {@code ConcurrentHashMap.newKeySet()}: {@code add()} es atómico (respaldado por
     * {@code ConcurrentHashMap.putIfAbsent} internamente) — dos hilos llamando
     * {@code add(mismoUdid)} nunca pueden obtener {@code true} ambos.
     */
    private static final Set<String> RECOVERY_REQUEST_PENDING = ConcurrentHashMap.newKeySet();

    private IOSRecoveryManager() {}

    /** Resultado de una llamada a {@link #recover} — nunca certifica READY por sí mismo. */
    public static final class RecoveryResult {
        public final IOSRecoveryOutcome outcome;
        public final String udid;
        /** Diagnóstico FRESCO tomado al inicio de recover(), antes de decidir si actuar. */
        public final IOSRunnerReadinessResult diagnosisBeforeRecovery;
        /** Diagnóstico FRESCO tomado después de la operación real — null si no se intentó ninguna. */
        public final IOSRunnerReadinessResult diagnosisAfterRecovery;
        /** Excepción real si la operación de recuperación falló con un error inesperado — null en cualquier otro caso. */
        public final Throwable failureCause;

        RecoveryResult(IOSRecoveryOutcome outcome, String udid,
                       IOSRunnerReadinessResult diagnosisBeforeRecovery,
                       IOSRunnerReadinessResult diagnosisAfterRecovery,
                       Throwable failureCause) {
            this.outcome                 = outcome;
            this.udid                    = udid;
            this.diagnosisBeforeRecovery = diagnosisBeforeRecovery;
            this.diagnosisAfterRecovery  = diagnosisAfterRecovery;
            this.failureCause            = failureCause;
        }

        @Override
        public String toString() {
            return "IOSRecoveryResult{outcome=" + outcome
                    + ", udid=" + udid
                    + ", before=" + (diagnosisBeforeRecovery != null ? diagnosisBeforeRecovery.status : "null")
                    + ", after=" + (diagnosisAfterRecovery != null ? diagnosisAfterRecovery.status : "null")
                    + (failureCause != null ? ", failureCause=" + failureCause : "")
                    + '}';
        }
    }

    /**
     * Único punto de entrada. {@code diagnosis} (el parámetro recibido) se usa
     * SOLO para logging de la solicitud — nunca para decidir, porque podría
     * estar obsoleto (ver TAREA 7, sección 8, idempotencia). La decisión real
     * siempre se toma sobre un diagnóstico pedido de nuevo aquí mismo.
     */
    public static RecoveryResult recover(
            BackendClient client, String executionId, String udid, IOSRunnerReadinessResult diagnosis) {

        if (udid == null || udid.isBlank()) {
            throw new IllegalArgumentException("udid requerido");
        }

        log("RECOVERY_REQUESTED", udid,
                "receivedStatus=" + (diagnosis != null ? diagnosis.status : "null"));

        // ── Idempotencia: SIEMPRE un diagnóstico fresco antes de actuar ────────
        // Evita el escenario descrito en TAREA 7 (Job A ve NOT_READY, WDA arranca
        // por otra vía mientras tanto, Job A dispararía un segundo intento sobre
        // datos obsoletos si confiara ciegamente en el parámetro recibido).
        IOSRunnerReadinessResult fresh = IOSRunnerReadinessEngine.evaluate(client, executionId, udid);
        log("FRESH_DIAGNOSIS", udid, "status=" + fresh.status + " errorCode=" + fresh.errorCode);

        if (!isWdaNotStarted(fresh)) {
            // No es el caso que este slice sabe manejar (ya resuelto, ya en curso,
            // requiere acción manual, o es un error/código todavía no cubierto).
            // No se ejecuta ninguna acción — se preserva el diagnóstico tal cual.
            // No se toca RECOVERY_REQUEST_PENDING en absoluto en esta rama (nunca
            // se adquirió), así que no hay nada que liberar.
            return withoutAction(udid, fresh);
        }

        // ── TAREA 8.2 — guard de solicitud de recovery, ATÓMICO ────────────────
        // add() en un Set respaldado por ConcurrentHashMap es atómico: si dos hilos
        // llaman recover(mismoUdid) casi simultáneamente, como mucho UNO obtiene
        // true. El otro retorna aquí mismo, ANTES de tocar isBuildInFlight/
        // runPreflight/acquire/release — nunca llega a ejecutar la operación real.
        if (!RECOVERY_REQUEST_PENDING.add(udid)) {
            log("RECOVERY_REQUEST_ALREADY_PENDING", udid, "another recover() call owns this UDID right now");
            return new RecoveryResult(IOSRecoveryOutcome.RECOVERY_IN_PROGRESS, udid, fresh, null, null);
        }
        try {
            // Re-chequeo DENTRO del guard: el fresh de arriba ya reflejaría RECOVERING
            // si isBuildInFlight fuera true en ese instante, pero se revalida aquí por
            // si ALGÚN OTRO llamador (no-Recovery, p.ej. una ejecución real) inició un
            // build en la ventana entre el diagnóstico fresco y adquirir este guard.
            if (WdaLifecycleOwner.isBuildInFlight(udid)) {
                log("EXISTING_WDA_OPERATION_DETECTED", udid, "waiting/reusing existing operation");
                return new RecoveryResult(IOSRecoveryOutcome.RECOVERY_IN_PROGRESS, udid, fresh, null, null);
            }

            log("STARTING_WDA_RECOVERY", udid, "delegating to IosPreflightManager.runPreflight()");
            try {
                // Única "operación real": el mismo camino que ya usan JobExecutor y el
                // Mirror. Consumer.RECOVERY (TAREA 8) evita colisionar con una ejecución
                // real concurrente que también tenga JOB_EXECUTION registrado. El guard
                // de arriba (RECOVERY_REQUEST_PENDING) es lo que ahora garantiza que
                // nunca hay una SEGUNDA llamada a recover() ejecutando este mismo bloque
                // en paralelo para este UDID (TAREA 8.1).
                IosPreflightManager.runPreflight(client, executionId, udid, WdaLifecycleOwner.Consumer.RECOVERY);
            } catch (Exception e) {
                log("RECOVERY_EXCEPTION", udid, e.getClass().getSimpleName() + ": " + e.getMessage());
                return new RecoveryResult(IOSRecoveryOutcome.RECOVERY_FAILED, udid, fresh, null, e);
            } finally {
                // Esta clase no necesita mantener WDA vivo por su cuenta — solo dispara
                // la operación y libera de inmediato; si una ejecución real la sigue
                // necesitando, su propio registro (JOB_EXECUTION) la mantiene viva.
                WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.RECOVERY, client, executionId, udid);
            }
            log("RECOVERY_OPERATION_COMPLETED", udid, null);

            // ── READY solo puede venir de un nuevo evaluate() real ─────────────
            IOSRunnerReadinessResult after = IOSRunnerReadinessEngine.evaluate(client, executionId, udid);
            log("FRESH_DIAGNOSIS_AFTER_RECOVERY", udid, "status=" + after.status + " errorCode=" + after.errorCode);

            return new RecoveryResult(mapAfterRecovery(after.status), udid, fresh, after, null);
        } finally {
            // SIEMPRE se libera — éxito, ERROR, ACTION_REQUIRED o excepción — porque
            // este bloque finally envuelve TODO lo que ocurre después de un add()
            // exitoso. Ninguna entrada puede quedar huérfana en RECOVERY_REQUEST_PENDING.
            RECOVERY_REQUEST_PENDING.remove(udid);
        }
    }

    // ── Clasificación (solo lectura de campos ya calculados por el Engine) ─────

    private static boolean isWdaNotStarted(IOSRunnerReadinessResult r) {
        return r.status == IOSRunnerReadinessResult.Status.NOT_READY
                && "IOS_WDA_NOT_STARTED".equals(r.errorCode);
    }

    /**
     * Sin acción tomada. ACTION_REQUIRED y RECOVERING se traducen explícitamente
     * (TAREA 7); todo lo demás (READY, ERROR, OFFLINE, o un NOT_READY con un
     * errorCode distinto — hoy no existe ese caso, pero se maneja igual por
     * seguridad) es NO_ACTION_REQUIRED: este manager, en este vertical slice,
     * simplemente no sabe actuar sobre ello — no significa que un intento propio
     * haya fallado (por eso NO se usa RECOVERY_FAILED aquí).
     */
    private static RecoveryResult withoutAction(String udid, IOSRunnerReadinessResult fresh) {
        IOSRecoveryOutcome outcome;
        if (fresh.status == IOSRunnerReadinessResult.Status.ACTION_REQUIRED) {
            outcome = IOSRecoveryOutcome.ACTION_REQUIRED;
        } else if (fresh.status == IOSRunnerReadinessResult.Status.RECOVERING) {
            outcome = IOSRecoveryOutcome.RECOVERY_IN_PROGRESS;
        } else {
            outcome = IOSRecoveryOutcome.NO_ACTION_REQUIRED;
        }
        log("NO_ACTION", udid, "status=" + fresh.status + " outcome=" + outcome);
        return new RecoveryResult(outcome, udid, fresh, null, null);
    }

    // Package-private (no "private") para permitir probar la tabla de mapeo
    // directamente, sin necesitar forzar un xcodebuild real a terminar en cada
    // resultado posible (ERROR/OFFLINE/etc.) — mismo patrón ya usado en este
    // proyecto (ver IOSDeviceScanner.parseDevicectlOutput, "Package-private for
    // unit testing").
    static IOSRecoveryOutcome mapAfterRecovery(IOSRunnerReadinessResult.Status afterStatus) {
        switch (afterStatus) {
            case READY:            return IOSRecoveryOutcome.RECOVERY_SUCCEEDED;
            case RECOVERING:       return IOSRecoveryOutcome.RECOVERY_IN_PROGRESS;
            case ACTION_REQUIRED:  return IOSRecoveryOutcome.ACTION_REQUIRED;
            case ERROR:
            case NOT_READY:
            case OFFLINE:
            default:                return IOSRecoveryOutcome.RECOVERY_FAILED;
        }
    }

    // ── Logging mínimo ──────────────────────────────────────────────────────

    private static void log(String event, String udid, String detail) {
        System.out.printf("[%s] [IOSRecoveryManager] %s udid=%s%s%n",
                LocalTime.now().format(TS), event, udid, detail != null ? " " + detail : "");
    }
}
