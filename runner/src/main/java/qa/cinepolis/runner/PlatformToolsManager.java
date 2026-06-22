package qa.cinepolis.runner;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages the embedded ADB binary.
 *
 * The Agent NEVER uses PATH, ANDROID_HOME, or Android Studio.
 * ADB is always the embedded binary at:
 *
 *   Windows : {runnerDir}\platform-tools\adb.exe
 *   Mac/Linux: {runnerDir}/platform-tools/adb
 *
 * resolveAdb() ALWAYS returns the embedded path (never null).
 * If the binary is absent, it is downloaded automatically (3 attempts).
 * After resolveAdb(), call isAdbFunctional() to know if ADB truly works.
 */
public class PlatformToolsManager {

    private static final String WIN_URL   = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";
    private static final String MAC_URL   = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip";
    private static final String LINUX_URL = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip";

    private static final long   MIN_ZIP_BYTES = 5_000_000L; // Google zip is ~8 MB

    private final Path   runnerDir;
    private final String os;
    private final String backendUrl; // used as proxy fallback if Google CDN is blocked

    private volatile String  resolvedAdb  = null;
    private volatile String  cachedVersion = null;
    private volatile boolean adbFunctional = false;

    public PlatformToolsManager(Path runnerDir, String os, String backendUrl) {
        this.runnerDir  = runnerDir;
        this.os         = os;
        this.backendUrl = backendUrl;
    }

