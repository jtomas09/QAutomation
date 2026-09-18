package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado LÓGICO del escenario en ejecución — nunca WebElement/MobileElement/By/
 * Rectangle/referencias WDA de ningún tipo.
 *
 * TAREA arquitectura (requisito funcional no negociable): la aplicación
 * {@code com.cinepolis.go} se reinicia SIEMPRE entre escenarios de
 * {@code SeleccionAsientos} (ver {@code base.BaseTest#resetApplicationBetweenTests()}),
 * sin ninguna excepción basada en este contexto. Una iteración PREVIA de esta
 * misma arquitectura usaba {@code isSeatMapContextValid()} para OMITIR ese
 * relanzamiento y la navegación completa cuando la pantalla de asientos parecía
 * seguir vigente — ese acoplamiento (contexto → decisión de lifecycle) fue
 * explícitamente eliminado: este contexto ya NO controla si la app se reinicia
 * ni si un test puede saltarse su propia navegación.
 *
 * Su única responsabilidad ahora es representar, de forma puramente informativa/
 * diagnóstica, qué ocurrió DURANTE el escenario actual (qué película/horario se
 * seleccionó, en qué pantalla terminó) — útil para logging, nunca como entrada de
 * una decisión de lifecycle entre tests. {@code resetApplicationBetweenTests()}
 * invalida este contexto por completo (misma llamada, {@link #resetAll()}) en
 * cada reset, así que ningún estado del escenario anterior puede sobrevivir al
 * siguiente.
 *
 * Estático porque el ciclo de vida de {@code SeleccionAsientos} es
 * {@code TestInstance.Lifecycle.PER_CLASS} (una sola instancia de test para toda
 * la suite, ver {@code base.BaseTest}) — un campo estático es, de todas formas, la
 * forma correcta de representar un estado que conceptualmente pertenece a la
 * EJECUCIÓN, no a una instancia de test en particular.
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

    // ── Consultas (solo informativas — nunca controlan lifecycle entre tests) ──
    // TAREA arquitectura: isSeatMapContextValid() se eliminó — su único propósito
    // era decidir si se podía omitir el reinicio de la app o la navegación entre
    // escenarios, exactamente el acoplamiento que esta tarea prohíbe. El hecho de
    // que currentScreen()==SEAT_MAP nunca debe usarse como evidencia para evitar
    // un relanzamiento.

    public static String movieSelected()    { return movieSelected; }
    public static String scheduleSelected() { return scheduleSelected; }
    public static String cinemaSelected()   { return cinemaSelected; }
    public static Screen currentScreen()    { return currentScreen; }
}
