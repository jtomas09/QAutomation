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

    private volatile String  resolvedAdb  = null;
    private volatile String  cachedVersion = null;
    private volatile boolean adbFunctional = false;

    public PlatformToolsManager(Path runnerDir, String os) {
        this.runnerDir = runnerDir;
        this.os        = os;
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

        // Remove stale/partial zip
        Files.deleteIfExists(zipFile);

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
        System.out.printf("[PlatformTools] Descarga: %.1f MB%n", size / 1_048_576.0);

        if (size < MIN_ZIP_BYTES) {
            Files.deleteIfExists(zipFile);
            throw new IOException("ZIP invalido: " + size + " bytes (esperado >5MB)");
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
