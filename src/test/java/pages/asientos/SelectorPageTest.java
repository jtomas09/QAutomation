package pages.asientos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — corrección de subconteo real de selección de asientos.
 *
 * Prueba pura (sin Appium/WDA/hardware) del algoritmo de desambiguación de fila en
 * {@link SelectorPage#indiceMasCercanoPorY(List, int)} — la causa raíz real de
 * "solo 2 de 3 asientos confirmados" (evidencia RUN-1004): el número de asiento se
 * repite una vez por fila, y la versión anterior de
 * {@code reubicarAsientoPorNumero()} devolvía el primer resultado sin importar la
 * fila. No requiere iPhone físico.
 */
@DisplayName("SelectorPage — desambiguación de fila por posición Y")
class SelectorPageTest {

    @Test
    @DisplayName("1. Con varias filas compartiendo el mismo número, elige la más cercana a la Y esperada")
    void picksClosestRow_amongMultipleMatches() {
        // Evidencia real: el mismo número de asiento aparece una vez por fila
        // (fila A y="240", fila F y="365", etc. — captura real de page source).
        List<Integer> centrosY = List.of(240, 293, 347, 401, 419);
        int elegido = SelectorPage.indiceMasCercanoPorY(centrosY, 405); // candidato esperado cerca de y=401
        assertEquals(3, elegido);
    }

    @Test
    @DisplayName("2. Coincidencia exacta de Y -> elige esa fila sin ambigüedad")
    void exactYMatch_isChosen() {
        List<Integer> centrosY = List.of(100, 200, 300);
        assertEquals(1, SelectorPage.indiceMasCercanoPorY(centrosY, 200));
    }

    @Test
    @DisplayName("3. Un solo resultado -> se elige ese, sin importar qué tan lejos esté")
    void singleResult_isChosenRegardless() {
        List<Integer> centrosY = List.of(999);
        assertEquals(0, SelectorPage.indiceMasCercanoPorY(centrosY, 10));
    }

    @Test
    @DisplayName("4. Sin resultados -> -1 (nunca se inventa un índice)")
    void noResults_returnsMinusOne() {
        assertEquals(-1, SelectorPage.indiceMasCercanoPorY(List.of(), 100));
    }

    @Test
    @DisplayName("5. Empate exacto entre dos filas -> elige la primera encontrada (determinístico, nunca al azar)")
    void tie_picksFirstDeterministically() {
        List<Integer> centrosY = List.of(190, 210);
        assertEquals(0, SelectorPage.indiceMasCercanoPorY(centrosY, 200));
    }
}
