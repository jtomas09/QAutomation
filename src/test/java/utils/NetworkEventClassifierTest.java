package utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NetworkEventClassifier")
class NetworkEventClassifierTest {

    @ParameterizedTest(name = "status {0} -> {1}")
    @CsvSource({
            "200, SUCCESS",
            "201, SUCCESS",
            "204, SUCCESS",
            "301, REDIRECT",
            "302, REDIRECT",
            "400, CLIENT_ERROR",
            "401, CLIENT_ERROR",
            "404, CLIENT_ERROR",
            "500, SERVER_ERROR",
            "502, SERVER_ERROR",
            "503, SERVER_ERROR",
            "504, SERVER_ERROR",
    })
    void classifiesByStatusCode(int statusCode, String expected) {
        assertEquals(NetworkEventClassifier.ErrorType.valueOf(expected),
                NetworkEventClassifier.classify(statusCode, null));
    }

    @Test
    @DisplayName("timeout — sin status code, con networkErrorType")
    void classifiesTimeout() {
        assertEquals(NetworkEventClassifier.ErrorType.TIMEOUT,
                NetworkEventClassifier.classify(null, "TIMEOUT"));
    }

    @Test
    @DisplayName("connection error — sin status code")
    void classifiesConnectionError() {
        assertEquals(NetworkEventClassifier.ErrorType.CONNECTION_ERROR,
                NetworkEventClassifier.classify(null, "CONNECTION_ERROR"));
    }

    @Test
    @DisplayName("DNS/SSL/reset — variantes de error de red")
    void classifiesOtherNetworkErrors() {
        assertEquals(NetworkEventClassifier.ErrorType.DNS_ERROR, NetworkEventClassifier.classify(null, "DNS_ERROR"));
        assertEquals(NetworkEventClassifier.ErrorType.SSL_ERROR, NetworkEventClassifier.classify(null, "SSL_ERROR"));
        assertEquals(NetworkEventClassifier.ErrorType.CONNECTION_RESET, NetworkEventClassifier.classify(null, "CONNECTION_RESET"));
    }

    @Test
    @DisplayName("null status y null networkErrorType -> UNKNOWN")
    void nullEverything_isUnknown() {
        assertEquals(NetworkEventClassifier.ErrorType.UNKNOWN, NetworkEventClassifier.classify(null, null));
    }

    @Test
    @DisplayName("networkErrorType inválido -> UNKNOWN (no lanza)")
    void invalidNetworkErrorType_isUnknown() {
        assertEquals(NetworkEventClassifier.ErrorType.UNKNOWN, NetworkEventClassifier.classify(null, "NO_EXISTE"));
    }

    @Test
    @DisplayName("isError: SUCCESS/REDIRECT/UNKNOWN nunca son error")
    void successAndRedirectAreNotErrors() {
        assertFalse(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.SUCCESS));
        assertFalse(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.REDIRECT));
        assertFalse(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.UNKNOWN));
    }

    @Test
    @DisplayName("isError: 4xx/5xx y errores de red sí son error")
    void clientServerAndNetworkErrorsAreErrors() {
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.CLIENT_ERROR));
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.SERVER_ERROR));
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.TIMEOUT));
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.CONNECTION_ERROR));
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.DNS_ERROR));
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.SSL_ERROR));
        assertTrue(NetworkEventClassifier.isError(NetworkEventClassifier.ErrorType.CONNECTION_RESET));
    }
}
