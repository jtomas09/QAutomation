package qa.cinepolis.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
 *   1. Workspace ausente                       → git clone
 *   2. Workspace presente y sano                → git fetch + reset --hard + clean -fd + pull --ff-only
 *   3. Workspace presente pero CORRUPTO         → eliminar + git clone (auto-recuperación, sin intervención manual)
 *   4. .git apunta a un repositorio DISTINTO    → eliminar + git clone
 *   5. Validate Gradle structure (gradlew, build.gradle, settings.gradle)
 *   6. Return workspace File ready for ProcessBuilder.directory()
 *
 * "Sano" ya NO significa solo "la carpeta .git existe" — significa que git lo
 * reconoce como un repositorio válido (rev-parse resuelve) Y que su remote
 * origin coincide con el repositorio configurado. Esto evita reclonar un
 * checkout perfectamente bueno por una comprobación superficial, y evita
 * confiar en un checkout realmente corrupto solo porque la carpeta ".git"
 * existe físicamente.
 *
 * Un fallo de RED (fetch no responde) NUNCA borra el workspace — se sigue
 * usando la copia existente. Solo una corrupción real (refs ilegibles, reset
 * irrecuperable, o remote distinto) dispara el borrado + re-clonado.
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

    private enum WorkspaceState { FRESH, VALID, CORRUPTED, FOREIGN_REPO, NO_GIT_BUT_GRADLE }
    private enum UpdateResult   { SUCCESS, NETWORK_FAILURE, CORRUPTED }
    private record WorkspaceInspection(WorkspaceState state, String detail) {}

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

        WorkspaceInspection insp = inspectWorkspace();

        switch (insp.state()) {
            case FRESH -> {
                logWorkspaceStatus(executionId, "📂 Workspace no encontrado", "No existe",
                        "Clonando repositorio...");
                if (!cloneRepo(executionId)) {
                    client.sendLog(executionId, "ERROR", "❌ No fue posible sincronizar el repositorio.");
                    return null;
                }
                client.sendLog(executionId, "INFO", "✅ Clonación completada.");
            }
            case NO_GIT_BUT_GRADLE -> {
                logWorkspaceStatus(executionId, "📂 Workspace encontrado", "Válido (sin historial git)",
                        "No es posible sincronizar con el repositorio remoto — se usará la copia existente.");
                client.sendLog(executionId, "WARN", "⚠ Utilizando última versión disponible.");
                return validateAndReturn(executionId);
            }
            case CORRUPTED, FOREIGN_REPO -> {
                String motivo = insp.detail() != null ? insp.detail()
                        : "git no reconoce el workspace como un repositorio válido "
                        + "(refs corruptas, clon interrumpido, o carpeta .git incompleta).";
                logCorruptedAndReclone(executionId, motivo);
                if (!cloneRepo(executionId)) {
                    client.sendLog(executionId, "ERROR", "❌ No fue posible sincronizar el repositorio.");
                    return null;
                }
                client.sendLog(executionId, "INFO", "✅ Clonación completada.");
            }
            case VALID -> {
                logWorkspaceStatus(executionId, "📂 Workspace encontrado", "Válido",
                        "Actualizando repositorio...");
                UpdateResult result = updateRepo(executionId);
                switch (result) {
                    case SUCCESS -> client.sendLog(executionId, "INFO", "✅ Workspace actualizado correctamente");
                    case NETWORK_FAILURE -> {
                        client.sendLog(executionId, "ERROR",
                                "❌ No fue posible sincronizar el repositorio (fallo de red).");
                        client.sendLog(executionId, "WARN", "⚠ Utilizando última versión disponible.");
                    }
                    case CORRUPTED -> {
                        logCorruptedAndReclone(executionId,
                                "git reset --hard falló de forma irrecuperable durante la actualización.");
                        if (!cloneRepo(executionId)) {
                            client.sendLog(executionId, "ERROR", "❌ No fue posible sincronizar el repositorio.");
                            return null;
                        }
                        client.sendLog(executionId, "INFO", "✅ Clonación completada.");
                    }
                }
            }
        }

        return validateAndReturn(executionId);
    }

    public File getWorkspaceDir() { return workspaceDir; }

    // ── Inspección del workspace ──────────────────────────────────────────────

    /**
     * Determina el estado real del workspace — nunca se conforma con "la carpeta
     * .git existe". Un repositorio corrupto (clon interrumpido, refs ilegibles) o
     * apuntando a un remote distinto al configurado se trata igual que "ausente":
     * dispara un re-clonado automático, sin intervención manual.
     */
    private WorkspaceInspection inspectWorkspace() {
        File dotGit = new File(workspaceDir, ".git");
        if (!dotGit.exists()) {
            if (workspaceDir.exists() && isValidGradleProject()) {
                return new WorkspaceInspection(WorkspaceState.NO_GIT_BUT_GRADLE, null);
            }
            return new WorkspaceInspection(WorkspaceState.FRESH, null);
        }
        if (!isGitDirHealthy()) {
            return new WorkspaceInspection(WorkspaceState.CORRUPTED,
                    "git no reconoce el workspace como un repositorio válido "
                    + "(refs corruptas, clon interrumpido, o carpeta .git incompleta).");
        }
        String mismatch = checkOriginMismatchReason();
        if (mismatch != null) {
            return new WorkspaceInspection(WorkspaceState.FOREIGN_REPO, mismatch);
        }
        return new WorkspaceInspection(WorkspaceState.VALID, null);
    }

    /** ¿Resuelve git este directorio como un repositorio real y no truncado? */
    private boolean isGitDirHealthy() {
        return runGitQuiet(List.of("git", "rev-parse", "--is-inside-work-tree"))
            && runGitQuiet(List.of("git", "rev-parse", "HEAD"));
    }

    /** Si el remote "origin" no coincide con el repositorio configurado, es un repo distinto. */
    private String checkOriginMismatchReason() {
        String currentOrigin = captureGitOutput(List.of("git", "remote", "get-url", "origin"));
        if (currentOrigin == null || currentOrigin.isBlank()) {
            return "El workspace no tiene un remote 'origin' configurado.";
        }
        if (!normalizeRepoUrl(currentOrigin).equalsIgnoreCase(normalizeRepoUrl(repoUrl))) {
            return "El repositorio configurado cambió: " + currentOrigin.trim() + " → " + repoUrl;
        }
        return null;
    }

    private static String normalizeRepoUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        if (u.endsWith("/"))    u = u.substring(0, u.length() - 1);
        return u.toLowerCase();
    }

    /**
     * Elimina un lock file residual de un proceso git anterior interrumpido
     * (p.ej. Runner detenido a mitad de un fetch). El Runner opera un workspace
     * a la vez de forma secuencial — si llegamos aquí, ningún proceso git legítimo
     * sigue sosteniendo ese lock.
     */
    private void clearStaleLocks(String executionId) {
        File lock = new File(workspaceDir, ".git/index.lock");
        if (lock.exists()) {
            client.sendTechLog(executionId,
                    "[Workspace] Lock residual detectado (.git/index.lock) — eliminando antes de continuar.");
            lock.delete();
        }
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    private void logWorkspaceStatus(String executionId, String titulo, String estado, String accion) {
        client.sendLog(executionId, "INFO",
                titulo + "\n\n"
                + "Ruta:\n" + workspaceDir.getAbsolutePath() + "\n\n"
                + "Estado:\n" + estado + "\n\n"
                + "Repositorio:\n" + repoUrl + "\n\n"
                + "Branch:\n" + repoBranch + "\n\n"
                + "Acción:\n" + accion);
    }

    private void logCorruptedAndReclone(String executionId, String motivo) {
        client.sendLog(executionId, "WARN",
                "⚠ Workspace corrupto\n\n"
                + "Motivo:\n" + motivo + "\n\n"
                + "Acción:\n" + "Eliminando workspace...");
        deleteDirectory(workspaceDir);
        client.sendLog(executionId, "INFO", "📥 Clonando nuevamente...");
    }

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

    /**
     * Actualiza un workspace ya sano: fetch → reset --hard → clean -fd → pull --ff-only.
     *
     * reset --hard ya deja el branch local exactamente igual a origin/<branch> —
     * el pull --ff-only final es una verificación de seguridad (debería ser
     * siempre un no-op "Already up to date"), no un paso funcionalmente necesario;
     * se conserva porque hace explícito en los logs que el estado final coincide
     * con el remoto, y detectaría de inmediato cualquier inconsistencia inesperada.
     *
     * Un fallo de fetch (red) devuelve NETWORK_FAILURE — el workspace existente NO
     * se toca. Un fallo de reset --hard devuelve CORRUPTED — ahí sí se justifica
     * eliminar y re-clonar, porque un reset que falla indica un repositorio en un
     * estado que git ya no puede reconciliar por su cuenta.
     */
    private UpdateResult updateRepo(String executionId) {
        clearStaleLocks(executionId);

        boolean fetched = false;
        for (int attempt = 1; attempt <= NETWORK_OP_MAX_ATTEMPTS; attempt++) {
            client.sendLog(executionId, "INFO", "📥 git fetch");
            try {
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
        if (!fetched) return UpdateResult.NETWORK_FAILURE;

        boolean resetOk;
        client.sendLog(executionId, "INFO", "♻ git reset --hard origin/" + repoBranch);
        try {
            resetOk = runGit(
                    List.of("git", "reset", "--hard", "origin/" + repoBranch),
                    workspaceDir, executionId, 30);
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️ Error en git reset --hard: " + e.getMessage());
            resetOk = false;
        }
        if (!resetOk) return UpdateResult.CORRUPTED;

        client.sendLog(executionId, "INFO", "🧹 git clean -fd");
        try {
            if (!runGit(List.of("git", "clean", "-fd"), workspaceDir, executionId, 30)) {
                client.sendLog(executionId, "WARN",
                        "⚠️ git clean -fd no se completó — archivos sin seguimiento podrían persistir.");
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️ Error en git clean -fd: " + e.getMessage());
        }

        client.sendLog(executionId, "INFO", "⬇ git pull --ff-only");
        try {
            if (!runGit(List.of("git", "pull", "--ff-only", "origin", repoBranch),
                    workspaceDir, executionId, 60)) {
                client.sendLog(executionId, "WARN",
                        "⚠️ git pull --ff-only no confirmó fast-forward — "
                        + "el reset --hard anterior ya sincronizó el branch, se continúa.");
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️ Error en git pull --ff-only: " + e.getMessage());
        }

        return UpdateResult.SUCCESS;
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

    /** Variante silenciosa para chequeos internos de salud — no reenvía nada al backend. */
    private boolean runGitQuiet(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workspaceDir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes(); // drenar sin loguear
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Captura stdout de un comando git de solo lectura (p.ej. remote get-url). */
    private String captureGitOutput(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workspaceDir);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return (done && p.exitValue() == 0) ? out.trim() : null;
        } catch (Exception e) {
            return null;
        }
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
