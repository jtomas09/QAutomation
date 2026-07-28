package qa.cinepolis.backend.controller;

import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.ExecutionEventDTO;
import qa.cinepolis.backend.service.ExecutionService;

import java.util.Map;

/**
 * POST /api/events — canal para eventos de dominio (ExecutionEventDTO), publicados
 * por qa.cinepolis.runner.events.ExecutionEventPublisher. Convive con /api/logs
 * (LogController) — ver ExecutionService.addLog()/addEvent().
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EventController {

    private final ExecutionService execService;

    public EventController(ExecutionService execService) {
        this.execService = execService;
    }

    @PostMapping("/events")
    public Map<String, String> addEvent(@RequestBody ExecutionEventDTO event) {
        execService.addEvent(event);
        return Map.of("result", "ok");
    }
}
