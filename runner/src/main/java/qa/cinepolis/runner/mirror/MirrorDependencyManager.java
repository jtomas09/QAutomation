package qa.cinepolis.runner.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Descarga y prepara automáticamente ffmpeg y scrcpy — el usuario NUNCA
 * ejecuta `brew install` ni ninguna instalación manual. Mismo patrón exacto
 * que PlatformToolsManager (que ya hace esto para ADB): binario embebido,
 * se descarga solo si falta, se reintenta con backoff, nunca lanza — un
 * fallo aquí solo significa que ese provider queda "no soportado" y la
 * cadena de fallback usa el siguiente candidato.
 *
 * ffmpeg: el instalador (AutomationQA-Runner-macOS.sh) YA descarga ffmpeg de
 * forma autosuficiente para la grabación de video iOS — ensureFfmpeg() revisa
 * PRIMERO esa misma ruta (runtime/ffmpeg/ffmpeg, ver IOSVideoRecordingManager.
 * resolveFfmpegBin()) antes de descargar una segunda copia propia en
 * {runnerDir}/mirror-tools/ — evita duplicar lo que ya existe.
 *
 * Fuentes verificadas manualmente contra binarios reales antes de integrar
 * esto (macOS arm64 y x86_64, sin bloqueo de Gatekeeper al descargar vía
 * HttpClient — mismo mecanismo, sin atributo de cuarentena, que ya usa
 * PlatformToolsManager para el zip de platform-tools):
 *   - ffmpeg arm64 (macOS):  osxexperts.net (build estático, sin Rosetta)
 *   - ffmpeg x86_64 (macOS): evermeet.cx (API JSON, siempre la versión actual)
 *   - scrcpy (todas las plataformas): GitHub Releases oficiales de
 *     Genymobile/scrcpy — release "latest" resuelta dinámicamente vía la
 *     API de GitHub, nunca una versión fija hardcodeada.
 */
