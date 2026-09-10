package flujos.ios;

import io.appium.java_client.AppiumDriver;
import pages.asientos.SelectorPage;
import pages.ios.IOSCinemasHelper;
import pages.ios.IOSMovieSelector;
import pages.ios.IOSScheduleSelector;
import pages.ios.IOSSeatAlertHandler;
import pages.ios.IOSSeatMap;
import utils.Reintento;
import utils.TestSteps;

/**
 * Punto de entrada iOS para los flujos de Asientos (TAREA 30, Fase 8).
 *
 * Espejo de {@link flujos.AsientosFlujo} (misma forma de API: mismos nombres de
 * método, misma firma), pero orquestando la familia {@code pages.ios.*} en vez de
 * llamar directamente a {@link SelectorPage}. {@link flujos.AsientosFlujo} NO se
 * modifica ni se elimina — hoy no es la ruta usada por
 * {@code tests.México.asientos.SeleccionAsientos} (esa clase llama a
 * {@code AsientosPagina}/{@code SelectorPage} directo), así que esta clase queda
 * preparada como punto de extensión sin migrar ningún test todavía.
 *
 * Estrategia de migración documentada (ver entregable de TAREA 30 para el detalle):
 * en una tarea futura, {@code SeleccionAsientos} podría cambiar de
 * {@code page.metodoX()} a {@code new IOSAsientosFlow(driver).metodoX(driver)} (iOS) /
 * {@code new AsientosFlujo(driver).metodoX(driver)} (Android) — decidido en tiempo de
 * construcción del test según {@code DriverFactory.isIOS()}, exactamente como ya se
 * decide la plataforma en el resto del framework, y NUNCA con un
 * {@code if (isIOS())} dentro de una clase Android existente. Esa migración no se
 * hace en esta tarea porque implica cambiar el comportamiento observable de los 9
 * tests reales — fuera del alcance de una tarea puramente arquitectónica.
 */
public class IOSAsientosFlow {

    private final SelectorPage legacyPage;
    private final IOSMovieSelector movieSelector;
    private final IOSScheduleSelector scheduleSelector;
    private final IOSSeatMap seatMap;
    private final IOSSeatAlertHandler seatAlertHandler;
    // TAREA 32 — compuesto igual que las otras 4 fachadas, PERO ningún método de esta
    // clase lo invoca todavía: PromosGuard/ClubGuard/ZoneGuard para los 9 tests reales
    // de SeleccionAsientos ocurre en BaseTest.beforeEach()/afterEach() (vía
    // pages.common.CinemasHelper directamente), no dentro del cuerpo de esos tests —
    // por eso IOSAsientosFlow nunca necesitó llamarlo hasta ahora. Se deja compuesto
    // y listo para cuando BaseTest.java sea actualizado (tarea futura, fuera de
    // alcance de TAREA 31/32) para invocar esta clase en vez de CinemasHelper para iOS.
    private final IOSCinemasHelper cinemasHelper;

    public IOSAsientosFlow(AppiumDriver driver) {
        this.legacyPage = new SelectorPage(driver);
        this.movieSelector = new IOSMovieSelector(driver, legacyPage);
        this.scheduleSelector = new IOSScheduleSelector(driver, legacyPage);
        this.seatMap = new IOSSeatMap(driver, legacyPage);
        this.seatAlertHandler = new IOSSeatAlertHandler(driver, legacyPage);
        this.cinemasHelper = new IOSCinemasHelper(driver);
    }

    // ─── Flujos de navegación ─────────────────────────────────────────────────

    /**
     * Selecciona una película aleatoria disponible y su primer horario, descartando
     * automáticamente alertas de sala especial o 3D. Mismo comportamiento y mismo
     * número de reintentos que {@link flujos.AsientosFlujo#seleccionarPeliculaYHorario()}.
     */
    public void seleccionarPeliculaYHorario() {
        Reintento.intentar(datos.Constantes.REINTENTOS_SCROLL, () -> {
            movieSelector.abrirPeliculaYMostrarHorarios();
            scheduleSelector.seleccionarPrimerHorarioDescartandoAlertas();
        });
    }

    /**
     * Espejo exacto de {@link SelectorPage#seleccionarPeliculaRandomYHorarioDescartandoAlertas()}
     * — a diferencia de {@link #seleccionarPeliculaYHorario()} (que sí envuelve la
     * operación en {@link Reintento}, mismo comportamiento que
     * {@link flujos.AsientosFlujo#seleccionarPeliculaYHorario()}), este método NO
     * reintenta: {@code tests.México.asientos.SeleccionAsientos} llama hoy a la
     * variante de {@link SelectorPage} directamente, sin ningún wrapper de reintento —
     * TAREA 31 preserva ese comportamiento exacto al conectar ese test a este flujo.
     */
    public void seleccionarPeliculaRandomYHorarioDescartandoAlertas() {
        movieSelector.abrirPeliculaYMostrarHorarios();
        scheduleSelector.seleccionarPrimerHorarioDescartandoAlertas();
    }

