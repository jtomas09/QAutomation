package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.TestCaseResult;

import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BackendClient {

    private final String     baseUrl;
    private final String     token;
    private final String     runnerId;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    // Cached ADB path resolved once and reused on every heartbeat
    private static volatile String resolvedAdbPath = null;

    public BackendClient(String baseUrl, String token) {
        this(baseUrl, token, "runner-unknown");
    }

    public BackendClient(String baseUrl, String token, String runnerId) {
        this.baseUrl  = baseUrl.replaceAll("/$", "");
        this.token    = token;
        this.runnerId = runnerId;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Job API
    // ─────────────────────────────────────────────────────────────────────

    public Optional<JobDto> getNextJob() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/jobs/next"))
                .header("Authorization", "Bearer " + token)
                .header("X-Runner-Id", runnerId)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 204) return Optional.empty();
        if (res.statusCode() != 200) throw new RuntimeException("GET /api/jobs/next -> " + res.statusCode());
        String body = res.body();
        System.out.println("[BackendClient] /api/jobs/next raw JSON: " + body);
        JobDto dto = json.readValue(body, JobDto.class);
        System.out.printf("[BackendClient] Deserialized → videoEnabled=%b sendMail=%b executionId=%s%n",
                dto.videoEnabled, dto.sendMail, dto.executionId);
        return Optional.of(dto);
    }

    public void sendLog(String executionId, String level, String message) {
        try {
            String body = json.writeValueAsString(
                    Map.of("executionId", executionId, "level", level, "message", message));
            post("/api/logs", body);
        } catch (Exception e) {
            System.err.println("[BackendClient] sendLog error: " + e.getMessage());
        }
    }

    public boolean isJobAborted(String executionId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/executions/" + executionId))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) return false;
            return res.body().contains("\"ABORTED\"") || res.body().contains("\"ABORTING\"");
        } catch (Exception e) {
            return false;
        }
    }

    public void confirmAbort(String executionId) {
        try { post("/api/executions/" + executionId + "/abort-confirm", "{}"); }
        catch (Exception e) { System.err.println("[BackendClient] confirmAbort error: " + e.getMessage()); }
    }

    public void ping() {
        try { post("/api/jobs/ping", "{}"); }
        catch (Exception e) { System.err.println("[BackendClient] ping error: " + e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Runner Heartbeat — Universal Runner
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Enterprise heartbeat with full Universal Runner capabilities.
     * POST /api/runners
     * Payload includes: runnerId, platform, version, status, os, hostname,
     *                   androidSupported, iosSupported, devices[], timestamp
     * Returns pending command from X-Runner-Command header, or null.
     */
    public String sendHeartbeat(String runnerId, String platform, String version,
                                String status, String os, String hostname,
                                boolean androidSupported, boolean iosSupported,
                                List<Map<String, String>> devices) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runnerId",         runnerId);
            payload.put("platform",         platform);
            payload.put("version",          version);
            payload.put("status",           status);
            payload.put("os",               os);
            payload.put("hostname",         hostname);
            payload.put("androidSupported", androidSupported);
            payload.put("iosSupported",     iosSupported);
            payload.put("devices",          devices);
            payload.put("timestamp",        java.time.Instant.now().toString());

            String body = json.writeValueAsString(payload);
            HttpResponse<String> res = post("/api/runners", body);
            return res.headers().firstValue("X-Runner-Command").orElse(null);
        } catch (Exception e) {
            System.err.println("[BackendClient] heartbeat error: " + e.getMessage());
            return null;
        }
    }

    /** Backward-compat overload (no OS capabilities). */
    public String sendHeartbeat(String runnerId, String platform, String version,
                                String status, List<Map<String, String>> devices) {
        return sendHeartbeat(runnerId, platform, version, status,
                "UNKNOWN", runnerId, true, false, devices);
    }

    /**
     * Registers devices with the Device Farm.
     * POST /api/devices/register — body: { runnerId, devices: [...] }
     */
    public void registerDevices(String runnerId, List<Map<String, String>> devices) {
        if (devices.isEmpty()) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runnerId", runnerId);
            payload.put("devices",  devices);
            String body = json.writeValueAsString(payload);
            HttpResponse<String> res = post("/api/devices/register", body);
            System.out.printf("[Runner] Device Farm: %d dispositivo(s) registrado(s) (HTTP %d)%n",
                    devices.size(), res.statusCode());
        } catch (Exception e) {
            System.err.println("[BackendClient] registerDevices error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ADB auto-discovery
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Resolves the ADB executable once and caches the result.
     * Search order: ANDROID_HOME → common SDK paths → PATH fallback.
     */
    public static String findAdb() {
        if (resolvedAdbPath != null) return resolvedAdbPath;

        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome == null) androidHome = System.getProperty("ANDROID_HOME");
        if (androidHome != null) {
            String c = androidHome + File.separator + "platform-tools" + File.separator + "adb";
            if (new File(c).exists() || new File(c + ".exe").exists()) {
                resolvedAdbPath = c;
                return resolvedAdbPath;
            }
        }

        String home   = System.getProperty("user.home", "");
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isWin = osName.contains("win");
        String user   = System.getProperty("user.name", "");

        List<String> candidates = isWin
            ? List.of(
                home  + "\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
                "C:\\Android\\platform-tools\\adb.exe",
                "C:\\Program Files\\Android\\platform-tools\\adb.exe",
                "C:\\Users\\" + user + "\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe")
            : List.of(
                home + "/Library/Android/sdk/platform-tools/adb",
                home + "/Android/Sdk/platform-tools/adb",
                "/usr/local/android-sdk/platform-tools/adb",
                "/opt/android-sdk/platform-tools/adb");

        for (String c : candidates) {
            if (new File(c).exists()) {
                resolvedAdbPath = c;
                System.out.println("[ADB] Encontrado en: " + c);
                return resolvedAdbPath;
            }
        }

        resolvedAdbPath = "adb";  // trust PATH
        return resolvedAdbPath;
    }

    /**
     * Discovers all connected Android devices via `adb devices -l`.
     * Returns maps with: udid, deviceName, model, manufacturer, platform, platformVersion, status.
     */
    public static List<Map<String, String>> discoverAndroidDevices() {
        List<Map<String, String>> result = new ArrayList<>();
        String adb = findAdb();
        try {
            Process p = new ProcessBuilder(adb, "devices", "-l")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return result; }

            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            for (String line : out.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("List of") || line.startsWith("*")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;

                String udid  = parts[0];
                String state = parts[1];

                if ("unauthorized".equals(state)) {
                    System.out.printf("[ADB] ⚠ No autorizado: %s — acepta el permiso de depuracion USB.%n", udid);
                    continue;
                }
                if ("offline".equals(state)) {
                    System.out.printf("[ADB] ⚠ Offline: %s — reconecta el cable USB.%n", udid);
                    continue;
                }
                if (!"device".equals(state)) continue;

                // Parse model from adb -l output
                String model = "";
                for (String token : parts) {
                    if (token.startsWith("model:")) model = token.substring(6).replace("_", " ");
                }
                if (model.isEmpty()) model = udid;

                String manufacturer = inferManufacturer(model);
                String platformVersion = getAndroidVersion(adb, udid);
                String friendlyName    = getFriendlyName(adb, udid, model);

                Map<String, String> d = new LinkedHashMap<>();
                d.put("udid",            udid);
                d.put("deviceName",      friendlyName);
                d.put("model",           model);
                d.put("manufacturer",    manufacturer);
                d.put("platform",        "ANDROID");
                d.put("platformVersion", platformVersion);
                d.put("status",          "AVAILABLE");
                result.add(d);

                System.out.printf("[ADB] ✓ %s | Android %s | %s%n", friendlyName, platformVersion, udid);
            }
        } catch (Exception e) {
            System.err.println("[ADB] Error al descubrir dispositivos: " + e.getMessage());
            System.err.println("      Verifica que ADB este en PATH o ANDROID_HOME configurado.");
        }
        return result;
    }

    private static String inferManufacturer(String model) {
        String m = model.toLowerCase();
        if (m.contains("galaxy") || m.startsWith("sm-")) return "Samsung";
        if (m.startsWith("pixel"))                        return "Google";
        if (m.contains("oneplus"))                        return "OnePlus";
        if (m.contains("xiaomi") || m.contains("redmi")) return "Xiaomi";
        if (m.contains("huawei"))                         return "Huawei";
        if (m.contains("motorola") || m.startsWith("moto")) return "Motorola";
        if (m.contains("oppo"))                           return "OPPO";
        if (m.contains("vivo"))                           return "Vivo";
        return "";
    }

    private static String getAndroidVersion(String adb, String udid) {
        try {
            Process p = new ProcessBuilder(adb, "-s", udid, "shell",
                    "getprop", "ro.build.version.release")
                    .redirectErrorStream(true).start();
            p.waitFor(4, TimeUnit.SECONDS);
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) { return ""; }
    }

    private static String getFriendlyName(String adb, String udid, String fallback) {
        try {
            Process p = new ProcessBuilder(adb, "-s", udid, "shell",
                    "getprop", "ro.product.model")
                    .redirectErrorStream(true).start();
            p.waitFor(4, TimeUnit.SECONDS);
            String name = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return (name != null && !name.isBlank()) ? name : fallback;
        } catch (Exception e) { return fallback; }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  iOS discovery (macOS only — requires Xcode command-line tools)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Discovers connected iOS physical devices via `xcrun xctrace list devices`.
     * Skips simulators. Only works on macOS with Xcode installed.
     */
    public static List<Map<String, String>> discoverIosDevices() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            p.waitFor(10, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            java.util.regex.Pattern pat = java.util.regex.Pattern.compile(
                    "^(.+?)\\s+\\(([\\d.]+)\\)\\s+\\[([0-9A-Fa-f-]+)\\]\\s*$");

            for (String line : out.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("==") || line.contains("Simulator")) continue;
                java.util.regex.Matcher m = pat.matcher(line);
                if (!m.find()) continue;

                String deviceName      = m.group(1).trim();
                String platformVersion = m.group(2).trim();
                String udid            = m.group(3).trim();

                Map<String, String> d = new LinkedHashMap<>();
                d.put("udid",            udid);
                d.put("deviceName",      deviceName);
                d.put("model",           deviceName);
                d.put("manufacturer",    "Apple");
                d.put("platform",        "IOS");
                d.put("platformVersion", platformVersion);
                d.put("status",          "AVAILABLE");
                result.add(d);

                System.out.printf("[iOS] ✓ %s | iOS %s | %s%n", deviceName, platformVersion, udid);
            }
        } catch (Exception e) {
            System.err.println("[iOS] Error al descubrir dispositivos: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Results & Video
    // ─────────────────────────────────────────────────────────────────────

    public void sendResult(String executionId, int passed, int failed, int skipped,
                           String allureUrl, List<TestCaseResult> testCases) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", executionId);
        payload.put("passed",      passed);
        payload.put("failed",      failed);
        payload.put("skipped",     skipped);
        if (allureUrl  != null)                         payload.put("allureUrl",  allureUrl);
        if (testCases  != null && !testCases.isEmpty()) payload.put("testCases",  testCases);
        String body = json.writeValueAsString(payload);
        HttpResponse<String> res = post("/api/results", body);
        if (res.statusCode() != 200)
            throw new RuntimeException("POST /api/results → " + res.statusCode() + " " + res.body());
    }

    public void uploadVideo(String executionId, String suiteName, String testName, Path videoFile) {
        try {
            byte[] bytes    = Files.readAllBytes(videoFile);
            String filename = videoFile.getFileName().toString();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/executions/" + executionId + "/videos"))
                    .header("Content-Type",  "application/octet-stream")
                    .header("Authorization", "Bearer " + token)
                    .header("X-File-Name",   filename)
                    .header("X-Suite-Name",  suiteName != null ? suiteName : "")
                    .header("X-Test-Name",   testName  != null ? testName  : "")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 200) {
                System.out.printf("[BackendClient] Video: %s (%dKB)%n", filename, bytes.length / 1024);
            } else {
                System.err.printf("[BackendClient] uploadVideo error: %d %s%n", res.statusCode(), res.body());
            }
        } catch (Exception e) {
            System.err.println("[BackendClient] uploadVideo error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type",  "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
