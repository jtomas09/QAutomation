package qa.cinepolis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.ExecutionStatus;
import qa.cinepolis.backend.service.ExecutionService;
import qa.cinepolis.backend.service.SseService;

import java.util.Map;
import java.util.Optional;

@RestController
public class StreamController {

    private final ExecutionService execService;
    private final SseService       sseService;
    private final ObjectMapper     json = new ObjectMapper();

    public StreamController(ExecutionService execService, SseService sseService) {
        this.execService = execService;
        this.sseService  = sseService;
    }

    /**
     * GET /api/run/{id}/stream
     * SSE stream for a specific execution. Replays existing logs on connect
     * and sends 'done' immediately if the execution has already finished.
     */
    @GetMapping(value = "/api/run/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        SseEmitter emitter = sseService.register(id);

        Optional<Execution> opt = execService.findById(id);
        if (opt.isPresent()) {
            Execution exec = opt.get();

            // Replay accumulated logs for late / reconnecting clients
            exec.getLogs().forEach(log -> {
                try {
                    emitter.send(SseEmitter.event().name("log").data(json.writeValueAsString(log)));
                } catch (Exception ignored) {}
            });

            // If already finished, flush done event and close
            ExecutionStatus s = exec.getStatus();
            boolean isTerminal = s == ExecutionStatus.COMPLETED || s == ExecutionStatus.PASSED
                    || s == ExecutionStatus.FAILED  || s == ExecutionStatus.SKIPPED
                    || s == ExecutionStatus.ABORTED;
            if (isTerminal) {
                try {
                    emitter.send(SseEmitter.event().name("done").data(json.writeValueAsString(
                            Map.of("passed",  exec.getPassed(),
                                   "failed",  exec.getFailed(),
                                   "skipped", exec.getSkipped(),
                                   "total",   exec.getTotal()))));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }

        return emitter;
    }
}
