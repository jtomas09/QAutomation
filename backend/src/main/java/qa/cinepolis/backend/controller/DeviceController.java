package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.Device;
import qa.cinepolis.backend.model.DeviceStatus;
import qa.cinepolis.backend.store.DeviceStore;

import java.util.*;

/**
 * Device Farm API — all device information originates from Runner Agent discovery.
 * No manual env-var configuration required.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceStore deviceStore;

    public DeviceController(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    /** GET /api/devices — all registered devices (stale ones marked OFFLINE first) */
    @GetMapping
    public List<Device> getAllDevices() {
        deviceStore.markOfflineIfStale();
        return deviceStore.findAll();
    }

    /** GET /api/devices/available — only AVAILABLE devices */
    @GetMapping("/available")
    public List<Device> getAvailableDevices(
            @RequestParam(required = false) String platform) {
        deviceStore.markOfflineIfStale();
        return platform != null && !platform.isBlank()
                ? deviceStore.findAvailableForPlatform(platform)
                : deviceStore.findAvailable();
    }

    /** GET /api/devices/{udid} — device by UDID */
    @GetMapping("/{udid}")
    public ResponseEntity<Device> getDevice(@PathVariable String udid) {
        return deviceStore.findByUdid(udid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/devices/register — bulk-register devices from a Runner Agent.
     * Body: { runnerId, devices: [ {udid, deviceName, model, manufacturer, platform, platformVersion} ] }
     * Runner calls this on startup and every 30s during heartbeat.
     */
    @PostMapping("/register")
    public Map<String, Object> registerDevices(@RequestBody Map<String, Object> payload) {
        String runnerId = (String) payload.get("runnerId");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawDevices = (List<Map<String, Object>>) payload.get("devices");
        if (rawDevices == null) rawDevices = Collections.emptyList();

        int registered = 0;
        for (Map<String, Object> raw : rawDevices) {
            Device d = mapToDevice(raw, runnerId);
            deviceStore.upsert(d);
            registered++;
        }

        return Map.of("result", "ok", "registered", registered);
    }

    /**
     * POST /api/devices/heartbeat — runner confirms devices are still alive.
     * Body: { runnerId, udids: ["R5CX...", "RF8N..."] }
     * Updates lastSeen for listed UDIDs without changing status.
     */
    @PostMapping("/heartbeat")
    public Map<String, String> heartbeat(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<String> udids = (List<String>) payload.getOrDefault("udids", Collections.emptyList());
        udids.forEach(udid -> deviceStore.findByUdid(udid).ifPresent(d -> {
            d.setLastSeen(java.time.Instant.now());
            if (d.getStatus() == DeviceStatus.OFFLINE) d.setStatus(DeviceStatus.AVAILABLE);
        }));
        return Map.of("result", "ok");
    }

    /**
     * PUT /api/devices/status — update a single device status.
     * Body: { udid, status: "AVAILABLE" | "BUSY" | "OFFLINE" | "MAINTENANCE" }
     */
    @PutMapping("/status")
    public ResponseEntity<Map<String, String>> updateStatus(@RequestBody Map<String, String> payload) {
        String udid      = payload.get("udid");
        String statusStr = payload.get("status");
        if (udid == null || statusStr == null) return ResponseEntity.badRequest().build();
        try {
            DeviceStatus status = DeviceStatus.valueOf(statusStr.toUpperCase());
            deviceStore.updateStatus(udid, status);
            return ResponseEntity.ok(Map.of("result", "ok"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + statusStr));
        }
    }

    /** DELETE /api/devices/{udid} — remove a device from the pool */
    @DeleteMapping("/{udid}")
    public Map<String, String> removeDevice(@PathVariable String udid) {
        deviceStore.remove(udid);
        return Map.of("result", "ok");
    }

    private Device mapToDevice(Map<String, Object> raw, String runnerId) {
        Device d = new Device();
        d.setUdid((String) raw.get("udid"));
        d.setDeviceName((String) raw.get("deviceName"));
        d.setModel((String) raw.get("model"));
        d.setManufacturer((String) raw.get("manufacturer"));
        d.setPlatform(((String) raw.getOrDefault("platform", "ANDROID")).toUpperCase());
        d.setPlatformVersion((String) raw.get("platformVersion"));
        d.setRunnerId(runnerId);
        String statusStr = (String) raw.getOrDefault("status", "AVAILABLE");
        try { d.setStatus(DeviceStatus.valueOf(statusStr.toUpperCase())); }
        catch (Exception e) { d.setStatus(DeviceStatus.AVAILABLE); }
        return d;
    }
}
