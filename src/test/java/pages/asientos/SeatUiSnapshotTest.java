package pages.asientos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — investigación de contenedor para acotar el escaneo del mapa de asientos.
 *
 * Pruebas puras (sin Appium/WDA/hardware) de {@link SeatUiSnapshot#capturar(String)}
 * (parseo de page source ya con profundidad/padre) y del algoritmo de ancestro común
 * más profundo — la lógica que decide, a partir de un page source real ya obtenido,
 * si existe un contenedor único que englobe todos los candidatos de asiento. No
 * requiere iPhone físico: opera sobre XML sintético con una estructura conocida.
 */
@DisplayName("SeatUiSnapshot — profundidad y ancestro común (contenedor)")
class SeatUiSnapshotTest {

    private static final String XML_CON_CONTENEDOR =
            "<AppiumAUT>"
            + "  <Root>"
            + "    <Otro x=\"0\" y=\"0\" width=\"400\" height=\"800\">"
            + "      <Contenedor x=\"10\" y=\"200\" width=\"380\" height=\"200\">"
            + "        <Button name=\"1\" x=\"20\" y=\"210\" width=\"16\" height=\"16\"/>"
            + "        <Button name=\"2\" x=\"40\" y=\"210\" width=\"16\" height=\"16\"/>"
            + "        <Button name=\"3\" x=\"20\" y=\"230\" width=\"16\" height=\"16\"/>"
            + "      </Contenedor>"
            + "      <Otro name=\"Continuar, 0\" x=\"10\" y=\"700\" width=\"380\" height=\"60\"/>"
            + "    </Otro>"
            + "  </Root>"
            + "</AppiumAUT>";

    @Test
    @DisplayName("1. capturar() asigna profundidad creciente por nivel de anidamiento")
    void capturar_assignsIncreasingDepthPerNestingLevel() {
        List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(XML_CON_CONTENEDOR).nodos;
        SeatUiSnapshot.Nodo raiz = nodos.stream().filter(n -> n.tag.equals("AppiumAUT")).findFirst().orElseThrow();
        SeatUiSnapshot.Nodo boton1 = nodos.stream().filter(n -> "1".equals(n.attrs.get("name"))).findFirst().orElseThrow();
        assertEquals(0, raiz.depth);
        assertTrue(boton1.depth > raiz.depth, "El botón debe estar más profundo que la raíz");
    }

    @Test
    @DisplayName("2. ancestroComunMasProfundo() encuentra el contenedor real que engloba a TODOS los candidatos")
    void lca_findsRealContainerEnclosingAllCandidates() {
        List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(XML_CON_CONTENEDOR).nodos;
        List<SeatUiSnapshot.Nodo> candidatos = nodos.stream()
                .filter(n -> n.tag.equals("Button"))
                .toList();
        assertEquals(3, candidatos.size());

        SeatUiSnapshot.Nodo lca = SeatUiSnapshot.ancestroComunMasProfundo(nodos, candidatos);
        assertNotNull(lca);
        assertEquals("Contenedor", lca.tag);
        assertEquals("380", lca.attrs.get("width"));
    }

    @Test
    @DisplayName("3. Sin candidatos -> ancestroComunMasProfundo() devuelve null (nunca inventa un contenedor)")
    void lca_withNoCandidates_returnsNull() {
        List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(XML_CON_CONTENEDOR).nodos;
        assertNull(SeatUiSnapshot.ancestroComunMasProfundo(nodos, List.of()));
    }

    @Test
    @DisplayName("4. Un solo candidato -> el LCA es el propio nodo (caso trivial)")
    void lca_withSingleCandidate_isTheNodeItself() {
        List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(XML_CON_CONTENEDOR).nodos;
        SeatUiSnapshot.Nodo boton1 = nodos.stream().filter(n -> "1".equals(n.attrs.get("name"))).findFirst().orElseThrow();
        SeatUiSnapshot.Nodo lca = SeatUiSnapshot.ancestroComunMasProfundo(nodos, List.of(boton1));
        assertEquals(boton1.index, lca.index);
    }

    @Test
    @DisplayName("5. Candidatos en subárboles sin contenedor común más allá de la raíz -> LCA es la raíz (nunca null si hay >=1 candidato)")
    void lca_withCandidatesInDifferentBranches_isTheRoot() {
        List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(XML_CON_CONTENEDOR).nodos;
        SeatUiSnapshot.Nodo boton1 = nodos.stream().filter(n -> "1".equals(n.attrs.get("name"))).findFirst().orElseThrow();
        SeatUiSnapshot.Nodo continuar = nodos.stream()
                .filter(n -> "Continuar, 0".equals(n.attrs.get("name"))).findFirst().orElseThrow();
        SeatUiSnapshot.Nodo lca = SeatUiSnapshot.ancestroComunMasProfundo(nodos, List.of(boton1, continuar));
        assertNotNull(lca);
        assertEquals("Otro", lca.tag);
        assertEquals("400", lca.attrs.get("width")); // el "Otro" externo, no el Contenedor interno
    }
}
