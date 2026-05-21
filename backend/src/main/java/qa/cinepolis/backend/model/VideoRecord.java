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

    public String  getId()           { return id; }
    public String  getExecutionId()  { return executionId; }
    public String  getSuiteName()    { return suiteName; }
    public String  getTestName()     { return testName; }
    public String  getOriginalName() { return originalName; }
    public long    getSizeBytes()    { return sizeBytes; }
    public Instant getCreatedAt()    { return createdAt; }

    public void setId(String id)                   { this.id = id; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public void setSuiteName(String suiteName)     { this.suiteName = suiteName; }
    public void setTestName(String testName)       { this.testName = testName; }
    public void setOriginalName(String name)       { this.originalName = name; }
    public void setSizeBytes(long sizeBytes)       { this.sizeBytes = sizeBytes; }
    public void setCreatedAt(Instant createdAt)    { this.createdAt = createdAt; }
}
