package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.model.JobDto;
import qa.cinepolis.runner.model.TestCaseResult;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BackendClient {

    private final String     baseUrl;
    private final String     token;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public BackendClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.token   = token;
    }

    /** Returns the next PENDING job, or empty if queue is empty (204). */
    public Optional<JobDto> getNextJob() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/jobs/next"))
                .header("Authorization", "Bearer " + token)
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

    /** Returns true if the execution has been marked ABORTED by the backend. Does not throw. */
    public boolean isJobAborted(String executionId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/executions/" + executionId))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() == 200 && res.body().contains("\"ABORTED\"");
        } catch (Exception e) {
            return false;
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
