package qa.cinepolis.runner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDto {
    public String  executionId;
    public String  suite;
    public String  env;
    public String  device;
    public String  country;
    public boolean videoEnabled;
}
