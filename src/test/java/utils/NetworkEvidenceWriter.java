package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Construye el contenido JSON de {@code network-traffic.json} y
 * {@code network-errors.json} a partir de eventos ya clasificados. Pura —
 * ninguna operación de red ni de archivo aquí (la escritura a disco vive en
 * {@link NetworkMonitoringExtension}) — 100% testeable con datos sintéticos.
 */
public final class NetworkEvidenceWriter {

    /** Un evento de red ya SANITIZADO (ver NetworkEventSanitizer) — headers y body listos para persistir. */
    public record NetworkEvent(
            String timestamp, long epochMillis, String method, String url, String host, String path,
            Map<String, String> query, Map<String, String> requestHeaders, String requestBody,
            boolean requestBodyTruncated, String requestContentType,
            Integer statusCode, Map<String, String> responseHeaders, String responseBody,
            boolean responseBodyTruncated, String responseContentType, Long durationMs,
            NetworkEventClassifier.ErrorType errorType, String networkErrorMessage
    ) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NetworkEvidenceWriter() {}

    /**
     * Aplica el límite configurado de tamaño de responseBody. Si el cuerpo ya
     * venía truncado por el addon (tope crudo interno), o excede el límite
     * configurado por el usuario, el resultado queda marcado
     * {@code responseBodyTruncated=true} y el texto reemplazado por el
     * marcador estándar — nunca se escribe un cuerpo parcial ambiguo.
     */
    public static NetworkEvent applyResponseBodyLimit(NetworkEvent e, long maxResponseBodySize) {
        if (e.responseBody() == null) return e;
        boolean exceeds = e.responseBody().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxResponseBodySize;
        if (!exceeds && !e.responseBodyTruncated()) return e;
        return new NetworkEvent(
                e.timestamp(), e.epochMillis(), e.method(), e.url(), e.host(), e.path(),
                e.query(), e.requestHeaders(), e.requestBody(), e.requestBodyTruncated(), e.requestContentType(),
                e.statusCode(), e.responseHeaders(),
                exceeds ? "[TRUNCATED - SIZE LIMIT EXCEEDED]" : e.responseBody(),
                exceeds || e.responseBodyTruncated(),
                e.responseContentType(), e.durationMs(), e.errorType(), e.networkErrorMessage()
        );
    }

    public static String toTrafficJson(String executionId, String suite, String test, String device,
                                        String platform, String startedAt, String finishedAt,
                                        List<NetworkEvent> events) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("executionId", executionId);
        root.put("suite", suite);
        root.put("test", test);
        root.put("device", device);
        root.put("platform", platform);
        root.put("startedAt", startedAt);
        root.put("finishedAt", finishedAt);

        int success = 0, redirects = 0, clientErrors = 0, serverErrors = 0, networkErrors = 0;
        for (NetworkEvent e : events) {
            switch (e.errorType()) {
                case SUCCESS -> success++;
                case REDIRECT -> redirects++;
                case CLIENT_ERROR -> clientErrors++;
                case SERVER_ERROR -> serverErrors++;
                case TIMEOUT, CONNECTION_ERROR, DNS_ERROR, SSL_ERROR, CONNECTION_RESET -> networkErrors++;
                default -> {}
            }
        }
        ObjectNode summary = root.putObject("summary");
        summary.put("totalRequests", events.size());
        summary.put("success", success);
        summary.put("redirects", redirects);
        summary.put("clientErrors", clientErrors);
        summary.put("serverErrors", serverErrors);
        summary.put("networkErrors", networkErrors);

        ArrayNode requests = root.putArray("requests");
        for (NetworkEvent e : events) requests.add(toEventNode(e, true));

        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    public static String toErrorsJson(String executionId, String suite, String test,
                                       List<NetworkEvent> errorEvents) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("executionId", executionId);
        root.put("suite", suite);
        root.put("test", test);
        ArrayNode errors = root.putArray("errors");
        for (NetworkEvent e : errorEvents) errors.add(toEventNode(e, false));
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static ObjectNode toEventNode(NetworkEvent e, boolean compact) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("timestamp", e.timestamp());
        node.put("method", e.method());
        node.put("url", e.url());
        if (e.statusCode() != null) node.put("statusCode", e.statusCode()); else node.putNull("statusCode");
        if (e.durationMs() != null) node.put("durationMs", e.durationMs());
        if (NetworkEventClassifier.isError(e.errorType())) {
            node.put("errorType", e.errorType().name());
        }
        if (!compact) {
            node.put("host", e.host());
            node.put("path", e.path());
            ObjectNode reqHeaders = node.putObject("requestHeaders");
            if (e.requestHeaders() != null) e.requestHeaders().forEach(reqHeaders::put);
            ObjectNode resHeaders = node.putObject("responseHeaders");
            if (e.responseHeaders() != null) e.responseHeaders().forEach(resHeaders::put);
            if (e.responseBody() != null) node.put("responseBody", e.responseBody());
            node.put("responseBodyTruncated", e.responseBodyTruncated());
            if (e.networkErrorMessage() != null) node.put("networkErrorMessage", e.networkErrorMessage());
        }
        return node;
    }
}
