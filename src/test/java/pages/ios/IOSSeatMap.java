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
     * Intenta seleccionar más de 10 asientos y valida que aparezca la alerta de
     * límite máximo. Lanza si la alerta nunca aparece (ver TAREA 29: el mapa de
     * asientos — no este loop de tap — es el sospechoso principal cuando este método
     * tarda ~180s sin encontrar la alerta).
     */
    public List<String> seleccionarMasDe10AsientosYValidarAlerta() {
        return legacy.seleccionarMasDe10AsientosYValidarAlerta();
    }

    /** Selecciona un asiento especial (discapacidad). Devuelve su identificador. */
    public String seleccionarAsientoEspecial() {
        return legacy.seleccionarAsientoEspecial();
    }

    /**
     * Verifica (una sola llamada, sin espera implícita) si la app sigue mostrando
     * la pantalla de asientos ahora mismo. Usado para decidir de forma SEGURA (nunca
     * asumida) si un test puede reutilizar la pantalla actual en vez de repetir
     * PromosGuard/MovieDetection/MovieOpen/ScheduleSelection.
     */
    public boolean estaEnPantallaDeAsientos() {
        return legacy.estaRealmenteEnPantallaDeAsientos();
    }
}
