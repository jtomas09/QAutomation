package qa.cinepolis.backend.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Runner {
    private String       runnerId;
    private String       platform;       // android | ios
    private String       version;
    private RunnerStatus status;
    private Instant      lastSeen;
    private Instant      registeredAt;
    private List<RunnerDevice> devices = new CopyOnWriteArrayList<>();

    public String       getRunnerId()              { return runnerId; }
    public void         setRunnerId(String v)      { this.runnerId = v; }
    public String       getPlatform()              { return platform; }
    public void         setPlatform(String v)      { this.platform = v; }
    public String       getVersion()               { return version; }
    public void         setVersion(String v)       { this.version = v; }
    public RunnerStatus getStatus()                { return status; }
    public void         setStatus(RunnerStatus v)  { this.status = v; }
    public Instant      getLastSeen()              { return lastSeen; }
    public void         setLastSeen(Instant v)     { this.lastSeen = v; }
    public Instant      getRegisteredAt()          { return registeredAt; }
    public void         setRegisteredAt(Instant v) { this.registeredAt = v; }
    public List<RunnerDevice> getDevices()                  { return devices; }
    public void               setDevices(List<RunnerDevice> v) { this.devices = v; }
}
