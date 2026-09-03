package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 4 — tests del nuevo campo {@code wdaErrorCode} en
 * {@link IOSRunnerReadinessResult}, y de su integración con
 * {@link IOSRunnerReadinessEngine}.
 *
 * Los casos A-F del enunciado (Trust/Signing/Provisioning/Build/Startup/Unknown)
 * ya están cubiertos exhaustivamente a nivel de clasificación pura por
 * {@code IOSWdaErrorClassifierTest} (TAREA 3.2) — aquí NO se repite esa
 * cobertura, se prueba específicamente que {@link IOSRunnerReadinessResult}
 * puede TRANSPORTAR ambos valores (errorCode String existente + wdaErrorCode
 * enum nuevo) sin que uno reemplace al otro, y que el Engine real conecta
 * ambos correctamente para el único caso reproducible sin depender de que el
 * iPhone físico esté conectado en este momento (OFFLINE — UDID inexistente).
 *
 * Los casos que requieren llegar hasta la clasificación final del Engine
 * (A-F) necesitan que el dispositivo pase primero CONNECTED/PAIRED/DEVELOPER_MODE
 * (y, para B-F, también TRUSTED/PROVISIONED) — es decir, requieren el iPhone
 * físico real conectado. Esa validación end-to-end se hizo de forma manual
 * contra el dispositivo real (ver reporte, sección 4) y NO se deja como test
 * JUnit permanente para no volver la suite dependiente de que el hardware
 * esté presente en cada ejecución futura.
 */
@DisplayName("IOSRunnerReadinessResult — wdaErrorCode (TAREA 4)")
class IOSRunnerReadinessResultWdaErrorCodeTest {

    @Test
    @DisplayName("Por defecto, wdaErrorCode es NONE si nunca se establece")
    void defaultWdaErrorCode_isNone() {
        IOSRunnerReadinessResult result = IOSRunnerReadinessResult.builder()
                .udid("test-udid")
                .status(IOSRunnerReadinessResult.Status.READY)
                .build();

        assertEquals(IOSWdaErrorCode.NONE, result.wdaErrorCode);
    }

    @Test
    @DisplayName("El Builder transporta cualquier IOSWdaErrorCode sin alterar el resto del resultado")
    void builder_roundTripsWdaErrorCode() {
        for (IOSWdaErrorCode code : IOSWdaErrorCode.values()) {
            IOSRunnerReadinessResult result = IOSRunnerReadinessResult.builder()
                    .udid("test-udid")
                    .status(IOSRunnerReadinessResult.Status.ERROR)
                    .wdaErrorCode(code)
                    .build();
            assertEquals(code, result.wdaErrorCode);
        }
    }

    @Test
    @DisplayName("errorCode (String, TAREA 2) y wdaErrorCode (enum, TAREA 4) son independientes — "
            + "ninguno reemplaza al otro")
    void errorCodeStringAndWdaErrorCodeEnum_areIndependent() {
        IOSRunnerReadinessResult result = IOSRunnerReadinessResult.builder()
                .udid("test-udid")
                .status(IOSRunnerReadinessResult.Status.ACTION_REQUIRED)
                .reason("El dispositivo requiere confiar en la aplicación de desarrollo.")
                .errorCode("IOS_DEVELOPER_TRUST_REQUIRED")           // String preexistente (TAREA 2)
                .wdaErrorCode(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED) // enum nuevo (TAREA 4)
                .build();

        assertEquals("IOS_DEVELOPER_TRUST_REQUIRED", result.errorCode);
        assertEquals(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED, result.wdaErrorCode);
        assertEquals("El dispositivo requiere confiar en la aplicación de desarrollo.", result.reason);
    }

    @Test
    @DisplayName("toString() incluye wdaErrorCode para diagnóstico/logs")
    void toString_includesWdaErrorCode() {
        IOSRunnerReadinessResult result = IOSRunnerReadinessResult.builder()
                .udid("test-udid")
                .status(IOSRunnerReadinessResult.Status.ERROR)
                .wdaErrorCode(IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED)
                .build();

        assertTrue(result.toString().contains("wdaErrorCode=IOS_PROVISIONING_REQUIRED"));
    }

    // ── CASO H — OFFLINE (reproducible sin depender del iPhone físico) ────────

    @Test
    @DisplayName("CASO H — Engine real, UDID inexistente: status=OFFLINE, wdaErrorCode=NONE "
            + "(no hay error real de WDA que clasificar)")
    void engineOffline_hasNoWdaErrorCode() {
        BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");
        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(
                client, "tarea4-test-offline", "00000000-000000000000000A");

        assertEquals(IOSRunnerReadinessResult.Status.OFFLINE, result.status);
        assertEquals(IOSWdaErrorCode.NONE, result.wdaErrorCode,
                "Un dispositivo offline no tiene ningún texto de WDA que clasificar — debe ser NONE, "
                + "nunca UNKNOWN ni IOS_WDA_BUILD_FAILED");
        assertFalse(result.readyForExecution);
    }
}
