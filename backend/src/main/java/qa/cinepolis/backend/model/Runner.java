package qa.cinepolis.backend.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Runner {
    private String       runnerId;
    private String       platform;       // android | ios (legacy routing)
    private String       version;
    private RunnerStatus status;
    private Instant      lastSeen;
    private Instant      registeredAt;
    private List<RunnerDevice> devices = new CopyOnWriteArrayList<>();

    // Universal Runner capability fields
    private String  os;               // WINDOWS | MACOS | LINUX
    private String  hostname;
    private Boolean androidSupported;
    private Boolean iosSupported;

    // Embedded ADB / platform-tools diagnostics (sent by PlatformToolsManager)
    private String  adbPath;
    private String  adbVersion;
    private Boolean adbExists;
    private Boolean adbOk;
    private Integer devicesFound;
    private Boolean platformToolsInstalled;

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
    public List<RunnerDevice> getDevices()                     { return devices; }
    public void               setDevices(List<RunnerDevice> v) { this.devices = v; }

    // Universal Runner
    public String  getOs()                    { return os; }
    public void    setOs(String v)            { this.os = v; }
    public String  getHostname()              { return hostname; }
    public void    setHostname(String v)      { this.hostname = v; }
    public Boolean getAndroidSupported()      { return androidSupported; }
    public void    setAndroidSupported(Boolean v) { this.androidSupported = v; }
    public Boolean getIosSupported()          { return iosSupported; }
    public void    setIosSupported(Boolean v) { this.iosSupported = v; }

    // ADB diagnostics
    public String  getAdbPath()                    { return adbPath; }
    public void    setAdbPath(String v)            { this.adbPath = v; }
    public String  getAdbVersion()                 { return adbVersion; }
    public void    setAdbVersion(String v)         { this.adbVersion = v; }
    public Boolean getAdbExists()                  { return adbExists; }
    public void    setAdbExists(Boolean v)         { this.adbExists = v; }
    public Boolean getAdbOk()                      { return adbOk; }
    public void    setAdbOk(Boolean v)             { this.adbOk = v; }
    public Integer getDevicesFound()               { return devicesFound; }
    public void    setDevicesFound(Integer v)      { this.devicesFound = v; }
    public Boolean getPlatformToolsInstalled()     { return platformToolsInstalled; }
    public void    setPlatformToolsInstalled(Boolean v) { this.platformToolsInstalled = v; }
}
