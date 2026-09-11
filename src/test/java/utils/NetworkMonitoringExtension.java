package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Recorta la evidencia de Network Monitoring correspondiente a CADA test, por
 * ventana de tiempo, sobre el NDJSON compartido que produce el proxy
 * (administrado por RunnerAgent — ver runner/.../NetworkMonitoringManager.java).
 *
 * Registrada como segundo {@code @ExtendWith} en {@link base.BaseTest} junto a
 * {@link PdfReportExtension} — no la reemplaza, no depende de ella. Con
 * {@code networkMonitoringEnabled=false} (default, propagado por RunnerAgent
 * solo cuando networkMonitoring.enabled=true en su config) esta clase es
 * enteramente un no-op: ni siquiera intenta leer ningún archivo.
 *
 * Fail-safe explícito (requisito de la tarea): cualquier excepción durante la
 * generación de evidencia se registra como warning y NUNCA se propaga — no
 * puede hacer fallar un test ni cambiar su resultado PASS/FAIL/SKIP.
 *
 * Se usa solo {@link AfterEachCallback} (no {@code TestWatcher}) porque JUnit 5
 * garantiza que corre exactamente una vez por test, sin importar si terminó en
 * éxito, fallo por assertion, o SKIP por {@code TestAbortedException} — evita
 * la necesidad (y el riesgo de doble-escritura) de implementar además
 * testSuccessful/testFailed/testAborted.
 */
