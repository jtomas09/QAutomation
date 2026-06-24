package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.store.RunnerConfigStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET  /api/runner/config  — Runner fetches this at startup and before each job.
 * POST /api/runner/config  — Admin can update config at runtime (no restart needed).
 *
 * This is the single source of truth for which repository all Runners clone.
 */
@RestController
@RequestMapping("/api/runner")
@CrossOrigin(origins = "*")
public class RunnerConfigController {

    private final RunnerConfigStore store;

    public RunnerConfigController(RunnerConfigStore store) {
        this.store = store;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getRunnerConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("repositoryUrl", store.getRepositoryUrl());
        body.put("branch",        store.getBranch());
        body.put("projectName",   store.getProjectName());
        body.put("configured",    store.isConfigured());

        if (!store.isConfigured()) {
            body.put("warning",
                "REPO_URL not set. Set the REPO_URL environment variable on the backend, "
                + "or POST to /api/runner/config to configure at runtime.");
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, String>> setRunnerConfig(
            @RequestBody RunnerConfigRequest req) {
        store.setConfig(req.repositoryUrl(), req.branch(), req.projectName());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    record RunnerConfigRequest(String repositoryUrl, String branch, String projectName) {}
}
