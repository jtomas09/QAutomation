package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;

/**
 * Stores the project path configured by the user and the validation
 * status reported back by the Runner agent after it checks the path.
 */
@Component
public class ProjectPathStore {

    private volatile String projectPath = "";

    // ── Repo config (set by user via UI, used by Runner to auto-manage workspace) ─
    private volatile String repoUrl    = "";
    private volatile String repoBranch = "main";

    // ── Validation result (reported by the Runner) ────────────────────────────
    private volatile boolean   validGradlew        = false;
    private volatile boolean   validBuildGradle     = false;
    private volatile boolean   validSettingsGradle  = false;
    private volatile boolean   validProject         = false;
    private volatile String    validatedPath        = "";
    private volatile String    validatedAt          = "";

    // ── Path ──────────────────────────────────────────────────────────────────

    public String getProjectPath() { return projectPath; }

    public void setProjectPath(String path) {
        this.projectPath = (path != null) ? path.trim() : "";
        // Reset validation when path changes
        this.validProject        = false;
        this.validGradlew        = false;
        this.validBuildGradle    = false;
        this.validSettingsGradle = false;
        this.validatedPath       = "";
        this.validatedAt         = "";
    }

    // ── Validation (set by Runner heartbeat) ──────────────────────────────────

    public void setValidation(String checkedPath, boolean gradlew, boolean buildGradle,
                              boolean settingsGradle, boolean valid, String checkedAt) {
        this.validatedPath        = checkedPath != null ? checkedPath : "";
        this.validGradlew         = gradlew;
        this.validBuildGradle     = buildGradle;
        this.validSettingsGradle  = settingsGradle;
        this.validProject         = valid;
        this.validatedAt          = checkedAt != null ? checkedAt : "";
    }

    public boolean isValidProject()        { return validProject;        }
    public boolean isValidGradlew()        { return validGradlew;        }
    public boolean isValidBuildGradle()    { return validBuildGradle;    }
    public boolean isValidSettingsGradle() { return validSettingsGradle; }
    public String  getValidatedPath()      { return validatedPath;       }
    public String  getValidatedAt()        { return validatedAt;         }

    public boolean hasValidation()         { return !validatedAt.isEmpty(); }

    // ── Repo config ───────────────────────────────────────────────────────────

    public String getRepoUrl()    { return repoUrl;    }
    public String getRepoBranch() { return repoBranch; }

    public void setRepoConfig(String url, String branch) {
        this.repoUrl    = (url    != null) ? url.trim()    : "";
        this.repoBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";
    }
}