    // ─── Flujos de selección de asientos ─────────────────────────────────────

    /** Selecciona 1 asiento disponible al azar y continúa. */
    public void seleccionar1Asiento(AppiumDriver driver) {
        TestSteps.run("Selección de asiento disponible",
            () -> seatMap.seleccionarAsientoRandomDisponible(), driver);
        TestSteps.run("Continuar con asiento seleccionado",
            () -> seatMap.continuar(), driver);
    }

    /** Selecciona 3 asientos aleatorios y continúa. */
    public void seleccionar3AsientosAleatorios(AppiumDriver driver) {
        TestSteps.run("Selección de 3 asientos disponibles",
            () -> seatMap.seleccionar3AsientosRandomDisponibles(), driver);
        TestSteps.run("Continuar con asientos seleccionados",
            () -> seatMap.continuar(), driver);
    }

    /** Selecciona 3 asientos consecutivos y continúa. */
    public void seleccionar3AsientosConsecutivos(AppiumDriver driver) {
        TestSteps.run("Selección de 3 asientos consecutivos",
            () -> seatMap.seleccionar3AsientosConsecutivosDisponibles(), driver);
        TestSteps.run("Continuar con asientos seleccionados",
            () -> seatMap.continuar(), driver);
    }

    /** Selecciona y deselecciona 3 asientos consecutivos (validación de UI). */
    public void seleccionarYDeseleccionar3AsientosConsecutivos(AppiumDriver driver) {
        TestSteps.run("Pantalla de asientos",
            () -> seatMap.seleccionarYDeseleccionar3AsientosConsecutivosDisponibles(), driver);
    }

    /** Intenta seleccionar más de 10 asientos y valida que aparezca la alerta de límite. */
    public void validarLimite10Asientos(AppiumDriver driver) {
        TestSteps.run("Pantalla de asientos",
            () -> seatMap.seleccionarMasDe10AsientosYValidarAlerta(), driver);
    }

    // ─── Flujos de funciones especiales ──────────────────────────────────────

    /** Cambia el horario desde la pantalla de mapa de asientos. */
    public void cambiarHorario(AppiumDriver driver) {
        TestSteps.run("Pantalla de asientos",
            () -> scheduleSelector.cambiarHorarioEnPantallaAsientos(), driver);
    }

    /**
     * Activa el filtro 3D; si no existe ninguna función 3D disponible, aborta el test
     * (SKIP). El panel de filtros se cierra automáticamente antes de propagar el SKIP.
     */
    public void seleccionarFiltro3D(AppiumDriver driver) {
        try {
            TestSteps.run("Seleccionar filtro 3D",
                () -> movieSelector.seleccionarFiltro3D(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            cerrarPanelFiltros(driver);
            throw e;
        }
        TestSteps.run("Selección de Película y horario",
            () -> movieSelector.seleccionarPeliculaRandomYHorario(), driver);
    }

    /**
     * Activa el filtro Sala Junior y selecciona función. Si no hay funciones
     * disponibles, aborta el test (SKIP).
     */
    public void seleccionarSalaJunior(AppiumDriver driver) {
        try {
            TestSteps.run("Seleccionar filtro Sala Junior",
                () -> movieSelector.seleccionarFiltroSalaJunior(), driver);
        } catch (org.opentest4j.TestAbortedException e) {
            cerrarPanelFiltros(driver);
            throw e;
        }
        TestSteps.run("Selección de Película y horario",
            () -> legacyPage.seleccionarPeliculaYHorarioSalaJunior(), driver);
    }

    /**
     * Selecciona un asiento especial (discapacidad) y valida la alerta. Si no existen
     * asientos especiales disponibles, aborta (SKIP).
     */
    public void seleccionarAsientoEspecialYValidarAlerta(AppiumDriver driver) {
        TestSteps.run("Seleccionar asiento especial",
            () -> seatMap.seleccionarAsientoEspecial(), driver);
        TestSteps.run("Validar alerta de asiento especial",
            () -> seatAlertHandler.validarYManejarAlertaAsientoEspecial(true), driver);
    }

    // ─── Privado ──────────────────────────────────────────────────────────────

    private void cerrarPanelFiltros(AppiumDriver driver) {
        try {
            driver.navigate().back();
            Thread.sleep(400);
        } catch (Exception ignored) {}
    }
}
