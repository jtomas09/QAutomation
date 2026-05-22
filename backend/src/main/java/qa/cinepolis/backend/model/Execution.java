package qa.cinepolis.backend.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Execution {

    private String          executionId;
    private String          suite;
    private String          env;
    private String          device;
    private String          country;
    private ExecutionStatus status;
    private Instant         startTime;
    private Instant         endTime;
    private int             passed;
    private int             failed;
    private int             skipped;
    private int             total;
    private List<LogEvent>  logs = new CopyOnWriteArrayList<>();
    private String          allureUrl;
    private boolean         videoEnabled;
    private String          testClass;

    public String          getExecutionId()              { return executionId; }
    public void            setExecutionId(String v)      { this.executionId = v; }
    public String          getSuite()                    { return suite; }
    public void            setSuite(String v)            { this.suite = v; }
    public String          getEnv()                      { return env; }
    public void            setEnv(String v)              { this.env = v; }
    public String          getDevice()                   { return device; }
    public void            setDevice(String v)           { this.device = v; }
    public String          getCountry()                  { return country; }
    public void            setCountry(String v)          { this.country = v; }
    public ExecutionStatus getStatus()                   { return status; }
    public void            setStatus(ExecutionStatus v)  { this.status = v; }
    public Instant         getStartTime()                { return startTime; }
    public void            setStartTime(Instant v)       { this.startTime = v; }
    public Instant         getEndTime()                  { return endTime; }
    public void            setEndTime(Instant v)         { this.endTime = v; }
    public int             getPassed()                   { return passed; }
    public void            setPassed(int v)              { this.passed = v; }
    public int             getFailed()                   { return failed; }
    public void            setFailed(int v)              { this.failed = v; }
    public int             getSkipped()                  { return skipped; }
    public void            setSkipped(int v)             { this.skipped = v; }
    public int             getTotal()                    { return total; }
    public void            setTotal(int v)               { this.total = v; }
    public List<LogEvent>  getLogs()                     { return logs; }
    public void            setLogs(List<LogEvent> v)     { this.logs = v; }
    public String          getAllureUrl()                 { return allureUrl; }
    public void            setAllureUrl(String v)        { this.allureUrl = v; }
    public boolean         isVideoEnabled()              { return videoEnabled; }
    public void            setVideoEnabled(boolean v)   { this.videoEnabled = v; }
    public String          getTestClass()                { return testClass; }
    public void            setTestClass(String v)        { this.testClass = v; }
}
