package utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacción de información sensible en evidencia de Network Monitoring — nunca
 * se omite el campo (para dejar claro que existía), se reemplaza su valor por
 * {@code [REDACTED]}. Pura, sin dependencias externas, 100% testeable.
 */
public final class NetworkEventSanitizer {

    private static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization", "x-auth-token"
    );

    private static final Pattern SENSITIVE_HEADER_NAME_PATTERN =
            Pattern.compile("(?i).*(token|secret|api[-_]?key|apikey|session).*");

    // Número de tarjeta: 13-19 dígitos, con o sin separadores por grupos de 4.
    private static final Pattern CARD_NUMBER =
            Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    private static final Pattern CVV_FIELD =
            Pattern.compile("(?i)(\"?\\b(?:cvv|cvc)\\b\"?\\s*[:=]\\s*)\"?\\d{3,4}\"?");

    private static final Pattern PASSWORD_FIELD =
            Pattern.compile("(?i)(\"?\\bpassword\\b\"?\\s*[:=]\\s*)\"?[^\",}\\s]+\"?");

    private static final Pattern SECRET_LIKE_FIELD =
            Pattern.compile("(?i)(\"?\\b(?:secret|api[-_]?key|apikey|access[-_]?token)\\b\"?\\s*[:=]\\s*)\"?[^\",}\\s]+\"?");

    private NetworkEventSanitizer() {}

    /** Devuelve una copia del mapa de headers con los valores sensibles redactados. */
    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) return result;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String key = e.getKey();
            String lower = key == null ? "" : key.toLowerCase();
            boolean sensitive = SENSITIVE_HEADER_NAMES.contains(lower)
                    || SENSITIVE_HEADER_NAME_PATTERN.matcher(lower).matches();
            result.put(key, sensitive ? REDACTED : e.getValue());
        }
        return result;
    }

    /** Redacta patrones sensibles dentro de un body (JSON/texto plano). */
    public static String sanitizeBody(String body) {
        if (body == null || body.isBlank()) return body;
        String out = body;
        out = replaceGroup1Keep(CVV_FIELD, out);
        out = replaceGroup1Keep(PASSWORD_FIELD, out);
        out = replaceGroup1Keep(SECRET_LIKE_FIELD, out);
        out = CARD_NUMBER.matcher(out).replaceAll(REDACTED);
        return out;
    }

    private static String replaceGroup1Keep(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(input, last, m.start());
            sb.append(m.group(1)).append('"').append(REDACTED).append('"');
            last = m.end();
        }
        sb.append(input.substring(last));
        return sb.toString();
    }
}
