package qa.cinepolis.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.RunRequest;
import qa.cinepolis.backend.service.DeviceReadinessService;
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

    private final ExecutionService        execService;
    private final ExecutionDeviceStore    deviceConfigStore;
    private final DeviceReadinessService  readiness;

    public RunController(ExecutionService execService, ExecutionDeviceStore deviceConfigStore,
                          DeviceReadinessService readiness) {
        this.execService       = execService;
        this.deviceConfigStore = deviceConfigStore;
        this.readiness         = readiness;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runSuite(@RequestBody RunRequest request) {
        List<String> configured = deviceConfigStore.getDeviceUdids();
        log.info("[RunController] CONFIGURACIÓN GUARDADA: {}", configured);
        log.info("[RunController] RUN GENERADO: device={} suite={} env={} country={} videoEnabled={}",
                request.getDevice(), request.getSuite(), request.getEnvironment(),
                request.getCountry(), request.isVideoEnabled());

        if (!configured.isEmpty() && !configured.contains(request.getDevice())) {
            log.info("[RunController] La config guardada del Dashboard ({}) no incluye el Device Target " +
                    "de este RUN ({}) — esto es esperado: el Device Target de un RUN lo decide el modal de " +
                    "ejecución, no la configuración global del Dashboard.", configured, request.getDevice());
        }

        // REGLA DE SEGURIDAD (Device Target): un RUN nunca se crea sin un device
        // explícito — nunca se usa "el dispositivo configurado en Dashboard" ni
        // "el primer disponible" como fallback. Rechazo explícito, sin excepción.
        if (request.getDevice() == null || request.getDevice().isBlank()) {
            log.warn("[RunController] ❌ Ejecución rechazada — sin Device Target: suite={}", request.getSuite());
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("success", false);
            rejected.put("message", "La ejecución no tiene un Device Target configurado.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(rejected);
        }

        // Problema 5 — nunca crear una Execution destinada a fallar: se valida
        // disponibilidad ANTES de encolar, con el mismo criterio (AVAILABLE) que
        // JobController usará después vía DeviceStore.claimDevice(). Esto no
        // elimina la carrera "se desconectó justo después de encolar" (ver
        // JobController) — solo evita el caso común (dispositivo ya offline
        // desde antes de intentar ejecutar).
        if (!readiness.isReady(request.getDevice())) {
            String label = request.getDeviceName() != null && !request.getDeviceName().isBlank()
                    ? request.getDeviceName() + " / " + request.getDevice()
                    : request.getDevice();
            String reason = "Device Target " + label + " no está disponible.";
            log.warn("[RunController] ❌ Ejecución rechazada — dispositivo no listo: {} ({})",
                    request.getDevice(), readiness.notReadyReason(request.getDevice()));
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("success", false);
            rejected.put("message", reason);
            rejected.put("device",  request.getDevice());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(rejected);
        }

        Execution exec = execService.create(
                request.getSuite(),
                request.getEnvironment(),
                request.getDevice(),
                request.getCountry(),
                request.isVideoEnabled()
        );

        // Device Target — logging explícito pedido: este RUN queda pinneado a
        // ESTE device de forma permanente en la Execution (campo por-instancia,
        // ver Execution.device/deviceUdid) — cambiar la selección en Dashboard
        // después de este punto no afecta a este RUN en absoluto.
        log.info("[RunDevice] RUN={}", exec.getExecutionId());
        log.info("[RunDevice] Target device: name={} platform={} udid={}",
                request.getDeviceName(), request.getDevicePlatform(), request.getDevice());

        // Caso grabado en Record Studio (ver RunRequest.RecordedCase) — se adjunta
        // tal cual a la Execution ya creada; JobController lo incluye en el JSON
        // del job cuando el Runner lo reclama. Ausente en cualquier ejecución normal.
        if (request.getRecordedCase() != null) {
            exec.setRecordedCaseClassName(request.getRecordedCase().getClassName());
            exec.setRecordedCaseSource(request.getRecordedCase().getSource());
            exec.setRecordedCaseName(request.getRecordedCase().getCaseName());
            log.info("[RunController] Caso grabado — className={} udid={}",
                    request.getRecordedCase().getClassName(), request.getDevice());
        }

        // Suite grabada en Record Studio (ver RunRequest.recordedCases) — misma
        // idea que arriba pero para N TestCases de una TestSuite. Log explícito
        // pedido para poder comprobar EXACTAMENTE qué se va a ejecutar antes de
        // que el Runner toque Gradle (ver JobExecutor.buildCommand — nunca cae a
        // tests.RunAllTests cuando esto está presente).
        if (request.getRecordedCases() != null && !request.getRecordedCases().isEmpty()) {
            exec.setSuiteId(request.getSuiteId());
            exec.setRecordedCases(request.getRecordedCases());
            List<String> testCaseIds = request.getRecordedCases().stream()
                    .map(RunRequest.RecordedCase::getTestCaseId).toList();
            log.info("[SuiteExecution] suiteId={} suiteName={} testCaseIds={}",
                    request.getSuiteId(), request.getSuite(), testCaseIds);
            int n = 1;
            for (RunRequest.RecordedCase rc : request.getRecordedCases()) {
                log.info("[SuiteExecution] Resolved testCases: {}. id={} name={}",
                        n++, rc.getTestCaseId(), rc.getCaseName());
            }
        }

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
