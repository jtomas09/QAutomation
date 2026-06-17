package qa.cinepolis.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import qa.cinepolis.backend.store.DeviceStore;
import qa.cinepolis.backend.store.RunnerStore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DeviceStore deviceStore;
    private final RunnerStore runnerStore;

    public HealthController(DeviceStore deviceStore, RunnerStore runnerStore) {
        this.deviceStore = deviceStore;
        this.runnerStore = runnerStore;
    }

    @GetMapping("/")
    public String home() {
        return "QAutomation Backend v2 Online";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * GET /api/diagnostics — quick connectivity check for the Device Farm.
     * Returns runner count, device count, and last heartbeat time.
     * Useful for debugging when devices don't appear in the dashboard.
     */
    @GetMapping("/api/diagnostics")
    public Map<String, Object> diagnostics() {
        runnerStore.markOfflineIfStale();
        deviceStore.markOfflineIfStale();

        long runnersTotal   = runnerStore.findAll().size();
        long runnersOnline  = runnerStore.findAll().stream()
                .filter(r -> r.getStatus() != qa.cinepolis.backend.model.RunnerStatus.OFFLINE)
                .count();
        long devicesTotal   = deviceStore.findAll().size();
        long devicesAvail   = deviceStore.findAvailable().size();
        long devicesBusy    = deviceStore.findAll().stream()
                .filter(d -> d.getStatus() == qa.cinepolis.backend.model.DeviceStatus.BUSY)
                .count();
        long devicesOffline = deviceStore.findAll().stream()
                .filter(d -> d.getStatus() == qa.cinepolis.backend.model.DeviceStatus.OFFLINE)
                .count();

        // Last heartbeat across all runners
        Instant lastHeartbeat = runnerStore.findAll().stream()
                .map(r -> r.getLastSeen())
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",          "OK");
        result.put("timestamp",       Instant.now().toString());
        result.put("runners",         Map.of(
                "total",  runnersTotal,
                "online", runnersOnline,
                "list",   runnerStore.findAll().stream()
                        .map(r -> Map.of(
                                "id",       r.getRunnerId(),
                                "status",   r.getStatus() != null ? r.getStatus().name() : "UNKNOWN",
                                "platform", r.getPlatform() != null ? r.getPlatform() : "",
                                "lastSeen", r.getLastSeen() != null ? r.getLastSeen().toString() : "never",
                                "devices",  r.getDevices() != null ? r.getDevices().size() : 0
                        ))
                        .toList()
        ));
        result.put("devices",         Map.of(
                "total",    devicesTotal,
                "available", devicesAvail,
                "busy",     devicesBusy,
                "offline",  devicesOffline
        ));
        result.put("lastHeartbeat",   lastHeartbeat != null ? lastHeartbeat.toString() : "never");
        result.put("hint",
                runnersOnline == 0
                    ? "Sin runners activos. Inicia start-runner.bat (Windows) o start-runner.sh (Mac/Linux)."
                    : devicesTotal == 0
                        ? "Runner conectado pero sin dispositivos. Conecta un dispositivo USB y acepta el permiso de depuracion."
                        : "OK - " + devicesAvail + " disponibles, " + devicesBusy + " en uso.");
        return result;
    }
}
