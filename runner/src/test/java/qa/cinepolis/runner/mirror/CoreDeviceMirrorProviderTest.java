package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * meetsMinimumVersion() es la ÚNICA parte de CoreDeviceMirrorProvider que hoy
 * tiene un comportamiento real (el resto siempre reporta "no disponible" a
 * propósito — ver javadoc de la clase). Verifica el parseo de versión contra
 * casos reales y de borde, sin depender de un dispositivo físico.
 */
@DisplayName("CoreDeviceMirrorProvider.meetsMinimumVersion")
class CoreDeviceMirrorProviderTest {

    @Test
    @DisplayName("iOS 26.6 (el dispositivo real investigado) no cumple — requiere 27+")
    void ios26DoesNotMeetMinimum() {
        assertFalse(CoreDeviceMirrorProvider.meetsMinimumVersion("26.6"));
    }

    @Test
    @DisplayName("iOS 27.0 sí cumple el mínimo")
    void ios27MeetsMinimum() {
        assertTrue(CoreDeviceMirrorProvider.meetsMinimumVersion("27.0"));
    }

    @Test
    @DisplayName("versiones mayores a 27 también cumplen")
    void ios28MeetsMinimum() {
        assertTrue(CoreDeviceMirrorProvider.meetsMinimumVersion("28.1"));
    }

    @Test
    @DisplayName("versión null o vacía nunca cumple")
    void nullOrBlankNeverMeets() {
        assertFalse(CoreDeviceMirrorProvider.meetsMinimumVersion(""));
    }

    @Test
    @DisplayName("versión no numérica no lanza excepción — nunca cumple")
    void malformedVersionNeverThrows() {
        assertFalse(CoreDeviceMirrorProvider.meetsMinimumVersion("not-a-version"));
    }
}
