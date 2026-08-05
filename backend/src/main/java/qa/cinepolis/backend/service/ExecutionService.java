package qa.cinepolis.backend.service;

import org.springframework.stereotype.Service;
import qa.cinepolis.backend.model.*;
import qa.cinepolis.backend.store.DeviceStore;
import qa.cinepolis.backend.store.ExecutionStore;

import java.time.Instant;
import java.util.*;

@Service
public class ExecutionService {

    private final ExecutionStore store;
    private final SseService     sse;
    private final DeviceStore    deviceStore;

    public ExecutionService(ExecutionStore store, SseService sse, DeviceStore deviceStore) {
        this.store       = store;
        this.sse         = sse;
        this.deviceStore = deviceStore;
    }

    public Execution create(String suite, String env, String device, String country, boolean videoEnabled) {
        return store.create(suite, env, device, country, videoEnabled, null);
    }

    public Execution create(String suite, String env, String device, String country, boolean videoEnabled, String testClass) {
        return store.create(suite, env, device, country, videoEnabled, testClass);
    }

    /** Atomically claims the next QUEUED job and marks it STARTING (Runner received it, preflight begins). */
    public synchronized Optional<Execution> claimNextPending() {
        return store.findNextPending().filter(e -> {
            if (e.getStatus() != ExecutionStatus.QUEUED) return false;
            e.setStatus(ExecutionStatus.STARTING);
            sse.broadcast(e.getExecutionId(), "status",
                    Map.of("status", "STARTING", "executionId", e.getExecutionId()));
            return true;
        });
    }

    /** Runner transitions STARTING → RUNNING when Gradle process actually starts executing tests. */
    public void setRunning(String executionId) {
        store.findById(executionId).ifPresent(e -> {
            if (e.getStatus() != ExecutionStatus.STARTING) return;
            e.setStatus(ExecutionStatus.RUNNING);
            sse.broadcast(executionId, "status", Map.of("status", "RUNNING"));
        });
    }

    // FIX real (evidencia en vivo — el Timeline seguía mostrando stdout crudo de
    // Gradle/JUnit: "base.BaseTest", "PdfSuiteMerger", "[Test worker] INFO
    // config.DriverFactory...", etc.): esta clase antes traducía CADA log legacy a
    // un ExecutionEventDTO ("puente de compatibilidad") y lo mandaba al MISMO canal
    // de eventos que la narración de negocio real — el frontend intentaba
    // reclasificarlo después con un regex heredado, que nunca pudo reconocer de
    // forma confiable texto crudo de estas herramientas (ese regex es precisamente
    // el enfoque que se descartó: "no quiero ocultar líneas mediante regex").
    //
    // Ahora los dos canales son independientes DESDE EL ORIGEN, no por filtrado
    // posterior: addLog()/"log" sigue siendo el canal de Developer Log — TODO
    // stdout/stderr, sin excepción, exactamente igual que siempre. addEvent()/
    // "execution-event" SOLO recibe lo que un emisor publica explícitamente vía
    // ExecutionEventPublisher (ver Runner) — nunca se deriva de una línea de texto.
    public void addLog(String executionId, String level, String message) {
        store.findById(executionId).ifPresent(e -> {
            LogEvent log = LogEvent.of(level, message);
            e.getLogs().add(log);
            sse.broadcast(executionId, "log", log);
        });
    }

    /**
     * Evento de negocio real — ver ExecutionEventDTO. Persistido (en memoria) +
     * reenviado por SSE. Único punto de entrada al canal "execution-event": solo
     * lo alimenta ExecutionEventPublisher.publish() del Runner, nunca addLog().
     */
    public void addEvent(ExecutionEventDTO event) {
        store.findById(event.executionId()).ifPresent(e -> {
            e.getEvents().add(event);
            sse.broadcast(event.executionId(), "execution-event", event);
        });
    }

    /**
     * Generic status update — routes guarded transitions through their specific methods
     * so that pre-condition checks (e.g. RUNNING→FINALIZING only from RUNNING) are enforced
     * even when called via the generic POST /api/jobs/{id}/status endpoint.
     */
    public void updateStatus(String executionId, String statusStr) {
        try {
            ExecutionStatus target = ExecutionStatus.valueOf(statusStr.toUpperCase());
            switch (target) {
                case RUNNING            -> setRunning(executionId);
                case FINALIZING         -> setFinalizing(executionId);
                case FAILED_FINALIZATION -> setFailedFinalization(executionId);
                default -> store.findById(executionId).ifPresent(e -> {
                    e.setStatus(target);
                    sse.broadcast(executionId, "status", Map.of("status", target.name()));
                });
            }
        } catch (IllegalArgumentException ignored) {}
    }

