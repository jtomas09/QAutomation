package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.JobStatusUpdate;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final ExecutionService execService;

    public JobController(ExecutionService execService) {
        this.execService = execService;
    }

    /**
     * GET /api/jobs/next
     * Runner polls this to claim the next PENDING job.
     * Returns 204 when the queue is empty.
     */
    @GetMapping("/next")
    public ResponseEntity<?> getNextJob() {
        execService.recordRunnerPing();   // runner is alive
        Optional<Execution> opt = execService.claimNextPending();
        if (opt.isEmpty()) return ResponseEntity.noContent().build();

        Execution exec = opt.get();
        return ResponseEntity.ok(Map.of(
                "executionId", exec.getExecutionId(),
                "suite",       exec.getSuite(),
                "env",         exec.getEnv(),
                "device",      exec.getDevice(),
                "country",     exec.getCountry()
        ));
    }

    /**
     * POST /api/jobs/ping
     * Runner sends this while executing a job so the heartbeat stays alive.
     */
    @PostMapping("/ping")
    public Map<String, String> ping() {
        execService.recordRunnerPing();
        return Map.of("result", "ok");
    }

    /**
     * POST /api/jobs/{id}/status
     * Runner updates job lifecycle status (RUNNING, COMPLETED, FAILED, ABORTED).
     */
    @PostMapping("/{id}/status")
    public Map<String, String> updateStatus(@PathVariable String id,
                                            @RequestBody  JobStatusUpdate update) {
        execService.updateStatus(id, update.status());
        return Map.of("result", "ok");
    }
}
