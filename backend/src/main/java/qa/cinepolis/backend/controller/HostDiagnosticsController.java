package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Runner;
import qa.cinepolis.backend.store.RunnerStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Host Diagnostics endpoint — flat, self-contained summary per host (runner).
 *
 * Unlike GET /api/runners/{id}/diagnostics (nested, ADB-focused),
 * this endpoint returns a flat snapshot suitable for dashboards and monitoring:
 *
 *   GET /api/hosts/{hostId}/diagnostics
 *
 * Response:
 * {
 *   "hostId":          "mac-mini-01",
 *   "status":          "ONLINE",
 *   "jreInstalled":    true,
 *   "nodeInstalled":   true,
 *   "appiumInstalled": true,
 *   "adbInstalled":    true,
 *   "xcodeInstalled":  true,
 *   "iosReady":        true,
 *   "devicesDetected": 3,
 *   "lastHeartbeat":   "2026-06-22T12:00:00Z"
 * }
 */
@RestController
@RequestMapping("/api/hosts")
public class HostDiagnosticsController {

    private final RunnerStore runnerStore;

    public HostDiagnosticsController(RunnerStore runnerStore) {
        this.runnerStore = runnerStore;
    }

    @GetMapping("/{hostId}/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics(@PathVariable String hostId) {
        return runnerStore.findById(hostId)
                .map(runner -> {
                    Map<String, Object> d = new LinkedHashMap<>();

                    d.put("hostId",          runner.getRunnerId());
                    d.put("status",          runner.getStatus() != null
                                             ? runner.getStatus().name() : "UNKNOWN");
                    d.put("jreInstalled",    bool(runner.getJreInstalled()));
                    d.put("nodeInstalled",   bool(runner.getNodeInstalled()));
                    d.put("appiumInstalled", bool(runner.getAppiumInstalled()));
                    d.put("adbInstalled",    bool(runner.getAdbOk()));
                    d.put("xcodeInstalled",  bool(runner.getXcodeInstalled()));

                    // iosReady: explicit field (CAMBIO 2) or derived from xcode + iosSupported
                    boolean iosReady = runner.getIosReady() != null
                            ? runner.getIosReady()
                            : (bool(runner.getXcodeInstalled()) && bool(runner.getIosSupported()));
                    d.put("iosReady", iosReady);

                    d.put("devicesDetected", runner.getDevices() != null
                                             ? runner.getDevices().size() : 0);
                    d.put("lastHeartbeat",   runner.getLastSeen());

                    return ResponseEntity.ok(d);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static boolean bool(Boolean v) { return Boolean.TRUE.equals(v); }
}
