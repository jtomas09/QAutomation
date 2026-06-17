package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.Runner;
import qa.cinepolis.backend.model.RunnerDevice;
import qa.cinepolis.backend.model.RunnerStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunnerStore {

    private final ConcurrentHashMap<String, Runner> runners = new ConcurrentHashMap<>();

    public Runner upsert(Runner update) {
        return runners.compute(update.getRunnerId(), (id, existing) -> {
            Runner r = (existing != null) ? existing : new Runner();
            if (r.getRegisteredAt() == null) r.setRegisteredAt(Instant.now());
            r.setRunnerId(id);
            r.setLastSeen(Instant.now());
            if (update.getPlatform() != null)                          r.setPlatform(update.getPlatform());
            if (update.getVersion()  != null)                          r.setVersion(update.getVersion());
            if (update.getStatus()   != null)                          r.setStatus(update.getStatus());
            if (update.getDevices()  != null && !update.getDevices().isEmpty()) r.setDevices(update.getDevices());
            return r;
        });
    }

    public Optional<Runner> findById(String runnerId) {
        return Optional.ofNullable(runners.get(runnerId));
    }

    public List<Runner> findAll() {
        return new ArrayList<>(runners.values());
    }

    public void markOfflineIfStale() {
        Instant cutoff = Instant.now().minusSeconds(60);
        runners.values().forEach(r -> {
            if (r.getLastSeen() != null && r.getLastSeen().isBefore(cutoff)
                    && r.getStatus() != RunnerStatus.OFFLINE) {
                r.setStatus(RunnerStatus.OFFLINE);
            }
        });
    }

    /** Returns the first ONLINE runner that supports the given platform. */
    public Optional<Runner> findOnlineForPlatform(String platform) {
        markOfflineIfStale();
        return runners.values().stream()
                .filter(r -> r.getStatus() != RunnerStatus.OFFLINE)
                .filter(r -> platform == null || platform.isBlank()
                        || platform.equalsIgnoreCase(r.getPlatform()))
                .findFirst();
    }
}
