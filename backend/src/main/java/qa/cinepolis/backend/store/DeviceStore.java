package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.Device;
import qa.cinepolis.backend.model.DeviceStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory device registry.  Thread-safe via ConcurrentHashMap + synchronized claim.
 * All device info originates from Runner Agent discovery — no manual configuration.
 */
@Component
public class DeviceStore {

    private final ConcurrentHashMap<String, Device> devices = new ConcurrentHashMap<>();

    /** Register or update a device (upsert by UDID). Called on runner heartbeat. */
    public Device upsert(Device update) {
        return devices.compute(update.getUdid(), (udid, existing) -> {
            Device d = (existing != null) ? existing : new Device();
            if (d.getRegisteredAt() == null) d.setRegisteredAt(Instant.now());
            d.setUdid(udid);
            d.setLastSeen(Instant.now());
            if (update.getDeviceName()      != null) d.setDeviceName(update.getDeviceName());
            if (update.getModel()           != null) d.setModel(update.getModel());
            if (update.getManufacturer()    != null) d.setManufacturer(update.getManufacturer());
            if (update.getPlatform()        != null) d.setPlatform(update.getPlatform());
            if (update.getPlatformVersion() != null) d.setPlatformVersion(update.getPlatformVersion());
            if (update.getRunnerId()        != null) d.setRunnerId(update.getRunnerId());
            // Only overwrite status if device is not BUSY (don't reset an active device)
            if (d.getStatus() != DeviceStatus.BUSY && d.getStatus() != DeviceStatus.MAINTENANCE) {
                d.setStatus(update.getStatus() != null ? update.getStatus() : DeviceStatus.AVAILABLE);
            }
            return d;
        });
    }

    public Optional<Device> findByUdid(String udid) {
        return Optional.ofNullable(devices.get(udid));
    }

    public List<Device> findAll() {
        return new ArrayList<>(devices.values());
    }

    public List<Device> findAvailable() {
        return devices.values().stream()
                .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                .toList();
    }

    public List<Device> findAvailableForPlatform(String platform) {
        return devices.values().stream()
                .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                .filter(d -> platform == null || platform.isBlank()
                        || platform.equalsIgnoreCase(d.getPlatform()))
                .toList();
    }

    /**
     * Atomically claims the best AVAILABLE device matching the given hints.
     * Priority: exact deviceName match > platform match > any available.
     * Returns empty if no AVAILABLE device found.
     */
    public synchronized Optional<Device> claimDevice(String preferredName, String platform, String executionId) {
        // 1) Exact name match (case-insensitive)
        Optional<Device> candidate = devices.values().stream()
                .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                .filter(d -> preferredName != null && !preferredName.isBlank()
                        && d.getDeviceName() != null
                        && d.getDeviceName().equalsIgnoreCase(preferredName))
                .findFirst();

        // 2) Partial name match
        if (candidate.isEmpty() && preferredName != null && !preferredName.isBlank()) {
            final String nameLower = preferredName.toLowerCase().replaceAll("\\s+", "");
            candidate = devices.values().stream()
                    .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                    .filter(d -> d.getDeviceName() != null)
                    .filter(d -> d.getDeviceName().toLowerCase().replaceAll("\\s+", "").contains(nameLower)
                            || nameLower.contains(d.getDeviceName().toLowerCase().replaceAll("\\s+", "")))
                    .findFirst();
        }

        // 3) Any available device for the platform
        if (candidate.isEmpty()) {
            candidate = devices.values().stream()
                    .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                    .filter(d -> platform == null || platform.isBlank()
                            || platform.equalsIgnoreCase(d.getPlatform()))
                    .findFirst();
        }

        candidate.ifPresent(d -> {
            d.setStatus(DeviceStatus.BUSY);
            d.setActiveExecutionId(executionId);
        });
        return candidate;
    }

    /** Releases a device back to AVAILABLE after execution completes. */
    public synchronized void releaseDevice(String udid) {
        Device d = devices.get(udid);
        if (d != null && d.getStatus() == DeviceStatus.BUSY) {
            d.setStatus(DeviceStatus.AVAILABLE);
            d.setActiveExecutionId(null);
        }
    }

    public void updateStatus(String udid, DeviceStatus status) {
        Device d = devices.get(udid);
        if (d != null) d.setStatus(status);
    }

    public void remove(String udid) {
        devices.remove(udid);
    }

    /** Marks devices with lastSeen > 90s ago as OFFLINE (unless BUSY or MAINTENANCE). */
    public void markOfflineIfStale() {
        Instant cutoff = Instant.now().minusSeconds(90);
        devices.values().forEach(d -> {
            if (d.getStatus() == DeviceStatus.BUSY || d.getStatus() == DeviceStatus.MAINTENANCE) return;
            if (d.getLastSeen() != null && d.getLastSeen().isBefore(cutoff)) {
                d.setStatus(DeviceStatus.OFFLINE);
            }
        });
    }
}
