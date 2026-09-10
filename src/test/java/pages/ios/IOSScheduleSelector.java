package pages.ios;

import io.appium.java_client.AppiumDriver;
import pages.asientos.SelectorPage;

/**
 * Responsabilidad iOS: obtener horarios, seleccionar horario, descartar alertas
 * propias de la pantalla de horarios (Restricciones/Sala Junior, "Aceptar y
 * continuar"/Atención) y validar la transición hacia la pantalla de asientos
 * (TAREA 30, Fase 5).
 *
 * Mapa Android → iOS: hoy "Schedule selector" vive dentro de {@link SelectorPage}
 * (clase compartida, NO modificada por esta tarea) — en particular
 * {@code seleccionarPrimerHorarioDescartandoAlertas()}, identificado en TAREA 29 como
 * el hotspot que explica el patrón "ScheduleSelection ~42-52s". Esta clase SOLO delega
 * en la instancia existente: mismo algoritmo, mismos timeouts (1000/100, 600/100,
 * 1500/150 ms), sin sleeps ni retries nuevos — la optimización real queda para una
 * tarea futura, una vez que la lógica se pueda mover aquí sin arrastrar a Android.
 */
public class IOSScheduleSelector extends IOSBasePage {

    private final SelectorPage legacy;

    public IOSScheduleSelector(AppiumDriver driver) {
        super(driver);
        this.legacy = new SelectorPage(driver);
    }

    /** Comparte la misma instancia de SelectorPage que {@link IOSMovieSelector}. */
    public IOSScheduleSelector(AppiumDriver driver, SelectorPage shared) {
        super(driver);
        this.legacy = shared;
    }

    /**
     * ScheduleSelection: recorre los horarios disponibles descartando alertas
     * inesperadas (Restricciones/Sala Junior) hasta encontrar uno navegable.
     * Devuelve el texto del horario elegido.
     */
    public String seleccionarPrimerHorarioDescartandoAlertas() {
        return legacy.seleccionarPrimerHorarioDescartandoAlertas();
    }

    /** Selecciona el primer horario visible en el grid, sin manejo de alertas. */
    public void seleccionarHorario() {
        legacy.seleccionarHorario();
    }

    /** Selecciona el primer horario disponible (variante simple, usada fuera de Asientos). */
    public String seleccionarPrimerHorarioDisponible() {
        return legacy.seleccionarPrimerHorarioDisponible();
    }

    /** Selecciona un horario aleatorio disponible, haciendo scroll hasta maxScrolls veces. */
    public String seleccionarHorarioRandomDisponible(int maxScrolls) {
        return legacy.seleccionarHorarioRandomDisponible(maxScrolls);
    }

    /** Cambia el horario desde la pantalla de mapa de asientos. */
    public String cambiarHorarioEnPantallaAsientos() {
        return legacy.cambiarHorarioEnPantallaAsientos();
    }

    /** Alerta de Restricciones (Sala Junior u otras) — visibilidad y manejo. */
    public boolean estaVisibleAlertaRestricciones() {
        return legacy.estaVisibleAlertaRestricciones();
    }

    public void validarYManejarAlertaRestricciones(boolean aceptar) {
        legacy.validarYManejarAlertaRestricciones(aceptar);
    }

    /** Alerta "Aceptar y continuar" / Atención, propia del flujo de horarios. */
    public boolean aceptarAlertaAceptarYContinuarSiPresente() {
        return legacy.aceptarAlertaAceptarYContinuarSiPresente();
    }

    public boolean estaVisibleAlertaAtencion() {
        return legacy.estaVisibleAlertaAtencion();
    }

    public boolean aceptarAlertaAtencionSiPresente() {
        return legacy.aceptarAlertaAtencionSiPresente();
    }
}
