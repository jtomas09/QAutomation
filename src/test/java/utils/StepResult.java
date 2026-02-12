package utils;

public class StepResult {

    private final String stepName;
    private final String status;       // "OK" o "FAIL"
    private final String screenshotPath;

    public StepResult(String stepName, String status, String screenshotPath) {
        this.stepName = stepName;
        this.status = status;
        this.screenshotPath = screenshotPath;
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

}
