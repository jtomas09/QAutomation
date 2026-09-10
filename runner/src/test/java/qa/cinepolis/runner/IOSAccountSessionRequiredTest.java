package qa.cinepolis.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 26A — tests de la integración de {@code AppleSigningProbe.Status.ACCOUNT_SESSION_REQUIRED}
 * con {@link IOSWdaErrorClassifier}/{@link IOSRunnerReadinessEngine}.
 *
 * Casos 6-10 pedidos por la tarea (PROVISIONING_REQUIRED, SIGNING_ERROR/TRUST_REQUIRED,
 * WDA_STARTUP_FAILED, UNKNOWN → comportamiento existente intacto) ya están cubiertos por
 * los tests preexistentes en {@code IOSRunnerReadinessEngineRecoveringSemanticsTest}
 * (TEST 4/5/5b/5c/5d/6) — no se duplican aquí; se confirmó que producen exactamente el
 * mismo resultado antes y después de este cambio (ver entregable, sección de regresión).
 *
 * TEST 2/3/5 de este archivo requieren el iPhone físico real conectado (mismo UDID que
 * el resto de la suite, misma limitación honesta ya documentada en
 * IOSRunnerReadinessEngineRecoveringSemanticsTest — no hay forma de simular
 * CONNECTED/PAIRED/DEVELOPER_MODE sin tocar clases que esta tarea prohíbe modificar).
 */
@DisplayName("IOS_ACCOUNT_SESSION_REQUIRED — integración TAREA 26A")
class IOSAccountSessionRequiredTest {

    private static final String UDID = "00008110-000129261482601E";
    private static final String PROBE_REASON =
            "[APPLE-SIGNING-PROBE] ACCOUNT_SESSION_REQUIRED — No Accounts: la sesión de Apple ID "
            + "utilizada por Xcode/xcodebuild no está disponible para provisioning automático. "
            + "Requiere reautenticación en Xcode.";

    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    @AfterEach
    void cleanup() {
        WdaLifecycleOwner.resetForRetry(UDID);
    }

    @Test
    @DisplayName("1. Marcador del probe -> IOSWdaErrorCode.IOS_ACCOUNT_SESSION_REQUIRED "
            + "(sin necesitar dispositivo/Xcode/Apple ID)")
    void probeMarker_classifiesAsAccountSessionRequired() {
        assertEquals(IOSWdaErrorCode.IOS_ACCOUNT_SESSION_REQUIRED,
                IOSWdaErrorClassifier.classify(PROBE_REASON));
    }

    @Test
    @DisplayName("1b. Texto RAW de xcodebuild sin el marcador del probe sigue clasificando "
            + "IOS_SIGNING_REQUIRED (no se rompe el comportamiento existente)")
    void rawXcodebuildTextWithoutMarker_stillClassifiesAsSigningRequired() {
        // Mismo texto ya cubierto por IOSWdaErrorClassifierTest — confirma que agregar el
        // nuevo patrón (máxima prioridad) no cambia la clasificación de texto RAW existente.
        assertEquals(IOSWdaErrorCode.IOS_SIGNING_REQUIRED,
                IOSWdaErrorClassifier.classify("No Accounts: Add a new account in Accounts settings."));
    }

    @Test
    @DisplayName("2 y 3. ACCOUNT_SESSION_REQUIRED -> Status.ACTION_REQUIRED, readyForExecution=false "
            + "(requiere iPhone físico conectado)")
    void accountSessionRequired_isActionRequiredAndNotReady() {
        WdaLifecycleOwner.markTerminalError(UDID, PROBE_REASON);

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea26a-test2-3", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.ACTION_REQUIRED, result.status,
                "Si este assert falla con OFFLINE/PAIRING/DEVELOPER_MODE, el iPhone físico "
                + "(00008110-000129261482601E) no está conectado/emparejado/con Developer Mode "
                + "activo en este momento — precondición de este test, no una regresión.");
        assertEquals(IOSWdaErrorCode.IOS_ACCOUNT_SESSION_REQUIRED, result.wdaErrorCode);
        assertEquals("IOS_ACCOUNT_SESSION_REQUIRED", result.errorCode);
        // IOSRunnerReadinessEngine.evaluate() nunca propaga terminalReason crudo como
        // result.reason — usa un mensaje canned amigable, igual que el branch hermano
        // IOS_DEVELOPER_TRUST_REQUIRED (ver "El dispositivo requiere confiar..." unas
        // líneas arriba en la misma clase). Esta aserción esperaba el texto crudo del
        // probe por error desde TAREA 26A; quedó enmascarada porque el dispositivo real
        // nunca había alcanzado esta rama (requiere CONNECTED+PAIRED+DEVELOPER_MODE
        // reales) hasta la restauración de provisioning de TAREA 29.
        assertEquals("La sesión de Apple ID / Xcode necesaria para provisioning automático no está "
                + "disponible ahora mismo — requiere reautenticación en Xcode.", result.reason,
                "reason usa el mensaje canned del Engine, no el texto crudo del probe");
        assertFalse(result.readyForExecution);
        assertNotEquals(IOSRunnerReadinessResult.Status.RECOVERING, result.status);
        assertNotEquals(IOSRunnerReadinessResult.Status.ERROR, result.status,
                "A diferencia de SIGNING/PROVISIONING genéricos, este caso NO es un build ya "
                + "fallido reintentable sin más — es una acción humana requerida.");
    }

    @Test
    @DisplayName("5. ACCOUNT_SESSION_REQUIRED marcado -> WdaLifecycleOwner.isTerminalError()=true "
            + "(bloquea reintentos del Mirror/otros consumidores sin acción explícita)")
    void accountSessionRequired_blocksRetryViaTerminalError() {
        assertFalse(WdaLifecycleOwner.isTerminalError(UDID), "Precondición: sin error terminal previo");

        WdaLifecycleOwner.markTerminalError(UDID, PROBE_REASON);

        assertTrue(WdaLifecycleOwner.isTerminalError(UDID));
        assertEquals(PROBE_REASON, WdaLifecycleOwner.terminalErrorReason(UDID));

        // Mismo mecanismo que IOSMirrorProvider.start() ya usa hoy (isTerminalError) para
        // negarse a reintentar un build hasta un /retry explícito del usuario — no se
        // modificó WdaLifecycleOwner para lograr esto, solo se usa su API ya pública.
        WdaLifecycleOwner.resetForRetry(UDID);
        assertFalse(WdaLifecycleOwner.isTerminalError(UDID),
                "resetForRetry() (el único camino de salida, disparado por una acción explícita "
                + "del usuario) debe seguir liberando el estado terminal sin cambios de esta tarea");
    }
}
