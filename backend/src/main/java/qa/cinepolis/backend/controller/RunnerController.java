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

    /** Pending commands per runner: runnerId → "START" | "STOP" | "RESTART" */
    private final ConcurrentHashMap<String, String> pendingCommands = new ConcurrentHashMap<>();

    public RunnerController(RunnerStore runnerStore) {
        this.runnerStore = runnerStore;
    }

    /** GET /api/runners — list all registered runners (marks stale ones OFFLINE first) */
    @GetMapping
    public List<Runner> getAllRunners() {
        runnerStore.markOfflineIfStale();
        return runnerStore.findAll();
    }

    /** GET /api/runners/status — summary counts + full runner list */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        runnerStore.markOfflineIfStale();
        List<Runner> all = runnerStore.findAll();
        long online  = all.stream().filter(r -> r.getStatus() != RunnerStatus.OFFLINE).count();
        long busy    = all.stream().filter(r -> r.getStatus() == RunnerStatus.BUSY).count();
        return Map.of("total", all.size(), "online", online, "busy", busy, "runners", all);
    }

    /**
     * POST /api/runners — runner registers itself or sends a heartbeat.
     * Body: { runnerId, platform, version, status, devices[], timestamp }
     * Response header X-Runner-Command carries any pending command (START/STOP/RESTART).
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
        update.setVersion((String) payload.get("version"));

        String statusStr = (String) payload.getOrDefault("status", "ONLINE");
        try { update.setStatus(RunnerStatus.valueOf(statusStr.toUpperCase())); }
        catch (Exception e) { update.setStatus(RunnerStatus.ONLINE); }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devRaw = (List<Map<String, Object>>) payload.get("devices");
        if (devRaw != null) {
            List<RunnerDevice> devices = devRaw.stream().map(d -> {
                RunnerDevice rd = new RunnerDevice();
                rd.setDeviceId((String) d.get("deviceId"));
                rd.setDeviceName((String) d.get("deviceName"));
                rd.setPlatform((String) d.get("platform"));
                rd.setStatus((String) d.getOrDefault("status", "available"));
                return rd;
            }).toList();
            update.setDevices(devices);
        }

        Runner saved = runnerStore.upsert(update);

        String cmd = pendingCommands.remove(runnerId);
        if (cmd != null) {
            return ResponseEntity.ok()
                    .header("X-Runner-Command", cmd)
                    .body(saved);
        }
        return ResponseEntity.ok(saved);
    }

    /** GET /api/runners/{id}/command — runner polls for a pending command (204 if none) */
    @GetMapping("/{id}/command")
    public ResponseEntity<Map<String, String>> getCommand(@PathVariable String id) {
        String cmd = pendingCommands.remove(id);
        if (cmd == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(Map.of("command", cmd));
    }

    /** POST /api/runners/start — enqueue START command for one or all runners */
    @PostMapping("/start")
    public Map<String, String> startRunner(@RequestBody(required = false) Map<String, Object> payload) {
        enqueueCommand(payload, "START");
        return Map.of("result", "ok", "command", "START");
    }

    /** POST /api/runners/stop — enqueue STOP command for one or all runners */
    @PostMapping("/stop")
    public Map<String, String> stopRunner(@RequestBody(required = false) Map<String, Object> payload) {
        enqueueCommand(payload, "STOP");
        return Map.of("result", "ok", "command", "STOP");
    }

    /** POST /api/runners/restart — enqueue RESTART command for one or all runners */
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
