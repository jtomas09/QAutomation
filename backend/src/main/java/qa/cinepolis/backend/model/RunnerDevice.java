package qa.cinepolis.backend.model;

public class RunnerDevice {
    private String deviceId;
    private String deviceName;
    private String platform;
    private String status;  // available | inuse | offline

    public String getDeviceId()             { return deviceId; }
    public void   setDeviceId(String v)     { this.deviceId = v; }
    public String getDeviceName()           { return deviceName; }
    public void   setDeviceName(String v)   { this.deviceName = v; }
    public String getPlatform()             { return platform; }
    public void   setPlatform(String v)     { this.platform = v; }
    public String getStatus()               { return status; }
    public void   setStatus(String v)       { this.status = v; }
}