final class MirrorDependencyManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path toolsDir;
    private final Path installerFfmpegPath;
    private volatile String resolvedFfmpegPath; // caché: cuál de las dos rutas resultó funcional

    MirrorDependencyManager(Path runnerDir) {
        this.toolsDir = runnerDir.resolve("mirror-tools");
        // El instalador (AutomationQA-Runner-macOS.sh) YA descarga ffmpeg de forma
        // autosuficiente para la grabación de video iOS (ver IOSVideoRecordingManager.
        // resolveFfmpegBin(), misma convención de ruta) — se reutiliza esa instancia
        // en vez de descargar una segunda copia redundante. Solo si esa ruta no
        // existe o no ejecuta (p.ej. instalación previa a que el instalador agregara
        // ffmpeg, o falta Rosetta 2 en Apple Silicon) se recurre a la descarga propia
        // de este archivo (ver ensureFfmpeg()), que sí puede resolver un build arm64
        // nativo sin depender de Rosetta.
        this.installerFfmpegPath = runnerDir.resolve("runtime").resolve("ffmpeg").resolve("ffmpeg");
    }

    Path ffmpegDir() { return toolsDir.resolve("ffmpeg"); }
    Path scrcpyDir() { return toolsDir.resolve("scrcpy"); }
    Path iosScreenCaptureDir() { return toolsDir.resolve("ios-screen-capture"); }

    /** La ruta de ffmpeg que efectivamente funcionó (instalador o descarga propia) — llamar tras ensureFfmpeg(). */
    String embeddedFfmpegPath() {
        return resolvedFfmpegPath != null
                ? resolvedFfmpegPath
                : ffmpegDir().resolve(isWindows() ? "ffmpeg.exe" : "ffmpeg").toString();
    }

    String embeddedScrcpyPath() {
        return scrcpyDir().resolve(isWindows() ? "scrcpy.exe" : "scrcpy").toString();
    }

    String embeddedIosScreenCapturePath() {
        return iosScreenCaptureDir().resolve("ios-screen-capture").toString();
    }

    /** Descarga ffmpeg si no está ya presente y funcional. Nunca lanza — devuelve el resultado. */
    boolean ensureFfmpeg() {
        String installerPath = installerFfmpegPath.toString();
        if (probe(installerPath, "-version")) {
            resolvedFfmpegPath = installerPath;
            return true;
        }

        String embedded = ffmpegDir().resolve(isWindows() ? "ffmpeg.exe" : "ffmpeg").toString();
        if (probe(embedded, "-version")) { resolvedFfmpegPath = embedded; return true; }
        if (!isMac() && !isLinux()) {
            System.out.println("[MirrorDependencyManager] Descarga automática de ffmpeg aún no soportada en este SO — "
                    + "se usará el ffmpeg del sistema si existe (ver BinaryLocator).");
            return false;
        }
        System.out.println("[MirrorDependencyManager] ffmpeg no encontrado (ni en la ruta del instalador ni embebido) — "
                + "descargando automáticamente...");
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                downloadFfmpeg();
                if (probe(embedded, "-version")) {
                    System.out.println("[MirrorDependencyManager] ffmpeg listo: " + embedded);
                    resolvedFfmpegPath = embedded;
                    return true;
                }
            } catch (Exception e) {
                System.err.println("[MirrorDependencyManager] Intento " + attempt + "/3 de ffmpeg falló: " + e.getMessage());
            }
            sleepBackoff(attempt);
        }
        System.err.println("[MirrorDependencyManager] No se pudo obtener ffmpeg automáticamente — "
                + "AVFoundation/scrcpy quedarán no disponibles, se usará el siguiente fallback.");
        return false;
    }

    /** Descarga scrcpy (y su scrcpy-server embebido) si no está presente. Nunca lanza. */
    boolean ensureScrcpy() {
        String embedded = embeddedScrcpyPath();
        if (probe(embedded, "--version")) return true;
        System.out.println("[MirrorDependencyManager] scrcpy no encontrado — descargando automáticamente...");
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                downloadScrcpy();
                if (probe(embedded, "--version")) {
                    System.out.println("[MirrorDependencyManager] scrcpy listo: " + embedded);
                    return true;
                }
            } catch (Exception e) {
                System.err.println("[MirrorDependencyManager] Intento " + attempt + "/3 de scrcpy falló: " + e.getMessage());
            }
            sleepBackoff(attempt);
        }
        System.err.println("[MirrorDependencyManager] No se pudo obtener scrcpy automáticamente — "
                + "Android caerá a ADB screencap (ya soportado, sin acción del usuario).");
        return false;
    }

    /**
     * Extrae (si falta) el binario ios-screen-capture embebido en el propio JAR
     * — a diferencia de ffmpeg/scrcpy, este no se descarga de internet: es
     * código propio (ver runner/native/macos/), compilado una vez y empaquetado
     * como recurso, así que "asegurarlo" es solo copiarlo a disco y marcarlo
     * ejecutable. Solo aplica a macOS (isSupported() en el provider ya filtra
     * por SO antes de llegar aquí). Nunca lanza.
     */
    boolean ensureIosScreenCapture() {
        if (!isMac()) return false;
        String embedded = embeddedIosScreenCapturePath();
        if (probe(embedded, "--help")) return true; // ya extraído en una corrida anterior

        try (InputStream in = MirrorDependencyManager.class.getResourceAsStream("/native/macos/ios-screen-capture")) {
            if (in == null) {
                System.err.println("[MirrorDependencyManager] ios-screen-capture no está embebido en este JAR "
                        + "(build incompleto) — AVFoundation caerá a libimobiledevice.");
                return false;
            }
            Files.createDirectories(iosScreenCaptureDir());
            Path dest = Path.of(embedded);
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            makeExecutableAndUnquarantine(dest);
            System.out.println("[MirrorDependencyManager] ios-screen-capture listo: " + embedded);
            return true;
        } catch (Exception e) {
            System.err.println("[MirrorDependencyManager] No se pudo extraer ios-screen-capture: " + e.getMessage());
            return false;
        }
    }

    // ── ffmpeg ────────────────────────────────────────────────────────────

    private void downloadFfmpeg() throws Exception {
        Files.createDirectories(ffmpegDir());
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isArm = arch.contains("aarch64") || arch.contains("arm");

        if (isMac()) {
            String url = isArm
                    ? "https://www.osxexperts.net/ffmpeg9arm.zip"     // build estático nativo arm64
                    : ffmpegEvermeetUrl();                             // API JSON siempre-actual, x86_64
            Path zip = Files.createTempFile("ffmpeg-dl-", ".zip");
            try {
                downloadFile(url, zip);
                unzipFlat(zip, ffmpegDir());
            } finally {
                Files.deleteIfExists(zip);
            }
        } else {
            // Linux: sin una fuente estática única verificada — se documenta como
            // limitación conocida en vez de adivinar una URL no probada.
            throw new IOException("Descarga automática de ffmpeg en Linux aún no implementada");
        }

        Path bin = ffmpegDir().resolve("ffmpeg");
        if (Files.exists(bin)) makeExecutableAndUnquarantine(bin);
    }

    /** Consulta la API JSON de evermeet.cx para obtener SIEMPRE la URL del release actual (nunca hardcodeada). */
    private static String ffmpegEvermeetUrl() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://evermeet.cx/ffmpeg/info/ffmpeg/release"))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("evermeet.cx HTTP " + res.statusCode());
        JsonNode root = MAPPER.readTree(res.body());
        String url = root.path("download").path("zip").path("url").asText(null);
        if (url == null || url.isBlank()) throw new IOException("evermeet.cx no reportó una URL de descarga");
        return url;
    }

    // ── scrcpy ────────────────────────────────────────────────────────────

    private void downloadScrcpy() throws Exception {
        Files.createDirectories(scrcpyDir());
        JsonNode release = fetchLatestScrcpyRelease();
        String assetName = scrcpyAssetNameForThisPlatform(release);
        String assetUrl = null;
        for (JsonNode asset : release.path("assets")) {
            if (assetName.equals(asset.path("name").asText())) {
                assetUrl = asset.path("browser_download_url").asText(null);
                break;
            }
        }
        if (assetUrl == null) throw new IOException("No se encontró el asset '" + assetName + "' en el release de scrcpy");

        if (assetUrl.endsWith(".zip")) {
            Path zip = Files.createTempFile("scrcpy-dl-", ".zip");
            try {
                downloadFile(assetUrl, zip);
                unzipFlat(zip, scrcpyDir());
            } finally {
                Files.deleteIfExists(zip);
            }
        } else { // .tar.gz (macOS/Linux)
            Path tarGz = Files.createTempFile("scrcpy-dl-", ".tar.gz");
            try {
                downloadFile(assetUrl, tarGz);
                untarFlat(tarGz, scrcpyDir());
            } finally {
                Files.deleteIfExists(tarGz);
            }
        }

        Path bin = scrcpyDir().resolve(isWindows() ? "scrcpy.exe" : "scrcpy");
        if (Files.exists(bin)) makeExecutableAndUnquarantine(bin);
        Path server = scrcpyDir().resolve("scrcpy-server");
        if (Files.exists(server)) makeExecutableAndUnquarantine(server);
        Path adb = scrcpyDir().resolve(isWindows() ? "adb.exe" : "adb");
        if (Files.exists(adb)) makeExecutableAndUnquarantine(adb); // no se usa (el Runner usa su propio adb), pero no estorba
    }

    private static JsonNode fetchLatestScrcpyRelease() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/Genymobile/scrcpy/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("GitHub API HTTP " + res.statusCode());
        return MAPPER.readTree(res.body());
    }

    private String scrcpyAssetNameForThisPlatform(JsonNode release) throws IOException {
        // El nombre real del asset SÍ incluye la "v" del tag (confirmado contra
        // la API real: "scrcpy-macos-aarch64-v4.1.tar.gz") — no quitarla.
        String tag = release.path("tag_name").asText(); // p.ej. "v4.1"
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isArm = arch.contains("aarch64") || arch.contains("arm");

        if (isMac()) return "scrcpy-macos-" + (isArm ? "aarch64" : "x86_64") + "-" + tag + ".tar.gz";
        if (isWindows()) return "scrcpy-win" + (System.getProperty("os.arch", "").contains("64") ? "64" : "32") + "-" + tag + ".zip";
        if (isLinux()) return "scrcpy-linux-x86_64-" + tag + ".tar.gz";
        throw new IOException("Sistema operativo no reconocido para descarga de scrcpy");
    }

    // ── Descarga/extracción genéricas ────────────────────────────────────

    private static void downloadFile(String url, Path dest) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET().build();
        HttpResponse<Path> res = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (res.statusCode() != 200) {
            Files.deleteIfExists(dest);
            throw new IOException("HTTP " + res.statusCode() + " descargando " + url);
        }
    }

    /** Extrae un zip "aplanando" cualquier carpeta contenedora — solo interesan los archivos, no la estructura del release. */
    private static void unzipFlat(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().contains("__MACOSX")) { zis.closeEntry(); continue; }
                String flatName = Path.of(entry.getName()).getFileName().toString();
                Path target = destDir.resolve(flatName).normalize();
                if (!target.startsWith(destDir)) { zis.closeEntry(); continue; } // zip-slip
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
    }

    /** Extrae un .tar.gz aplanado — usa el `tar` del sistema (siempre presente en macOS/Linux), sin dependencias nuevas. */
    private static void untarFlat(Path tarGzFile, Path destDir) throws Exception {
        Path stagingDir = Files.createTempDirectory("untar-staging-");
        try {
            Process p = new ProcessBuilder("tar", "-xzf", tarGzFile.toString(), "-C", stagingDir.toString()).start();
            boolean done = p.waitFor(60, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); throw new IOException("tar timeout"); }
            if (p.exitValue() != 0) throw new IOException("tar exit " + p.exitValue());

            try (var stream = Files.walk(stagingDir)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        Files.copy(file, destDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        System.err.println("[MirrorDependencyManager] no se pudo copiar " + file + ": " + e.getMessage());
                    }
                });
            }
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    /**
     * Quita el atributo de cuarentena de Gatekeeper si existiera (defensivo —
     * las descargas vía HttpClient/curl no lo activan, a diferencia de un
     * navegador, verificado manualmente con binarios reales antes de integrar
     * esto) y marca el archivo ejecutable.
     */
    private static void makeExecutableAndUnquarantine(Path file) {
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (Exception ignored) { } // Windows no soporta PosixFilePermission

        if (isMac()) {
            try {
                new ProcessBuilder("xattr", "-d", "com.apple.quarantine", file.toString())
                        .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) { } // sin atributo que quitar — no es un error
        }
    }

    private static boolean probe(String path, String versionFlag) {
        try {
            if (!Files.exists(Path.of(path))) return false;
            Process p = new ProcessBuilder(path, versionFlag).redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            p.getInputStream().readAllBytes();
            if (!done) { p.destroyForcibly(); return false; }
            return true; // no se exige exit 0 — algunos binarios devuelven !=0 con --version igual válido
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleepBackoff(int attempt) {
        try { Thread.sleep(2_000L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static boolean isMac()     { return System.getProperty("os.name", "").toLowerCase().contains("mac"); }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
    private static boolean isLinux()   { return System.getProperty("os.name", "").toLowerCase().contains("nux"); }
}
