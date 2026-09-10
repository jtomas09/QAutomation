package pages.ios;

import io.appium.java_client.AppiumDriver;
import pages.asientos.SelectorPage;

/**
 * Responsabilidad iOS: detección y manejo de alertas propias de la pantalla de
 * asientos (asiento especial/discapacidad) — TAREA 30, Fase 6.
 *
 * La alerta de "límite máximo de asientos" (más de 10) hoy vive EMBEBIDA dentro de
 * {@code SelectorPage.seleccionarMasDe10AsientosYValidarAlerta()} (ver
 * {@link IOSSeatMap}) sin un método público independiente de solo-verificación —
 * extraerla es trabajo de una tarea futura de optimización, no de esta tarea de
 * arquitectura (no se cambia funcionalidad todavía).
 */
public class IOSSeatAlertHandler extends IOSBasePage {

    private final SelectorPage legacy;

    public IOSSeatAlertHandler(AppiumDriver driver) {
        super(driver);
        this.legacy = new SelectorPage(driver);
    }

    /** Comparte la misma instancia de SelectorPage que el resto de la familia iOS. */
    public IOSSeatAlertHandler(AppiumDriver driver, SelectorPage shared) {
        super(driver);
        this.legacy = shared;
    }

    /** True si la alerta de asiento especial (discapacidad) está visible ahora mismo. */
    public boolean estaVisibleAlertaAsientoEspecial() {
        return legacy.estaVisibleAlertaAsientoEspecial();
    }

    /** Valida y maneja (acepta o cancela) la alerta de asiento especial. */
    public void validarYManejarAlertaAsientoEspecial(boolean aceptar) {
        legacy.validarYManejarAlertaAsientoEspecial(aceptar);
    }
}
