package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 3 — tests puros del Comparator, sin dispositivo físico. Construyen
 * IOSRunnerReadinessResult sintéticos vía su Builder (package-private, mismo
 * paquete) y comparan contra strings de currentFlowStatus ya normalizados —
 * exactamente la forma en que {@link IOSReadinessShadowComparator#compare}
 * espera recibirlos.
 */
@DisplayName("IOSReadinessShadowComparator")
class IOSReadinessShadowComparatorTest {

    private static IOSRunnerReadinessResult resultWithStatus(IOSRunnerReadinessResult.Status status) {
        return resultWithStatus(status, null);
    }

    private static IOSRunnerReadinessResult resultWithStatus(
            IOSRunnerReadinessResult.Status status, String errorCode) {
        return IOSRunnerReadinessResult.builder()
                .udid("00008110-000129261482601E")
                .status(status)
                .errorCode(errorCode)
                .build();
    }

    @Test
    @DisplayName("READY vs READY -> match")
    void readyVsReady_matches() {
        IOSReadinessShadowComparison shadow = IOSReadinessShadowComparator.compare(
                resultWithStatus(IOSRunnerReadinessResult.Status.READY), "READY");

        assertTrue(shadow.verdictMatches);
        assertNull(shadow.mismatchReason);
    }

    @Test
    @DisplayName("READY vs NOT_READY -> mismatch")
    void readyVsNotReady_mismatches() {
        IOSReadinessShadowComparison shadow = IOSReadinessShadowComparator.compare(
                resultWithStatus(IOSRunnerReadinessResult.Status.READY), "NOT_READY");

        assertFalse(shadow.verdictMatches);
        assertNotNull(shadow.mismatchReason);
    }

    @Test
    @DisplayName("ACTION_REQUIRED vs ERROR -> mismatch, con explicación específica de Developer Trust")
    void actionRequiredVsError_mismatchesAndExplainsTrustGap() {
        IOSReadinessShadowComparison shadow = IOSReadinessShadowComparator.compare(
                resultWithStatus(IOSRunnerReadinessResult.Status.ACTION_REQUIRED,
                        "IOS_DEVELOPER_TRUST_REQUIRED"),
                "ERROR");

        assertFalse(shadow.verdictMatches);
        assertNotNull(shadow.mismatchReason);
        assertTrue(shadow.mismatchReason.contains("Developer Trust"),
                "El mismatchReason debe explicar el caso conocido de Developer Trust: "
                + shadow.mismatchReason);
    }

    @Test
    @DisplayName("OFFLINE vs OFFLINE -> match")
    void offlineVsOffline_matches() {
        IOSReadinessShadowComparison shadow = IOSReadinessShadowComparator.compare(
                resultWithStatus(IOSRunnerReadinessResult.Status.OFFLINE), "OFFLINE");

        assertTrue(shadow.verdictMatches);
        assertNull(shadow.mismatchReason);
    }

    @Test
    @DisplayName("RECOVERING vs NOT_READY -> mismatch (categorías distintas)")
    void recoveringVsNotReady_mismatches() {
        IOSReadinessShadowComparison shadow = IOSReadinessShadowComparator.compare(
                resultWithStatus(IOSRunnerReadinessResult.Status.RECOVERING, "IOS_WDA_NOT_STARTED"),
                "NOT_READY");

        assertFalse(shadow.verdictMatches);
        assertNotNull(shadow.mismatchReason);
        assertTrue(shadow.mismatchReason.contains("RECOVERING_SEMANTICS_REVIEW_REQUIRED"),
                "Debe marcar la observación de semántica de RECOVERING: " + shadow.mismatchReason);
    }

    @Test
    @DisplayName("UNKNOWN (engine sin status) vs ERROR -> el Comparator no revienta y marca mismatch")
    void unknownVsError_handledGracefully() {
        IOSReadinessShadowComparison shadow = IOSReadinessShadowComparator.compare(null, "ERROR");

        assertFalse(shadow.verdictMatches);
        assertEquals("ERROR", shadow.currentFlowStatus);
        assertNotNull(shadow.mismatchReason);
    }

    // ── normalizeReadyReason — cobertura directa del mapeo de texto real ────────

    @Test
    @DisplayName("normalizeReadyReason: readyForExecution=true siempre es READY, incluso con reason no nulo")
    void normalizeReadyReason_trueIsAlwaysReady() {
        assertEquals("READY", IOSReadinessShadowComparator.normalizeReadyReason(true, "cualquier texto"));
    }

    @Test
    @DisplayName("normalizeReadyReason: texto real de pairing -> ACTION_REQUIRED")
    void normalizeReadyReason_pairingTextMapsToActionRequired() {
        assertEquals("ACTION_REQUIRED", IOSReadinessShadowComparator.normalizeReadyReason(
                false, "Dispositivo no emparejado — desbloquea el iPhone y acepta «Confiar en este Mac»"));
    }

    @Test
    @DisplayName("normalizeReadyReason: texto genérico real 'WDA no confirmado — Testing cancelled...' -> ERROR")
    void normalizeReadyReason_genericWdaFailureMapsToError() {
        // Evidencia real de esta sesión (RUN-1005/1006/1007): este es el texto que
        // notReadyReason contiene HOY para un fallo de Developer Trust — no la línea
        // específica de Xcode, sino el resumen genérico.
        assertEquals("ERROR", IOSReadinessShadowComparator.normalizeReadyReason(
                false, "WDA no confirmado — Testing cancelled because the build failed."));
    }

    @Test
    @DisplayName("normalizeReadyReason: texto no reconocido -> UNKNOWN, nunca una suposición")
    void normalizeReadyReason_unrecognizedTextMapsToUnknown() {
        assertEquals("UNKNOWN", IOSReadinessShadowComparator.normalizeReadyReason(
                false, "un motivo completamente inventado que no existe en el código real"));
    }
}
