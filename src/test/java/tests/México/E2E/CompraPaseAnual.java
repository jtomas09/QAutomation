package tests.México.E2E;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import base.BaseTest;
import config.DriverFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import pages.common.CinemasHelper;
import pages.homeCartelera.SelectorsHome;
import pages.mapaAsientos.SelectorsMapaAsientos;
import pages.seleccionCines.SelectorsCines;
import utils.TestSteps;

/**
 * Flujo automatizado de Compra con Pase Anual.
 *
 * Reutiliza la misma arquitectura de FlujosCompraNoLogin:
 *   1. Seleccionar cine
 *   2. Seleccionar función / horario
 *   3. Seleccionar asiento (reutiliza mapa.seleccionarAsiento())
 *   4. Continuar al selector de boletos
 *   5. Activar pestaña Pase Anual
 *   6. Ingresar folio
 *   7. Presionar Aplicar
 *   8. Validar resultado
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Compra con Pase Anual - México")
public class CompraPaseAnual extends BaseTest {

    /** Folio configurable vía propiedad de sistema: -DpaseAnualFolio=XXXX */
    private static final String FOLIO_DEFAULT = "0000 0000 0000 0000";

    private SelectorsCines        cines;
    private SelectorsHome         home;
    private SelectorsMapaAsientos mapa;

    @BeforeAll
    void configurarPais() {
        driver = DriverFactory.getDriver();
        new SelectorsHome(driver).cambiarPaisMexico();
        new CinemasHelper(driver).ensureMexicoCinemaSelected();
    }

    @BeforeEach
    void setUp() {
        cines = new SelectorsCines(driver);
        home  = new SelectorsHome(driver);
        mapa  = new SelectorsMapaAsientos(driver);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: Flujo completo de Compra con Pase Anual
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Compra con Pase Anual - Aplicar Folio")
    @Story("Compra con Pase Anual sin sesión iniciada - México")
    void compraPaseAnual() {
        String folio = System.getProperty("paseAnualFolio", FOLIO_DEFAULT);

        TestSteps.run("Seleccionar función y asiento", () -> {
            cines.ensureCineTradicionalSeleccionado();
            home.seleccionarPrimerHorario();
            mapa.seleccionarAsiento();
            mapa.clickContinuarMapaAsientos();
        }, driver);

        TestSteps.run("Ingresar folio Pase Anual", () -> {
            mapa.seleccionarTabPaseAnual();
            mapa.ingresarFolioPaseAnual(folio);
            mapa.clickAplicarFolio();
        }, driver);

        TestSteps.run("Validar folio aplicado correctamente", () -> {
            mapa.validarFolioAplicado();
        }, driver);
    }
}
