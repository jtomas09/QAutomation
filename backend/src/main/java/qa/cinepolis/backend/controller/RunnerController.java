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
        if (payload.containsKey("computerName")) update.setComputerName((String) payload.get("computerName"));
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

        // Component telemetry (v4.0)
        if (payload.containsKey("jreInstalled"))    update.setJreInstalled(Boolean.TRUE.equals(payload.get("jreInstalled")));
        if (payload.containsKey("jreVersion"))      update.setJreVersion((String) payload.get("jreVersion"));
        if (payload.containsKey("nodeInstalled"))   update.setNodeInstalled(Boolean.TRUE.equals(payload.get("nodeInstalled")));
        if (payload.containsKey("nodeVersion"))     update.setNodeVersion((String) payload.get("nodeVersion"));
        if (payload.containsKey("appiumInstalled")) update.setAppiumInstalled(Boolean.TRUE.equals(payload.get("appiumInstalled")));
        if (payload.containsKey("appiumVersion"))   update.setAppiumVersion((String) payload.get("appiumVersion"));
        if (payload.containsKey("xcodeInstalled"))  update.setXcodeInstalled(Boolean.TRUE.equals(payload.get("xcodeInstalled")));
        if (payload.containsKey("xcodeVersion"))    update.setXcodeVersion((String) payload.get("xcodeVersion"));

        // Host Status (v6 — HostStatusManager)
        if (payload.containsKey("hostStatus"))  update.setHostStatus((String) payload.get("hostStatus"));
        if (payload.containsKey("iosReady"))    update.setIosReady(Boolean.TRUE.equals(payload.get("iosReady")));
        // Device Stream Service (Phase 10 — Live Preview)
        if (payload.containsKey("streamUrl"))   update.setStreamUrl((String) payload.get("streamUrl"));

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

    /** GET /api/runners/{id}/diagnostics — full component health for a specific runner */
    @GetMapping("/{id}/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics(@PathVariable String id) {
        return runnerStore.findById(id)
                .map(runner -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("runnerId",  runner.getRunnerId());
                    d.put("status",    runner.getStatus());
                    d.put("lastSeen",  runner.getLastSeen());
                    d.put("hostname",  runner.getHostname());
                    d.put("os",        runner.getOs());
                    d.put("version",   runner.getVersion());

                    Map<String, Object> adb = new LinkedHashMap<>();
                    adb.put("path",    runner.getAdbPath());
                    adb.put("version", runner.getAdbVersion());
                    adb.put("exists",  runner.getAdbExists());
                    adb.put("ok",      runner.getAdbOk());
                    adb.put("devicesFound", runner.getDevicesFound());
                    d.put("adb", adb);

                    Map<String, Object> components = new LinkedHashMap<>();
                    components.put("jre",    Map.of(
                            "installed", Boolean.TRUE.equals(runner.getJreInstalled()),
                            "version",   safe(runner.getJreVersion())));
                    components.put("node",   Map.of(
                            "installed", Boolean.TRUE.equals(runner.getNodeInstalled()),
                            "version",   safe(runner.getNodeVersion())));
                    components.put("appium", Map.of(
                            "installed", Boolean.TRUE.equals(runner.getAppiumInstalled()),
                            "version",   safe(runner.getAppiumVersion())));
                    components.put("xcode",  Map.of(
                            "installed", Boolean.TRUE.equals(runner.getXcodeInstalled()),
                            "version",   safe(runner.getXcodeVersion())));
                    d.put("components", components);
                    d.put("devices", runner.getDevices());
                    return ResponseEntity.ok(d);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static String safe(String v) { return v != null ? v : "unavailable"; }

    private void enqueueCommand(Map<String, Object> payload, String command) {
        String runnerId = payload != null ? (String) payload.get("runnerId") : null;
        if (runnerId == null || runnerId.isBlank()) {
            runnerStore.findAll().forEach(r -> pendingCommands.put(r.getRunnerId(), command));
        } else {
            pendingCommands.put(runnerId, command);
        }
    }
}
