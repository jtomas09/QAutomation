package tests.México.E2E;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import pages.asientos.AsientosPagina;
import pages.mapaAsientos.SelectorsMapaAsientos;
import utils.TestSteps;

/**
 * Flujo automatizado de Compra con Pase Anual.
 *
 * Arquitectura equivalente a FlujosCompraNoLogin:
 *   1. BaseTest gestiona driver + selección de cine México (@BeforeEach)
 *   2. seleccionarPeliculaRandomYHorarioDescartandoAlertas() navega a la
 *      función y descarta alertas de clasificación, Atención, etc.
 *   3. seleccionarAsiento() + clickContinuarMapaAsientos() llegan al
 *      selector de boletos.
 *   4. Pestaña Pase Anual → ingresar folio → Aplicar → validar.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Compra con Pase Anual - México")
public class CompraPaseAnual extends BaseTest {

    /** Folio configurable vía -DpaseAnualFolio=XXXX */
    private static final String FOLIO_DEFAULT = "0000 0000 0000 0000";

    private AsientosPagina        asientosPagina;
    private SelectorsMapaAsientos mapa;

    @BeforeEach
    void setUp() {
        // BaseTest.setUp() ya creó el driver y seleccionó cine México.
        asientosPagina = new AsientosPagina(driver);
        mapa           = new SelectorsMapaAsientos(driver);
    }

    @Test
    @Order(1)
    @DisplayName("Compra con Pase Anual - Aplicar Folio")
    @Story("Compra con Pase Anual sin sesión iniciada - México")
    void compraPaseAnual() {
        String folio = System.getProperty("paseAnualFolio", FOLIO_DEFAULT);

        // Paso 1: seleccionar película y horario, descartando cualquier alerta
        // (clasificación C, Atención/vibraciones, Restricciones, etc.)
        TestSteps.run("Seleccionar película y función", () ->
            asientosPagina.seleccionarPeliculaRandomYHorarioDescartandoAlertas()
        , driver);

        // Paso 2: seleccionar asiento y continuar al selector de boletos
        TestSteps.run("Seleccionar asiento", () -> {
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
        }, driver);

        // Paso 3: pestaña Pase Anual → ingresar folio → Aplicar
        TestSteps.run("Ingresar folio Pase Anual", () -> {
            mapa.seleccionarTabPaseAnual();
            mapa.ingresarFolioPaseAnual(folio);
            mapa.clickAplicarFolio();
        }, driver);

        // Paso 4: validar que el folio fue aceptado sin errores
        TestSteps.run("Validar folio aplicado correctamente", () ->
            mapa.validarFolioAplicado()
        , driver);
    }
}
