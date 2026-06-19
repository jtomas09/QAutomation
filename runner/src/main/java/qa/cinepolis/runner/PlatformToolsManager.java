package qa.cinepolis.runner;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages ADB (Android Debug Bridge) installation.
 *
 * Resolution order:
 *   1. PATH (system-wide adb)
 *   2. ANDROID_HOME env variable
 *   3. Common SDK install locations (Android Studio, etc.)
 *   4. Agent data dir: ~/.automationqa/android/platform-tools/  (already downloaded)
 *   5. Auto-download platform-tools from Google CDN → agent data dir
 *
 * Once resolved, the path is cached for the lifetime of the process.
 */
public class PlatformToolsManager {

    private static final String WIN_URL   = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";
    private static final String MAC_URL   = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip";
    private static final String LINUX_URL = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip";

    private final Path   dataDir;    // ~/.automationqa  or  %LOCALAPPDATA%\AutomationQA
    private final String os;
    private volatile String resolvedAdb = null;

    public PlatformToolsManager(Path dataDir, String os) {
        this.dataDir = dataDir;
        this.os      = os;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns the absolute path to the adb executable, downloading platform-tools
     * if needed.  Returns null only when download also fails.
     */
    public String resolveAdb() {
        if (resolvedAdb != null) return resolvedAdb;

        // Fast path: search known locations without downloading
        String found = searchExisting();
        if (found != null) {
            resolvedAdb = found;
            return found;
        }

        // Slow path: download platform-tools
        System.out.println("[PlatformTools] ADB no encontrado — descargando platform-tools...");
        try {
            download();
            found = embeddedAdbPath().toString();
            if (Files.exists(Path.of(found))) {
                resolvedAdb = found;
                System.out.println("[PlatformTools] ADB listo: " + found);
                return found;
            }
        } catch (Exception e) {
            System.err.println("[PlatformTools] Error descargando platform-tools: " + e.getMessage());
        }
        System.err.println("[PlatformTools] ADB no disponible. Android no sera descubierto.");
        return null;
    }

    public boolean isAdbAvailable() {
        return resolveAdb() != null;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String searchExisting() {
        for (String candidate : buildCandidates()) {
            if (candidate == null || candidate.isBlank()) continue;
            if (probe(candidate)) {
                System.out.println("[PlatformTools] ADB encontrado: " + candidate);
                return candidate;
            }
        }
        return null;
    }

    private List<String> buildCandidates() {
        List<String> list = new ArrayList<>();
        // System PATH
        list.add(isWindows() ? "adb.exe" : "adb");

        // ANDROID_HOME
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null && !androidHome.isBlank()) {
            list.add(androidHome + sep() + "platform-tools" + sep() + adbBin());
        }

        String home = System.getProperty("user.home", "");

        if (isWindows()) {
            list.add(home + "\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe");
            list.add("C:\\Android\\platform-tools\\adb.exe");
            list.add("C:\\Program Files\\Android\\platform-tools\\adb.exe");
        } else {
            list.add(home + "/Library/Android/sdk/platform-tools/adb");
            list.add(home + "/Android/Sdk/platform-tools/adb");
            list.add("/usr/local/android-sdk/platform-tools/adb");
            list.add("/opt/android-sdk/platform-tools/adb");
        }

        // Agent embedded path (previously downloaded)
        list.add(embeddedAdbPath().toString());

        return list;
    }

    private Path embeddedAdbPath() {
        return dataDir.resolve("android").resolve("platform-tools").resolve(adbBin());
    }

    private boolean probe(String adbPath) {
        try {
            Process p = new ProcessBuilder(adbPath, "version")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            p.getInputStream().readAllBytes();
            if (done && p.exitValue() == 0) return true;
            p.destroyForcibly();
        } catch (Exception ignored) {}
        return false;
    }

    private void download() throws Exception {
        String url = isWindows() ? WIN_URL : ("MACOS".equals(os) ? MAC_URL : LINUX_URL);
        Path targetDir = dataDir.resolve("android");
        Files.createDirectories(targetDir);
        Path zipFile = targetDir.resolve("platform-tools.zip");

        System.out.println("[PlatformTools] Descargando: " + url);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET().build();
        HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(zipFile));
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " al descargar platform-tools");
        }
        System.out.printf("[PlatformTools] Descarga completada: %.1f MB%n",
                Files.size(zipFile) / 1_048_576.0);

        System.out.println("[PlatformTools] Extrayendo...");
        unzip(zipFile, targetDir);
        Files.deleteIfExists(zipFile);

        // Make adb executable on Unix
        if (!isWindows()) {
            Path adb = embeddedAdbPath();
            if (Files.exists(adb)) {
                Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(adb));
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                Files.setPosixFilePermissions(adb, perms);
            }
        }
        System.out.println("[PlatformTools] Extraccion completa.");
    }

    private static void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) continue; // zip slip protection
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

    private String adbBin()  { return isWindows() ? "adb.exe" : "adb"; }
    private boolean isWindows() { return "WINDOWS".equals(os); }
    private String sep() { return File.separator; }
}
