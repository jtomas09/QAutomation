package tests.México.asientos;

import base.BaseTest;
import config.DriverFactory;
import flujos.ios.IOSAsientosFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import pages.asientos.AsientosPagina;
import utils.TestSteps;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Pruebas de NO Afectación - Asientos")
public class SeleccionAsientos extends BaseTest {

    private AsientosPagina page;
    // TAREA 31 — punto de entrada iOS (ver flujos.ios.IOSAsientosFlow). Android sigue
    // usando exclusivamente `page` como siempre; `iosFlow` solo se crea/usa en
    // DriverFactory.isIOS()==true, nunca al revés.
    private IOSAsientosFlow iosFlow;

    @BeforeEach
    void setUp() {
        page = new AsientosPagina(driver);
        if (DriverFactory.isIOS()) {
            iosFlow = new IOSAsientosFlow(driver);
        }
    }

    private void seleccionarPeliculaYHorario() {
        if (DriverFactory.isIOS()) {
            TestSteps.run("Selección de Película y horario", () ->
                iosFlow.seleccionarPeliculaRandomYHorarioDescartandoAlertas(), driver);
        } else {
            TestSteps.run("Selección de Película y horario", () ->
                page.seleccionarPeliculaRandomYHorarioDescartandoAlertas(), driver);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Selección de 1 Asiento")
    @Story("Asientos")
    void seleccion1Asiento() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionar1Asiento(driver);
            return;
        }
        TestSteps.run("Selección de asiento disponible", () -> page.seleccionarAsientoRandomDisponible(), driver);
        TestSteps.run("Continuar con asiento seleccionado", () -> page.continuar(), driver);
    }

    @Test
    @Order(2)
    @DisplayName("Selección de Múltiples Asientos")
    @Story("Asientos")
    void seleccionMultiplesAsientos() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionar3AsientosAleatorios(driver);
            return;
        }
        TestSteps.run("Selección de 3 asientos disponibles", () -> page.seleccionar3AsientosRandomDisponibles(), driver);
        TestSteps.run("Continuar con asientos seleccionados", () -> page.continuar(), driver);
    }

    @Test
    @Order(3)
    @DisplayName("Selección de Asientos Consecutivos")
    @Story("Asientos")
    void seleccionAsientosConsecutivos() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionar3AsientosConsecutivos(driver);
            return;
        }
        TestSteps.run("Selección de 3 asientos consecutivos", () -> page.seleccionar3AsientosConsecutivosDisponibles(), driver);
        TestSteps.run("Continuar con asientos seleccionados", () -> page.continuar(), driver);
    }

    @Test
    @Order(4)
    @DisplayName("Selección de Asientos y Deselección de los Asientos")
    @Story("Asientos")
    void seleccionAsientosYDeseleccion() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionarYDeseleccionar3AsientosConsecutivos(driver);
            return;
        }
        TestSteps.run("Pantalla de asientos", () ->
            page.seleccionarYDeseleccionar3AsientosConsecutivosDisponibles(), driver);
    }

    @Test
    @Order(5)
    @DisplayName("Selección de más de 10 Asientos con Mensaje de Alerta")
    @Story("Asientos")
    void seleccion11Asientos() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.validarLimite10Asientos(driver);
            return;
        }
        TestSteps.run("Pantalla de asientos", () ->
            page.seleccionarMasDe10AsientosYValidarAlerta(), driver);
    }

    @Test
    @Order(6)
    @DisplayName("Cambio de Horario en el Mapa de Asientos")
    @Story("Asientos")
    void cambioHorarioAsientos() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.cambiarHorario(driver);
            return;
        }
        TestSteps.run("Pantalla de asientos", () ->
            page.cambiarHorarioEnPantallaAsientos(), driver);
    }

    @Test
    @Order(7)
    @DisplayName("Verificación de Banner en Asientos 3D")
    @Story("Asientos")
    void asientos3D() {
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionarFiltro3D(driver);
            return;
        }
        try {
            TestSteps.run("Seleccionar filtro 3D", () -> page.seleccionarFiltro3D(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            // TestSteps ya capturó screenshot del panel abierto; cerramos el panel y re-lanzamos
            try { driver.navigate().back(); Thread.sleep(400); } catch (Exception ignored) {}
            throw e;
        }
        TestSteps.run("Selección de Película y horario", () -> page.seleccionarPeliculaRandomYHorario(), driver);
    }

    @Test
    @Order(8)
    @DisplayName("Validación de Alerta en Asiento Especial")
    @Story("Asientos")
    void alertaAsientoEspecial() {
        seleccionarPeliculaYHorario();
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionarAsientoEspecialYValidarAlerta(driver);
            return;
        }
        try {
            TestSteps.run("Seleccionar asiento especial", () -> page.seleccionarAsientoEspecial(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            throw e;
        }
        TestSteps.run("Validar alerta de asiento especial", () -> page.validarYManejarAlertaAsientoEspecial(true), driver);
    }

    @Test
    @Order(9)
    @DisplayName("Verificación de Banner en Sala Junior")
    @Story("Asientos")
    void asientosSalaJunior() {
        if (DriverFactory.isIOS()) {
            iosFlow.seleccionarSalaJunior(driver);
            return;
        }
        try {
            TestSteps.run("Seleccionar filtro Sala Junior", () -> page.seleccionarFiltroSalaJunior(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            try { driver.navigate().back(); Thread.sleep(400); } catch (Exception ignored) {}
            throw e;
        }
        try {
            TestSteps.run("Selección de Película y horario", () -> page.seleccionarPeliculaYHorarioSalaJunior(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            throw e;
        }
    }
}