    public void complete(String executionId, int passed, int failed, int skipped, String allureUrl) {
        complete(executionId, passed, failed, skipped, allureUrl, null, 0);
    }

    public void complete(String executionId, int passed, int failed, int skipped,
                         String allureUrl, List<qa.cinepolis.backend.model.TestCaseResult> testCases) {
        complete(executionId, passed, failed, skipped, allureUrl, testCases, 0);
    }

    public void complete(String executionId, int passed, int failed, int skipped,
                         String allureUrl, List<qa.cinepolis.backend.model.TestCaseResult> testCases,
                         int expectedCount) {
        store.findById(executionId).ifPresent(e -> {
            e.setPassed(passed);
            e.setFailed(failed);
            e.setSkipped(skipped);
            int total = passed + failed + skipped;
            e.setTotal(total);
            e.setExpectedCount(expectedCount);

            // INCOMPLETE tiene prioridad sobre COMPLETED/FAILED: una suite que terminó con
            // menos casos de los planificados (Gradle/JVM terminó antes de tiempo, incidente
            // de stream, etc.) nunca debe verse como "Completado" solo porque los pocos casos
            // que sí corrieron pasaron — expectedCount=0 (Runner no lo pudo calcular) preserva
            // el comportamiento anterior (failed==0 ? COMPLETED : FAILED).
            ExecutionStatus status = (expectedCount > 0 && total < expectedCount)
                    ? ExecutionStatus.INCOMPLETE
                    : (failed == 0 ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED);
            e.setStatus(status);
            e.setEndTime(Instant.now());
            if (allureUrl  != null && !allureUrl.isBlank())  e.setAllureUrl(allureUrl);
            if (testCases  != null && !testCases.isEmpty())  e.setTestCases(testCases);

            // Release the allocated device back to AVAILABLE
            if (e.getDeviceUdid() != null) deviceStore.releaseDevice(e.getDeviceUdid());

            sse.broadcast(executionId, "done", Map.of(
                    "passed",  passed,
                    "failed",  failed,
                    "skipped", skipped,
                    "total",   e.getTotal()
            ));
            sse.complete(executionId);
        });
    }

    /**
     * Marca la ejecución como FAILED sin que haya llegado a correr — caso de carrera
     * (Problema 5): un dispositivo estaba AVAILABLE al encolar (POST /api/run) pero
     * se desconectó antes de que el Runner pidiera el job (GET /api/jobs/next).
     * A diferencia de complete()/confirmAbort(), nunca hubo un dispositivo realmente
     * reclamado (claimDevice() ya devolvió empty), así que no hay nada que liberar en
     * DeviceStore. El broadcast "done" (misma forma que complete()) es necesario para
     * que el frontend salga de status='running' — sin él el timer de "Actividad en
     * Tiempo Real" seguiría corriendo indefinidamente (mismo bug ya corregido antes).
     */
    public void failWithReason(String executionId, String reason) {
        store.findById(executionId).ifPresent(e -> {
            e.setStatus(ExecutionStatus.FAILED);
            e.setFailureReason(reason);
            e.setEndTime(Instant.now());
            sse.broadcast(executionId, "done", Map.of(
                    "passed",  0,
                    "failed",  0,
                    "skipped", 0,
                    "total",   0,
                    "failureReason", reason
            ));
            sse.complete(executionId);
        });
    }

    /**
     * Marca la ejecución como ABORTING (señal al runner para que detenga el proceso).
     * El runner cambia el estado a ABORTED cuando confirma que Gradle fue terminado.
     * Si no hay runner activo (ejecución QUEUED), pasa directo a ABORTED.
     */
    public void abort(String executionId) {
        store.findById(executionId).ifPresent(e -> {
            if (e.getStatus() == ExecutionStatus.RUNNING) {
                // Hay un runner activo; marcar ABORTING y esperar confirmación del runner
                e.setStatus(ExecutionStatus.ABORTING);
                sse.broadcast(executionId, "status", Map.of("status", "ABORTING"));
            } else {
                // QUEUED u otro estado — no hay runner, abortar directamente
                e.setStatus(ExecutionStatus.ABORTED);
                e.setEndTime(Instant.now());
                sse.broadcast(executionId, "done",
                        Map.of("aborted", true, "passed", 0, "failed", 0, "skipped", 0, "total", 0));
                sse.complete(executionId);
            }
        });
    }

