package qa.cinepolis.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ScheduledJob {

    private String  id;
    private String  name;
    private String  suite;
    private String  device;

    @JsonProperty("env")
    private String  environment;

    private String  country;
    private boolean videoEnabled;
    private String  cronExpression;
    private boolean enabled;
    private Instant lastRun;
    private Instant nextRun;
    private String  lastStatus;   // PENDING | TRIGGERED | ERROR

    public String  getId()                         { return id; }
    public void    setId(String id)                { this.id = id; }

    public String  getName()                       { return name; }
    public void    setName(String name)            { this.name = name; }

    public String  getSuite()                      { return suite; }
    public void    setSuite(String suite)          { this.suite = suite; }

    public String  getDevice()                     { return device; }
    public void    setDevice(String device)        { this.device = device; }

    public String  getEnvironment()                { return environment; }
    public void    setEnvironment(String e)        { this.environment = e; }

    public String  getCountry()                    { return country; }
    public void    setCountry(String country)      { this.country = country; }

    public boolean isVideoEnabled()                { return videoEnabled; }
    public void    setVideoEnabled(boolean v)      { this.videoEnabled = v; }

    public String  getCronExpression()             { return cronExpression; }
    public void    setCronExpression(String cron)  { this.cronExpression = cron; }

    public boolean isEnabled()                     { return enabled; }
    public void    setEnabled(boolean enabled)     { this.enabled = enabled; }

    public Instant getLastRun()                    { return lastRun; }
    public void    setLastRun(Instant t)           { this.lastRun = t; }

    public Instant getNextRun()                    { return nextRun; }
    public void    setNextRun(Instant t)           { this.nextRun = t; }

    public String  getLastStatus()                 { return lastStatus; }
    public void    setLastStatus(String s)         { this.lastStatus = s; }
}
