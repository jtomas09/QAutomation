package qa.cinepolis.backend.model;

import java.time.Instant;

public class VideoSuiteSummary {

    private String  suiteName;
    private int     videoCount;
    private Instant lastExecutionAt;
    private long    totalSizeBytes;
    private String  overallStatus; // PASSED | FAILED | MIXED | UNKNOWN

    public VideoSuiteSummary() {}

    public VideoSuiteSummary(String suiteName, int videoCount, Instant lastExecutionAt,
                              long totalSizeBytes, String overallStatus) {
        this.suiteName       = suiteName;
        this.videoCount      = videoCount;
        this.lastExecutionAt = lastExecutionAt;
        this.totalSizeBytes  = totalSizeBytes;
        this.overallStatus   = overallStatus;
    }

    public String  getSuiteName()       { return suiteName; }
    public int     getVideoCount()      { return videoCount; }
    public Instant getLastExecutionAt() { return lastExecutionAt; }
    public long    getTotalSizeBytes()  { return totalSizeBytes; }
    public String  getOverallStatus()   { return overallStatus; }
}
