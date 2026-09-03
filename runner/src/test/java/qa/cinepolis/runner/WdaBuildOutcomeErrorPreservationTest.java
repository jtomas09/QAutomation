package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 3.1 — tests de la regla de preservación de {@code WdaManager.BuildOutcome}.
 *
 * LIMITACIÓN CONOCIDA (documentada, no evitada con un refactor grande):
 * {@code WdaManager.streamBuildOutput(...)} es {@code private} y lee directamente
 * el {@link java.io.InputStream} de un {@link Process} real de xcodebuild — no es
 * practicable invocarlo desde un test unitario sin cambiar su visibilidad o su
 * firma, algo que esta tarea explícitamente no debe hacer ("no cambiar el
 * contrato existente de WdaManager"). Por eso estos tests ejercitan directamente
 * {@code BuildOutcome.captureError(String, boolean)} — el método nuevo que SÍ es
 * puro y package-private, y que concentra el 100% de la lógica de preservación
 * que streamBuildOutput ahora delega en él en sus tres puntos de captura. La
 * clasificación específica/genérica de cada línea (qué combinación de {@code
 * specific=true/false} corresponde a cada línea real) se reproduce aquí
 * exactamente como la hace streamBuildOutput, usando texto REAL capturado de
 * ejecuciones reales de xcodebuild contra el iPhone físico de esta sesión
 * (RUN-1005/1006/1007 y una re-ejecución manual verificada en esta misma tarea)
 * — no se inventa ningún texto.
 *
 * TAREA 8.4 — se agregan los casos de "most specific known error wins" (evidencia
 * real: TAREA 8.3, ejecución E2E contra hardware físico, donde el diagnóstico
 * operativo genérico de devicectl — "ERROR: The operation couldn't be completed.
 * (CoreDeviceCLISupport.DiagnoseError error 0.)" — llegó ANTES que la causa real,
 * "Timed out while enabling automation mode.", y quedó bloqueando la causa real
 * porque el detector de "error:" en streamBuildOutput() marcaba TODA línea con
 * "error:" como specific=true sin verificar si realmente aportaba una causa).
 * La regla de {@code BuildOutcome.captureError(String,boolean)} en sí no cambió
 * (ya implementaba correctamente "most specific wins" dado un valor de {@code
 * specific} correcto en cada llamada) — el fix real fue en el llamador. Estos
 * tests documentan el contrato completo de captureError(), incluyendo los casos
 * que ya funcionaban antes de TAREA 8.4 y el caso nuevo que antes fallaba.
 */
@DisplayName("WdaManager.BuildOutcome — preservación de la causa real de fallo")
class WdaBuildOutcomeErrorPreservationTest {

    // ── CASO A — causa específica (Invalid trust settings) + resumen genérico ──

    @Test
    @DisplayName("CASO A: 'Invalid trust settings' no es sobrescrito por el resumen genérico")
    void specificTrustError_survivesGenericSummary() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.",
            true);
        outcome.captureError("Testing cancelled because the build failed.", false);

        assertEquals(
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.",
            outcome.capturedError());
    }

    // ── CASO B — "No Accounts" + resumen genérico ──────────────────────────────

    @Test
    @DisplayName("CASO B: 'No Accounts' se conserva, no el resumen genérico")
    void noAccounts_isPreserved() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError("No Accounts: Add a new account in Accounts settings.", true);
        outcome.captureError("Testing cancelled because the build failed.", false);

        assertEquals("No Accounts: Add a new account in Accounts settings.", outcome.capturedError());
    }

    // ── CASO C — provisioning + resumen genérico ───────────────────────────────

    @Test
    @DisplayName("CASO C: línea de provisioning específica se conserva")
    void provisioningError_isPreserved() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(
            "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
            + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.",
            true);
        outcome.captureError("Testing cancelled because the build failed.", false);

        assertEquals(
            "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
            + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.",
            outcome.capturedError());
    }

    // ── CASO D — solo error genérico (sin causa específica disponible) ─────────

    @Test
    @DisplayName("CASO D: sin causa específica, el resumen genérico SÍ se conserva")
    void genericOnly_isReturnedWhenNoSpecificCauseExists() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError("Testing cancelled because the build failed.", false);

        assertEquals("Testing cancelled because the build failed.", outcome.capturedError());
    }

    // ── Regresión exacta de RUN-1005/1006/1007 — secuencia real completa ───────

    @Test
    @DisplayName("Secuencia real de RUN-1005/1006/1007: se conserva la PRIMERA causa específica, "
            + "nunca el resumen final")
    void realRunSequence_preservesFirstSpecificCause() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        // Orden EXACTO observado dentro del bloque "Testing failed:" en el log real
        // de esta sesión (automationqa-runner.log, RUN-1005 en adelante).
        outcome.captureError("No Accounts: Add a new account in Accounts settings.", true);
        outcome.captureError(
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.",
            true);
        outcome.captureError("No Accounts: Add a new account in Accounts settings.", true);
        outcome.captureError(
            "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
            + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.",
            true);
        outcome.captureError("Testing cancelled because the build failed.", false);

        // ANTES de TAREA 3.1 esto habría sido "Testing cancelled because the build failed."
        // (evidencia real: notReadyReason observado literalmente así en RUN-1005/1006/1007).
        assertEquals("No Accounts: Add a new account in Accounts settings.", outcome.capturedError());
        assertNotEquals("Testing cancelled because the build failed.", outcome.capturedError());
    }

    // ── Encadenamiento con la captura previa por "error:" (fuera del bloque) ───

    @Test
    @DisplayName("Una causa específica capturada ANTES del bloque 'Testing failed:' "
            + "(vía detector de 'error:') tampoco es reemplazada por el resumen genérico")
    void specificErrorCapturedBeforeBlock_alsoSurvives() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        // Línea real vista ANTES de "Testing failed:" en el log (detector genérico de "error:").
        outcome.captureError(
            ".../WebDriverAgent.xcodeproj: error: No Accounts: Add a new account in Accounts settings. "
            + "(in target 'WebDriverAgentLib' from project 'WebDriverAgent')",
            true);
        // Luego, dentro del bloque "Testing failed:", llega el resumen genérico.
        outcome.captureError("Testing cancelled because the build failed.", false);

        assertTrue(outcome.capturedError().contains("No Accounts"));
        assertNotEquals("Testing cancelled because the build failed.", outcome.capturedError());
    }

    // ── TAREA 8.4 — "most specific known error wins" ───────────────────────────

    private static final String GENERIC_COREDEVICE_DIAGNOSTIC =
            "ERROR: The operation couldn't be completed. (CoreDeviceCLISupport.DiagnoseError error 0.)";
    private static final String TIMED_OUT_AUTOMATION_MODE =
            "Timed out while enabling automation mode.";

    @Test
    @DisplayName("TAREA 8.4 — CASO 1: Generic → Specific: el diagnóstico genérico de devicectl "
            + "es reemplazado por la causa real posterior, y clasifica IOS_WDA_STARTUP_FAILED")
    void genericCoreDeviceDiagnostic_thenSpecificTimeout_isReplaced() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        // Reproduce exactamente la secuencia real de TAREA 8.3: el detector de "error:"
        // corregido en TAREA 8.4 pasa specific=false para esta línea conocida.
        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);
        outcome.captureError(TIMED_OUT_AUTOMATION_MODE, true);

        assertEquals(TIMED_OUT_AUTOMATION_MODE, outcome.capturedError());
        assertEquals(IOSWdaErrorCode.IOS_WDA_STARTUP_FAILED,
                IOSWdaErrorClassifier.classify(outcome.capturedError()));
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 2: Specific → Generic: la causa real ya capturada "
            + "no es reemplazada por un diagnóstico genérico posterior")
    void specificTimeout_thenGenericCoreDeviceDiagnostic_isPreserved() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(TIMED_OUT_AUTOMATION_MODE, true);
        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);

        assertEquals(TIMED_OUT_AUTOMATION_MODE, outcome.capturedError());
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 3: Generic → Generic: se conserva el primer genérico")
    void genericThenAnotherGeneric_firstIsPreserved() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);
        outcome.captureError("Testing cancelled because the build failed.", false);

        assertEquals(GENERIC_COREDEVICE_DIAGNOSTIC, outcome.capturedError());
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 4: Specific → Specific: se conserva el primer específico "
            + "(semántica ya existente de TAREA 3.1, sin cambios)")
    void specificThenAnotherSpecific_firstIsPreserved() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(TIMED_OUT_AUTOMATION_MODE, true);
        outcome.captureError(
                "No Accounts: Add a new account in Accounts settings.", true);

        assertEquals(TIMED_OUT_AUTOMATION_MODE, outcome.capturedError());
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 5: Trust → Generic: se conserva y clasifica "
            + "IOS_DEVELOPER_TRUST_REQUIRED")
    void trustError_thenGeneric_isPreservedAndClassified() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(
                "Invalid trust settings. Restore system default trust settings for certificate "
                + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.",
                true);
        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);

        assertEquals(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED,
                IOSWdaErrorClassifier.classify(outcome.capturedError()));
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 6: Provisioning → Generic: se conserva y clasifica "
            + "IOS_PROVISIONING_REQUIRED")
    void provisioningError_thenGeneric_isPreservedAndClassified() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(
                "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
                + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.",
                true);
        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);

        assertEquals(IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED,
                IOSWdaErrorClassifier.classify(outcome.capturedError()));
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 7: Signing → Generic: se conserva y clasifica "
            + "IOS_SIGNING_REQUIRED")
    void signingError_thenGeneric_isPreservedAndClassified() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError("No Accounts: Add a new account in Accounts settings.", true);
        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);

        assertEquals(IOSWdaErrorCode.IOS_SIGNING_REQUIRED,
                IOSWdaErrorClassifier.classify(outcome.capturedError()));
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 8: Build failure → Generic: se conserva y clasifica "
            + "IOS_WDA_BUILD_FAILED")
    void buildFailedError_thenGeneric_isPreservedAndClassified() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        // "BUILD FAILED" — línea real emitida por xcodebuild en un fallo de compilación
        // (ver WdaManager.streamBuildOutput(), rama `upper.contains("BUILD FAILED")`);
        // coincide con IOSWdaErrorClassifier.BUILD_FAILED_PAT ("build failed").
        outcome.captureError("BUILD FAILED", true);
        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);

        assertEquals(IOSWdaErrorCode.IOS_WDA_BUILD_FAILED,
                IOSWdaErrorClassifier.classify(outcome.capturedError()));
    }

    @Test
    @DisplayName("TAREA 8.4 — CASO 9: solo genérico: se conserva como respaldo y clasifica UNKNOWN")
    void genericOnly_isFallbackAndClassifiesUnknown() {
        WdaManager.BuildOutcome outcome = WdaManager.BuildOutcome.started();

        outcome.captureError(GENERIC_COREDEVICE_DIAGNOSTIC, false);

        assertEquals(GENERIC_COREDEVICE_DIAGNOSTIC, outcome.capturedError());
        assertEquals(IOSWdaErrorCode.UNKNOWN,
                IOSWdaErrorClassifier.classify(outcome.capturedError()));
    }
}
