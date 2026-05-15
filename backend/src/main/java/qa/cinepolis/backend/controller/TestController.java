package qa.cinepolis.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import qa.cinepolis.backend.model.RunRequest;
import qa.cinepolis.backend.service.TestRunnerService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    private final TestRunnerService runner;

    public TestController(TestRunnerService runner) {
        this.runner = runner;
    }

    /**
     * GET /api/status — estado actual de ejecución.
     * Usado por el frontend para saber si hay algo corriendo al cargar.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("running", runner.isRunning());
    }

    /**
     * POST /api/run — inicia ejecución, responde con SSE stream.
     * El frontend usa EventSource para escuchar los logs en tiempo real.
     *
     * FASE 4: React reemplaza mockRunTest() por:
     *   const es = new EventSource(`/api/run?suite=...&env=...&device=...`);
     *   es.addEventListener('log',  e => addLog(JSON.parse(e.data)));
     *   es.addEventListener('done', () => es.close());
     */
    @GetMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(
        @RequestParam String suite,
        @RequestParam String env,
        @RequestParam String device,
        @RequestParam(defaultValue = "mexico") String country
    ) {
        return runner.run(new RunRequest(suite, env, device, country));
    }

    /**
     * DELETE /api/run — aborta la ejecución en curso.
     */
    @DeleteMapping("/run")
    public Map<String, String> stop() {
        runner.stop();
        return Map.of("result", "stopped");
    }

    /**
     * GET /api/config — devuelve catálogos para que el frontend no los hardcodee.
     */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
            "environments", new String[]{"QA", "PROD", "STG"},
            "suites",       new String[]{"Smoke Tests", "Full Suite", "Regresión", "Sanity"},
            "devices",      new String[]{"Galaxy A56 5G", "Galaxy S23", "Pixel 7", "BrowserStack"}
        );
    }
}
