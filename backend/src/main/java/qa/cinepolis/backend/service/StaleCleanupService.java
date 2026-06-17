package qa.cinepolis.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import qa.cinepolis.backend.store.DeviceStore;
import qa.cinepolis.backend.store.RunnerStore;

/**
 * Periodically marks stale runners and devices as OFFLINE.
 * Runs every 30s so the Dashboard always reflects real connectivity
 * even when no GET /api/devices request triggers the on-demand check.
 */
@Service
public class StaleCleanupService {

    private final DeviceStore deviceStore;
    private final RunnerStore runnerStore;

    public StaleCleanupService(DeviceStore deviceStore, RunnerStore runnerStore) {
        this.deviceStore = deviceStore;
        this.runnerStore = runnerStore;
    }

    @Scheduled(fixedDelay = 30_000)
    public void cleanupStale() {
        runnerStore.markOfflineIfStale();
        deviceStore.markOfflineIfStale();
    }
}
