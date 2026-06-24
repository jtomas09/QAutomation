package qa.cinepolis.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the local git workspace for the automation project.
 *
 * Workspace location (auto-managed, never user-configured):
 *   {agentDataDir}/workspace/{repoName}/
 *
 * Per-execution flow:
 *   1. If workspace/.git missing → git clone
 *   2. If workspace/.git present → git fetch + reset --hard
 *   3. Validate Gradle structure (gradlew, build.gradle, settings.gradle)
 *   4. Return workspace File ready for ProcessBuilder.directory()
 */
public class WorkspaceManager {

    private final File          workspaceDir;
    private final String        repoUrl;
    private final String        repoBranch;
    private final BackendClient client;

    public WorkspaceManager(File workspaceDir, String repoUrl, String repoBranch,
                            BackendClient client) {
        this.workspaceDir = workspaceDir;
        this.repoUrl      = (repoUrl != null) ? repoUrl.trim() : "";
        this.repoBranch   = (repoBranch != null && !repoBranch.isBlank()) ? repoBranch.trim() : "main";
        this.client       = client;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ensures the workspace is present and up-to-date.
     * Returns the workspace directory on success, null on failure (logs already sent).
     */
    public File ensureWorkspace(String executionId) {
        if (repoUrl.isBlank()) {
            // No URL configured — use existing workspace if valid
            if (workspaceDir.exists() && isValidGradleProject()) {
                client.sendLog(executionId, "INFO",
                        "✅ Usando workspace local: " + workspaceDir.getAbsolutePath());
                return workspaceDir;
            }
            client.sendLog(executionId, "ERROR",
                    "❌ URL de repositorio no configurada.");
            client.sendLog(executionId, "ERROR",
                    "   Configure la URL en Configuración → Runner Settings → Repositorio.");
            return null;
        }

        File dotGit = new File(workspaceDir, ".git");

        if (!dotGit.exists()) {
            // Directory may exist but be incomplete — remove it before cloning
            if (workspaceDir.exists()) {
                client.sendLog(executionId, "WARN",
                        "⚠️ Workspace incompleto encontrado. Eliminando y re-clonando…");
                deleteDirectory(workspaceDir);
            }
            client.sendLog(executionId, "INFO", "📥 Repositorio no encontrado. Clonando...");
            if (!cloneRepo(executionId)) return null;
            client.sendLog(executionId, "INFO", "📥 Clonación completada.");
        } else {
            client.sendLog(executionId, "INFO", "🔄 Actualizando repositorio...");
            if (!updateRepo(executionId)) {
                client.sendLog(executionId, "WARN",
                        "⚠️ No se pudo actualizar el repositorio — se usa la versión local.");
            } else {
                client.sendLog(executionId, "INFO", "✅ Repositorio actualizado.");
            }
        }

        return validateAndReturn(executionId);
    }

    public File getWorkspaceDir() { return workspaceDir; }

    // ── Git operations ────────────────────────────────────────────────────────

    private boolean cloneRepo(String executionId) {
        try {
            workspaceDir.getParentFile().mkdirs();
            List<String> cmd = List.of(
                    "git", "clone",
                    "--branch", repoBranch,
                    repoUrl,
                    workspaceDir.getAbsolutePath());
            return runGit(cmd, workspaceDir.getParentFile(), executionId, 600);
        } catch (Exception e) {
            client.sendLog(executionId, "ERROR", "❌ Error al clonar repositorio: " + e.getMessage());
            return false;
        }
    }

    private boolean updateRepo(String executionId) {
        try {
            // Fetch latest from remote
            boolean fetched = runGit(
                    List.of("git", "fetch", "origin"),
                    workspaceDir, executionId, 120);
            if (!fetched) return false;

            // Reset to remote branch — discards any local changes (runner should never modify)
            return runGit(
                    List.of("git", "reset", "--hard", "origin/" + repoBranch),
                    workspaceDir, executionId, 30);
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️ Error actualizando repositorio: " + e.getMessage());
            return false;
        }
    }

    private boolean runGit(List<String> cmd, File workingDir, String executionId,
                           int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                client.sendLog(executionId, "INFO", "[git] " + line);
                System.out.println("[git] " + line);
            }
        }

        boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            client.sendLog(executionId, "ERROR",
                    "❌ Timeout en operación git (" + timeoutSeconds + "s): " + cmd.get(1));
            return false;
        }
        return p.exitValue() == 0;
    }

    // ── Gradle validation ─────────────────────────────────────────────────────

    private File validateAndReturn(String executionId) {
        boolean isWindows      = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean gradlew        = new File(workspaceDir, isWindows ? "gradlew.bat" : "gradlew").exists();
        boolean buildGradle    = new File(workspaceDir, "build.gradle").exists()
                              || new File(workspaceDir, "build.gradle.kts").exists();
        boolean settingsGradle = new File(workspaceDir, "settings.gradle").exists()
                              || new File(workspaceDir, "settings.gradle.kts").exists();
        boolean valid          = gradlew && buildGradle && settingsGradle;

        // Report to backend so the UI can show ✓/✗ badges
        client.reportProjectValidation(
                workspaceDir.getAbsolutePath(), gradlew, buildGradle, settingsGradle, valid);

        if (!gradlew)
            client.sendLog(executionId, "ERROR",
                    "❌ No se encontró " + (isWindows ? "gradlew.bat" : "gradlew")
                    + " en: " + workspaceDir.getAbsolutePath());
        if (!buildGradle)
            client.sendLog(executionId, "ERROR",
                    "❌ No se encontró build.gradle[.kts] en: " + workspaceDir.getAbsolutePath());
        if (!settingsGradle)
            client.sendLog(executionId, "ERROR",
                    "❌ No se encontró settings.gradle[.kts] en: " + workspaceDir.getAbsolutePath());

        if (!valid) return null;

        client.sendLog(executionId, "INFO",
                "✅ Proyecto Gradle válido: " + workspaceDir.getAbsolutePath());
        return workspaceDir;
    }

    private boolean isValidGradleProject() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(workspaceDir, isWindows ? "gradlew.bat" : "gradlew").exists()
            && (new File(workspaceDir, "build.gradle").exists()
                || new File(workspaceDir, "build.gradle.kts").exists())
            && (new File(workspaceDir, "settings.gradle").exists()
                || new File(workspaceDir, "settings.gradle.kts").exists());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static void deleteDirectory(File dir) {
        try {
            Files.walk(Path.of(dir.getAbsolutePath()))
                 .sorted(Comparator.reverseOrder())
                 .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
    }

    /**
     * Extracts the project directory name from a git URL.
     * "https://github.com/org/automation-project.git" → "automation-project"
     */
    public static String repoNameFromUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) return "automation-project";
        String name = repoUrl.trim().replaceAll("\\.git$", "");
        int slash = name.lastIndexOf('/');
        String result = (slash >= 0 && slash < name.length() - 1)
                ? name.substring(slash + 1)
                : name;
        return result.isBlank() ? "automation-project" : result;
    }
}
