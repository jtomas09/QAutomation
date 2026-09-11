package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NetworkEvidenceWriter")
class NetworkEvidenceWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static NetworkEvidenceWriter.NetworkEvent event(
            String method, String url, Integer statusCode, String responseBody, Long durationMs,
            NetworkEventClassifier.ErrorType errorType) {
        return new NetworkEvidenceWriter.NetworkEvent(
                "2026-09-11T10:00:00Z", 1000L, method, url, "example.com", "/api/x",
                Map.of(), Map.of(), "", false, "application/json",
                statusCode, Map.of(), responseBody, false, "application/json", durationMs,
                errorType, null);
    }

    @Test
    @DisplayName("responseBody por debajo del límite queda intacto")
    void doesNotTruncateWhenUnderLimit() {
        NetworkEvidenceWriter.NetworkEvent e = event("GET", "https://x/api", 200, "short body", 100L,
                NetworkEventClassifier.ErrorType.SUCCESS);
        NetworkEvidenceWriter.NetworkEvent result = NetworkEvidenceWriter.applyResponseBodyLimit(e, 1024);
        assertEquals("short body", result.responseBody());
        assertFalse(result.responseBodyTruncated());
    }

    @Test
    @DisplayName("responseBody que excede el límite se trunca con el marcador estándar")
    void truncatesWhenOverLimit() {
        String bigBody = "x".repeat(2000);
        NetworkEvidenceWriter.NetworkEvent e = event("POST", "https://x/api/seats", 500, bigBody, 2381L,
                NetworkEventClassifier.ErrorType.SERVER_ERROR);
        NetworkEvidenceWriter.NetworkEvent result = NetworkEvidenceWriter.applyResponseBodyLimit(e, 100);

        assertEquals("[TRUNCATED - SIZE LIMIT EXCEEDED]", result.responseBody());
        assertTrue(result.responseBodyTruncated());
    }

    @Test
    @DisplayName("network-traffic.json contiene el resumen y las requests con sus campos reales")
    void buildsTrafficJsonWithSummary() throws Exception {
        List<NetworkEvidenceWriter.NetworkEvent> events = List.of(
                event("GET", "https://example/api/cinemas", 200, "{}", 320L, NetworkEventClassifier.ErrorType.SUCCESS),
                event("POST", "https://example/api/seats", 500, "{\"message\":\"Internal Server Error\"}", 2381L,
                        NetworkEventClassifier.ErrorType.SERVER_ERROR)
        );

        String json = NetworkEvidenceWriter.toTrafficJson(
                "20260910-184500-001", "Smoke", "SeleccionAsientosTest", "iPhone", "iOS",
                "2026-09-10T18:45:00Z", "2026-09-10T18:46:00Z", events);

        JsonNode root = MAPPER.readTree(json);
        assertEquals("20260910-184500-001", root.get("executionId").asText());
        assertEquals(2, root.get("summary").get("totalRequests").asInt());
        assertEquals(1, root.get("summary").get("success").asInt());
        assertEquals(1, root.get("summary").get("serverErrors").asInt());
        assertEquals(2, root.get("requests").size());
        assertEquals(500, root.get("requests").get(1).get("statusCode").asInt());
        assertEquals("SERVER_ERROR", root.get("requests").get(1).get("errorType").asText());
    }

    @Test
    @DisplayName("network-errors.json solo contiene los eventos de error, con responseBody")
    void buildsErrorsJsonWithOnlyErrors() throws Exception {
        List<NetworkEvidenceWriter.NetworkEvent> errors = List.of(
                event("POST", "https://example/api/seats", 500, "{\"message\":\"Internal Server Error\"}", 2381L,
                        NetworkEventClassifier.ErrorType.SERVER_ERROR),
                event("GET", "https://example/api/food/menu", 404, "{\"message\":\"Not Found\"}", 800L,
                        NetworkEventClassifier.ErrorType.CLIENT_ERROR)
        );

        String json = NetworkEvidenceWriter.toErrorsJson(
                "20260910-184500-001", "Smoke", "SeleccionAsientosTest", errors);

        JsonNode root = MAPPER.readTree(json);
        assertEquals(2, root.get("errors").size());
        assertEquals(500, root.get("errors").get(0).get("statusCode").asInt());
        assertTrue(root.get("errors").get(0).get("responseBody").asText().contains("Internal Server Error"));
        assertEquals(404, root.get("errors").get(1).get("statusCode").asInt());
    }

    @Test
    @DisplayName("network-traffic.json con cero eventos no lanza y refleja summary en cero")
    void buildsEmptyTrafficJsonSafely() throws Exception {
        String json = NetworkEvidenceWriter.toTrafficJson(
                "exec-1", "Smoke", "Test", "device", "Android", "t0", "t1", List.of());
        JsonNode root = MAPPER.readTree(json);
        assertEquals(0, root.get("summary").get("totalRequests").asInt());
        assertEquals(0, root.get("requests").size());
    }
}
