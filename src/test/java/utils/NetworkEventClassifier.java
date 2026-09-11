package utils;

/**
 * Clasificación pura de resultados de red — sin dependencias externas, sin red
 * real, 100% testeable con datos sintéticos.
 */
public final class NetworkEventClassifier {

    public enum ErrorType {
        SUCCESS, REDIRECT, CLIENT_ERROR, SERVER_ERROR,
        TIMEOUT, CONNECTION_ERROR, DNS_ERROR, SSL_ERROR, CONNECTION_RESET,
        UNKNOWN
    }

    private NetworkEventClassifier() {}

    /**
     * Clasifica un evento de red. Si {@code networkErrorType} viene informado
     * (evento sin respuesta HTTP — timeout/DNS/SSL/conexión), tiene prioridad
     * sobre el status code (que en ese caso es null de todas formas).
     */
    public static ErrorType classify(Integer statusCode, String networkErrorType) {
        if (networkErrorType != null && !networkErrorType.isBlank()) {
            try {
                return ErrorType.valueOf(networkErrorType.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ErrorType.UNKNOWN;
            }
        }
        if (statusCode == null) return ErrorType.UNKNOWN;
        if (statusCode >= 200 && statusCode < 300) return ErrorType.SUCCESS;
        if (statusCode >= 300 && statusCode < 400) return ErrorType.REDIRECT;
        if (statusCode >= 400 && statusCode < 500) return ErrorType.CLIENT_ERROR;
        if (statusCode >= 500 && statusCode < 600) return ErrorType.SERVER_ERROR;
        return ErrorType.UNKNOWN;
    }

    /** True para cualquier clasificación que deba aparecer en network-errors.json. */
    public static boolean isError(ErrorType type) {
        return type == ErrorType.CLIENT_ERROR
            || type == ErrorType.SERVER_ERROR
            || type == ErrorType.TIMEOUT
            || type == ErrorType.CONNECTION_ERROR
            || type == ErrorType.DNS_ERROR
            || type == ErrorType.SSL_ERROR
            || type == ErrorType.CONNECTION_RESET;
    }
}