    /** Convenience constructor for tests / callers without backend access. */
    public PlatformToolsManager(Path runnerDir, String os) {
        this(runnerDir, os, null);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns the absolute path to the embedded adb binary.
     * Downloads platform-tools if absent (up to 3 attempts).
     * NEVER returns null — callers use isAdbFunctional() to check usability.
     */
    public String resolveAdb() {
        if (resolvedAdb != null) return resolvedAdb;

        Path embedded = embeddedAdbPath();

        System.out.println("[PlatformTools] ADB Path:   " + embedded);
        System.out.println("[PlatformTools] ADB Exists: " + Files.exists(embedded));

        // Binary already present and functional
        if (Files.exists(embedded) && probe(embedded.toString())) {
            resolvedAdb   = embedded.toString();
            adbFunctional = true;
            System.out.println("[PlatformTools] ADB listo (embebido).");
            return resolvedAdb;
        }

        // Binary absent or broken — download
        System.out.println("[PlatformTools] ADB no encontrado en ruta embebida. Descargando platform-tools...");
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.printf("[PlatformTools] Descarga intento %d/3...%n", attempt);
            try {
                download();
                if (Files.exists(embedded) && probe(embedded.toString())) {
                    resolvedAdb   = embedded.toString();
                    adbFunctional = true;
                    System.out.println("[PlatformTools] ADB descargado y listo: " + resolvedAdb);
                    return resolvedAdb;
                }
                System.err.println("[PlatformTools] adb.exe no encontrado tras extraccion en intento " + attempt);
            } catch (Exception e) {
                System.err.printf("[PlatformTools] Intento %d/3 fallido: %s%n", attempt, e.getMessage());
            }
            if (attempt < 3) {
                try { Thread.sleep(3000L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }

        // All attempts failed — still return embedded path so callers log clearly
        resolvedAdb   = embedded.toString();
        adbFunctional = false;
        System.err.println("[PlatformTools] ADB NO disponible. Verifica conexion a internet.");
        System.err.println("[PlatformTools] Ruta esperada: " + resolvedAdb);
        return resolvedAdb;
    }

    /** True only when the embedded adb binary exists and responds to 'adb version'. */
    public boolean isAdbFunctional() {
        resolveAdb(); // ensure resolution was attempted
        return adbFunctional;
    }

    /**
     * Returns the ADB version string (e.g. "35.0.2").
     * Returns "unavailable" if ADB is not functional.
     */
    public String getAdbVersion() {
        if (cachedVersion != null) return cachedVersion;
        if (!isAdbFunctional()) { cachedVersion = "unavailable"; return cachedVersion; }
        try {
            Process p = new ProcessBuilder(resolvedAdb, "version")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!done) p.destroyForcibly();
            for (String line : out.split("\n")) {
                if (line.startsWith("Android Debug Bridge version")) {
                    cachedVersion = line.substring("Android Debug Bridge version ".length()).trim();
                    return cachedVersion;
                }
            }
            cachedVersion = "unknown";
        } catch (Exception e) {
            cachedVersion = "error";
        }
        return cachedVersion;
    }

    public Path getToolsDir() {
        return runnerDir.resolve("platform-tools");
    }

    /**
     * Clears cached resolution so the next resolveAdb() call re-attempts
     * the download. Used by SelfHealingManager between retry cycles.
     */
    public void reset() {
        this.resolvedAdb   = null;
        this.cachedVersion = null;
        this.adbFunctional = false;
    }

    /**
     * Full ADB daemon recovery:
     *   1. adb kill-server  — stops any hung/crashed daemon
     *   2. adb start-server — brings a fresh daemon up
     *   3. reset() + resolveAdb() — re-validates the binary
     *
     * Returns true when ADB is functional after the operation.
     *
     * Used by DependencySelfHealingManager when ADB is unreachable
     * but the binary still exists (daemon crash, not a missing-file scenario).
     */
    public boolean healAdbServer() {
        Path adbBin = embeddedAdbPath();
        if (Files.exists(adbBin)) {
            String adb = adbBin.toString();
            try {
                System.out.println("[PlatformTools] Reiniciando ADB server (kill → start)...");

                Process kill = new ProcessBuilder(adb, "kill-server")
                        .redirectErrorStream(true).start();
                kill.waitFor(8, TimeUnit.SECONDS);
                if (kill.isAlive()) kill.destroyForcibly();

                Thread.sleep(800L);

                Process start = new ProcessBuilder(adb, "start-server")
                        .redirectErrorStream(true).start();
                start.waitFor(15, TimeUnit.SECONDS);
                if (start.isAlive()) start.destroyForcibly();

                Thread.sleep(1500L);
                System.out.println("[PlatformTools] ADB server reiniciado.");
            } catch (Exception e) {
                System.err.println("[PlatformTools] Error al reiniciar ADB server: " + e.getMessage());
            }
        }
        reset();
        resolveAdb();
        return isAdbFunctional();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Path embeddedAdbPath() {
        return runnerDir.resolve("platform-tools").resolve(isWindows() ? "adb.exe" : "adb");
    }

    private boolean probe(String adbPath) {
        try {
            Process p = new ProcessBuilder(adbPath, "version")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            p.getInputStream().readAllBytes();
            if (!done) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception ignored) { return false; }
    }

    private void download() throws Exception {
        String url     = isWindows() ? WIN_URL : ("MACOS".equals(os) ? MAC_URL : LINUX_URL);
        Path   zipFile = runnerDir.resolve("platform-tools.zip");

        Files.createDirectories(runnerDir);
        Files.deleteIfExists(zipFile);

        System.out.println("[PlatformTools] Descargando: " + url);

        // Attempt 1: Java HttpClient (cross-platform, follows all redirects)
        boolean downloaded = false;
        try {
            downloadViaHttpClient(url, zipFile);
            if (Files.exists(zipFile) && Files.size(zipFile) >= MIN_ZIP_BYTES) {
                downloaded = true;
                System.out.printf("[PlatformTools] Descarga OK via HttpClient: %.1f MB%n",
                        Files.size(zipFile) / 1_048_576.0);
            }
        } catch (Exception e) {
            System.err.println("[PlatformTools] HttpClient fallo: " + e.getMessage());
            Files.deleteIfExists(zipFile);
        }

        // Attempt 2 (Windows only): PowerShell — handles NTLM proxy, Windows credentials
        if (!downloaded && isWindows()) {
            System.out.println("[PlatformTools] Intentando descarga via PowerShell (soporte proxy NTLM)...");
            try {
                downloadViaPowerShell(url, zipFile);
                if (Files.exists(zipFile) && Files.size(zipFile) >= MIN_ZIP_BYTES) {
                    downloaded = true;
                    System.out.printf("[PlatformTools] Descarga OK via PowerShell: %.1f MB%n",
                            Files.size(zipFile) / 1_048_576.0);
                }
            } catch (Exception e) {
                System.err.println("[PlatformTools] PowerShell fallo: " + e.getMessage());
                Files.deleteIfExists(zipFile);
            }
        }

        // Attempt 3: Backend proxy — bypasses corporate firewall blocks on dl.google.com
        // The backend (Railway) can always reach Google; the runner can always reach the backend.
        if (!downloaded && backendUrl != null && !backendUrl.isBlank()) {
            String platform   = isWindows() ? "windows" : ("MACOS".equals(os) ? "macos" : "linux");
            String proxyUrl   = backendUrl.replaceAll("/+$", "") +
                                "/api/runner/download/platform-tools/" + platform;
            System.out.println("[PlatformTools] Intentando via proxy backend: " + proxyUrl);
            try {
                downloadViaHttpClient(proxyUrl, zipFile);
                if (Files.exists(zipFile) && Files.size(zipFile) >= MIN_ZIP_BYTES) {
                    downloaded = true;
                    System.out.printf("[PlatformTools] Descarga OK via backend proxy: %.1f MB%n",
                            Files.size(zipFile) / 1_048_576.0);
                }
            } catch (Exception e) {
                System.err.println("[PlatformTools] Backend proxy fallo: " + e.getMessage());
                Files.deleteIfExists(zipFile);
            }
        }

        if (!downloaded) {
            Files.deleteIfExists(zipFile);
            throw new IOException(
                    "Fallo la descarga de platform-tools. Intentado: Google CDN, PowerShell, backend proxy. " +
                    "Verifica acceso a internet desde este equipo.");
        }

        System.out.println("[PlatformTools] Extrayendo en: " + runnerDir);
        unzip(zipFile, runnerDir);
        Files.deleteIfExists(zipFile);

        // chmod +x on Unix
        if (!isWindows()) {
            Path toolsDir = runnerDir.resolve("platform-tools");
            if (Files.exists(toolsDir)) {
                Files.walk(toolsDir)
                        .filter(p -> !Files.isDirectory(p))
                        .forEach(p -> {
                            try {
                                Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(p));
                                perms.add(PosixFilePermission.OWNER_EXECUTE);
                                perms.add(PosixFilePermission.GROUP_EXECUTE);
                                Files.setPosixFilePermissions(p, perms);
                            } catch (Exception ignored) {}
                        });
            }
        }
        System.out.println("[PlatformTools] Extraccion completa.");
    }

    private void downloadViaHttpClient(String url, Path zipFile) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET().build();

        HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(zipFile));
        if (res.statusCode() != 200) {
            Files.deleteIfExists(zipFile);
            throw new IOException("HTTP " + res.statusCode() + " descargando platform-tools");
        }
    }

