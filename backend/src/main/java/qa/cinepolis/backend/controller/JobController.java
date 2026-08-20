package qa.cinepolis.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Device;
import qa.cinepolis.backend.model.DeviceAppConfig;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.JobStatusUpdate;
import qa.cinepolis.backend.service.ExecutionService;
import qa.cinepolis.backend.store.DeviceAppConfigStore;
import qa.cinepolis.backend.store.DeviceStore;
import qa.cinepolis.backend.store.ReportEmailStore;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final ExecutionService    execService;
    private final ReportEmailStore    reportEmailStore;
    private final DeviceStore         deviceStore;
    private final DeviceAppConfigStore appConfigStore;

    public JobController(ExecutionService execService, ReportEmailStore reportEmailStore,
                         DeviceStore deviceStore, DeviceAppConfigStore appConfigStore) {
        this.execService      = execService;
        this.reportEmailStore = reportEmailStore;
        this.deviceStore      = deviceStore;
        this.appConfigStore   = appConfigStore;
    }

    /**
     * GET /api/jobs/next
     * Runner polls this to claim the next QUEUED job.
     * Dynamically selects the best AVAILABLE device from the DeviceStore.
     * Returns 204 when the queue is empty.
     */
    @GetMapping("/next")
    public ResponseEntity<?> getNextJob(
            @RequestHeader(value = "X-Runner-Id", required = false) String runnerId) {
        execService.recordRunnerPing();

        Optional<Execution> opt = execService.claimNextPending();
        if (opt.isEmpty()) return ResponseEntity.noContent().build();

        Execution exec = opt.get();

        // ── Device selection — UDID-first, no implicit fallback ──────────────
        String requestedDevice = exec.getDevice();   // UDID sent by frontend
        String platform        = inferPlatform(requestedDevice, runnerId);

        log.info("[JobController] DISPOSITIVO CONFIGURADO EN EL RUN: {} | executionId={}",
                requestedDevice, exec.getExecutionId());

        Optional<Device> deviceOpt = deviceStore.claimDevice(requestedDevice, platform, exec.getExecutionId());

        // Problema 5 — carrera: el dispositivo estaba AVAILABLE cuando se creó la
        // Execution (ver RunController), pero se desconectó antes de este despacho.
        // Antes: se construía y devolvía igual un job con udid="" (HTTP 200), y el
        // Runner lo descubría tarde. Ahora: se marca la Execution como FAILED con
        // motivo, y se responde 204 — el Runner nunca ve un job con dispositivo vacío,
        // simplemente sigue con su siguiente poll.
        if (deviceOpt.isEmpty()) {
            String reason = "Dispositivo desconectado antes de poder ejecutarse: " + requestedDevice;
            log.warn("[JobController] DISPOSITIVO NO DISPONIBLE — marcando ejecución como FAILED. " +
                    "'{}' no encontrado en Device Farm. executionId={}",
                    requestedDevice, exec.getExecutionId());
            execService.failWithReason(exec.getExecutionId(), reason);
            return ResponseEntity.noContent().build();
        }

        Device d = deviceOpt.get();
        exec.setDeviceUdid(d.getUdid());
        exec.setDevicePlatformVersion(d.getPlatformVersion());
        exec.setDevice(d.getDeviceName() != null ? d.getDeviceName() : requestedDevice);
        exec.setAssignedRunnerId(runnerId);
        String assignedPlatform = d.getPlatform() != null ? d.getPlatform() : "";

        log.info("[JobController] DISPOSITIVO ASIGNADO: {} / {} / {}",
                exec.getDevice(), assignedPlatform, d.getUdid());

        Map<String, Object> job = new java.util.LinkedHashMap<>();
        job.put("executionId",     exec.getExecutionId());
        job.put("suite",           exec.getSuite());
        job.put("env",             exec.getEnv());
        job.put("device",          exec.getDevice());
        job.put("country",         exec.getCountry());
        job.put("videoEnabled",    exec.isVideoEnabled());
        job.put("testClass",       exec.getTestClass());
        job.put("sendMail",        reportEmailStore.isEnabled());
        job.put("reportEmails",    reportEmailStore.getMailTo());
        job.put("udid",            exec.getDeviceUdid()          != null ? exec.getDeviceUdid()          : "");
        job.put("platformVersion", exec.getDevicePlatformVersion() != null ? exec.getDevicePlatformVersion() : "");
        job.put("deviceName",      exec.getDevice());
        job.put("platform",        assignedPlatform);

        // Caso grabado en Record Studio (ver RunController/RunRequest.RecordedCase)
        // — presente solo cuando esta ejecución viene de Suites→Ejecutar sobre un
        // caso grabado. El Runner (JobExecutor) usa estos campos para escribir y
        // compilar el test dinámico en vez de resolver la suite vía SUITE_MAP.
        if (exec.getRecordedCaseClassName() != null) {
            job.put("recordedCaseClassName", exec.getRecordedCaseClassName());
            job.put("recordedCaseSource",    exec.getRecordedCaseSource());
            job.put("recordedCaseName",      exec.getRecordedCaseName());
        }

        // Suite grabada en Record Studio (ver Execution.recordedCases) — el Runner
        // (JobDto.recordedCases) le da prioridad absoluta sobre SUITE_MAP.
        if (exec.getRecordedCases() != null && !exec.getRecordedCases().isEmpty()) {
            job.put("suiteId",       exec.getSuiteId());
            job.put("recordedCases", exec.getRecordedCases());
        }

        // Inject per-device app config if present
        String lookupUdid = exec.getDeviceUdid() != null ? exec.getDeviceUdid() : requestedDevice;
        appConfigStore.get(lookupUdid).ifPresent(cfg -> {
            job.put("appPackage", cfg.getAppPackage() != null ? cfg.getAppPackage() : "");
            job.put("bundleId",   cfg.getBundleId()   != null ? cfg.getBundleId()   : "");
            job.put("appMode",    cfg.getAppMode()    != null ? cfg.getAppMode()    : "INSTALLED");
        });

        return ResponseEntity.ok(job);
    }

    /**
     * POST /api/jobs/ping
     * Runner sends this while executing a job so the heartbeat stays alive.
     */
    @PostMapping("/ping")
    public Map<String, String> ping() {
        execService.recordRunnerPing();
        return Map.of("result", "ok");
    }

    /**
     * POST /api/jobs/{id}/status
     * Runner updates job lifecycle status (RUNNING, COMPLETED, FAILED, ABORTED).
     */
    @PostMapping("/{id}/status")
    public Map<String, String> updateStatus(@PathVariable String id,
                                            @RequestBody  JobStatusUpdate update) {
        execService.updateStatus(id, update.status());
        return Map.of("result", "ok");
    }

    private String inferPlatform(String deviceName, String runnerId) {
        if (deviceName != null) {
            String lower = deviceName.toLowerCase();
            if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) return "IOS";
            if (lower.contains("galaxy") || lower.contains("pixel") || lower.contains("android")) return "ANDROID";
        }
        // Fall back to runner's platform if known
        return null;
    }
}
