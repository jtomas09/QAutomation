package qa.cinepolis.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
            if (workspaceDir.exists() && isValidGradleProject()) {
                // Valid workspace without .git (e.g. offline / previously extracted)
                client.sendLog(executionId, "ERROR", "❌ No fue posible sincronizar el repositorio.");
                client.sendLog(executionId, "WARN",  "⚠ Utilizando última versión disponible.");
                return validateAndReturn(executionId);
            }
            // No usable local copy — delete partial dir and clone fresh
            if (workspaceDir.exists()) {
                client.sendLog(executionId, "WARN",
                        "⚠ Workspace incompleto detectado. Eliminando y re-clonando…");
                deleteDirectory(workspaceDir);
            }
            client.sendLog(executionId, "INFO", "📥 Repositorio no encontrado. Clonando...");
            if (!cloneRepo(executionId)) {
                client.sendLog(executionId, "ERROR", "❌ No fue posible sincronizar el repositorio.");
                return null;
            }
            client.sendLog(executionId, "INFO", "📥 Clonación completada.");
        } else {
            client.sendLog(executionId, "INFO", "🔄 Actualizando repositorio...");
            if (!updateRepo(executionId)) {
                client.sendLog(executionId, "ERROR", "❌ No fue posible sincronizar el repositorio.");
                client.sendLog(executionId, "WARN",  "⚠ Utilizando última versión disponible.");
            } else {
                client.sendLog(executionId, "INFO", "✅ Repositorio actualizado.");
            }
        }

        return validateAndReturn(executionId);
    }

    public File getWorkspaceDir() { return workspaceDir; }

    // ── Git operations ────────────────────────────────────────────────────────

    /** Network-bound git ops (clone/fetch) get a bounded retry — a single transient
     *  reset/EOF (observed in practice on unstable connections) should not force a
     *  whole job to fail and the workspace to be wiped for nothing. */
    private static final int    NETWORK_OP_MAX_ATTEMPTS = 3;
    private static final long[] NETWORK_OP_BACKOFF_MS   = {5_000, 15_000};

    private boolean cloneRepo(String executionId) {
        for (int attempt = 1; attempt <= NETWORK_OP_MAX_ATTEMPTS; attempt++) {
            try {
                workspaceDir.getParentFile().mkdirs();
                // Clean up any partial checkout left by a previous failed attempt —
                // `git clone` refuses to clone into a non-empty existing directory.
                if (workspaceDir.exists()) deleteDirectory(workspaceDir);

                List<String> cmd = List.of(
                        "git", "clone", "--progress",
                        "--branch", repoBranch,
                        repoUrl,
                        workspaceDir.getAbsolutePath());
                if (runGit(cmd, workspaceDir.getParentFile(), executionId, 600)) return true;
            } catch (Exception e) {
                client.sendLog(executionId, "ERROR",
                        "❌ Error al clonar repositorio (intento " + attempt + "/" + NETWORK_OP_MAX_ATTEMPTS
                                + "): " + e.getMessage());
            }
            if (attempt < NETWORK_OP_MAX_ATTEMPTS) retryBackoff(executionId, attempt);
        }
        return false;
    }

    private boolean updateRepo(String executionId) {
        boolean fetched = false;
        for (int attempt = 1; attempt <= NETWORK_OP_MAX_ATTEMPTS; attempt++) {
            try {
                // Fetch latest from remote
                if (runGit(List.of("git", "fetch", "--progress", "origin"),
                        workspaceDir, executionId, 120)) {
                    fetched = true;
                    break;
                }
            } catch (Exception e) {
                client.sendLog(executionId, "WARN",
                        "⚠️ Error actualizando repositorio (intento " + attempt + "/" + NETWORK_OP_MAX_ATTEMPTS
                                + "): " + e.getMessage());
            }
            if (attempt < NETWORK_OP_MAX_ATTEMPTS) retryBackoff(executionId, attempt);
        }
        if (!fetched) return false;

        try {
            // Reset to remote branch — discards any local changes (runner should never modify).
            // Purely local/fast — no retry needed here.
            return runGit(
                    List.of("git", "reset", "--hard", "origin/" + repoBranch),
                    workspaceDir, executionId, 30);
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️ Error actualizando repositorio: " + e.getMessage());
            return false;
        }
    }

    private void retryBackoff(String executionId, int attemptJustFailed) {
        long waitMs = NETWORK_OP_BACKOFF_MS[Math.min(attemptJustFailed - 1, NETWORK_OP_BACKOFF_MS.length - 1)];
        client.sendLog(executionId, "WARN",
                "⚠ Reintentando en " + (waitMs / 1000) + "s (intento "
                        + (attemptJustFailed + 1) + "/" + NETWORK_OP_MAX_ATTEMPTS + ")…");
        try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // Matches git's own --progress lines (e.g. "Receiving objects:  42% (123/456)",
    // "Resolving deltas: 100% (78/78)") — these repeat rapidly during an active
    // transfer and are throttled below so a slow clone doesn't flood the backend
    // with one HTTP POST per percentage tick.
    private static final Pattern PROGRESS_LINE = Pattern.compile(".*\\d{1,3}%.*");
    private static final long    PROGRESS_FORWARD_THROTTLE_MS = 3_000;

    private boolean runGit(List<String> cmd, File workingDir, String executionId,
                           int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        long lastProgressForwardMs = 0L;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("[git] " + line); // full detail always kept in the local Runner log

                boolean isProgress = PROGRESS_LINE.matcher(line).matches();
                long now = System.currentTimeMillis();
                if (!isProgress || now - lastProgressForwardMs >= PROGRESS_FORWARD_THROTTLE_MS) {
                    client.sendLog(executionId, "INFO", "[git] " + line);
                    if (isProgress) lastProgressForwardMs = now;
                }
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
        boolean isWindows   = System.getProperty("os.name", "").toLowerCase().contains("win");
        String  gradlewName = isWindows ? "gradlew.bat" : "gradlew";
        File    gradlewFile = new File(workspaceDir, gradlewName);

        boolean gradlew        = gradlewFile.exists();
        boolean buildGradle    = new File(workspaceDir, "build.gradle").exists()
                              || new File(workspaceDir, "build.gradle.kts").exists();
        boolean settingsGradle = new File(workspaceDir, "settings.gradle").exists()
                              || new File(workspaceDir, "settings.gradle.kts").exists();

        // ── Ensure gradlew is executable (git clone does not preserve +x on all systems) ──
        if (gradlew && !isWindows) {
            client.sendLog(executionId, "INFO", "🔧 Verificando permisos de gradlew...");
            if (gradlewFile.canExecute()) {
                client.sendLog(executionId, "INFO", "⚠ gradlew ya tenía permisos de ejecución.");
            } else {
                gradlewFile.setExecutable(true);
                if (gradlewFile.canExecute()) {
                    client.sendLog(executionId, "INFO", "✅ Permisos aplicados correctamente.");
                } else {
                    client.sendLog(executionId, "ERROR",
                            "❌ No se pudieron aplicar permisos de ejecución a gradlew.");
                }
            }
        }

        boolean execOk = isWindows || gradlewFile.canExecute();
        boolean valid  = gradlew && buildGradle && settingsGradle && execOk;

        // Report to backend so the UI can show ✓/✗ badges
        client.reportProjectValidation(
                workspaceDir.getAbsolutePath(), gradlew && execOk, buildGradle, settingsGradle, valid);

        if (!gradlew)
            client.sendLog(executionId, "ERROR",
                    "❌ No se encontró " + gradlewName + " en: " + workspaceDir.getAbsolutePath());
        if (gradlew && !execOk)
            client.sendLog(executionId, "ERROR",
                    "❌ gradlew no tiene permisos de ejecución: " + gradlewFile.getAbsolutePath());
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
        boolean isWindows   = System.getProperty("os.name", "").toLowerCase().contains("win");
        File    gradlewFile = new File(workspaceDir, isWindows ? "gradlew.bat" : "gradlew");
        if (!gradlewFile.exists()) return false;
        if (!isWindows && !gradlewFile.canExecute()) gradlewFile.setExecutable(true);
        return (isWindows || gradlewFile.canExecute())
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
