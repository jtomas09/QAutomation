package qa.cinepolis.backend.model;

public class DeviceAppConfig {

    private String deviceId;
    private String platform;
    private String appMode;    // INSTALLED | APK | IPA
    private String appName;
    private String appPackage;
    private String bundleId;
    private String appVersion;
    private String source;

    public DeviceAppConfig() {}

    public DeviceAppConfig(String deviceId, String platform, String appMode,
                           String appName, String appPackage, String bundleId,
                           String appVersion, String source) {
        this.deviceId   = deviceId;
        this.platform   = platform;
        this.appMode    = appMode;
        this.appName    = appName;
        this.appPackage = appPackage;
        this.bundleId   = bundleId;
        this.appVersion = appVersion;
        this.source     = source;
    }

    public String getDeviceId()   { return deviceId; }
    public String getPlatform()   { return platform; }
    public String getAppMode()    { return appMode; }
    public String getAppName()    { return appName; }
    public String getAppPackage() { return appPackage; }
    public String getBundleId()   { return bundleId; }
    public String getAppVersion() { return appVersion; }
    public String getSource()     { return source; }

    public void setDeviceId(String deviceId)     { this.deviceId   = deviceId; }
    public void setPlatform(String platform)     { this.platform   = platform; }
    public void setAppMode(String appMode)       { this.appMode    = appMode; }
    public void setAppName(String appName)       { this.appName    = appName; }
    public void setAppPackage(String appPackage) { this.appPackage = appPackage; }
    public void setBundleId(String bundleId)     { this.bundleId   = bundleId; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public void setSource(String source)         { this.source     = source; }
}