    /**
     * Downloads using PowerShell, which natively handles Windows proxy authentication
     * (NTLM, Kerberos) and reads IE/WinHTTP proxy settings. Used as fallback when
     * Java HttpClient fails in corporate networks.
     *
     * Uses a temp .ps1 file instead of -Command to avoid TerminatorExpectedAtEndOfString
     * when the zip path contains apostrophes (e.g. C:\Users\John's PC\...).
     */
    private void downloadViaPowerShell(String url, Path zipFile) throws Exception {
        // Escape any double-quotes in paths for embedding in a PS double-quoted string
        String psUrl = url.replace("\"", "`\"");
        String psDst = zipFile.toString().replace("\"", "`\"");

        String script =
                "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\r\n" +
                "$downloadUrl = \"" + psUrl + "\"\r\n" +
                "$zipPath     = \"" + psDst + "\"\r\n" +
                "Write-Host \"URL: $downloadUrl\"\r\n" +
                "Write-Host \"Destino: $zipPath\"\r\n" +
                "try {\r\n" +
                "    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -UseBasicParsing -TimeoutSec 300\r\n" +
                "    Write-Host 'OK-IWR'\r\n" +
                "} catch {\r\n" +
                "    try {\r\n" +
                "        (New-Object System.Net.WebClient).DownloadFile($downloadUrl, $zipPath)\r\n" +
                "        Write-Host 'OK-WC'\r\n" +
                "    } catch {\r\n" +
                "        Write-Host \"ERROR: $($_.Exception.Message)\"\r\n" +
                "        Write-Host \"ERROR DETALLE: $($Error[0])\"\r\n" +
                "        exit 1\r\n" +
                "    }\r\n" +
                "}\r\n";

        Path ps1 = Files.createTempFile("qa_dl_pt_", ".ps1");
        try {
            Files.writeString(ps1, script, StandardCharsets.UTF_8);
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.toString())
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(350, TimeUnit.SECONDS);
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!done) { p.destroyForcibly(); throw new IOException("PowerShell timeout (350s)"); }
            System.out.println("[PlatformTools] PowerShell: " + output.trim());
            if (p.exitValue() != 0) throw new IOException("PowerShell exit " + p.exitValue() + ": " + output.trim());
        } finally {
            Files.deleteIfExists(ps1);
        }
    }

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

    private boolean isWindows() { return "WINDOWS".equals(os); }
}
