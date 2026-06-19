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
 * Manages the embedded ADB (Android Debug Bridge) binary.
 *
 * The Agent NEVER depends on Android Studio, Android SDK, ANDROID_HOME, or PATH.
 * ADB is always resolved from the embedded location inside the runner install dir:
 *
 *   Windows : {runnerDir}\platform-tools\adb.exe
 *   macOS   : {runnerDir}/platform-tools/adb
 *   Linux   : {runnerDir}/platform-tools/adb
 *
 * If platform-tools are absent, they are downloaded automatically from Google CDN
 * to the same embedded location.  All callers must use the path returned by
 * resolveAdb() — never a bare "adb" string.
 */
public class PlatformToolsManager {

    private static final String WIN_URL   = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";
    private static final String MAC_URL   = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip";
    private static final String LINUX_URL = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip";

    // Minimum acceptable ZIP size — Google's zip is ~8 MB; reject obvious errors
    private static final long MIN_ZIP_BYTES = 1_000_000L;

    private final Path   runnerDir;  // directory where platform-tools/ lives
    private final String os;

    private volatile String resolvedAdb  = null;
    private volatile String cachedVersion = null;

    /**
     * @param runnerDir  Directory that will contain the platform-tools/ subdirectory.
     *                   On Windows: %LOCALAPPDATA%\AutomationQA\runner
     *                   On Mac/Linux: ~/.automationqa
     */
    public PlatformToolsManager(Path runnerDir, String os) {
        this.runnerDir = runnerDir;
        this.os        = os;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns the absolute path to the embedded adb executable.
     * Downloads platform-tools on first call if they are absent.
     * Returns null only when download also fails.
     */
    public String resolveAdb() {
        if (resolvedAdb != null) return resolvedAdb;

        Path embedded = embeddedAdbPath();

        // Fast path: already installed
        if (Files.exists(embedded) && probe(embedded.toString())) {
            resolvedAdb = embedded.toString();
            System.out.println("[PlatformTools] ADB embebido listo: " + resolvedAdb);
            return resolvedAdb;
        }

        // Slow path: download
        System.out.println("[PlatformTools] ADB no encontrado en ruta embebida — descargando platform-tools...");
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                download();
                if (Files.exists(embedded) && probe(embedded.toString())) {
                    resolvedAdb = embedded.toString();
                    System.out.println("[PlatformTools] ADB listo tras descarga: " + resolvedAdb);
                    return resolvedAdb;
                }
            } catch (Exception e) {
                System.err.printf("[PlatformTools] Intento %d/%d fallido: %s%n", attempt, 2, e.getMessage());
            }
        }

        System.err.println("[PlatformTools] ADB embebido no disponible. Los dispositivos Android no seran detectados.");
        return null;
    }

    public boolean isAdbAvailable() {
        return resolveAdb() != null;
    }

    /**
     * Returns the ADB version string, e.g. "35.0.2".
     * Resolves (and downloads) ADB first if needed.
     */
    public String getAdbVersion() {
        if (cachedVersion != null) return cachedVersion;
        String adb = resolveAdb();
        if (adb == null) { cachedVersion = "unavailable"; return cachedVersion; }
        try {
            Process p = new ProcessBuilder(adb, "version")
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

    /** Path to the platform-tools directory (may not exist yet). */
    public Path getToolsDir() {
        return runnerDir.resolve("platform-tools");
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
            p.getInputStream().readAllBytes(); // drain
            if (!done) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception ignored) { return false; }
    }

    private void download() throws Exception {
        String url       = isWindows() ? WIN_URL : ("MACOS".equals(os) ? MAC_URL : LINUX_URL);
        Path   zipFile   = runnerDir.resolve("platform-tools.zip");

        Files.createDirectories(runnerDir);

        System.out.println("[PlatformTools] Descargando: " + url);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET().build();

        HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(zipFile));
        if (res.statusCode() != 200) {
            Files.deleteIfExists(zipFile);
            throw new IOException("HTTP " + res.statusCode() + " al descargar platform-tools");
        }

        long size = Files.size(zipFile);
        System.out.printf("[PlatformTools] Descarga completada: %.1f MB%n", size / 1_048_576.0);

        if (size < MIN_ZIP_BYTES) {
            Files.deleteIfExists(zipFile);
            throw new IOException("ZIP descargado demasiado pequeno (" + size + " bytes) — probable error de red");
        }

        System.out.println("[PlatformTools] Extrayendo...");
        // The zip contains a top-level platform-tools/ directory;
        // extracting to runnerDir produces runnerDir/platform-tools/adb[.exe]
        unzip(zipFile, runnerDir);
        Files.deleteIfExists(zipFile);

        // Make binaries executable on Unix
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

    private static void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    zis.closeEntry();
                    continue; // zip slip protection
                }
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
