package qa.cinepolis.backend.service;

import org.springframework.stereotype.Service;
import qa.cinepolis.backend.model.*;
import qa.cinepolis.backend.store.ExecutionStore;

import java.time.Instant;
import java.util.*;

@Service
public class ExecutionService {

    private final ExecutionStore store;
    private final SseService     sse;

    public ExecutionService(ExecutionStore store, SseService sse) {
        this.store = store;
        this.sse   = sse;
    }

    public Execution create(String suite, String env, String device, String country, boolean videoEnabled) {
        return store.create(suite, env, device, country, videoEnabled, null);
    }

    public Execution create(String suite, String env, String device, String country, boolean videoEnabled, String testClass) {
        return store.create(suite, env, device, country, videoEnabled, testClass);
    }

    /** Atomically claims the next QUEUED job and marks it RUNNING. */
    public synchronized Optional<Execution> claimNextPending() {
        return store.findNextPending().filter(e -> {
            if (e.getStatus() != ExecutionStatus.QUEUED) return false;
            e.setStatus(ExecutionStatus.RUNNING);
            sse.broadcast(e.getExecutionId(), "status",
                    Map.of("status", "RUNNING", "executionId", e.getExecutionId()));
            return true;
        });
    }

    public void addLog(String executionId, String level, String message) {
        store.findById(executionId).ifPresent(e -> {
            LogEvent log = LogEvent.of(level, message);
            e.getLogs().add(log);
            sse.broadcast(executionId, "log", log);
        });
    }

    public void updateStatus(String executionId, String statusStr) {
        store.findById(executionId).ifPresent(e -> {
            try {
                ExecutionStatus status = ExecutionStatus.valueOf(statusStr.toUpperCase());
                e.setStatus(status);
                sse.broadcast(executionId, "status", Map.of("status", status.name()));
            } catch (IllegalArgumentException ignored) {}
        });
    }

    public void complete(String executionId, int passed, int failed, int skipped, String allureUrl) {
        complete(executionId, passed, failed, skipped, allureUrl, null);
    }

    public void complete(String executionId, int passed, int failed, int skipped,
                         String allureUrl, List<qa.cinepolis.backend.model.TestCaseResult> testCases) {
        store.findById(executionId).ifPresent(e -> {
            e.setPassed(passed);
            e.setFailed(failed);
            e.setSkipped(skipped);
            e.setTotal(passed + failed + skipped);
            e.setStatus(failed == 0 ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
            e.setEndTime(Instant.now());
            if (allureUrl  != null && !allureUrl.isBlank())  e.setAllureUrl(allureUrl);
            if (testCases  != null && !testCases.isEmpty())  e.setTestCases(testCases);

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
            sse.broadcast(executionId, "done",
                    Map.of("aborted", true, "passed", 0, "failed", 0, "skipped", 0, "total", 0));
            sse.complete(executionId);
        });
    }

    public Optional<Execution> findById(String id) { return store.findById(id); }
    public List<Execution>     findAll()            { return store.findAll(); }

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
