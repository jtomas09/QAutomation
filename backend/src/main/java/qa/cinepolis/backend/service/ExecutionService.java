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

    public Execution create(String suite, String env, String device, String country) {
        return store.create(suite, env, device, country);
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
        store.findById(executionId).ifPresent(e -> {
            e.setPassed(passed);
            e.setFailed(failed);
            e.setSkipped(skipped);
            e.setTotal(passed + failed + skipped);
            e.setStatus(failed == 0 ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
            e.setEndTime(Instant.now());
            if (allureUrl != null && !allureUrl.isBlank()) e.setAllureUrl(allureUrl);

            sse.broadcast(executionId, "done", Map.of(
                    "passed",  passed,
                    "failed",  failed,
                    "skipped", skipped,
                    "total",   e.getTotal()
            ));
            sse.complete(executionId);
        });
    }

    public void abort(String executionId) {
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

    /** Called every time the runner agent polls GET /api/run/pending. */
    public void recordRunnerPing() {
        lastRunnerPing = Instant.now();
    }

    /** True if the runner pinged within the last 15 seconds. */
    public boolean isRunnerOnline() {
        return lastRunnerPing.isAfter(Instant.now().minusSeconds(15));
    }
}
