package flujos;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import pages.alimentos.AlimentosLocators;
import pages.alimentos.SelectorsAlimentos;
import pages.alimentos.SelectorPage;
import pages.common.CinemasHelper;
import utils.Reintento;
import utils.TestSteps;

/**
 * Capa de flujos para la sección de Alimentos.
 *
 * Responsabilidades:
 *  - Orquesta pasos de alto nivel usando páginas como bloques primitivos.
 *  - Contiene lógica de negocio (qué hacer, en qué orden).
 *  - NO conoce XPaths ni detalles de UI (eso es responsabilidad de las páginas).
 *
 * Las páginas (SelectorPage, SelectorsAlimentos) solo deben interactuar con la UI.
 * Este flujo coordina la secuencia y toma decisiones.
 */
public class AlimentosFlujo {

    private final SelectorPage pagina;
    private final SelectorsAlimentos paginaAleatoria;
    private final CinemasHelper cinemasHelper;

    public AlimentosFlujo(AppiumDriver driver) {
        this.pagina          = new SelectorPage(driver);
        this.paginaAleatoria = new SelectorsAlimentos(driver);
        this.cinemasHelper   = new CinemasHelper(driver);
    }

    // ─── Flujos de preparación ────────────────────────────────────────────────

    /**
     * Abre el menú de alimentos desde el estado inicial de la app.
     * Maneja popups de notificaciones y pantallas de bienvenida.
     */
    public void abrirMenu() {
        Reintento.intentar(datos.Constantes.REINTENTOS_POPUP, () -> {
            pagina.cerrarPantalla();
            pagina.abrirMenu();
            pagina.cerrarPantalla();
        });
    }

    /**
     * Asegura que el cine indicado esté seleccionado antes de abrir el menú.
     */
    public void abrirMenuEnCine(String nombreCine) {
        cinemasHelper.ensureCinemaSelectedFromAlimentos(nombreCine);
        abrirMenu();
    }

    // ─── Flujos de compra ─────────────────────────────────────────────────────

    /**
     * Flujo completo: abre menú → selecciona producto aleatorio → personaliza → agrega al carrito.
     * Reintenta si la personalización falla (productos agotados, etc.).
     */
    public void agregarAlimentoAleatorioAlCarrito() {
        Allure.step("Flujo: agregar alimento aleatorio al carrito");
        Reintento.conBackoff(2, 1000).ejecutar(() ->
            paginaAleatoria.seleccionarAlimentoAleatorio()
        );
    }

    /**
     * Navega al carrito y valida que el botón de regreso al menú esté visible.
     */
    public void validarCarritoConProducto() {
        pagina.abrirCarrito();
        pagina.validarElementoVisible(AlimentosLocators.BTN_REGRESARMENU);
    }

    /**
     * Agrega un producto al carrito y valida el carrito.
     * Patrón reutilizable para todos los tests de menú.
     */
    public void agregarAlCarritoYValidar(AppiumDriver driver) {
        TestSteps.run("Agregar al carrito", () -> pagina.agregarCarrito(), driver);
        TestSteps.run("Abrir carrito y validar", () -> validarCarritoConProducto(), driver);
    }

    // ─── Flujos de vinculación VIP ────────────────────────────────────────────

    /**
     * Vincula una orden VIP manualmente eligiendo la primera opción disponible
     * en cada desplegable (película, hora, fila, número).
     */
    public void vincularOrdenVIPSinSesion() {
        paginaAleatoria.vincularOrdenVIPSinSesion();
    }

    /**
     * Ir a pagar consolidando errores acumulados de productos en un solo reporte.
     */
    public void irAPagar() {
        paginaAleatoria.clikIrAPagar();
    }
}
