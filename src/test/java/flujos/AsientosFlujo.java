package flujos;

import io.appium.java_client.AppiumDriver;
import pages.asientos.SelectorPage;
import utils.Reintento;
import utils.TestSteps;

/**
 * Capa de flujos para la sección de Asientos.
 *
 * Responsabilidades:
 *  - Orquesta la selección de película, horario y asientos.
 *  - Encapsula reintentos y manejo de alertas transientes.
 *  - NO contiene XPaths ni lógica de localización de elementos.
 *
 * Uso desde tests:
 *   AsientosFlujo flujo = new AsientosFlujo(driver);
 *   flujo.seleccionarPeliculaYHorario();
 *   flujo.seleccionar3AsientosConsecutivos();
 */
public class AsientosFlujo {

    private final AppiumDriver driver;
    private final SelectorPage pagina;

    public AsientosFlujo(AppiumDriver driver) {
        this.driver = driver;
        this.pagina = new SelectorPage(driver);
    }

    // ─── Flujos de navegación ─────────────────────────────────────────────────

    /**
     * Selecciona una película aleatoria disponible y su primer horario,
     * descartando automáticamente alertas de sala especial o 3D.
     */
    public void seleccionarPeliculaYHorario() {
        Reintento.intentar(datos.Constantes.REINTENTOS_SCROLL, () ->
            pagina.seleccionarPeliculaRandomYHorarioDescartandoAlertas()
        );
    }

    // ─── Flujos de selección de asientos ─────────────────────────────────────

    /** Selecciona 1 asiento disponible al azar y continúa. */
    public void seleccionar1Asiento(AppiumDriver driver) {
        TestSteps.run("Selección de asiento disponible",
            () -> pagina.seleccionarAsientoRandomDisponible(), driver);
        TestSteps.run("Continuar con asiento seleccionado",
            () -> pagina.continuar(), driver);
    }

    /** Selecciona 3 asientos aleatorios y continúa. */
    public void seleccionar3AsientosAleatorios(AppiumDriver driver) {
        TestSteps.run("Selección de 3 asientos disponibles",
            () -> pagina.seleccionar3AsientosRandomDisponibles(), driver);
        TestSteps.run("Continuar con asientos seleccionados",
            () -> pagina.continuar(), driver);
    }

    /** Selecciona 3 asientos consecutivos y continúa. */
    public void seleccionar3AsientosConsecutivos(AppiumDriver driver) {
        TestSteps.run("Selección de 3 asientos consecutivos",
            () -> pagina.seleccionar3AsientosConsecutivosDisponibles(), driver);
        TestSteps.run("Continuar con asientos seleccionados",
            () -> pagina.continuar(), driver);
    }

    /** Selecciona y deselecciona 3 asientos consecutivos (validación de UI). */
    public void seleccionarYDeseleccionar3AsientosConsecutivos(AppiumDriver driver) {
        TestSteps.run("Pantalla de asientos",
            () -> pagina.seleccionarYDeseleccionar3AsientosConsecutivosDisponibles(), driver);
    }

    /** Intenta seleccionar más de 10 asientos y valida que aparezca la alerta de límite. */
    public void validarLimite10Asientos(AppiumDriver driver) {
        TestSteps.run("Pantalla de asientos",
            () -> pagina.seleccionarMasDe10AsientosYValidarAlerta(), driver);
    }

    // ─── Flujos de funciones especiales ──────────────────────────────────────

    /** Cambia el horario desde la pantalla de mapa de asientos. */
    public void cambiarHorario(AppiumDriver driver) {
        TestSteps.run("Pantalla de asientos",
            () -> pagina.cambiarHorarioEnPantallaAsientos(), driver);
    }

    /**
     * Activa el filtro 3D; si no existe ninguna función 3D disponible, aborta el test (SKIP).
     * El panel de filtros se cierra automáticamente antes de propagar el SKIP.
     */
    public void seleccionarFiltro3D(AppiumDriver driver) {
        try {
            TestSteps.run("Seleccionar filtro 3D",
                () -> pagina.seleccionarFiltro3D(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            cerrarPanelFiltros();
            throw e;
        }
        TestSteps.run("Selección de Película y horario",
            () -> pagina.seleccionarPeliculaRandomYHorario(), driver);
    }

    /**
     * Activa el filtro Sala Junior y selecciona función.
     * Si no hay funciones disponibles, aborta el test (SKIP).
     */
    public void seleccionarSalaJunior(AppiumDriver driver) {
        try {
            TestSteps.run("Seleccionar filtro Sala Junior",
                () -> pagina.seleccionarFiltroSalaJunior(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            cerrarPanelFiltros();
            throw e;
        }
        TestSteps.run("Selección de Película y horario",
            () -> pagina.seleccionarPeliculaYHorarioSalaJunior(), driver);
    }

    /**
     * Selecciona un asiento especial (discapacidad) y valida la alerta.
     * Si no existen asientos especiales disponibles, aborta (SKIP).
     */
    public void seleccionarAsientoEspecialYValidarAlerta(AppiumDriver driver) {
        try {
            TestSteps.run("Seleccionar asiento especial",
                () -> pagina.seleccionarAsientoEspecial(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            throw e;
        }
        TestSteps.run("Validar alerta de asiento especial",
            () -> pagina.validarYManejarAlertaAsientoEspecial(true), driver);
    }

    // ─── Privado ──────────────────────────────────────────────────────────────

    private void cerrarPanelFiltros() {
        try {
            driver.navigate().back();
            Thread.sleep(400);
        } catch (Exception ignored) {}
    }
}