    /** Confirma el aborto (llamado por el runner tras matar Gradle). */
    public void confirmAbort(String executionId) {
        store.findById(executionId).ifPresent(e -> {
            e.setStatus(ExecutionStatus.ABORTED);
            e.setEndTime(Instant.now());
            // Release device on abort too
            if (e.getDeviceUdid() != null) deviceStore.releaseDevice(e.getDeviceUdid());
            sse.broadcast(executionId, "done",
                    Map.of("aborted", true, "passed", 0, "failed", 0, "skipped", 0, "total", 0));
            sse.complete(executionId);
        });
    }

    public Optional<Execution> findById(String id) { return store.findById(id); }
    public List<Execution>     findAll()            { return store.findAll(); }

    /**
     * Marca la ejecución como FINALIZING (post-procesamiento activo: cleanup, videos, Allure).
     * Guard: sólo acepta la transición desde RUNNING — rechaza si hay tests pendientes (estado
     * inconsistente) o si la ejecución ya terminó / fue abortada.
     */
    public void setFinalizing(String executionId) {
        store.findById(executionId).ifPresent(e -> {
            // Only RUNNING → FINALIZING is valid. Any other source state means the transition
            // is premature (still in preflight) or a duplicate (already finalizing/done).
            if (e.getStatus() != ExecutionStatus.RUNNING) return;
            e.setStatus(ExecutionStatus.FINALIZING);
            sse.broadcast(executionId, "status", Map.of("status", "FINALIZING"));
        });
    }

    /** Post-procesamiento falló de forma crítica — la ejecución no pudo completarse limpiamente. */
    public void setFailedFinalization(String executionId) {
        store.findById(executionId).ifPresent(e -> {
            if (e.getStatus() != ExecutionStatus.FINALIZING) return;
            e.setStatus(ExecutionStatus.FAILED_FINALIZATION);
            e.setEndTime(java.time.Instant.now());
            if (e.getDeviceUdid() != null) deviceStore.releaseDevice(e.getDeviceUdid());
            sse.broadcast(executionId, "status", Map.of("status", "FAILED_FINALIZATION"));
            // Sin este "done" (mismo mecanismo/forma que complete()), el frontend nunca
            // abandona 'running': solo reacciona a "status" para FINALIZING/ABORTING, y
            // sse.complete() cerraba el emitter en silencio antes de que llegara esta línea.
            sse.broadcast(executionId, "done", Map.of(
                    "passed",  e.getPassed(),
                    "failed",  e.getFailed(),
                    "skipped", e.getSkipped(),
                    "total",   e.getTotal()
            ));
            sse.complete(executionId);
        });
    }

    public boolean isRunning() {
        return store.findAll().stream()
                .anyMatch(e -> e.getStatus() == ExecutionStatus.RUNNING);
    }

    // ── Runner heartbeat ───────────────────────────────────────────────────────

    private volatile Instant lastRunnerPing = Instant.EPOCH;

    /** Called every time the runner agent polls GET /api/jobs/next or GET /api/run/pending. */
    public void recordRunnerPing() {
        lastRunnerPing = Instant.now();
    }

    /**
     * El runner se considera ONLINE si:
     *  - Ping reciente (últimos 30 s) — estado nominal en reposo
     *  - O hay una ejecución RUNNING activa — durante tests el runner está bloqueado
     *    en execute() y puede tardar >30 s sin poll; si hay job activo, el runner existe
     *  - O el último ping fue en los últimos 5 min — tolerancia para suites largas
     *    donde el heartbeat puede fallar por red sin que el runner muera
     */
    public boolean isRunnerOnline() {
        if (lastRunnerPing.isAfter(Instant.now().minusSeconds(30))) return true;
        if (isRunning()) return true;  // ejecución activa → runner necesariamente vivo
        if (lastRunnerPing.isAfter(Instant.now().minusSeconds(300))) return true; // 5 min tolerancia
        return false;
    }
}
