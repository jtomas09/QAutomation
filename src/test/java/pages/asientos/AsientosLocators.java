package pages.asientos;

import org.openqa.selenium.By;

/**
 * Locators centralizados para la pantalla de selección de asientos.
 * Reemplaza a AsientosLocator (singular, deprecada).
 */
public final class AsientosLocators {

    private AsientosLocators() {}

    // ─── Navegación ───────────────────────────────────────────────────────────

    public static final By BTN_CONTINUAR = By.xpath(
        "//android.widget.Button[@content-desc='Continuar']");

    public static final By BTN_REGRESAR = By.xpath(
        "//android.view.View[@content-desc='Ir atrás']");

    // ─── Filtros ──────────────────────────────────────────────────────────────

    public static final By BTN_FILTRO_3D = By.xpath(
        "//android.widget.TextView[@text='3D']");

    public static final By BTN_FILTRO_SALA_JUNIOR = By.xpath(
        "//android.widget.TextView[@text='Sala Junior']");

    // ─── Mapa de asientos ─────────────────────────────────────────────────────

    public static final By ASIENTO_DISPONIBLE = By.xpath(
        "//android.view.View[@content-desc='Asiento disponible']");

    public static final By ASIENTO_ESPECIAL = By.xpath(
        "//android.view.View[contains(@content-desc,'Especial') or contains(@content-desc,'Discapacidad')]");

    // ─── Alertas ──────────────────────────────────────────────────────────────

    public static final By ALERTA_LIMITE_ASIENTOS = By.xpath(
        "//android.widget.TextView[contains(@text,'máximo') or contains(@text,'límite')]");

    public static final By ALERTA_ASIENTO_ESPECIAL = By.xpath(
        "//android.widget.TextView[contains(@text,'especial') or contains(@text,'Especial')]");

    public static final By BTN_ACEPTAR_ALERTA = By.xpath(
        "//android.widget.Button[@text='Aceptar' or @text='OK']");
}
