package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica eventos de NEGOCIO reales del flujo del caso de prueba (Timeline —
 * ver arquitectura de eventos) directamente al backend, sin pasar por el
 * Runner ni por ningún log. Mismo canal/DTO que ya usa
 * qa.cinepolis.runner.events.ExecutionEventPublisher — este es el equivalente
 * del lado del módulo de tests, que no tiene acceso a BackendClient (módulo
 * separado). Precedente ya existente para llamadas salientes desde este mismo
 * módulo: NetlifyApi.java.
 *
 * No-op silencioso si faltan -DbackendUrl/-DexecutionId (p. ej. al correr un
 * test suelto fuera del Runner) — nunca debe romper ni ralentizar un test por
 * un problema de red al reportar telemetría; fire-and-forget (sendAsync).
 */
public final class TestFlowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TestFlowEventPublisher.class);

    private static final String BACKEND_URL  = System.getProperty("backendUrl", "").replaceAll("/$", "");
    private static final String EXECUTION_ID = System.getProperty("executionId", "");
    private static final boolean ENABLED = !BACKEND_URL.isBlank() && !EXECUTION_ID.isBlank();

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper json = new ObjectMapper();

    private TestFlowEventPublisher() {}

    public static void testStarted(String suite, String test) {
        publish("TEST_STARTED", "INFO", suite, test, test, null);
    }

    public static void testFinished(String suite, String test, boolean passed) {
        publish("TEST_FINISHED", passed ? "SUCCESS" : "ERROR", suite, test, test, null);
    }

    public static void stepStarted(String suite, String test, String stepName) {
        publish("TEST_STEP_STARTED", "INFO", suite, test, stepName, null);
    }

    public static void stepCompleted(String suite, String test, String stepName) {
        publish("TEST_STEP_COMPLETED", "SUCCESS", suite, test, stepName, null);
    }

    public static void stepFailed(String suite, String test, String stepName, String reason) {
        publish("TEST_STEP_FAILED", "ERROR", suite, test, stepName, reason);
    }

    public static void stepSkipped(String suite, String test, String stepName, String reason) {
        publish("TEST_STEP_SKIPPED", "WARN", suite, test, stepName, reason);
    }

    private static void publish(String type, String severity, String suite, String test,
                                 String message, String details) {
        if (!ENABLED) return;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("executionId", EXECUTION_ID);
            body.put("timestamp", Instant.now().toString());
            body.put("severity", severity);
            body.put("category", "BUSINESS");
            body.put("source", "test");
            body.put("type", type);
            body.put("message", message);
            body.put("details", details);
            body.put("suite", suite);
            body.put("test", test);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/api/events"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();

            http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        log.debug("[TestFlowEventPublisher] No se pudo publicar {} ({}): {}",
                                type, message, e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("[TestFlowEventPublisher] Error construyendo evento {} ({}): {}",
                    type, message, e.getMessage());
        }
    }
}
