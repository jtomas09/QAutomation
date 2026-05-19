package qa.cinepolis.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.RunRequest;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    private final ExecutionService execService;

    public TestController(ExecutionService execService) {
        this.execService = execService;
    }

    /**
     * POST /api/run
     * Enqueues a new execution and returns {executionId, status} immediately.
     * Frontend subscribes to GET /api/run/{id}/stream for live SSE logs.
     */
    @PostMapping("/run")
    public Map<String, String> startRun(@RequestBody RunRequest req) {
        Execution exec = execService.create(req.suite(), req.env(), req.device(), req.country());
        return Map.of(
                "executionId", exec.getExecutionId(),
                "status",      exec.getStatus().name()
        );
    }

    /** DELETE /api/run/{id} — aborts a pending or running execution. */
    @DeleteMapping("/run/{id}")
    public Map<String, String> abortRun(@PathVariable String id) {
        execService.abort(id);
        return Map.of("result", "aborted", "executionId", id);
    }

    /** GET /api/status — whether any execution is currently running. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("running", execService.isRunning());
    }

    /** GET /api/config — option catalogs for the frontend selectors. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "environments", new String[]{"QA", "PROD", "STG"},
                "suites",       new String[]{"Smoke Tests", "Full Suite", "Regresión", "Sanity"},
                "devices",      new String[]{"Galaxy A56 5G", "Galaxy S23", "Pixel 7", "BrowserStack"}
        );
    }

    /** GET /api/executions — full execution history, newest first. */
    @GetMapping("/executions")
    public List<Execution> executions() {
        return execService.findAll();
    }

    /** GET /api/executions/{id} — detail of one execution including log lines. */
    @GetMapping("/executions/{id}")
    public Execution execution(@PathVariable String id) {
        return execService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Execution not found: " + id));
    }
}
