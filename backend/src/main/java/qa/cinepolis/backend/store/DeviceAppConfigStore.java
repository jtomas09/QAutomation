package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.DeviceAppConfig;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class DeviceAppConfigStore {

    private final Map<String, DeviceAppConfig> store = new ConcurrentHashMap<>();

    public void save(DeviceAppConfig config) {
        store.put(config.getDeviceId(), config);
    }

    public Optional<DeviceAppConfig> get(String deviceId) {
        return Optional.ofNullable(store.get(deviceId));
    }

    public Collection<DeviceAppConfig> getAll() {
        return store.values();
    }

    public Map<String, DeviceAppConfig> asMap() {
        return store.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void delete(String deviceId) {
        store.remove(deviceId);
    }
}
