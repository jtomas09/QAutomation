package pages.ios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — portar el caso "Selección de más de 10 Asientos" a un motor iOS
 * independiente (IOSSeatLimitEngine).
 *
 * Pruebas puras (sin Appium/WDA/hardware) de los tres algoritmos de
 * IOSSeatLimitEngine que no dependen de un WebElement/driver real: parseo del número
 * de asiento desde un atributo XCUITest, parseo del contador real desde el botón
 * "Continuar, N", e identidad física entre dos posiciones. No requiere iPhone físico.
 */
@DisplayName("IOSSeatLimitEngine — algoritmos puros")
class IOSSeatLimitEngineTest {

    @Test
    @DisplayName("1. parseNumeroAsiento: texto de 1-2 dígitos válido")
    void parseNumeroAsiento_valido() {
        assertEquals(7, IOSSeatLimitEngine.parseNumeroAsiento("7"));
        assertEquals(11, IOSSeatLimitEngine.parseNumeroAsiento("11"));
        assertEquals(11, IOSSeatLimitEngine.parseNumeroAsiento(" 11 "));
    }

    @Test
    @DisplayName("2. parseNumeroAsiento: rechaza texto que no es un número de asiento")
    void parseNumeroAsiento_invalido() {
        assertNull(IOSSeatLimitEngine.parseNumeroAsiento(null));
        assertNull(IOSSeatLimitEngine.parseNumeroAsiento(""));
        assertNull(IOSSeatLimitEngine.parseNumeroAsiento("Continuar"));
        assertNull(IOSSeatLimitEngine.parseNumeroAsiento("123")); // 3 dígitos, no es asiento
        assertNull(IOSSeatLimitEngine.parseNumeroAsiento("A1"));
    }

    @Test
    @DisplayName("3. parseContadorContinuar: extrae el número real de 'Continuar, N'")
    void parseContadorContinuar_valido() {
        assertEquals(1, IOSSeatLimitEngine.parseContadorContinuar("Continuar, 1"));
        assertEquals(10, IOSSeatLimitEngine.parseContadorContinuar("Continuar, 10"));
        assertEquals(3, IOSSeatLimitEngine.parseContadorContinuar("Continuar,3"));
    }

    @Test
    @DisplayName("4. parseContadorContinuar: null si el texto no calza con el formato esperado")
    void parseContadorContinuar_invalido() {
        assertNull(IOSSeatLimitEngine.parseContadorContinuar(null));
        assertNull(IOSSeatLimitEngine.parseContadorContinuar("Continuar"));
        assertNull(IOSSeatLimitEngine.parseContadorContinuar("Comprar, 10"));
    }

    @Test
    @DisplayName("5. mismaPosicionFisica: dentro de la tolerancia -> true")
    void mismaPosicionFisica_dentroDeTolerancia() {
        assertTrue(IOSSeatLimitEngine.mismaPosicionFisica(100, 100, 110, 110)); // distancia ~14.1
    }

    @Test
    @DisplayName("6. mismaPosicionFisica: fuera de la tolerancia -> false (asientos físicamente distintos)")
    void mismaPosicionFisica_fueraDeTolerancia() {
        assertFalse(IOSSeatLimitEngine.mismaPosicionFisica(100, 100, 200, 100)); // distancia 100
    }

    @Test
    @DisplayName("7. mismaPosicionFisica: posición idéntica -> true")
    void mismaPosicionFisica_identica() {
        assertTrue(IOSSeatLimitEngine.mismaPosicionFisica(500, 800, 500, 800));
    }
}
