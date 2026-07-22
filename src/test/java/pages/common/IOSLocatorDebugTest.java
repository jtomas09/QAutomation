package pages.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida el MECANISMO del motor de sugerencias de IOSLocatorDebug (parseo de page
 * source + prioridad de atributos) usando XML SINTÉTICO de prueba — NO evidencia
 * de un dispositivo real. Estos fixtures existen únicamente para probar que el
 * algoritmo de priorización (name > label > value > NSPredicate > posición) elige
 * correctamente dada una entrada conocida; no representan ni deben usarse para
 * concluir nada sobre los locators reales de la app (personalizar(), SEARCH_INPUT,
 * etc. — eso requiere una captura real de build/ios-diagnostics/ contra un iPhone).
 */
public class IOSLocatorDebugTest {

    @Test
    @DisplayName("parseCandidates: extrae atributos de un page source XCUITest sintético")
    void parseaCandidatosDeXmlSintetico() {
        String xml = "<AppiumAUT>"
                + "<XCUIElementTypeApplication name=\"Cinepolis\">"
                + "  <XCUIElementTypeButton name=\"add_to_cart_button\" label=\"\" value=\"\" "
                + "        enabled=\"true\" visible=\"true\" accessible=\"true\" x=\"10\" y=\"20\" width=\"100\" height=\"40\"/>"
                + "  <XCUIElementTypeButton name=\"\" label=\"Siguiente\" value=\"\" "
                + "        enabled=\"true\" visible=\"true\" accessible=\"true\" x=\"10\" y=\"80\" width=\"100\" height=\"40\"/>"
                + "  <XCUIElementTypeButton name=\"\" label=\"\" value=\"\" "
                + "        enabled=\"true\" visible=\"true\" accessible=\"false\" x=\"10\" y=\"140\" width=\"40\" height=\"40\"/>"
                + "</XCUIElementTypeApplication>"
                + "</AppiumAUT>";

        List<Map<String, String>> candidatos = IOSLocatorDebug.parseCandidates(xml);

        assertEquals(3, candidatos.size(), "debe encontrar los 3 XCUIElementTypeButton");
        assertEquals("add_to_cart_button", candidatos.get(0).get("name"));
        assertEquals("Siguiente", candidatos.get(1).get("label"));
        assertTrue(candidatos.get(2).get("name").isBlank() && candidatos.get(2).get("label").isBlank());
    }

    @Test
    @DisplayName("suggestLocator: prioriza name sobre label/value cuando ambos existen en el árbol")
    void priorizaNameSobreLabel() {
        List<Map<String, String>> candidatos = IOSLocatorDebug.parseCandidates(
                "<A><XCUIElementTypeButton name=\"\" label=\"Aceptar\" value=\"\"/>"
                        + "<XCUIElementTypeButton name=\"confirm_btn\" label=\"\" value=\"\"/></A>");

        String sugerencia = IOSLocatorDebug.suggestLocator(candidatos);
        assertTrue(sugerencia.contains("accessibilityId(\"confirm_btn\")"),
                "debe sugerir accessibilityId por name aunque otro candidato tenga label: " + sugerencia);
    }

    @Test
    @DisplayName("suggestLocator: cae a label cuando ningún candidato tiene name")
    void caeALabelSinName() {
        List<Map<String, String>> candidatos = IOSLocatorDebug.parseCandidates(
                "<A><XCUIElementTypeButton name=\"\" label=\"Siguiente\" value=\"\"/></A>");

        String sugerencia = IOSLocatorDebug.suggestLocator(candidatos);
        assertTrue(sugerencia.contains("@label='Siguiente'"), "debe sugerir por label: " + sugerencia);
    }

    @Test
    @DisplayName("suggestLocator: sin name/label/value, sugiere iOSNsPredicateString con advertencia")
    void caeANsPredicateSinAtributos() {
        List<Map<String, String>> candidatos = IOSLocatorDebug.parseCandidates(
                "<A><XCUIElementTypeButton name=\"\" label=\"\" value=\"\"/></A>");

        String sugerencia = IOSLocatorDebug.suggestLocator(candidatos);
        assertTrue(sugerencia.contains("iOSNsPredicateString"), "debe sugerir NSPredicate: " + sugerencia);
        assertTrue(sugerencia.contains("ADVERTENCIA"), "debe advertir la ambigüedad: " + sugerencia);
    }

    @Test
    @DisplayName("parseCandidates: XML vacío o inválido no lanza, retorna lista vacía")
    void toleraXmlInvalido() {
        assertEquals(0, IOSLocatorDebug.parseCandidates(null).size());
        assertEquals(0, IOSLocatorDebug.parseCandidates("").size());
        assertEquals(0, IOSLocatorDebug.parseCandidates("<no-cierra>").size());
    }

    @Test
    @DisplayName("isEnabled() es false por defecto — el modo no cambia comportamiento salvo activación explícita")
    void deshabilitadoPorDefecto() {
        assertTrue(!IOSLocatorDebug.isEnabled() || "true".equalsIgnoreCase(System.getProperty("IOS_LOCATOR_DEBUG")));
    }
}
