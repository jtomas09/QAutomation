package qa.cinepolis.backend.controller;

import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.ResultPayload;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResultController {

    private final ExecutionService execService;

    public ResultController(ExecutionService execService) {
        this.execService = execService;
    }

    /**
     * POST /api/results
     * Runner reports final pass/fail/skip counts and optional Allure URL.
     */
    @PostMapping("/results")
    public Map<String, String> saveResult(@RequestBody ResultPayload payload) {
        execService.complete(
                payload.executionId(),
                payload.passed(),
                payload.failed(),
                payload.skipped(),
                payload.allureUrl(),
                payload.testCases(),
                payload.expectedCount()
        );
        return Map.of("result", "ok", "executionId", payload.executionId());
    }
}
