package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.store.ReportEmailStore;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingsController {

    private final ReportEmailStore store;

    public SettingsController(ReportEmailStore store) {
        this.store = store;
    }

    @GetMapping("/report-emails")
    public Map<String, Object> getReportEmails() {
        return Map.of("enabled", store.isEnabled(), "emails", store.getEmails());
    }

    @PutMapping("/report-emails")
    public ResponseEntity<Map<String, String>> setReportEmails(@RequestBody ReportEmailsRequest req) {
        store.setEnabled(req.enabled());
        store.setEmails(req.emails() != null ? req.emails() : List.of());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    record ReportEmailsRequest(boolean enabled, List<String> emails) {}
}
