package qa.cinepolis.backend.controller;

import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.store.DeviceStore;
import qa.cinepolis.backend.store.RunnerStore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Platform metadata consumed by the Dashboard.
 *
 * GET /api/system/info         → infraState, versions
 * GET /api/system/update-check → version comparison
 *
 * File downloads are handled by RunnerDownloadController (/api/runner/download).
 */
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    private static final String RUNNER_VERSION  = "2.2.0";
    private static final String BACKEND_VERSION = "2.2.0";
    private static final String RELEASE_DATE    = "2025-06-17";

    private final RunnerStore runnerStore;
    private final DeviceStore deviceStore;

    public SystemInfoController(RunnerStore runnerStore, DeviceStore deviceStore) {
        this.runnerStore = runnerStore;
        this.deviceStore = deviceStore;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        int  runnerCount   = runnerStore.findAll().size();
        int  deviceCount   = deviceStore.findAll().size();
        long onlineRunners = runnerStore.findAll().stream()
                .filter(r -> r.getStatus() != null && !"OFFLINE".equals(r.getStatus().name()))
                .count();

        String infraState;
        if (runnerCount == 0)      infraState = "not_installed";
        else if (onlineRunners == 0) infraState = "offline";
        else if (deviceCount == 0) infraState = "scanning";
        else                       infraState = "ready";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("platform",       "Automation QA");
        resp.put("runnerVersion",  RUNNER_VERSION);
        resp.put("backendVersion", BACKEND_VERSION);
        resp.put("releaseDate",    RELEASE_DATE);
        resp.put("timestamp",      Instant.now().toString());
        resp.put("infraState",     infraState);
        resp.put("totalRunners",   runnerCount);
        resp.put("onlineRunners",  (int) onlineRunners);
        resp.put("totalDevices",   deviceCount);

        resp.put("download", Map.of(
                "windows", "/api/runner/download/windows",
                "macos",   "/api/runner/download/macos",
                "linux",   "/api/runner/download/linux"
        ));

        return resp;
    }

    @GetMapping("/update-check")
    public Map<String, Object> updateCheck(@RequestParam(defaultValue = "0.0.0") String currentVersion) {
        boolean hasUpdate = compareVersions(RUNNER_VERSION, currentVersion) > 0;
        return Map.of(
                "currentVersion", currentVersion,
                "latestVersion",  RUNNER_VERSION,
                "hasUpdate",      hasUpdate,
                "releaseDate",    RELEASE_DATE,
                "changelog",      hasUpdate
                        ? "Mejoras de rendimiento, soporte Universal Runner, auto-detección de OS."
                        : "Ya tienes la versión más reciente."
        );
    }

    private int compareVersions(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }
}
