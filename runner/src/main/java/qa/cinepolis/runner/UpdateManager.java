package qa.cinepolis.runner;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // ── Thread-safety ─────────────────────────────────────────────────────
    // scheduleAtFixedRate() ya garantiza que el MISMO task programado nunca se
    // solapa consigo mismo (la siguiente ejecución espera a que la anterior
    // termine). Este flag cubre el único otro escenario posible: un ciclo
    // STOP→START/RESTART crea un workScheduler nuevo mientras una invocación
    // de checkAndApply() del workScheduler anterior sigue en vuelo (p.ej.
    // bloqueada en I/O de red y aún no reactiva a la interrupción de
    // shutdownNow()). CAS no bloqueante — sin locks, sin esperas.
    private final AtomicBoolean checkInProgress = new AtomicBoolean(false);

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
     * Nunca reinicia mientras exista un Job activo o en proceso de ser reclamado:
     * desde que RunnerAgent.beginExclusiveUpdate() tiene éxito y hasta que se libera
     * (descarga fallida) o el JVM termina (update aplicado), RunnerAgent.tryBeginJob()
     * falla determinísticamente — el job-poll thread NO le pide un job nuevo al
     * backend durante toda la descarga/validación/rotate, sin importar cuánto tarde.
     * No es un re-chequeo en el tiempo: es exclusión mutua real vía CAS sobre el
     * mismo AtomicReference que usa el job-poll thread — la ventana de carrera no
     * se reduce, se elimina estructuralmente.
     *
     * Si el gate no está disponible (Runner ocupado), no se descarga nada, no se
     * reemplaza el JAR, no se llama System.exit() — se difiere al siguiente ciclo
     * programado (scheduleAtFixedRate(..., 60, 60, MINUTES)), sin hilos ni timers
     * nuevos.
     *
     * Safe to call from a scheduled thread — does not throw on transient errors.
     */
    public void checkAndApply() {
        if (!checkInProgress.compareAndSet(false, true)) {
            System.out.println("[Update] Verificación ya en curso en otro hilo — se omite esta invocación.");
            return;
        }
        boolean holdingGate = false;
        try {
            VersionInfo remote = fetchVersionInfo();
            if (remote == null) return;

            if (!isNewer(remote.version, currentVersion)) {
                System.out.println("[Update] En version actual: " + currentVersion);
                return;
            }

            System.out.println("[Update] Nueva versión detectada: " + remote.version
                    + " (actual: " + currentVersion + ")");

            // ── Gate exclusivo ───────────────────────────────────────────────────────
            // Desde aquí, RunnerAgent.tryBeginJob() falla mientras este gate siga en
            // GateOwner.UPDATE — el job-poll thread no puede reclamar ningún job
            // nuevo, ni ahora ni en 10 ms, ni durante toda la descarga.
            if (!RunnerAgent.beginExclusiveUpdate()) {
                System.out.println("[Update] Runner ocupado. Actualización diferida.");
                return;
            }
            holdingGate = true;

            System.out.println("[Update] Runner libre. Aplicando actualización.");

            // Download + SHA256 validation with up to 3 retry attempts
            Path newJar = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Path downloaded = downloadJar(remote);

                    // Fetch and compare SHA256 from dedicated endpoint, then fall back to version JSON
                    String sha256 = fetchRemoteSha256(remote);
                    if (sha256 != null && !sha256.isBlank()) {
                        System.out.printf("[Update] Validando integridad SHA256 (intento %d/3)...%n", attempt);
                        if (!ChecksumValidator.validate(downloaded, sha256)) {
                            Files.deleteIfExists(downloaded);
                            System.err.printf("[Update] SHA256 mismatch en intento %d — reintentando...%n", attempt);
                            if (attempt < 3) Thread.sleep(3000L * attempt);
                            continue;
                        }
                        System.out.println("[Update] SHA256 OK.");
                    }

                    newJar = downloaded;
                    break; // success
                } catch (IOException e) {
                    System.err.printf("[Update] Intento %d/3 fallido: %s%n", attempt, e.getMessage());
                    if (attempt < 3) {
                        try { Thread.sleep(3000L * attempt); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            if (newJar == null) {
                System.setProperty("HOST_STATUS", "DEGRADED");
                System.err.println("[Update] Fallo la descarga/validacion tras 3 intentos. Host marcado DEGRADED.");
                return; // finally libera el gate — el job-poll thread puede reclamar de nuevo
            }

            // Verificación final defensiva (cinturón y tirantes): con el gate
            // exclusivo tomado desde ANTES de la descarga, esta condición ya es
            // estructuralmente imposible de violar — tryBeginJob() no ha podido
            // tener éxito ni una sola vez desde beginExclusiveUpdate(). Se conserva
            // como constancia explícita en el log, no como el mecanismo real.
            System.out.println("[Update] Verificación final: sin jobs activos.");

            rotate(newJar);

            // Record baseline so DependencySelfHealingManager can detect tampering later
            try {
                Path activeJar = agentDataDir.resolve("runner").resolve("automationqa-runner.jar");
                ChecksumValidator.writeBaseline(activeJar);
            } catch (Exception e) {
                System.err.println("[Update] Warning: no se pudo guardar baseline SHA256: " + e.getMessage());
            }

            System.out.println("[Update] Reiniciando Runner con la versión " + remote.version
                    + " (gate exclusivo confirmado: sin jobs activos desde antes de la descarga).");
            // Único punto de entrada oficial para terminar la JVM (ver
            // RunnerAgent.requestShutdown): registra el motivo tipado y delega en
            // System.exit(), que dispara el shutdown hook ya existente
            // (stopAllServices(true)) — nunca se llama System.exit() directamente
            // desde aquí. El detalle rico (qué versión) ya quedó en el log de arriba;
            // el motivo tipado solo identifica la categoría.
            RunnerAgent.requestShutdown(0, RunnerAgent.ShutdownReason.AUTO_UPDATE);
            // never returns — el gate queda en UPDATE, pero la JVM ya terminó
        } catch (Throwable t) {
            // Throwable (no solo Exception) a propósito: este método corre dentro de
            // workScheduler.scheduleAtFixedRate(...). Si un Error (OutOfMemoryError,
            // LinkageError, etc.) escapara sin capturar, el JDK cancela silenciosamente
            // TODAS las ejecuciones futuras de este task periódico — el auto-update
            // quedaría deshabilitado para siempre, sin ningún log que lo indique. El
            // gate ya se libera igual vía el finally con Exception simple (Java lo
            // garantiza para cualquier Throwable) — este catch amplio protege el
            // scheduling del mecanismo, no el gate en sí.
            System.err.println("[Update] Error verificando actualizacion: " + t.getMessage());
        } finally {
            checkInProgress.set(false);
            // Si System.exit(0) se ejecutó, este código nunca se alcanza (la JVM ya
            // terminó) — no hay riesgo de "liberar" un gate que ya no importa.
            if (holdingGate) RunnerAgent.endExclusiveUpdate();
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
        // Expected: {"version":"2.3.0","downloadUrl":"/api/runner/download/jar","sha256":"abc..."}
        String version = jsonString(json, "version");
        if (version == null) return null;
        String dlUrl  = jsonString(json, "downloadUrl");
        String sha256 = jsonString(json, "sha256");
        return new VersionInfo(version, dlUrl != null ? dlUrl : DOWNLOAD_PATH, sha256);
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

    // ── Remote SHA256 ─────────────────────────────────────────────────────────

    /**
     * Fetches the official SHA256 for the runner JAR.
     *
     * Priority:
     *   1. GET {backendUrl}/api/runner/download/jar.sha256  — dedicated endpoint (CAMBIO 5)
     *   2. sha256 field from VersionInfo JSON               — fallback
     *
     * Returns null when neither source is available (SHA256 check is then skipped).
     */
    private String fetchRemoteSha256(VersionInfo info) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/runner/download/jar.sha256"))
                    .header("Authorization", "Bearer " + runnerToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                String body = res.body().trim();
                if (!body.isEmpty()) {
                    String hash = body.split("\\s+")[0].trim();
                    if (hash.length() == 64) {
                        System.out.println("[Update] SHA256 obtenido del endpoint dedicado.");
                        return hash;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Update] Endpoint jar.sha256 no accesible — usando SHA256 del JSON de version: "
                    + e.getMessage());
        }
        return info.sha256; // fallback: sha256 from version JSON
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
        final String sha256; // optional — backend may omit for older deploys
        VersionInfo(String version, String downloadUrl, String sha256) {
            this.version     = version;
            this.downloadUrl = downloadUrl;
            this.sha256      = sha256;
        }
    }
}
