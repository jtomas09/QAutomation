package pages.ios;

import io.appium.java_client.AppiumDriver;
import pages.asientos.SelectorPage;

/**
 * Responsabilidad iOS: obtener películas visibles, seleccionar película, abrir
 * película y detectar la transición a la pantalla de horarios (TAREA 30, Fase 4).
 * Incluye los filtros de cartelera (3D / Sala Junior) porque actúan ANTES de la
 * selección de película, sobre la misma pantalla de cartelera.
 *
 * Mapa Android → iOS: hoy la responsabilidad "Movie selector" vive MEZCLADA con la
 * rama Android dentro de {@link SelectorPage} (clase compartida, NO modificada por
 * esta tarea) — p. ej. {@code abrirPrimerPeliculaDesdeVerSinopsis()} ya bifurca
 * internamente con {@code isIOS()} (implicitlyWait 8s iOS vs 3s Android) sin que esta
 * tarea toque esa lógica. Esta clase delega en la MISMA instancia de
 * {@link SelectorPage}: NO se copia el algoritmo, NO se cambia la aleatoriedad ni los
 * criterios de negocio — es exactamente el mismo camino de ejecución de hoy, con un
 * punto de entrada propio de iOS para permitir optimizarlo después sin tocar Android.
 */
public class IOSMovieSelector extends IOSBasePage {

    private final SelectorPage legacy;

    public IOSMovieSelector(AppiumDriver driver) {
        super(driver);
        this.legacy = new SelectorPage(driver);
    }

    /** Permite compartir la misma instancia de SelectorPage entre varias fachadas iOS
     *  (p. ej. desde {@code flujos.ios.IOSAsientosFlow}) para no perder el caché interno
     *  de {@code SelectorPage} (p. ej. horarioVisibleCachedAtMs) entre pasos del flujo. */
    public IOSMovieSelector(AppiumDriver driver, SelectorPage shared) {
        super(driver);
        this.legacy = shared;
    }

    /**
     * MovieDetection + MovieFiltering + MovieOpen: escanea la cartelera, elige y abre
     * el primer candidato válido. Devuelve el nombre de la película abierta.
     */
    public String abrirPrimerPeliculaDesdeVerSinopsis() {
        return legacy.abrirPrimerPeliculaDesdeVerSinopsis();
    }

    /** MovieOpen completo: navega popups iniciales, espera cartelera, abre película y
     *  deja la pantalla en la pestaña de horarios (sin seleccionar un horario todavía). */
    public void abrirPeliculaYMostrarHorarios() {
        legacy.abrirPeliculaYMostrarHorarios();
    }

    /** Película aleatoria + primer horario disponible (sin descartar alertas). */
    public void seleccionarPeliculaRandomYHorario() {
        legacy.seleccionarPeliculaRandomYHorario();
    }

    /** Activa el filtro 3D en la cartelera (SKIP si no hay funciones 3D disponibles). */
    public void seleccionarFiltro3D() {
        legacy.seleccionarFiltro3D();
    }

    /** Activa el filtro Sala Junior en la cartelera (SKIP si no hay funciones disponibles). */
    public void seleccionarFiltroSalaJunior() {
        legacy.seleccionarFiltroSalaJunior();
    }

    /** Acceso a la instancia legacy compartida — permite componer esta fachada con
     *  {@link IOSScheduleSelector}/{@link IOSSeatMap}/{@link IOSSeatAlertHandler} sobre
     *  la MISMA instancia de SelectorPage dentro de un mismo flujo (ver
     *  {@code flujos.ios.IOSAsientosFlow}). */
    public SelectorPage sharedLegacyPage() {
        return legacy;
    }
}
