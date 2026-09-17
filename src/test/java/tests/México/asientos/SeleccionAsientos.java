package tests.México.asientos;

import base.BaseTest;
import config.DriverFactory;
import flujos.ios.IOSAsientosFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.asientos.AsientosPagina;
import utils.TestSteps;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Epic("Pruebas de NO Afectación - Asientos")
public class SeleccionAsientos extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(SeleccionAsientos.class);

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

    // FIX real (TAREA arquitectura — reemplaza el boolean pantallaAsientosReutilizable
    // de una iteración anterior): ya no hay ningún flag propio de esta clase. La
    // decisión de omitir navegación depende ÚNICAMENTE de
    // utils.SuiteExecutionContext.isSeatMapContextValid() — un estado LÓGICO que
    // solo es true cuando la navegación real (en pages.ios/flujos.ios) marcó "sigo
    // en la pantalla de asientos" y ningún test posterior lo invalidó. Este mismo
    // método sirve para CUALQUIER test que llegue a llamarlo, sin necesidad de
    // hardcodear "después del test 4" o "después del test 5" — si un futuro test
    // deja el contexto válido, este método lo reutiliza automáticamente; si no,
    // cae al camino de navegación completo de siempre. Nunca se asume: siempre se
    // reverifica contra la UI real antes de confiar en el contexto.
    private void seleccionarPeliculaYHorario() {
        if (DriverFactory.isIOS() && utils.SuiteExecutionContext.isSeatMapContextValid()
                && iosFlow.estaEnPantallaDeAsientos()) {
            log.info("[SeleccionAsientos] Pantalla de asientos reutilizada (SuiteExecutionContext: "
                    + "movie={} schedule={}) — navegación completa omitida.",
                    utils.SuiteExecutionContext.movieSelected(), utils.SuiteExecutionContext.scheduleSelected());
            return;
        }
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
            // Este flujo nunca navega hacia adelante (selecciona y luego
            // deselecciona — vuelve a 0 asientos elegidos, en la MISMA pantalla).
            // No hace falta marcar nada aquí: SuiteExecutionContext ya sigue
            // válido porque nada lo invalidó — el siguiente test que llame a
            // seleccionarPeliculaYHorario() lo detecta automáticamente.
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
            // Nota: si este método lanza (TARGET_NOT_REACHABLE, el caso más común
            // hoy), la línea de abajo nunca se ejecuta — pero eso no importa: el
            // contexto de suite YA quedó marcado válido por
            // seleccionarPeliculaYHorario() más arriba, y este flujo NUNCA navega
            // fuera de la pantalla de asientos (ni en éxito ni en fallo), así que
            // nada necesita invalidarlo. tearDown() reverificará contra la UI real
            // antes de confiar en ese estado, sin importar cómo terminó este test.
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

    // FIX real (TAREA arquitectura — orquestación de suite): reordenado a @Order(8)
    // (antes 7). Este test nunca marca ni consulta SuiteExecutionContext (su propia
    // selección de película/horario usa movieSelector.seleccionarPeliculaRandomYHorario()
    // directamente, no el método que sí marca el contexto) — no hay evidencia de que
    // termine en el mapa de asientos, así que no participa del grupo reutilizable.
    // Se corre después de alertaAsientoEspecial (ahora @Order(7)) para que ESE test
    // sí pueda heredar el contexto válido dejado por cambioHorarioAsientos — mover
    // este test no cambia su resultado funcional, solo su posición en la secuencia.
    @Test
    @Order(8)
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

    // FIX real (TAREA arquitectura — orquestación de suite): reordenado a @Order(7)
    // (antes 8), inmediatamente después de cambioHorarioAsientos (@Order(6)). Ambos
    // llaman a seleccionarPeliculaYHorario() y cambioHorarioAsientos ahora conserva
    // el contexto como válido tras un cambio de horario exitoso (ver
    // IOSAsientosFlow.cambiarHorario() — ya no invalida, remarca con el mismo
    // movieSelected() y un horario honesto "(horario cambiado)") — este test puede
    // heredar esa preparación sin repetir MovieDetection/MovieOpen/ScheduleSelection.
    // Mismo criterio de siempre: seleccionarPeliculaYHorario() reverifica contra la
    // UI real antes de confiar en el contexto, nunca lo asume ciegamente.
    @Test
    @Order(7)
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
