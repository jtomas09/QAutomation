package qa.cinepolis.runner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Auto-provisiona el runtime de Network Monitoring (mitmdump) SIN depender de
 * Python/pip/Homebrew/Chocolatey instalados por el usuario — descarga el binario
 * STANDALONE oficial que el propio proyecto mitmproxy publica en su pipeline de
 * release (bundlea su propio Python vía PyInstaller; el usuario nunca lo ve).
 *
 * Reemplaza la versión anterior de esta clase (venv + pip), que sí dependía de un
 * Python del sistema — desviación corregida explícitamente en esta tarea.
 *
 * ORIGEN VERIFICADO (no una URL inventada): rastreado desde
 * `mitmproxy/mitmproxy` → `.github/workflows/main.yml` (`release/build.py
 * standalone-binaries` para Windows/Linux, `macos-app` para macOS) →
 * `release/deploy.py` (sube a `s3://snapshots.mitmproxy.org/{version}/`). Los 3
 * artefactos usados aquí fueron DESCARGADOS Y EJECUTADOS en esta misma sesión
 * para confirmar que funcionan sin Python del sistema, y sus SHA-256 fueron
 * calculados sobre esa descarga real (no copiados de ninguna fuente externa).
 *
 * Versión fijada deliberadamente (reproducibilidad) — subir de versión es un
 * cambio de código explícito con su propio hash verificado, nunca automático.
 */
public final class NetworkRuntimeProvisioner {

    private static final String MITM_VERSION = "12.2.3";
    private static final String BASE_URL = "https://snapshots.mitmproxy.org/" + MITM_VERSION + "/";

    /**
     * @param filename              nombre del archivo tal como lo publica mitmproxy
     * @param sha256                hash SHA-256 verificado (ver Javadoc de clase)
     * @param zip                   true = extraer con java.util.zip (Windows); false = tar.gz (macOS)
     * @param executableRelativePath ruta del ejecutable DENTRO del runtime ya extraído
     * @param tarMemberToExtract    para tar.gz: qué miembro completo extraer (macOS necesita
     *                              el .app COMPLETO — mitmdump depende de Contents/Frameworks/,
     *                              confirmado extrayendo solo el binario y viendo que falla con
     *                              "Failed to load Python shared library")
     */
    private record Artifact(String filename, String sha256, boolean zip,
                             String executableRelativePath, String tarMemberToExtract) {}

    private static final Map<String, Artifact> ARTIFACTS = Map.of(
            "MACOS:aarch64", new Artifact(
                    "mitmproxy-" + MITM_VERSION + "-macos-arm64.tar.gz",
                    "0a09ee3b82569e8985aff8186e4792618b8e5d0c766098db093d09a87d4b013a",
                    false, "mitmproxy.app/Contents/MacOS/mitmdump", "mitmproxy.app"),
            "MACOS:x86_64", new Artifact(
                    "mitmproxy-" + MITM_VERSION + "-macos-x86_64.tar.gz",
                    "7998187f5a0d399ab796af4523d3ad830ebe690726a41bc3e1df47a8e477a641",
                    false, "mitmproxy.app/Contents/MacOS/mitmdump", "mitmproxy.app"),
            "WINDOWS:x86_64", new Artifact(
                    "mitmproxy-" + MITM_VERSION + "-windows-x86_64.zip",
                    "04a01ea95ae96df75058a893e774957d294e69012dab1f4e256ce2b0c6725483",
                    true, "mitmdump.exe", null)
    );

    private final Path   agentDataDir;
    private final String os; // "MACOS" | "WINDOWS" | "LINUX" — ver RunnerConfig.detectOs()

    private volatile String  resolvedMitmdump    = null;
    private volatile boolean provisioningFailed  = false;
    private volatile String  lastFailureReason    = null;

    public NetworkRuntimeProvisioner(Path agentDataDir, String os) {
        this.agentDataDir = agentDataDir;
        this.os = os;
    }

    private Path runtimeDir() {
        return agentDataDir.resolve("runtime").resolve("network").resolve(MITM_VERSION);
    }

    private static String archId() {
        String raw = System.getProperty("os.arch", "").toLowerCase();
        return (raw.contains("aarch64") || raw.contains("arm64")) ? "aarch64" : "x86_64";
    }

    private Artifact resolveArtifact() {
        return ARTIFACTS.get(os + ":" + archId());
    }

    public String lastFailureReason() { return lastFailureReason; }

