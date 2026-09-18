package pages.asientos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — corrección de subconteo real de selección de asientos.
 *
 * Pruebas puras (sin Appium/WDA/hardware) de la lógica de decisión extraída de
 * {@link SeatSelectionEngine#select(int, SeatSelectionEngine.SeatPicker, SeatMap)}:
 * confirmación por contador, freno de reintentos, identidad física del asiento, y
 * la condición de éxito. Ninguna requiere iPhone físico — son funciones estáticas
 * sin efectos secundarios, mismo patrón ya usado en el resto de esta base de código
 * (AppleSigningProbe.classify(), IosPreflightManager.decidePreflightPath(), etc.).
 */
@DisplayName("SeatSelectionEngine — decisiones puras de confirmación/reintento/identidad")
class SeatSelectionEngineTest {

    // ── esSeleccionConfirmada(beforeCount, afterCount) ──────────────────────────

    @Test
    @DisplayName("1. Contador esperado vs contador real: avanza exactamente en 1 -> confirmado")
    void counterAdvancesByExactlyOne_isConfirmed() {
        assertTrue(SeatSelectionEngine.esSeleccionConfirmada(0, 1));
        assertTrue(SeatSelectionEngine.esSeleccionConfirmada(1, 2));
        assertTrue(SeatSelectionEngine.esSeleccionConfirmada(2, 3));
    }

    @Test
    @DisplayName("2. Selección NO confirmada: el contador se queda igual (tap ejecutado != seleccionado)")
    void counterUnchanged_isNotConfirmed() {
        // Caso real observado (RUN-1004): tapOk=true, pero afterCount==beforeCount.
        assertFalse(SeatSelectionEngine.esSeleccionConfirmada(1, 1));
    }

    @Test
    @DisplayName("3. Anomalía — el contador retrocede o avanza más de 1 -> tampoco es confirmación válida")
    void counterMovesUnexpectedly_isNotConfirmed() {
        assertFalse(SeatSelectionEngine.esSeleccionConfirmada(2, 1)); // retrocedió
        assertFalse(SeatSelectionEngine.esSeleccionConfirmada(0, 2)); // saltó de más
    }

    // ── seleccionCompleta(confirmados, objetivo) — éxito SOLO si target == confirmado ──

    @Test
    @DisplayName("4. Éxito solamente cuando el objetivo fue alcanzado (3 objetivo = 3 confirmados)")
    void targetReached_isComplete() {
        assertTrue(SeatSelectionEngine.seleccionCompleta(3, 3));
    }

    @Test
    @DisplayName("5. NO se marca éxito con menos confirmados que el objetivo (2 de 3, caso real RUN-1004)")
    void fewerThanTarget_isNotComplete() {
        assertFalse(SeatSelectionEngine.seleccionCompleta(2, 3));
    }

    // ── presupuestoAgotado(intentosRealizados, maxIntentosTotales) — reintento permitido ──

    @Test
    @DisplayName("6. Reintento permitido mientras no se alcance el presupuesto máximo")
    void retryAllowed_whileUnderBudget() {
        int maxIntentos = 3 + 6; // objetivo=3, margen=6 (mismos valores reales del engine)
        assertFalse(SeatSelectionEngine.presupuestoAgotado(8, maxIntentos));
    }

    @Test
    @DisplayName("7. Reintento agotado exactamente al llegar al presupuesto máximo")
    void retryExhausted_atBudgetLimit() {
        int maxIntentos = 3 + 6;
        assertTrue(SeatSelectionEngine.presupuestoAgotado(9, maxIntentos));
    }

    // ── mismoAsientoFisico(x1,y1,x2,y2,tolerancia) — identidad estable del asiento ──

    @Test
    @DisplayName("8. Identidad estable: mismas coordenadas (o dentro de tolerancia) -> mismo asiento físico")
    void samePosition_isSamePhysicalSeat() {
        assertTrue(SeatSelectionEngine.mismoAsientoFisico(100, 200, 105, 203, 30.0));
    }

    @Test
    @DisplayName("9. Identidad estable: coordenadas de otra fila (lejos) -> NO es el mismo asiento físico, "
            + "aunque comparta número (evidencia real: RUN-1006, colisión de número entre filas)")
    void differentRowSameNumber_isNotSamePhysicalSeat() {
        assertFalse(SeatSelectionEngine.mismoAsientoFisico(100, 200, 100, 260, 30.0));
    }

    @Test
    @DisplayName("10. Identidad estable: distancia exactamente en el borde de la tolerancia -> sigue siendo el mismo asiento")
    void distanceAtToleranceBoundary_isSamePhysicalSeat() {
        assertTrue(SeatSelectionEngine.mismoAsientoFisico(0, 0, 30, 0, 30.0));
    }
}
