package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;

/**
 * Single source of truth for the automation project repository configuration.
 *
 * Built-in defaults make the platform work out of the box — no env vars required.
 * Optional env var overrides for custom deployments:
 *   REPO_URL         — git clone URL  (e.g. https://github.com/org/project.git)
 *   REPO_BRANCH      — branch name    (default: main)
 *   PROJECT_NAME     — workspace dir  (default: QAutomation)
 *
 * All Runner instances pull this config via GET /api/runner/config.
 * Users never configure repos per-machine.
 */
@Component
public class RunnerConfigStore {

    private static final String DEFAULT_REPO_URL     = "https://github.com/jtomas09/QAutomation.git";
    private static final String DEFAULT_BRANCH       = "main";
    private static final String DEFAULT_PROJECT_NAME = "QAutomation";

    private volatile String repositoryUrl;
    private volatile String branch;
    private volatile String projectName;

    public RunnerConfigStore() {
        this.repositoryUrl = getEnv("REPO_URL",     DEFAULT_REPO_URL);
        this.branch        = getEnv("REPO_BRANCH",  DEFAULT_BRANCH);
        this.projectName   = getEnv("PROJECT_NAME", DEFAULT_PROJECT_NAME);
    }

    public String  getRepositoryUrl() { return repositoryUrl; }
    public String  getBranch()        { return branch;        }
    public String  getProjectName()   { return projectName;   }
    public boolean isConfigured()     { return true; }  // always configured via defaults

    /** Admin override — applied to all Runners without restart. */
    public void setConfig(String repositoryUrl, String branch, String projectName) {
        if (repositoryUrl != null && !repositoryUrl.isBlank())
            this.repositoryUrl = repositoryUrl.trim();
        if (branch != null && !branch.isBlank())
            this.branch = branch.trim();
        if (projectName != null && !projectName.isBlank())
            this.projectName = projectName.trim();
    }

    private static String getEnv(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
