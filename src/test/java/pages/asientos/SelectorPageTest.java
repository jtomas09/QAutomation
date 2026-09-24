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

    /**
     * TAREA — corrección de falsos positivos "Banner en Asientos 3D"/"Banner en Sala
     * Junior" (evidencia RUN-1001): prueba pura (sin Appium/WDA/hardware) de
     * {@link SelectorPage#androidEsVocabularioOFrangoDeFiltro(String)} — la lista exacta
     * de vocabulario del panel de filtros y la regex de rango horario que causaron que
     * el panel de filtros fuera tratado como si fuera la cartelera de películas.
     */
    @Test
    @DisplayName("6. Vocabulario exacto del panel de filtros (evidencia real RUN-1001) se reconoce")
    void androidVocabularioPanelFiltros_reconoceEvidenciaReal() {
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("idiomas"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("original"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("experiencias"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("pluus"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("sala junior"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("screen x"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("formatos"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("limpiar filtros"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("aplicar"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("categorías"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("preventa"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("sala de arte"));
    }

    @Test
    @DisplayName("7. Rango horario con guion (evidencia real RUN-1001) se reconoce, hora exacta ya cubierta no se duplica")
    void androidRangoHorario_reconoceEvidenciaReal() {
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("2:01 pm - 6:00 pm"));
        assertTrue(SelectorPage.androidEsVocabularioOFrangoDeFiltro("6:01 pm - 11:59 pm"));
        assertFalse(SelectorPage.androidEsVocabularioOFrangoDeFiltro("7:30 pm")); // hora exacta: regla ya existente aparte
    }

    @Test
    @DisplayName("8. Un título de película real (superset de una palabra del panel) NO se descarta por error")
    void androidVocabularioPanelFiltros_noDescartaTituloRealPorSubcadena() {
        // Exact-match, no CONTAINS: un título real que solo contenga alguna de estas
        // palabras como subcadena (ej. una película llamada "Original Sin") no debe
        // coincidir con la entrada exacta "original" del panel.
        assertFalse(SelectorPage.androidEsVocabularioOFrangoDeFiltro("original sin"));
        assertFalse(SelectorPage.androidEsVocabularioOFrangoDeFiltro("resident evil: noche cero"));
    }
}
