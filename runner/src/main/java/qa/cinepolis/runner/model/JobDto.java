package qa.cinepolis.runner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDto {
    public String  executionId;
    public String  suite;
    public String  testClass;
    public String  env;
    public String  device;         // user-selected device name (legacy / fallback)
    public String  country;

    @JsonProperty("videoEnabled")
    public boolean videoEnabled;

    @JsonProperty("sendMail")
    public boolean sendMail;

    public String  reportEmails;

    // ── Dynamic device capabilities (populated by backend DeviceStore) ──────
    public String  udid;            // physical device UDID from adb/xcrun discovery
    public String  platformVersion; // e.g. "15", "17.5"
    public String  deviceName;      // canonical discovered device name
}
