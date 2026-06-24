package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.DeviceAppConfig;
import qa.cinepolis.backend.store.DeviceAppConfigStore;

import java.util.Map;

@RestController
@RequestMapping("/api/device-app-configs")
public class DeviceAppConfigController {

    private final DeviceAppConfigStore configStore;

    public DeviceAppConfigController(DeviceAppConfigStore configStore) {
        this.configStore = configStore;
    }

    /** GET /api/device-app-configs/{udid} — config for a specific device */
    @GetMapping("/{udid}")
    public ResponseEntity<DeviceAppConfig> getConfig(@PathVariable String udid) {
        return configStore.get(udid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** POST /api/device-app-configs/{udid} — save (create or update) device app config */
    @PostMapping("/{udid}")
    public ResponseEntity<DeviceAppConfig> saveConfig(@PathVariable String udid,
                                                      @RequestBody DeviceAppConfig config) {
        config.setDeviceId(udid);
        configStore.save(config);
        return ResponseEntity.ok(config);
    }

    /** GET /api/device-app-configs — all configs keyed by udid */
    @GetMapping
    public Map<String, DeviceAppConfig> getAllConfigs() {
        return configStore.asMap();
    }

    /** DELETE /api/device-app-configs/{udid} — remove config for a device */
    @DeleteMapping("/{udid}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String udid) {
        configStore.delete(udid);
        return ResponseEntity.noContent().build();
    }
}
