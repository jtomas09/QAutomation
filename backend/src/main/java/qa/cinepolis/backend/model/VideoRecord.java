package qa.cinepolis.backend.model;

import java.time.Instant;

public class VideoRecord {

    private String  id;
    private String  executionId;
    private String  suiteName;
    private String  testName;
    private String  originalName;
    private long    sizeBytes;
    private Instant createdAt;
    // Denormalizados desde la Execution al momento de guardar (join en escritura,
    // evita que el Runner tenga que mandar más headers y evita joins en lectura).
    private String  status;  // PASS | FAIL | SKIP | UNKNOWN
    private String  device;
    private String  env;

    public String  getId()           { return id; }
    public String  getExecutionId()  { return executionId; }
    public String  getSuiteName()    { return suiteName; }
    public String  getTestName()     { return testName; }
    public String  getOriginalName() { return originalName; }
    public long    getSizeBytes()    { return sizeBytes; }
    public Instant getCreatedAt()    { return createdAt; }
    public String  getStatus()       { return status; }
    public String  getDevice()       { return device; }
    public String  getEnv()          { return env; }

    public void setId(String id)                   { this.id = id; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public void setSuiteName(String suiteName)     { this.suiteName = suiteName; }
    public void setTestName(String testName)       { this.testName = testName; }
    public void setOriginalName(String name)       { this.originalName = name; }
    public void setSizeBytes(long sizeBytes)       { this.sizeBytes = sizeBytes; }
    public void setCreatedAt(Instant createdAt)    { this.createdAt = createdAt; }
    public void setStatus(String status)           { this.status = status; }
    public void setDevice(String device)           { this.device = device; }
    public void setEnv(String env)                 { this.env = env; }
}
