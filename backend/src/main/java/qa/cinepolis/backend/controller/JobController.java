package qa.cinepolis.backend.controller;

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

        // ── Dynamic device selection ──────────────────────────────────────────
        // Infer platform from device name hint (e.g. "iPhone" → IOS)
        String preferredName = exec.getDevice();
        String platform      = inferPlatform(preferredName, runnerId);

        Optional<Device> deviceOpt = deviceStore.claimDevice(preferredName, platform, exec.getExecutionId());
        deviceOpt.ifPresent(d -> {
            exec.setDeviceUdid(d.getUdid());
            exec.setDevicePlatformVersion(d.getPlatformVersion());
            // Override device name with the auto-discovered canonical name
            exec.setDevice(d.getDeviceName() != null ? d.getDeviceName() : exec.getDevice());
            exec.setAssignedRunnerId(runnerId);
        });

        Map<String, Object> job = new java.util.LinkedHashMap<>();
        job.put("executionId",        exec.getExecutionId());
        job.put("suite",              exec.getSuite());
        job.put("env",                exec.getEnv());
        job.put("device",             exec.getDevice());
        job.put("country",            exec.getCountry());
        job.put("videoEnabled",       exec.isVideoEnabled());
        job.put("testClass",          exec.getTestClass());
        job.put("sendMail",           reportEmailStore.isEnabled());
        job.put("reportEmails",       reportEmailStore.getMailTo());
        // Dynamic capabilities — populated when a device was auto-selected
        job.put("udid",               exec.getDeviceUdid() != null ? exec.getDeviceUdid() : "");
        job.put("platformVersion",    exec.getDevicePlatformVersion() != null ? exec.getDevicePlatformVersion() : "");
        job.put("deviceName",         exec.getDevice());
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
