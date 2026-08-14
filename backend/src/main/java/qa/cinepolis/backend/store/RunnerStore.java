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
            // Core
            if (update.getPlatform() != null) r.setPlatform(update.getPlatform());
            if (update.getVersion()  != null) r.setVersion(update.getVersion());
            if (update.getStatus()   != null) r.setStatus(update.getStatus());
            if (update.getComputerName() != null) r.setComputerName(update.getComputerName());
            if (update.getDevices()  != null && !update.getDevices().isEmpty()) r.setDevices(update.getDevices());
            // Universal Runner
            if (update.getOs()               != null) r.setOs(update.getOs());
            if (update.getHostname()         != null) r.setHostname(update.getHostname());
            if (update.getAndroidSupported() != null) r.setAndroidSupported(update.getAndroidSupported());
            if (update.getIosSupported()     != null) r.setIosSupported(update.getIosSupported());
            // ADB diagnostics
            if (update.getAdbPath()    != null) r.setAdbPath(update.getAdbPath());
            if (update.getAdbVersion() != null) r.setAdbVersion(update.getAdbVersion());
            if (update.getAdbExists()  != null) r.setAdbExists(update.getAdbExists());
            if (update.getAdbOk()      != null) r.setAdbOk(update.getAdbOk());
            if (update.getDevicesFound()             != null) r.setDevicesFound(update.getDevicesFound());
            if (update.getPlatformToolsInstalled()   != null) r.setPlatformToolsInstalled(update.getPlatformToolsInstalled());
            // Component telemetry (v4.0)
            if (update.getJreInstalled()    != null) r.setJreInstalled(update.getJreInstalled());
            if (update.getJreVersion()      != null) r.setJreVersion(update.getJreVersion());
            if (update.getNodeInstalled()   != null) r.setNodeInstalled(update.getNodeInstalled());
            if (update.getNodeVersion()     != null) r.setNodeVersion(update.getNodeVersion());
            if (update.getAppiumInstalled() != null) r.setAppiumInstalled(update.getAppiumInstalled());
            if (update.getAppiumVersion()   != null) r.setAppiumVersion(update.getAppiumVersion());
            if (update.getXcodeInstalled()  != null) r.setXcodeInstalled(update.getXcodeInstalled());
            if (update.getXcodeVersion()    != null) r.setXcodeVersion(update.getXcodeVersion());
            // Host Status (v6 — HostStatusManager)
            if (update.getHostStatus() != null) r.setHostStatus(update.getHostStatus());
            if (update.getIosReady()   != null) r.setIosReady(update.getIosReady());
            // Device Stream Service (Phase 10 — Live Preview)
            if (update.getStreamUrl()  != null) r.setStreamUrl(update.getStreamUrl());
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
