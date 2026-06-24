package qa.cinepolis.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.RunRequest;
import qa.cinepolis.backend.service.ExecutionService;
import qa.cinepolis.backend.store.ExecutionDeviceStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RunController {

    private static final Logger log = LoggerFactory.getLogger(RunController.class);

    private final ExecutionService    execService;
    private final ExecutionDeviceStore deviceConfigStore;

    public RunController(ExecutionService execService, ExecutionDeviceStore deviceConfigStore) {
        this.execService       = execService;
        this.deviceConfigStore = deviceConfigStore;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runSuite(@RequestBody RunRequest request) {
        List<String> configured = deviceConfigStore.getDeviceUdids();
        log.info("[RunController] CONFIGURACIÓN GUARDADA: {}", configured);
        log.info("[RunController] RUN GENERADO: device={} suite={} env={} country={} videoEnabled={}",
                request.getDevice(), request.getSuite(), request.getEnvironment(),
                request.getCountry(), request.isVideoEnabled());

        if (!configured.isEmpty() && !configured.contains(request.getDevice())) {
            log.warn("[RunController] ⚠ La configuración almacenada no coincide con el dispositivo enviado. " +
                    "Configurado={} | Recibido={}", configured, request.getDevice());
        }

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
