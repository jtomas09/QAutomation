package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.TestCaseResult;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BackendClient {

    private final String     baseUrl;
    private final String     token;
    private final String     runnerId;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public BackendClient(String baseUrl, String token) {
        this(baseUrl, token, "runner-unknown");
    }

    public BackendClient(String baseUrl, String token, String runnerId) {
        this.baseUrl   = baseUrl.replaceAll("/$", "");
        this.token     = token;
        this.runnerId  = runnerId;
    }

    /** Returns the next PENDING job, or empty if queue is empty (204). */
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
        System.out.println("[BackendClient] Deserialized → videoEnabled=" + dto.videoEnabled
                + " sendMail=" + dto.sendMail + " executionId=" + dto.executionId);
        return Optional.of(dto);
    }

    /** Fire-and-forget log line (does not throw on failure). */
    public void sendLog(String executionId, String level, String message) {
        try {
            String body = json.writeValueAsString(
                    Map.of("executionId", executionId, "level", level, "message", message));
            post("/api/logs", body);
        } catch (Exception e) {
            System.err.println("[BackendClient] sendLog error: " + e.getMessage());
        }
    }

    /**
     * Retorna true si la ejecución fue marcada ABORTED o ABORTING por el backend.
     * ABORTING = el usuario pidió cancelar, el runner debe matar Gradle.
     * ABORTED  = confirmación final (también detiene el runner por si acaso).
     * Does not throw.
     */
    public boolean isJobAborted(String executionId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/executions/" + executionId))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) return false;
            String body = res.body();
            return body.contains("\"ABORTED\"") || body.contains("\"ABORTING\"");
        } catch (Exception e) {
            return false;
        }
    }

    /** Notifica al backend que el proceso Gradle fue terminado (confirma el aborto). Does not throw. */
    public void confirmAbort(String executionId) {
        try {
            post("/api/executions/" + executionId + "/abort-confirm", "{}");
        } catch (Exception e) {
            System.err.println("[BackendClient] confirmAbort error: " + e.getMessage());
        }
    }

    /** Keeps the runner heartbeat alive while executing a job. Does not throw. */
    public void ping() {
        try {
            post("/api/jobs/ping", "{}");
        } catch (Exception e) {
            System.err.println("[BackendClient] ping error: " + e.getMessage());
        }
    }

    /**
     * Enterprise heartbeat: registers/updates this runner on POST /api/runners.
     * Payload: { runnerId, platform, version, status, devices[], timestamp }
     * Returns the pending command ("START"|"STOP"|"RESTART") from X-Runner-Command header, or null.
     * Does not throw.
     */
    public String sendHeartbeat(String runnerId, String platform, String version,
                                String status, List<Map<String, String>> devices) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runnerId",  runnerId);
            payload.put("platform",  platform);
            payload.put("version",   version);
            payload.put("status",    status);
            payload.put("devices",   devices);
            payload.put("timestamp", java.time.Instant.now().toString());
            String body = json.writeValueAsString(payload);
            HttpResponse<String> res = post("/api/runners", body);
            return res.headers().firstValue("X-Runner-Command").orElse(null);
        } catch (Exception e) {
            System.err.println("[BackendClient] heartbeat error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Registers devices with the backend Device Farm.
     * Calls POST /api/devices/register with full device details.
     * Does not throw.
     */
    public void registerDevices(String runnerId, List<Map<String, String>> devices) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runnerId", runnerId);
            payload.put("devices",  devices);
            String body = json.writeValueAsString(payload);
            HttpResponse<String> res = post("/api/devices/register", body);
            System.out.println("[BackendClient] Dispositivos registrados: " + devices.size()
                    + " (" + res.statusCode() + ")");
        } catch (Exception e) {
            System.err.println("[BackendClient] registerDevices error: " + e.getMessage());
        }
    }

    /**
     * Discovers connected Android devices via `adb devices -l`.
     * Includes platformVersion via `adb shell getprop ro.build.version.release`.
     * Returns maps with: udid, deviceName, model, manufacturer, platform, platformVersion, status.
     * Does not throw.
     */
    public static List<Map<String, String>> discoverAndroidDevices() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"adb", "devices", "-l"});
            p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : out.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("List of")) continue;
                // Format: "R5CX123456  device product:a56xqs model:Galaxy_A56_5G device:a56xqs transport_id:1"
                String[] parts = line.split("\\s+");
                if (parts.length < 2 || !"device".equals(parts[1])) continue;
                String udid = parts[0];

                // Parse model and manufacturer
                String model = "";
                String manufacturer = "";
                for (String token : parts) {
                    if (token.startsWith("model:"))   model        = token.substring(6).replace("_", " ");
                    if (token.startsWith("usb:"))      {}  // skip
                    // manufacturer inferred from model prefix
                }
                if (model.isEmpty()) model = udid;
                if (model.startsWith("Galaxy"))  manufacturer = "Samsung";
                else if (model.startsWith("Pixel"))   manufacturer = "Google";
                else if (model.startsWith("OnePlus")) manufacturer = "OnePlus";
                else if (model.startsWith("Xiaomi") || model.startsWith("Redmi")) manufacturer = "Xiaomi";

                // Get platform version
                String platformVersion = getAndroidVersion(udid);

                Map<String, String> d = new LinkedHashMap<>();
                d.put("udid",            udid);
                d.put("deviceName",      model);
                d.put("model",           model);
                d.put("manufacturer",    manufacturer);
                d.put("platform",        "ANDROID");
                d.put("platformVersion", platformVersion);
                d.put("status",          "AVAILABLE");
                result.add(d);
            }
        } catch (Exception e) {
            System.err.println("[BackendClient] discoverAndroidDevices: " + e.getMessage());
        }
        return result;
    }

    private static String getAndroidVersion(String udid) {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"adb", "-s", udid, "shell", "getprop", "ro.build.version.release"});
            p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Discovers connected iOS physical devices via `xcrun xctrace list devices`.
     * Skips simulators. Only works on macOS. Does not throw.
     */
    public static List<Map<String, String>> discoverIosDevices() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"xcrun", "xctrace", "list", "devices"});
            p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // Format: "iPhone 13 Pro (16.4.1) [00008110-001C58CC14C0001E]"
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
            }
        } catch (Exception e) {
            System.err.println("[BackendClient] discoverIosDevices: " + e.getMessage());
        }
        return result;
    }

    /** Reports final execution results. Throws on HTTP error. */
    public void sendResult(String executionId, int passed, int failed, int skipped,
                           String allureUrl, List<TestCaseResult> testCases) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", executionId);
        payload.put("passed",      passed);
        payload.put("failed",      failed);
        payload.put("skipped",     skipped);
        if (allureUrl  != null)                          payload.put("allureUrl",  allureUrl);
        if (testCases  != null && !testCases.isEmpty())  payload.put("testCases",  testCases);
        String body = json.writeValueAsString(payload);
        HttpResponse<String> res = post("/api/results", body);
        if (res.statusCode() != 200)
            throw new RuntimeException("POST /api/results → " + res.statusCode() + " " + res.body());
    }

    /** Fire-and-forget: uploads an MP4 video file as raw bytes. Does not throw. */
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
                System.out.println("[BackendClient] Video subido: " + filename + " (" + bytes.length / 1024 + " KB)");
            } else {
                System.err.println("[BackendClient] uploadVideo error: " + res.statusCode() + " " + res.body());
            }
        } catch (Exception e) {
            System.err.println("[BackendClient] uploadVideo error: " + e.getMessage());
        }
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
