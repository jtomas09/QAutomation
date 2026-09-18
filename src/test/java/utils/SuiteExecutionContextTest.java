package utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA arquitectura — reset de aplicación entre escenarios / SuiteExecutionContext
 * ya no controla el lifecycle.
 *
 * Pruebas puras (sin Appium/WDA/hardware): {@link SuiteExecutionContext} es un
 * contenedor de estado estático simple, sin efectos secundarios de UI, así que su
 * comportamiento se puede verificar directamente. No requiere iPhone físico.
 *
 * Estas pruebas demuestran la mitad "estado" del contrato de reset — que
 * {@code resetAll()} invalida TODO sin importar en qué pantalla/estado estaba,
 * incluido específicamente {@code Screen.SEAT_MAP} (el caso que la arquitectura
 * anterior usaba para OMITIR el reinicio de la app; ahora demostrado que el reset
 * lo invalida igual). La otra mitad del contrato — que
 * {@code base.BaseTest#tearDown} llama a {@code resetApplicationBetweenTests()}
 * incondicionalmente sin importar PASS/FAIL/SKIP/excepción — no es verificable sin
 * un {@code AppiumDriver} real: este módulo no usa Mockito en ningún test existente
 * (confirmado) y no hay ningún seam de inyección de dependencias para el driver en
 * BaseTest, así que esa mitad se verifica por lectura de código (tearDown() ya no
 * tiene ninguna rama condicionada por SuiteExecutionContext ni por el resultado del
 * test — ver el propio código de BaseTest.java), no con un mock fabricado que no
 * probaría nada real.
 */
@DisplayName("SuiteExecutionContext — invalidación completa en reset")
class SuiteExecutionContextTest {

    @Test
    @DisplayName("1. resetAll() invalida TODO incluso partiendo de SEAT_MAP con película/horario marcados")
    void resetAll_invalidatesEverythingEvenFromSeatMap() {
        // Simula exactamente el estado que la arquitectura ANTERIOR usaba como
        // evidencia para OMITIR el reinicio de la app entre tests.
        SuiteExecutionContext.markCinemaSelected("Cinépolis Test");
        SuiteExecutionContext.markMovieAndScheduleSelected("Película X", "20:00");
        assertEquals(SuiteExecutionContext.Screen.SEAT_MAP, SuiteExecutionContext.currentScreen());
        assertEquals("Película X", SuiteExecutionContext.movieSelected());
        assertEquals("20:00", SuiteExecutionContext.scheduleSelected());

        SuiteExecutionContext.resetAll();

        assertNull(SuiteExecutionContext.movieSelected());
        assertNull(SuiteExecutionContext.scheduleSelected());
        assertNull(SuiteExecutionContext.cinemaSelected());
        assertEquals(SuiteExecutionContext.Screen.UNKNOWN, SuiteExecutionContext.currentScreen());
    }

    @Test
    @DisplayName("2. resetAll() es idempotente sobre un contexto ya vacío (no lanza, no deja rastro)")
    void resetAll_onAlreadyEmptyContext_staysEmpty() {
        SuiteExecutionContext.resetAll();
        SuiteExecutionContext.resetAll();
        assertNull(SuiteExecutionContext.movieSelected());
        assertNull(SuiteExecutionContext.scheduleSelected());
        assertNull(SuiteExecutionContext.cinemaSelected());
        assertEquals(SuiteExecutionContext.Screen.UNKNOWN, SuiteExecutionContext.currentScreen());
    }

    @Test
    @DisplayName("3. invalidateNavigation() borra película+horario+pantalla (caso: 'Continuar' avanzó)")
    void invalidateNavigation_clearsMovieScheduleAndScreen() {
        SuiteExecutionContext.markMovieAndScheduleSelected("Película Y", "18:30");
        SuiteExecutionContext.invalidateNavigation();
        assertNull(SuiteExecutionContext.movieSelected());
        assertNull(SuiteExecutionContext.scheduleSelected());
        assertEquals(SuiteExecutionContext.Screen.UNKNOWN, SuiteExecutionContext.currentScreen());
        SuiteExecutionContext.resetAll();
    }

    @Test
    @DisplayName("4. markMovieAndScheduleSelected() marca SEAT_MAP como pantalla actual")
    void markMovieAndScheduleSelected_setsScreenToSeatMap() {
        SuiteExecutionContext.markMovieAndScheduleSelected("Película Z", "22:15");
        assertEquals(SuiteExecutionContext.Screen.SEAT_MAP, SuiteExecutionContext.currentScreen());
        SuiteExecutionContext.resetAll();
    }
}
