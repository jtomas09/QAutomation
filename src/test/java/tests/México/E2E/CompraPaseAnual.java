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
 * Patrón:
 *   1. BaseTest gestiona driver + cine México (@BeforeEach heredado)
 *   2. seleccionarPeliculaRandomYHorarioDescartandoAlertas() navega al mapa
 *      descartando alertas de clasificación, Atención, etc.
 *   3. seleccionarAsientoRandomDisponible() usa SeatMap (60 s + fallbacks)
 *   4. clickContinuarMapaAsientos() verifica que el selector de boletos cargó
 *   5. Pestaña Pase Anual → folio → Aplicar → validar
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

        // Paso 1 — seleccionar película + función descartando alertas de clasificación
        TestSteps.run("Seleccionar película y función", () ->
            asientosPagina.seleccionarPeliculaRandomYHorarioDescartandoAlertas()
        , driver);

        // Paso 2 — seleccionar asiento usando SeatMap (60 s + estrategias S1-S4)
        TestSteps.run("Seleccionar asiento", () ->
            asientosPagina.seleccionarAsientoRandomDisponible()
        , driver);

        // Paso 3 — continuar al selector de boletos (verifica que la pantalla cargó)
        TestSteps.run("Continuar al selector de boletos", () ->
            mapa.clickContinuarMapaAsientos()
        , driver);

        // Paso 4 — activar pestaña Pase Anual, ingresar folio y aplicar
        TestSteps.run("Ingresar folio Pase Anual", () -> {
            mapa.seleccionarTabPaseAnual();
            mapa.ingresarFolioPaseAnual(folio);
            mapa.clickAplicarFolio();
        }, driver);

        // Paso 5 — validar que no hubo error de folio
        TestSteps.run("Validar folio aplicado correctamente", () ->
            mapa.validarFolioAplicado()
        , driver);
    }
}
