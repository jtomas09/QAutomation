package utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NetworkEventSanitizer")
class NetworkEventSanitizerTest {

    @Test
    @DisplayName("Authorization se redacta, headers normales no")
    void redactsAuthorizationHeader() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiIs...");
        headers.put("Content-Type", "application/json");

        Map<String, String> result = NetworkEventSanitizer.sanitizeHeaders(headers);

        assertEquals("[REDACTED]", result.get("Authorization"));
        assertEquals("application/json", result.get("Content-Type"));
    }

    @Test
    @DisplayName("Cookie y Set-Cookie se redactan (case-insensitive)")
    void redactsCookies() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("cookie", "session=abc123");
        headers.put("Set-Cookie", "auth=xyz; HttpOnly");

        Map<String, String> result = NetworkEventSanitizer.sanitizeHeaders(headers);

        assertEquals("[REDACTED]", result.get("cookie"));
        assertEquals("[REDACTED]", result.get("Set-Cookie"));
    }

    @Test
    @DisplayName("Headers con token/secret/api-key en el nombre se redactan")
    void redactsTokenLikeHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Api-Key", "sk_live_abcdef");
        headers.put("X-Session-Token", "abc.def.ghi");

        Map<String, String> result = NetworkEventSanitizer.sanitizeHeaders(headers);

        assertEquals("[REDACTED]", result.get("X-Api-Key"));
        assertEquals("[REDACTED]", result.get("X-Session-Token"));
    }

    @Test
    @DisplayName("password en el body se redacta preservando la estructura JSON")
    void redactsPasswordInBody() {
        String body = "{\"username\":\"jtomasb\",\"password\":\"SuperSecreta123\"}";
        String result = NetworkEventSanitizer.sanitizeBody(body);

        assertTrue(result.contains("[REDACTED]"), "Debe contener el marcador de redacción");
        assertTrue(result.contains("jtomasb"), "El username NO sensible debe conservarse");
        assertTrue(!result.contains("SuperSecreta123"), "La password real NUNCA debe aparecer en la evidencia");
    }

    @Test
    @DisplayName("CVV en el body se redacta")
    void redactsCvvInBody() {
        String body = "{\"card\":\"4111111111111111\",\"cvv\":\"123\"}";
        String result = NetworkEventSanitizer.sanitizeBody(body);

        assertTrue(!result.contains("123") || result.replace("[REDACTED]", "").indexOf("123") < 0,
                "El CVV real no debe aparecer sin redactar");
    }

    @Test
    @DisplayName("Número de tarjeta en el body se redacta")
    void redactsCardNumberInBody() {
        String body = "{\"cardNumber\":\"4111 1111 1111 1111\"}";
        String result = NetworkEventSanitizer.sanitizeBody(body);

        assertTrue(!result.contains("4111 1111 1111 1111"), "El número de tarjeta real nunca debe persistir");
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Body sin nada sensible queda intacto")
    void leavesNonSensitiveBodyUntouched() {
        String body = "{\"cinema\":\"La Perla\",\"seats\":3}";
        assertEquals(body, NetworkEventSanitizer.sanitizeBody(body));
    }

    @Test
    @DisplayName("Body null/blank no lanza")
    void handlesNullAndBlankBody() {
        assertEquals(null, NetworkEventSanitizer.sanitizeBody(null));
        assertEquals("", NetworkEventSanitizer.sanitizeBody(""));
    }
}
