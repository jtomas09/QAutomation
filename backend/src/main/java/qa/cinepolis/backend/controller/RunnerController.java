package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Runner;
import qa.cinepolis.backend.model.RunnerDevice;
import qa.cinepolis.backend.model.RunnerStatus;
import qa.cinepolis.backend.store.RunnerStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/runners")
public class RunnerController {

    private final RunnerStore runnerStore;

    /** Pending dashboard commands per runner: runnerId → "START" | "STOP" | "RESTART" */
    private final ConcurrentHashMap<String, String> pendingCommands = new ConcurrentHashMap<>();

    public RunnerController(RunnerStore runnerStore) {
        this.runnerStore = runnerStore;
    }

    /** GET /api/runners — all registered runners */
    @GetMapping
    public List<Runner> getAllRunners() {
        runnerStore.markOfflineIfStale();
        return runnerStore.findAll();
    }

    /** GET /api/runners/status — summary counts + list */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        runnerStore.markOfflineIfStale();
        List<Runner> all = runnerStore.findAll();
        long online   = all.stream().filter(r -> r.getStatus() == RunnerStatus.ONLINE || r.getStatus() == RunnerStatus.BUSY).count();
        long degraded = all.stream().filter(r -> r.getStatus() == RunnerStatus.DEGRADED).count();
        long busy     = all.stream().filter(r -> r.getStatus() == RunnerStatus.BUSY).count();
        long android  = all.stream().filter(r -> Boolean.TRUE.equals(r.getAndroidSupported())).count();
        long ios      = all.stream().filter(r -> Boolean.TRUE.equals(r.getIosSupported())).count();
        return Map.of(
                "total",    all.size(),
                "online",   online,
                "degraded", degraded,
                "busy",     busy,
                "android",  android,
                "ios",      ios,
                "runners",  all);
    }

    /**
     * POST /api/runners — Universal Runner heartbeat / registration.
     * Payload: { runnerId, platform, version, status,
     *            os, hostname, androidSupported, iosSupported,
     *            devices[], timestamp }
     * Response header X-Runner-Command carries a pending command if any.
     */
    @PostMapping
    public ResponseEntity<Runner> registerOrHeartbeat(@RequestBody Map<String, Object> payload) {
        String runnerId = (String) payload.get("runnerId");
        if (runnerId == null || runnerId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Runner update = new Runner();
        update.setRunnerId(runnerId);
        update.setPlatform((String) payload.get("platform"));
        update.setVersion((String)  payload.get("version"));

        // Status
        String statusStr = (String) payload.getOrDefault("status", "ONLINE");
        try { update.setStatus(RunnerStatus.valueOf(statusStr.toUpperCase())); }
        catch (Exception e) { update.setStatus(RunnerStatus.ONLINE); }

        // Universal Runner capability fields
        update.setOs((String) payload.get("os"));
        update.setHostname((String) payload.get("hostname"));
        if (payload.containsKey("androidSupported")) {
            update.setAndroidSupported(Boolean.TRUE.equals(payload.get("androidSupported")));
        }
        if (payload.containsKey("iosSupported")) {
            update.setIosSupported(Boolean.TRUE.equals(payload.get("iosSupported")));
        }

        // ADB / platform-tools diagnostics
        if (payload.containsKey("adbPath"))   update.setAdbPath((String) payload.get("adbPath"));
        if (payload.containsKey("adbVersion")) update.setAdbVersion((String) payload.get("adbVersion"));
        if (payload.containsKey("adbExists")) update.setAdbExists(Boolean.TRUE.equals(payload.get("adbExists")));
        if (payload.containsKey("adbOk"))     update.setAdbOk(Boolean.TRUE.equals(payload.get("adbOk")));
        if (payload.containsKey("devicesFound")) {
            Object df = payload.get("devicesFound");
            if (df instanceof Number n) update.setDevicesFound(n.intValue());
        }
        // platformToolsInstalled = adbExists AND adbOk
        update.setPlatformToolsInstalled(
                Boolean.TRUE.equals(update.getAdbExists()) && Boolean.TRUE.equals(update.getAdbOk()));

        // Device list (from heartbeat)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devRaw = (List<Map<String, Object>>) payload.get("devices");
        if (devRaw != null) {
            List<RunnerDevice> devices = devRaw.stream().map(d -> {
                RunnerDevice rd = new RunnerDevice();
                rd.setDeviceId((String)   d.getOrDefault("udid", d.getOrDefault("deviceId", "")));
                rd.setDeviceName((String) d.getOrDefault("deviceName", ""));
                rd.setPlatform((String)   d.getOrDefault("platform", ""));
                rd.setStatus((String)     d.getOrDefault("status", "available"));
                return rd;
            }).toList();
            update.setDevices(devices);
        }

        Runner saved = runnerStore.upsert(update);

        String cmd = pendingCommands.remove(runnerId);
        return cmd != null
                ? ResponseEntity.ok().header("X-Runner-Command", cmd).body(saved)
                : ResponseEntity.ok(saved);
    }

    /**
     * POST /api/runners/heartbeat — alias for POST /api/runners.
     * Allows runners to use either endpoint.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Runner> heartbeat(@RequestBody Map<String, Object> payload) {
        return registerOrHeartbeat(payload);
    }

    /** GET /api/runners/{id}/command — runner polls for pending command (204 if none) */
    @GetMapping("/{id}/command")
    public ResponseEntity<Map<String, String>> getCommand(@PathVariable String id) {
        String cmd = pendingCommands.remove(id);
        if (cmd == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(Map.of("command", cmd));
    }

    /** POST /api/runners/start */
    @PostMapping("/start")
    public Map<String, String> startRunner(@RequestBody(required = false) Map<String, Object> payload) {
        enqueueCommand(payload, "START");
        return Map.of("result", "ok", "command", "START");
    }

    /** POST /api/runners/stop */
    @PostMapping("/stop")
    public Map<String, String> stopRunner(@RequestBody(required = false) Map<String, Object> payload) {
        enqueueCommand(payload, "STOP");
        return Map.of("result", "ok", "command", "STOP");
    }

    /** POST /api/runners/restart */
    @PostMapping("/restart")
    public Map<String, String> restartRunner(@RequestBody(required = false) Map<String, Object> payload) {
        enqueueCommand(payload, "RESTART");
        return Map.of("result", "ok", "command", "RESTART");
    }

    /** GET /api/runners/devices — all devices aggregated across all runners */
    @GetMapping("/devices")
    public List<Map<String, Object>> getAllDevices() {
        runnerStore.markOfflineIfStale();
        List<Map<String, Object>> result = new ArrayList<>();
        runnerStore.findAll().forEach(runner ->
            runner.getDevices().forEach(device -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("deviceId",   device.getDeviceId());
                d.put("deviceName", device.getDeviceName());
                d.put("platform",   device.getPlatform());
                d.put("status",     device.getStatus());
                d.put("runnerId",   runner.getRunnerId());
                result.add(d);
            })
        );
        return result;
    }

    private void enqueueCommand(Map<String, Object> payload, String command) {
        String runnerId = payload != null ? (String) payload.get("runnerId") : null;
        if (runnerId == null || runnerId.isBlank()) {
            runnerStore.findAll().forEach(r -> pendingCommands.put(r.getRunnerId(), command));
        } else {
            pendingCommands.put(runnerId, command);
        }
    }
}