public class NetworkMonitoringExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(NetworkMonitoringExtension.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("networkMonitoringEnabled", "false"));
    private static final String  EVENTS_FILE    = System.getProperty("networkMonitoringEventsFile", "");
    private static final String  EVIDENCE_DIR   = System.getProperty("networkMonitoringEvidenceDir", "");
    private static final boolean CAPTURE_REQ_BODY  = Boolean.parseBoolean(System.getProperty("networkMonitoringCaptureRequestBody", "true"));
    private static final boolean CAPTURE_RES_BODY  = Boolean.parseBoolean(System.getProperty("networkMonitoringCaptureResponseBody", "true"));
    private static final long    MAX_RES_BODY_SIZE = Long.parseLong(System.getProperty("networkMonitoringMaxResponseBodySize", "1048576"));
    private static final boolean ATTACH_TO_ALLURE  = Boolean.parseBoolean(System.getProperty("networkMonitoringAttachToAllure", "true"));
    private static final boolean SAVE_ALL_TRAFFIC  = Boolean.parseBoolean(System.getProperty("networkMonitoringSaveAllTraffic", "true"));
    private static final boolean SAVE_ERRORS       = Boolean.parseBoolean(System.getProperty("networkMonitoringSaveErrors", "true"));
    private static final boolean REDACT            = Boolean.parseBoolean(System.getProperty("networkMonitoringRedact", "true"));
    private static final String  EXECUTION_ID      = System.getProperty("executionId", "unknown-execution");

    private static final ThreadLocal<Long> TEST_START_MS = new ThreadLocal<>();

    @Override
    public void beforeEach(ExtensionContext context) {
        if (!ENABLED) return;
        TEST_START_MS.set(System.currentTimeMillis());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (!ENABLED) return;
        Long startMs = TEST_START_MS.get();
        TEST_START_MS.remove();
        long endMs = System.currentTimeMillis();
        if (startMs == null) return;

        try {
            captureForTest(context, startMs, endMs);
        } catch (Exception e) {
            // Fail-safe: Network Monitoring es una capacidad auxiliar, nunca debe
            // afectar el resultado del test ni propagar una excepción aquí.
            log.warn("[NETWORK] Error generando evidencia de red (sin efecto en el resultado del test): {}",
                    e.getMessage());
        }
    }

    private void captureForTest(ExtensionContext context, long startMs, long endMs) throws Exception {
        if (EVENTS_FILE.isBlank()) return;
        Path eventsPath = Path.of(EVENTS_FILE);
        if (!Files.exists(eventsPath)) return;

        String suite    = sanitize(System.getProperty("executionName", "Suite"));
        String testName = sanitize(context.getDisplayName());
        String device   = System.getProperty("deviceName", System.getProperty("udid", "unknown"));
        String platform = config.DriverFactory.isIOS() ? "iOS" : "Android";

        List<NetworkEvidenceWriter.NetworkEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(eventsPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node;
            try {
                node = MAPPER.readTree(line);
            } catch (Exception ignored) {
                continue; // línea corrupta/incompleta (escritura concurrente) — se ignora, no rompe el resto
            }
            if (!"http_flow".equals(node.path("type").asText())) continue; // tls_handshake es solo para el Runner
            long epochMillis = node.path("epochMillis").asLong(0);
            if (epochMillis < startMs || epochMillis > endMs) continue;
            events.add(parseEvent(node));
        }

        if (events.isEmpty()) {
            log.debug("[NETWORK] Sin tráfico capturado para este test en su ventana de tiempo.");
            return;
        }

        events = events.stream()
                .map(e -> NetworkEvidenceWriter.applyResponseBodyLimit(e, MAX_RES_BODY_SIZE))
                .toList();

        List<NetworkEvidenceWriter.NetworkEvent> errors = events.stream()
                .filter(e -> utils.NetworkEventClassifier.isError(e.errorType()))
                .toList();

        Path testDir = Path.of(EVIDENCE_DIR, EXECUTION_ID, suite, testName);
        Files.createDirectories(testDir);

        String startedAt  = Instant.ofEpochMilli(startMs).toString();
        String finishedAt = Instant.ofEpochMilli(endMs).toString();

        if (SAVE_ALL_TRAFFIC) {
            String trafficJson = NetworkEvidenceWriter.toTrafficJson(
                    EXECUTION_ID, suite, testName, device, platform, startedAt, finishedAt, events);
            Path trafficFile = testDir.resolve("network-traffic.json");
            Files.writeString(trafficFile, trafficJson, StandardCharsets.UTF_8);
            log.info("[NETWORK] Evidence saved: {}", trafficFile);
            if (ATTACH_TO_ALLURE) {
                Allure.addAttachment("Network Traffic", "application/json",
                        new ByteArrayInputStream(trafficJson.getBytes(StandardCharsets.UTF_8)), ".json");
            }
        }

        if (SAVE_ERRORS && !errors.isEmpty()) {
            String errorsJson = NetworkEvidenceWriter.toErrorsJson(EXECUTION_ID, suite, testName, errors);
            Path errorsFile = testDir.resolve("network-errors.json");
            Files.writeString(errorsFile, errorsJson, StandardCharsets.UTF_8);
            log.info("[NETWORK] Evidence saved: {}", errorsFile);
            if (ATTACH_TO_ALLURE) {
                Allure.addAttachment("Network Errors", "application/json",
                        new ByteArrayInputStream(errorsJson.getBytes(StandardCharsets.UTF_8)), ".json");
            }
            for (NetworkEvidenceWriter.NetworkEvent e : errors) {
                log.warn("[NETWORK] ERROR: {} {} -> {}", e.method(), e.url(), e.statusCode());
            }
        }

        for (NetworkEvidenceWriter.NetworkEvent e : events) {
            log.debug("[NETWORK] Request captured: {} {}", e.method(), e.url());
            log.debug("[NETWORK] Response: {}", e.statusCode());
        }
    }

    private NetworkEvidenceWriter.NetworkEvent parseEvent(JsonNode node) {
        Map<String, String> reqHeaders = toStringMap(node.path("requestHeaders"));
        Map<String, String> resHeaders = toStringMap(node.path("responseHeaders"));
        Map<String, String> query      = toStringMap(node.path("query"));

        if (REDACT) {
            reqHeaders = NetworkEventSanitizer.sanitizeHeaders(reqHeaders);
            resHeaders = NetworkEventSanitizer.sanitizeHeaders(resHeaders);
        }

        String reqBody = CAPTURE_REQ_BODY ? node.path("requestBody").asText("") : "";
        String resBody = CAPTURE_RES_BODY ? node.path("responseBody").asText("") : "";
        if (REDACT) {
            reqBody = NetworkEventSanitizer.sanitizeBody(reqBody);
            resBody = NetworkEventSanitizer.sanitizeBody(resBody);
        }

        Integer statusCode = node.hasNonNull("statusCode") ? node.get("statusCode").asInt() : null;
        String  networkErrorType = node.hasNonNull("networkErrorType") ? node.get("networkErrorType").asText() : null;
        Long    durationMs = node.hasNonNull("durationMs") ? node.get("durationMs").asLong() : null;

        return new NetworkEvidenceWriter.NetworkEvent(
                node.path("timestamp").asText(""),
                node.path("epochMillis").asLong(0),
                node.path("method").asText(""),
                node.path("url").asText(""),
                node.path("host").asText(""),
                node.path("path").asText(""),
                query,
                reqHeaders,
                reqBody,
                node.path("requestBodyTruncated").asBoolean(false),
                node.path("requestContentType").asText(""),
                statusCode,
                resHeaders,
                resBody,
                node.path("responseBodyTruncated").asBoolean(false),
                node.path("responseContentType").asText(""),
                durationMs,
                utils.NetworkEventClassifier.classify(statusCode, networkErrorType),
                node.hasNonNull("networkErrorMessage") ? node.get("networkErrorMessage").asText() : null
        );
    }

    private static Map<String, String> toStringMap(JsonNode obj) {
        Map<String, String> map = new LinkedHashMap<>();
        if (obj == null || obj.isMissingNode() || obj.isNull()) return map;
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            map.put(e.getKey(), e.getValue().asText(""));
        }
        return map;
    }

    private static final Pattern UNSAFE_PATH_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    private static String sanitize(String s) {
        if (s == null) return "unknown";
        return UNSAFE_PATH_CHARS.matcher(s).replaceAll("_");
    }
}
