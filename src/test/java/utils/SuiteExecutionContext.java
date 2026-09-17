package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado LÓGICO de la suite en ejecución — nunca WebElement/MobileElement/By/
 * Rectangle/referencias WDA de ningún tipo. Reemplaza el patrón anterior de
 * "un boolean por transición" (p. ej. {@code skipNextRelaunch},
 * {@code pantallaAsientosReutilizable}) por un mecanismo único, genérico y
 * auto-descriptivo: quien REALMENTE modifica la navegación (las clases de
 * {@code pages.ios}/{@code flujos.ios}, no la clase de test) es quien marca o
 * invalida el estado, en el momento exacto en que deja de ser cierto — nunca se
 * invalida "todo" de forma incondicional después de cada test.
 *
 * {@link base.BaseTest#tearDown} y cualquier test que llame a
 * {@code seleccionarPeliculaYHorario()} consultan este contexto para decidir si
 * pueden omitir preparación repetida (PromosGuard, ClubGuard, MovieDetection,
 * MovieOpen, ScheduleSelection, cold start de app) — pero SIEMPRE deben
 * reverificar contra la UI real antes de confiar en él (ver
 * {@code SelectorPage.estaRealmenteEnPantallaDeAsientos()}); este contexto nunca
 * es, por sí solo, prueba suficiente de que la pantalla realmente está ahí.
 *
 * Estático porque JUnit 5 crea una instancia nueva de la clase de test por cada
 * {@code @Test} (lifecycle PER_METHOD, el default) — este es el único estado que
 * debe sobrevivir entre esas instancias, dentro de la misma suite/JVM.
 */
public final class SuiteExecutionContext {

    private static final Logger log = LoggerFactory.getLogger(SuiteExecutionContext.class);

    /** Pantalla lógica actual — nunca una referencia a un elemento real, solo una etiqueta de estado. */
    public enum Screen { UNKNOWN, HOME, MOVIE_LIST, MOVIE_DETAIL, SEAT_MAP, FILTERS, CONFIRMATION }

    private static volatile String executionId;
    private static volatile String suiteName;
    private static volatile String platform;

    private static volatile String cinemaSelected;
    private static volatile String movieSelected;
    private static volatile String scheduleSelected;
    private static volatile Screen currentScreen = Screen.UNKNOWN;

    private SuiteExecutionContext() {}

    // ── Ciclo de vida de la suite ───────────────────────────────────────────

    /** Llamar una vez al inicio de la suite (@BeforeAll) — deja el contexto en blanco. */
    public static void initSuite(String executionId, String suiteName, String platform) {
        SuiteExecutionContext.executionId = executionId;
        SuiteExecutionContext.suiteName = suiteName;
        SuiteExecutionContext.platform = platform;
        resetAll();
        log.info("[SuiteExecutionContext] initSuite executionId={} suite={} platform={}", executionId, suiteName, platform);
    }

    /** Reinicio total — solo tras un cold start real (relanzamiento de app) o al cerrar la suite. */
    public static void resetAll() {
        cinemaSelected = null;
        movieSelected = null;
        scheduleSelected = null;
        currentScreen = Screen.UNKNOWN;
    }

    // ── Marcar estado que SÍ es cierto ahora mismo (lo llama quien navega) ──

    public static void markCinemaSelected(String cinema) {
        cinemaSelected = cinema;
    }

    /** Se llama SOLO cuando la navegación realmente terminó en la pantalla de asientos. */
    public static void markMovieAndScheduleSelected(String movie, String schedule) {
        movieSelected = movie;
        scheduleSelected = schedule;
        currentScreen = Screen.SEAT_MAP;
        log.info("[SuiteExecutionContext] markMovieAndScheduleSelected movie={} schedule={} -> SEAT_MAP", movie, schedule);
    }

    public static void markScreen(Screen screen) {
        currentScreen = screen;
    }

    // ── Invalidación explícita — SOLO del estado que realmente cambió ───────

    /** La navegación avanzó más allá de la selección de película/horario (p. ej. otra búsqueda). */
    public static void invalidateMovie() {
        movieSelected = null;
        scheduleSelected = null;
        currentScreen = Screen.UNKNOWN;
        log.debug("[SuiteExecutionContext] invalidateMovie()");
    }

    /** El horario cambió (p. ej. Cambio de Horario) — la película sigue siendo válida, el horario no. */
    public static void invalidateSchedule() {
        scheduleSelected = null;
        log.debug("[SuiteExecutionContext] invalidateSchedule()");
    }

    /** Se salió de la pantalla de asientos (p. ej. "Continuar" hacia confirmación/pago). */
    public static void invalidateSeatMap() {
        currentScreen = Screen.UNKNOWN;
        log.debug("[SuiteExecutionContext] invalidateSeatMap()");
    }

    /** Invalidación amplia de navegación — usar cuando no se puede garantizar nada más específico. */
    public static void invalidateNavigation() {
        movieSelected = null;
        scheduleSelected = null;
        currentScreen = Screen.UNKNOWN;
        log.debug("[SuiteExecutionContext] invalidateNavigation()");
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    /**
     * true SOLO si el contexto lógico afirma "película + horario seleccionados y
     * pantalla de asientos activa". NUNCA es, por sí solo, prueba suficiente —
     * el llamador SIEMPRE debe reverificar contra la UI real antes de confiar en
     * el ahorro de navegación (ver {@code estaRealmenteEnPantallaDeAsientos()}).
     */
    public static boolean isSeatMapContextValid() {
        return currentScreen == Screen.SEAT_MAP && movieSelected != null && scheduleSelected != null;
    }

    public static String movieSelected()    { return movieSelected; }
    public static String scheduleSelected() { return scheduleSelected; }
    public static String cinemaSelected()   { return cinemaSelected; }
    public static Screen currentScreen()    { return currentScreen; }
}
