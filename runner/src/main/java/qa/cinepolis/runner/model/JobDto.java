package qa.cinepolis.runner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDto {
    public String  executionId;
    public String  suite;
    public String  testClass;
    public String  env;
    public String  device;
    public String  country;

    @JsonProperty("videoEnabled")
    public boolean videoEnabled;

    @JsonProperty("sendMail")
    public boolean sendMail;

    public String  reportEmails;
}
