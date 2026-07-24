package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.store.ExecutionDeviceStore;
import qa.cinepolis.backend.store.ProjectPathStore;
import qa.cinepolis.backend.store.ReportEmailStore;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingsController {

    private final ReportEmailStore     emailStore;
    private final ProjectPathStore     pathStore;
    private final ExecutionDeviceStore deviceStore;

    public SettingsController(ReportEmailStore emailStore,
                              ProjectPathStore pathStore,
                              ExecutionDeviceStore deviceStore) {
        this.emailStore  = emailStore;
        this.pathStore   = pathStore;
        this.deviceStore = deviceStore;
    }

    // ── Report emails ────────────────────────────────────────────────────────

    @GetMapping("/report-emails")
    public Map<String, Object> getReportEmails() {
        return Map.of("enabled", emailStore.isEnabled(), "emails", emailStore.getEmails());
    }

    @PutMapping("/report-emails")
    public ResponseEntity<Map<String, String>> setReportEmails(@RequestBody ReportEmailsRequest req) {
        emailStore.setEnabled(req.enabled());
        emailStore.setEmails(req.emails() != null ? req.emails() : List.of());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ── Project path ──────────────────────────────────────────────────────────

    @GetMapping("/project-path")
    public Map<String, Object> getProjectPath() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", pathStore.getProjectPath());
        if (pathStore.hasValidation()) {
            Map<String, Object> validation = new LinkedHashMap<>();
            validation.put("gradlew",        pathStore.isValidGradlew());
            validation.put("buildGradle",    pathStore.isValidBuildGradle());
            validation.put("settingsGradle", pathStore.isValidSettingsGradle());
            validation.put("valid",          pathStore.isValidProject());
            validation.put("checkedPath",    pathStore.getValidatedPath());
            validation.put("checkedAt",      pathStore.getValidatedAt());
            result.put("validation", validation);
        } else {
            result.put("validation", null);
        }
        return result;
    }

    @PostMapping("/project-path")
    public ResponseEntity<Map<String, String>> setProjectPath(@RequestBody ProjectPathRequest req) {
        pathStore.setProjectPath(req.path());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    /** Called by the Runner agent to report local validation results. */
    @PostMapping("/project-path/validation")
    public ResponseEntity<Map<String, String>> reportProjectValidation(
            @RequestBody ProjectValidationRequest req) {
        pathStore.setValidation(
                req.checkedPath(), req.gradlew(), req.buildGradle(),
                req.settingsGradle(), req.valid(), req.checkedAt());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ── Execution devices ─────────────────────────────────────────────────────

    @GetMapping("/execution-devices")
    public Map<String, Object> getExecutionDevices() {
        return Map.of(
                "devices",      deviceStore.getDeviceUdids(),
                "videoEnabled", deviceStore.isVideoEnabled());
    }

    @PostMapping("/execution-devices")
    public ResponseEntity<Map<String, String>> setExecutionDevices(
            @RequestBody ExecutionDevicesRequest req) {
        deviceStore.setDeviceUdids(req.devices() != null ? req.devices() : List.of());
        deviceStore.setVideoEnabled(req.videoEnabled());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ── Request records ───────────────────────────────────────────────────────

    record ReportEmailsRequest(boolean enabled, List<String> emails) {}

    record ProjectPathRequest(String path) {}

    record ProjectValidationRequest(
            String  checkedPath,
            boolean gradlew,
            boolean buildGradle,
            boolean settingsGradle,
            boolean valid,
            String  checkedAt) {}

    record ExecutionDevicesRequest(List<String> devices, boolean videoEnabled) {}
}
