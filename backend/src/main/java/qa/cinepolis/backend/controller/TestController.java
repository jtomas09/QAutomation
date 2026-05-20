package qa.cinepolis.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.ExecutionStatus;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class TestController {

    private final ExecutionService execService;

    public TestController(ExecutionService execService) {
        this.execService = execService;
    }

    /** GET /api/run/pending — returns the next QUEUED execution without claiming it. */
    @GetMapping("/run/pending")
    public ResponseEntity<?> pending() {
        execService.recordRunnerPing();   // runner is alive
        Optional<Execution> next = execService.findAll().stream()
                .filter(e -> e.getStatus() == ExecutionStatus.QUEUED)
                .min(Comparator.comparing(Execution::getStartTime));
        return next.isPresent()
                ? ResponseEntity.ok(next.get())
                : ResponseEntity.noContent().build();
    }

    /** DELETE /api/run/{id} — aborts a pending or running execution. */
    @DeleteMapping("/run/{id}")
    public Map<String, String> abortRun(@PathVariable String id) {
        execService.abort(id);
        return Map.of("result", "aborted", "executionId", id);
    }

    /** GET /api/status — whether any execution is currently running + runner heartbeat. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "running",       execService.isRunning(),
                "runnerOnline",  execService.isRunnerOnline()
        );
    }

    /** GET /api/config — option catalogs for the frontend selectors. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "environments", new String[]{"QA", "PROD", "STG"},
                "suites",       new String[]{"Smoke Tests", "Asientos", "Alimentos", "Checkout", "Full Suite"},
                "devices",      new String[]{"Galaxy A56 5G", "Galaxy S23", "Pixel 7", "BrowserStack"}
        );
    }

    /** GET /api/executions — full execution history, newest first. */
    @GetMapping("/executions")
    public List<Execution> executions() {
        return execService.findAll();
    }

    /** GET /api/executions/{id} — detail of one execution including logs. */
    @GetMapping("/executions/{id}")
    public Execution execution(@PathVariable String id) {
        return execService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Execution not found: " + id));
    }
}
