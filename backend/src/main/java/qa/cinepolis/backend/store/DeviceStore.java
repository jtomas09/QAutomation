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
     * Atomically claims the AVAILABLE device that matches the given identifier.
     *
     * Priority:
     *   0) Exact UDID match          — frontend always sends UDID; this is the primary path
     *   1) Exact deviceName match    — fallback for human-readable name sent by older clients
     *   2) Partial deviceName match  — e.g. "A56" matches "Samsung Galaxy A56"
     *
     * NO implicit fallback to "any available device" — if the requested device is not
     * found the caller must handle the empty result (not silently pick a random device).
     *
     * Returns empty if no AVAILABLE device matches.
     */
    public synchronized Optional<Device> claimDevice(String preferredUdidOrName, String platform, String executionId) {
        if (preferredUdidOrName == null || preferredUdidOrName.isBlank()) return Optional.empty();

        // 0) Exact UDID match — highest priority
        Optional<Device> candidate = devices.values().stream()
                .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                .filter(d -> preferredUdidOrName.equalsIgnoreCase(d.getUdid()))
                .findFirst();

        // 1) Exact deviceName match (case-insensitive)
        if (candidate.isEmpty()) {
            candidate = devices.values().stream()
                    .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                    .filter(d -> d.getDeviceName() != null
                            && d.getDeviceName().equalsIgnoreCase(preferredUdidOrName))
                    .findFirst();
        }

        // 2) Partial deviceName match — only for name-like strings (not UDID-length tokens)
        if (candidate.isEmpty() && preferredUdidOrName.length() < 20) {
            final String nameLower = preferredUdidOrName.toLowerCase().replaceAll("\\s+", "");
            candidate = devices.values().stream()
                    .filter(d -> d.getStatus() == DeviceStatus.AVAILABLE)
                    .filter(d -> d.getDeviceName() != null)
                    .filter(d -> d.getDeviceName().toLowerCase().replaceAll("\\s+", "").contains(nameLower)
                            || nameLower.contains(d.getDeviceName().toLowerCase().replaceAll("\\s+", "")))
                    .findFirst();
        }

        // No step 3 — never pick a random device as fallback.

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