    /**
     * Devuelve la ruta absoluta a `mitmdump`, descargándolo/verificándolo/
     * extrayéndolo si hace falta (una sola vez, cacheado en disco entre
     * ejecuciones — nunca se descarga de nuevo si ya está presente y verificado).
     * Nunca lanza. Devuelve null si la plataforma no está soportada o cualquier
     * paso falla — el llamador debe tratarlo como "Network Monitoring no
     * disponible ahora", nunca como error que interrumpa el Job.
     */
    public synchronized String resolveMitmdump() {
        if (resolvedMitmdump != null) return resolvedMitmdump;
        if (provisioningFailed) return null;

        Artifact artifact = resolveArtifact();
        if (artifact == null) {
            lastFailureReason = "Plataforma/arquitectura no soportada: " + os + "/" + archId();
            System.out.println("[NetworkMonitoring][Provisioner] " + lastFailureReason);
            provisioningFailed = true;
            return null;
        }

        Path execPath = runtimeDir().resolve(artifact.executableRelativePath()
                .replace("/", File.separator));
        if (Files.isExecutable(execPath) && probe(execPath)) {
            resolvedMitmdump = execPath.toString();
            return resolvedMitmdump;
        }

        System.out.println("[NetworkMonitoring][Provisioner] Network Runtime no encontrado en "
                + execPath + " — preparando automáticamente (una sola vez, sin Python/pip/Homebrew)...");

        Path archiveFile = agentDataDir.resolve("runtime").resolve("network")
                .resolve(artifact.filename());
        try {
            Files.createDirectories(runtimeDir());
            Files.createDirectories(archiveFile.getParent());

            downloadWithRetry(BASE_URL + artifact.filename(), archiveFile);

            String actualSha = sha256(archiveFile);
            if (!actualSha.equalsIgnoreCase(artifact.sha256())) {
                Files.deleteIfExists(archiveFile);
                throw new IOException("Checksum SHA-256 no coincide (esperado=" + artifact.sha256()
                        + ", real=" + actualSha + ") — descarga descartada por seguridad, NO se ejecuta.");
            }
            System.out.println("[NetworkMonitoring][Provisioner] ✓ Checksum SHA-256 verificado.");

            if (artifact.zip()) {
                unzip(archiveFile, runtimeDir());
            } else {
                extractTarMember(archiveFile, runtimeDir(), artifact.tarMemberToExtract());
            }
            Files.deleteIfExists(archiveFile);

            if (!"WINDOWS".equals(os)) makeExecutableRecursive(runtimeDir());

            if (Files.isExecutable(execPath) && probe(execPath)) {
                resolvedMitmdump = execPath.toString();
                System.out.println("[NetworkMonitoring][Provisioner] ✓ Network Runtime disponible: " + resolvedMitmdump);
                return resolvedMitmdump;
            }
            throw new IOException("mitmdump no ejecutable tras la extracción (" + execPath + ")");

        } catch (Exception e) {
            lastFailureReason = e.getMessage();
            System.out.println("[NetworkMonitoring][Provisioner] No se pudo preparar el runtime: "
                    + e.getMessage() + " — Network Monitoring quedará NO DISPONIBLE, el Job continúa normalmente.");
            try { Files.deleteIfExists(archiveFile); } catch (Exception ignored) {}
            provisioningFailed = true;
            return null;
        }
    }

    // ── Descarga (mismo patrón HttpClient que PlatformToolsManager) ───────────

    private static final int NETWORK_RETRY_ATTEMPTS = 3;

    private void downloadWithRetry(String url, Path dest) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= NETWORK_RETRY_ATTEMPTS; attempt++) {
            try {
                System.out.println("[NetworkMonitoring][Provisioner] Descargando (intento " + attempt
                        + "/" + NETWORK_RETRY_ATTEMPTS + "): " + url);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build();
                HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(dest,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING));
                if (res.statusCode() != 200) {
                    throw new IOException("HTTP " + res.statusCode());
                }
                return;
            } catch (Exception e) {
                last = e;
                try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
                if (attempt < NETWORK_RETRY_ATTEMPTS) {
                    try { Thread.sleep(2000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw new IOException("Descarga fallida tras " + NETWORK_RETRY_ATTEMPTS + " intentos: "
                + (last != null ? last.getMessage() : "?"), last);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ── Extracción ───────────────────────────────────────────────────────────

    /** Windows: puro Java, sin proceso externo (mismo patrón zip-slip-safe que PlatformToolsManager). */
    private static void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) { zis.closeEntry(); continue; } // zip-slip
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * macOS: usa el `tar` del propio sistema operativo — presente por defecto en
     * TODAS las instalaciones de macOS desde 2005 (parte del SO, no una
     * herramienta que el usuario instale, igual que `scutil`/`xcrun` ya usados
     * en RunnerConfig/WdaManager). Se evita escribir un parser TAR/PAX propio en
     * Java: el archivo real usa extended headers PAX para rutas largas
     * (confirmado — varios paths internos superan los 100 bytes del formato
     * USTAR clásico), y un parser casero mal probado arriesga extraer contenido
     * incorrecto en silencio — el binario `tar` del SO ya maneja esto de forma
     * robusta y es la opción más segura.
     */
    private static void extractTarMember(Path tarGzFile, Path destDir, String member) throws Exception {
        Files.createDirectories(destDir);
        Process p = new ProcessBuilder("tar", "-xzf", tarGzFile.toString(), "-C", destDir.toString(), member)
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        if (!done) { p.destroyForcibly(); throw new IOException("tar timeout"); }
        if (p.exitValue() != 0) throw new IOException("tar exit=" + p.exitValue() + " " + out.trim());
    }

    private static void makeExecutableRecursive(Path dir) {
        try {
            Files.walk(dir).filter(pth -> !Files.isDirectory(pth)).forEach(pth -> {
                try {
                    Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(pth));
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    perms.add(PosixFilePermission.GROUP_EXECUTE);
                    perms.add(PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(pth, perms);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private static boolean probe(Path bin) {
        try {
            Process p = new ProcessBuilder(bin.toString(), "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
