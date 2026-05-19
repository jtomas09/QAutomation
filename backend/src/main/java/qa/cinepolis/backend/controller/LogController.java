package qa.cinepolis.backend.controller;

import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.LogRequest;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LogController {

    private final ExecutionService execService;

    public LogController(ExecutionService execService) {
        this.execService = execService;
    }

    /**
     * POST /api/logs
     * Runner streams live log lines to the backend, which broadcasts them via SSE.
     */
    @PostMapping("/logs")
    public Map<String, String> addLog(@RequestBody LogRequest req) {
        execService.addLog(req.executionId(), req.level(), req.message());
        return Map.of("result", "ok");
    }
}
