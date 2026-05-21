package qa.cinepolis.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunRequest {

    private String suite;
    private String device;

    // Frontend sends "env"; @JsonProperty maps it to this field
    @JsonProperty("env")
    private String environment;

    private String  country;
    private boolean videoEnabled;

    public String  getSuite()       { return suite; }
    public void    setSuite(String suite) { this.suite = suite; }

    public String  getDevice()      { return device; }
    public void    setDevice(String device) { this.device = device; }

    public String  getEnvironment() { return environment; }
    public void    setEnvironment(String environment) { this.environment = environment; }

    public String  getCountry()     { return country; }
    public void    setCountry(String country) { this.country = country; }

    public boolean isVideoEnabled()           { return videoEnabled; }
    public void    setVideoEnabled(boolean v) { this.videoEnabled = v; }
}
