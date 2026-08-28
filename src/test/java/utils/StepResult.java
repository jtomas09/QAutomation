package utils;

public class StepResult {

    private final String stepName;
    private final String status;       // "OK", "SKIPPED" o "FAIL"/"ERROR"
    private final String screenshotPath;
    private final String reason;       // motivo real (solo relevante para SKIPPED); null si no aplica

    public StepResult(String stepName, String status, String screenshotPath) {
        this(stepName, status, screenshotPath, null);
    }

    public StepResult(String stepName, String status, String screenshotPath, String reason) {
        this.stepName = stepName;
        this.status = status;
        this.screenshotPath = screenshotPath;
        this.reason = reason;
    }

    public String getStepName() {
        return stepName;
    }

    public String getStatus() {
        return status;
    }

    public String getScreenshotPath() {
        return screenshotPath;
    }

    public String getReason() {
        return reason;
    }

}
