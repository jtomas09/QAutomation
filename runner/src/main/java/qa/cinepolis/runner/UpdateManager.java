package qa.cinepolis.runner;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Checks backend for a newer runner version, downloads the new JAR,
 * and restarts the JVM to apply the update.
 *
 * Backend endpoint: GET {backendUrl}/api/runner/version
 * Expected JSON: { "version": "2.3.0", "downloadUrl": "/api/runner/download/jar" }
 *
 * Update steps:
 *   1. Parse backend version. If equal or older than current → skip.
 *   2. Download new JAR to <agentDataDir>/update/automationqa-runner-<version>.jar
 *   3. Atomically replace <agentDataDir>/runner/automationqa-runner.jar
 *   4. Keep one rollback copy (previous version).
 *   5. Exec the new JAR with same JVM args (in-process replacement via System.exit(0)
 *      so the shell wrapper loop restarts the process).
 */
public class UpdateManager {

    private static final String VERSION_PATH = "/api/runner/version";
    private static final String DOWNLOAD_PATH = "/api/runner/download/jar";

    private final String backendUrl;
    private final String runnerToken;
    private final String currentVersion;
    private final Path   agentDataDir;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateManager(String backendUrl, String runnerToken,
                         String currentVersion, Path agentDataDir) {
        this.backendUrl      = backendUrl;
        this.runnerToken     = runnerToken;
        this.currentVersion  = currentVersion;
        this.agentDataDir    = agentDataDir;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Checks for an update and applies it if available.
     * If the update is applied the method triggers System.exit(0) so the
     * outer shell loop (run-runner.bat / run-runner.sh) restarts the JVM
     * with the new JAR.
     *
     * Safe to call from a scheduled thread — does not throw on transient errors.
     */
    public void checkAndApply() {
        try {
            VersionInfo remote = fetchVersionInfo();
            if (remote == null) return;

            if (!isNewer(remote.version, currentVersion)) {
                System.out.println("[Update] En version actual: " + currentVersion);
                return;
            }

            System.out.println("[Update] Nueva version disponible: " + remote.version +
                    " (actual: " + currentVersion + ")");
            Path newJar = downloadJar(remote);
            rotate(newJar);

            System.out.println("[Update] Actualizacion aplicada. Reiniciando...");
            System.exit(0); // shell loop restarts JVM with updated JAR
        } catch (Exception e) {
            System.err.println("[Update] Error verificando actualizacion: " + e.getMessage());
        }
    }

    // ── Version check ─────────────────────────────────────────────────────

    private VersionInfo fetchVersionInfo() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + VERSION_PATH))
                .header("Authorization", "Bearer " + runnerToken)
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            // Endpoint not deployed yet — silently skip
            return null;
        }
        if (res.statusCode() != 200) {
            throw new IOException("GET " + VERSION_PATH + " → HTTP " + res.statusCode());
        }

        return parseVersionInfo(res.body());
    }

    private VersionInfo parseVersionInfo(String json) {
        // Minimal JSON parse without Jackson dependency (runner has no jackson at compile time)
        // Expected: {"version":"2.3.0","downloadUrl":"/api/runner/download/jar"}
        String version = jsonString(json, "version");
        if (version == null) return null;
        String dlUrl = jsonString(json, "downloadUrl");
        return new VersionInfo(version, dlUrl != null ? dlUrl : DOWNLOAD_PATH);
    }

    /** Returns true when candidate is strictly newer than current using semver. */
    static boolean isNewer(String candidate, String current) {
        try {
            int[] a = semver(candidate);
            int[] b = semver(current);
            for (int i = 0; i < 3; i++) {
                if (a[i] > b[i]) return true;
                if (a[i] < b[i]) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int[] semver(String v) {
        String[] parts = v.replaceAll("[^0-9.]", "").split("\\.", 3);
        int[] out = {0, 0, 0};
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    // ── Download ──────────────────────────────────────────────────────────

    private Path downloadJar(VersionInfo info) throws Exception {
        String url = info.downloadUrl.startsWith("http")
                ? info.downloadUrl
                : backendUrl + info.downloadUrl;

        Path updateDir = agentDataDir.resolve("update");
        Files.createDirectories(updateDir);
        Path target = updateDir.resolve("automationqa-runner-" + info.version + ".jar");

        System.out.println("[Update] Descargando: " + url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + runnerToken)
                .timeout(Duration.ofMinutes(5))
                .GET().build();

        HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(target));
        if (res.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("Descarga fallo: HTTP " + res.statusCode());
        }

        long size = Files.size(target);
        if (size < 10_000) {
            Files.deleteIfExists(target);
            throw new IOException("JAR descargado demasiado pequeno (" + size + " bytes). Abortando update.");
        }
        System.out.printf("[Update] Descargado: %.1f KB%n", size / 1024.0);
        return target;
    }

    // ── Rotate ────────────────────────────────────────────────────────────

    /**
     * Atomically replaces the active JAR.
     * Previous version is kept as automationqa-runner.jar.bak for rollback.
     */
    private void rotate(Path newJar) throws IOException {
        Path runnerDir  = agentDataDir.resolve("runner");
        Files.createDirectories(runnerDir);
        Path activeJar  = runnerDir.resolve("automationqa-runner.jar");
        Path backupJar  = runnerDir.resolve("automationqa-runner.jar.bak");

        // Keep previous as backup
        if (Files.exists(activeJar)) {
            Files.copy(activeJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Update] Backup guardado: " + backupJar);
        }

        // Atomic move
        Files.move(newJar, activeJar, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        System.out.println("[Update] JAR actualizado: " + activeJar);
    }

    // ── Minimal JSON util ─────────────────────────────────────────────────

    private String jsonString(String json, String key) {
        String pat = "\"" + key + "\"";
        int idx = json.indexOf(pat);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pat.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    // ── DTO ───────────────────────────────────────────────────────────────

    private static class VersionInfo {
        final String version;
        final String downloadUrl;
        VersionInfo(String version, String downloadUrl) {
            this.version     = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
