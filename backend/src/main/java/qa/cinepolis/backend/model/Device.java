package qa.cinepolis.backend.model;

import java.time.Instant;

/**
 * Represents a physical device registered by a Runner Agent.
 * All fields are discovered dynamically — no manual env-var configuration.
 */
public class Device {
    private String       udid;
    private String       deviceName;      // human-friendly: "Galaxy A56 5G"
    private String       model;           // raw adb model: "Galaxy_A56_5G"
    private String       manufacturer;
    private String       platform;        // ANDROID | IOS
    private String       platformVersion; // "15", "17.5"
    private DeviceStatus status;
    private String       runnerId;
    private String       activeExecutionId;
    private Instant      lastSeen;
    private Instant      registeredAt;

    public String       getUdid()                    { return udid; }
    public void         setUdid(String v)            { this.udid = v; }
    public String       getDeviceName()              { return deviceName; }
    public void         setDeviceName(String v)      { this.deviceName = v; }
    public String       getModel()                   { return model; }
    public void         setModel(String v)           { this.model = v; }
    public String       getManufacturer()            { return manufacturer; }
    public void         setManufacturer(String v)    { this.manufacturer = v; }
    public String       getPlatform()                { return platform; }
    public void         setPlatform(String v)        { this.platform = v; }
    public String       getPlatformVersion()         { return platformVersion; }
    public void         setPlatformVersion(String v) { this.platformVersion = v; }
    public DeviceStatus getStatus()                  { return status; }
    public void         setStatus(DeviceStatus v)    { this.status = v; }
    public String       getRunnerId()                { return runnerId; }
    public void         setRunnerId(String v)        { this.runnerId = v; }
    public String       getActiveExecutionId()               { return activeExecutionId; }
    public void         setActiveExecutionId(String v)       { this.activeExecutionId = v; }
    public Instant      getLastSeen()                { return lastSeen; }
    public void         setLastSeen(Instant v)       { this.lastSeen = v; }
    public Instant      getRegisteredAt()            { return registeredAt; }
    public void         setRegisteredAt(Instant v)   { this.registeredAt = v; }
}
