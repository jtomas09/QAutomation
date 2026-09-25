package pages.ios;

import io.appium.java_client.AppiumDriver;
import pages.asientos.SelectorPage;

import java.util.List;

/**
 * Responsabilidad iOS: construir el mapa de asientos, localizar asientos y ejecutar
 * las distintas estrategias de selección (1, 3 aleatorios, 3 consecutivos, selección +
 * deselección, más de 10 con validación de alerta de límite) — TAREA 30, Fase 6.
 *
 * {@code SeatSelectionEngine} (el motor real de tap de asientos, ya identificado en
 * TAREA 29 como razonablemente bien optimizado) es una clase package-private de
 * {@code pages.asientos}, así que esta fachada NO la referencia directamente — la
 * reutiliza INDIRECTAMENTE a través de los métodos públicos de {@link SelectorPage}
 * que ya la invocan internamente. Esto cumple la Fase 6 ("reutilizarlo... sin
 * acoplarlo a Android") sin tener que tocar modificadores de acceso de una clase
 * compartida. Cero cambios de algoritmo, cero cambios de aleatoriedad.
 */
public class IOSSeatMap extends IOSBasePage {

    private final SelectorPage legacy;

    public IOSSeatMap(AppiumDriver driver) {
        super(driver);
        this.legacy = new SelectorPage(driver);
    }

    /** Comparte la misma instancia de SelectorPage que el resto de la familia iOS. */
    public IOSSeatMap(AppiumDriver driver, SelectorPage shared) {
        super(driver);
        this.legacy = shared;
    }

    /** Confirma la compra/selección actual (botón "Continuar"). */
    public void continuar() {
        legacy.continuar();
        // FIX real (TAREA arquitectura — ciclo de vida de suite): "Continuar"
        // navega hacia adelante, fuera de la pantalla de asientos (confirmación/
        // pago) — quien realmente conoce este hecho es este método, no la clase
        // de test. Invalida el contexto aquí mismo, en el momento exacto en que
        // deja de ser cierto — nunca se invalida "todo" de forma genérica después
        // de cada test.
        utils.SuiteExecutionContext.invalidateNavigation();
    }

    /** Selecciona 1 asiento disponible al azar. Devuelve su identificador. */
    public String seleccionarAsientoRandomDisponible() {
        return legacy.seleccionarAsientoRandomDisponible();
    }

    /** Selecciona 3 asientos disponibles al azar (no necesariamente consecutivos). */
    public List<String> seleccionar3AsientosRandomDisponibles() {
        return legacy.seleccionar3AsientosRandomDisponibles();
    }

    /** Selecciona 3 asientos consecutivos disponibles. */
    public List<String> seleccionar3AsientosConsecutivosDisponibles() {
        return legacy.seleccionar3AsientosConsecutivosDisponibles();
    }

    /** Selecciona y luego deselecciona 3 asientos consecutivos (validación de UI). */
    public List<String> seleccionarYDeseleccionar3AsientosConsecutivosDisponibles() {
        return legacy.seleccionarYDeseleccionar3AsientosConsecutivosDisponibles();
    }

    /**
     * TAREA — portado a un motor exclusivo de iOS ({@link IOSSeatLimitEngine}), sin
     * ninguna dependencia de {@link SelectorPage}/UiAutomator2 — único método de esta
     * fachada que dejó de delegar en `legacy` (el resto de la clase permanece
     * exactamente igual, ver comentario de la clase). Android sigue usando
     * exclusivamente {@code SelectorPage.seleccionarMasDe10AsientosYValidarAlerta()},
     * sin ningún cambio.
     */
    public List<String> seleccionarMasDe10AsientosYValidarAlerta() {
        return new IOSSeatLimitEngine(driver).seleccionarMasDe10AsientosYValidarAlerta();
    }

    /** Selecciona un asiento especial (discapacidad). Devuelve su identificador. */
    public String seleccionarAsientoEspecial() {
        return legacy.seleccionarAsientoEspecial();
    }
}
