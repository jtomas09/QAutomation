package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExecutionDeviceStore {

    private volatile List<String> deviceUdids = new ArrayList<>();

    public List<String> getDeviceUdids() {
        return new ArrayList<>(deviceUdids);
    }

    public void setDeviceUdids(List<String> udids) {
        this.deviceUdids = udids != null ? new ArrayList<>(udids) : new ArrayList<>();
    }
}
