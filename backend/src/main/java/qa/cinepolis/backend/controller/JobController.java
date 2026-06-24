package qa.cinepolis.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Device;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.JobStatusUpdate;
import qa.cinepolis.backend.service.ExecutionService;
import qa.cinepolis.backend.store.DeviceStore;
import qa.cinepolis.backend.store.ReportEmailStore;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final ExecutionService execService;
    private final ReportEmailStore reportEmailStore;
    private final DeviceStore      deviceStore;

    public JobController(ExecutionService execService, ReportEmailStore reportEmailStore,
                         DeviceStore deviceStore) {
        this.execService      = execService;
        this.reportEmailStore = reportEmailStore;
        this.deviceStore      = deviceStore;
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

        String assignedPlatform = "";
        if (deviceOpt.isPresent()) {
            Device d = deviceOpt.get();
            exec.setDeviceUdid(d.getUdid());
            exec.setDevicePlatformVersion(d.getPlatformVersion());
            exec.setDevice(d.getDeviceName() != null ? d.getDeviceName() : requestedDevice);
            exec.setAssignedRunnerId(runnerId);
            assignedPlatform = d.getPlatform() != null ? d.getPlatform() : "";

            log.info("[JobController] DISPOSITIVO ASIGNADO: {} / {} / {}",
                    exec.getDevice(), assignedPlatform, d.getUdid());
        } else {
            log.warn("[JobController] DISPOSITIVO NO DISPONIBLE: '{}' no encontrado en Device Farm. " +
                    "Verifica que el dispositivo esté conectado y el Runner activo. executionId={}",
                    requestedDevice, exec.getExecutionId());
        }

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
