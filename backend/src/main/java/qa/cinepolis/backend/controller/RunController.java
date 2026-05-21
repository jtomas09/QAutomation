package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.RunRequest;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RunController {

    private final ExecutionService execService;

    public RunController(ExecutionService execService) {
        this.execService = execService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runSuite(@RequestBody RunRequest request) {
        Execution exec = execService.create(
                request.getSuite(),
                request.getEnvironment(),
                request.getDevice(),
                request.getCountry(),
                request.isVideoEnabled()
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success",     true);
        body.put("executionId", exec.getExecutionId());
        body.put("status",      "QUEUED");
        body.put("suite",       request.getSuite());
        body.put("device",      request.getDevice());
        body.put("environment", request.getEnvironment());
        body.put("country",     request.getCountry());
        body.put("message",     "Execution queued");

        return ResponseEntity.ok(body);
    }
}
