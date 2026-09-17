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

    /**
     * Valida y maneja (acepta o cancela) la alerta de asiento especial.
     *
     * FIX real (TAREA arquitectura — matriz estática de transiciones): "Aceptar y
     * continuar" es, por nombre y función, una acción de progresión — no existe
     * evidencia real (page source) de que la app permanezca en la pantalla de
     * asientos después de aceptar. Ante la duda, se invalida de forma conservadora
     * (mismo criterio que ya aplica {@code SelectorPage.continuar()}: cualquier tap
     * que pueda avanzar el flujo invalida, nunca se asume que "se quedó igual").
     * Ningún test de esta suite necesita reutilizar el mapa de asientos después de
     * este método — el siguiente test (Sala Junior) requiere la pantalla de
     * filtros, no el mapa de asientos — así que invalidar aquí no cuesta ninguna
     * optimización real y sí cierra un riesgo: sin esto, un relanzamiento podía
     * omitirse por error si la app en realidad ya había avanzado.
     */
    public void validarYManejarAlertaAsientoEspecial(boolean aceptar) {
        legacy.validarYManejarAlertaAsientoEspecial(aceptar);
        if (aceptar) {
            utils.SuiteExecutionContext.invalidateNavigation();
        }
    }
}
