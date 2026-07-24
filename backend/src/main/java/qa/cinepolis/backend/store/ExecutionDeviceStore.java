package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExecutionDeviceStore {

    private volatile List<String> deviceUdids  = new ArrayList<>();
    private volatile boolean      videoEnabled = false;

    public List<String> getDeviceUdids() {
        return new ArrayList<>(deviceUdids);
    }

    public void setDeviceUdids(List<String> udids) {
        this.deviceUdids = udids != null ? new ArrayList<>(udids) : new ArrayList<>();
    }

    public boolean isVideoEnabled() {
        return videoEnabled;
    }

    public void setVideoEnabled(boolean videoEnabled) {
        this.videoEnabled = videoEnabled;
    }
}
