package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.model.JobDto;

import java.net.URI;
import java.net.http.*;
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
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 204) return Optional.empty();
        if (res.statusCode() != 200) throw new RuntimeException("GET /api/jobs/next → " + res.statusCode());
        return Optional.of(json.readValue(res.body(), JobDto.class));
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

    /** Reports final execution results. Throws on HTTP error. */
    public void sendResult(String executionId, int passed, int failed, int skipped, String allureUrl) throws Exception {
        Map<String, Object> payload = allureUrl != null
                ? Map.of("executionId", executionId, "passed", passed, "failed", failed, "skipped", skipped, "allureUrl", allureUrl)
                : Map.of("executionId", executionId, "passed", passed, "failed", failed, "skipped", skipped);
        String body = json.writeValueAsString(payload);
        HttpResponse<String> res = post("/api/results", body);
        if (res.statusCode() != 200)
            throw new RuntimeException("POST /api/results → " + res.statusCode() + " " + res.body());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
