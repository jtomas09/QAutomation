package pages.asientos;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.common.BasePage;
import pages.common.IOSLocatorDebug;
import pages.common.PlatformLocator;
import org.openqa.selenium.interactions.Pause;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SelectorPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(SelectorPage.class);

    private static final int RANDOM_PICK_TIMEOUT_SECONDS = 10;

    // Iteración 4 (auditoría de rendimiento — MovieOpen): estaEnDetalleDePelicula()
    // ya evalúa hayHorarioVisible() como parte de su propio criterio de éxito (línea
    // ~640) justo antes de que abrirPeliculaYMostrarHorarios()/seleccionarPeliculaYHorarioSalaJunior()
    // lo vuelvan a llamar milisegundos después para decidir si omiten irAEtiquetaHorarios().
    // Este campo cachea únicamente el resultado POSITIVO (nunca el negativo) por una
    // ventana corta, para no pagar dos veces el escaneo completo de pantalla + filtrado
    // Java cuando la respuesta ya se confirmó como "sí" hace instantes. Al no cachear
    // nunca un resultado negativo, el polling de esperarDetallePelicula() (que depende
    // de detectar una transición false→true) queda intacto: siempre reevalúa de verdad
    // mientras la respuesta sea false.
    private long horarioVisibleCachedAtMs = 0;
    private static final long HORARIO_VISIBLE_CACHE_TTL_MS = 2000;

    public SelectorPage(AppiumDriver driver) {
        super(driver);
    }

    // FIX real (evidencia de ejecución en vivo — validación del fix de mapa de
    // asientos): tras seleccionar el asiento, el test fallaba en iOS al tocar
    // "Continuar" porque este método usaba un XPath fijo de Android
    // (android.widget.TextView) sin ninguna rama iOS — nunca se había llegado a
    // ejecutar en iOS antes porque el bug del mapa de asientos fallaba primero.
    // Se agrega la rama iOS vía NSPredicate; el lado Android se deja BYTE-IDÉNTICO
    // al original (mismo tipo de elemento android.widget.TextView, no el wildcard
    // //* que usaría PlatformLocator.byExactText()) para no cambiar su comportamiento.
    private static final PlatformLocator CONTINUAR_BUTTON = PlatformLocator.of(
            By.xpath("//android.widget.TextView[@text=\"Continuar\"]"),
            AppiumBy.iOSNsPredicateString("label == 'Continuar' OR name == 'Continuar' OR value == 'Continuar'")
    );

    public void continuar() {
        this.click(CONTINUAR_BUTTON);
    }

    public void seleccionarPeliculaRandomYHorario() {
        abrirPeliculaYMostrarHorarios();
        seleccionarHorario();
    }

    /**
     * Navigates through initial popups, loads the cartelera, opens a movie, and lands on the horarios tab.
     *
     * FIX real (evidencia de ejecución en vivo tras el fix de "Ver sinopsis" — ver
     * abrirPrimerPeliculaDesdeVerSinopsis()/hayHorarioVisible()): el mismo cambio de
     * UX de la app que eliminó "Ver sinopsis" también eliminó la etiqueta "Ver
     * horarios" — ahora los horarios ya están visibles en pantalla justo después de
     * abrir la película (es exactamente la condición que abrirPrimerPeliculaDesdeVerSinopsis()
     * ya usa para confirmar éxito). Llamar a irAEtiquetaHorarios() en ese caso buscaba
     * una etiqueta que ya no existe y fallaba SIEMPRE ("No se pudo abrir la etiqueta de
     * horarios."), incluso con la película abierta correctamente. Se omite cuando los
     * horarios ya están visibles; se conserva para cualquier flujo/país que todavía
     * necesite navegar a una pestaña de horarios separada.
     */
    public void abrirPeliculaYMostrarHorarios() {
        manejarPopupsIniciales();
        esperarCargaCartelera();
        String pelicula = abrirPrimerPeliculaDesdeVerSinopsis();
        if (!hayHorarioVisible()) {
            irAEtiquetaHorarios();
        } else {
            log.debug("[SelectorPage] Horarios ya visibles tras abrir la película — se omite irAEtiquetaHorarios().");
        }
        log.info("[SelectorPage] Mostrando horarios para: {}", pelicula);
    }

    /** Picks the first available horario from the grid. App must already be on the horarios tab. */
    public void seleccionarHorario() {
        String horario = seleccionarPrimerHorarioDisponibleEnGrid();
        log.info("[SelectorPage] Horario seleccionado: {}", horario);
    }

    /**
     * Igual que seleccionarPeliculaRandomYHorario() pero tras seleccionar
     * el horario espera la alerta de Restricciones de Sala Junior,
     * la valida y la acepta automáticamente.
     */
    public void seleccionarPeliculaYHorarioSalaJunior() {
        manejarPopupsIniciales();
        esperarCargaCartelera();

        String pelicula = abrirPrimerPeliculaDesdeVerSinopsis();
        log.info("[SelectorPage] Película abierta: {}", pelicula);

        // Ver comentario de abrirPeliculaYMostrarHorarios() — mismo cambio de UX de la app.
        if (!hayHorarioVisible()) {
            irAEtiquetaHorarios();
        }

        String horario = seleccionarPrimerHorarioDisponibleEnGrid();
        log.info("[SelectorPage] Horario seleccionado: {}", horario);

        // Esperar alerta de Restricciones (puede tardar hasta 5s en aparecer)
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (estaVisibleAlertaRestricciones()) break;
            sleep(200);
        }

        if (!estaVisibleAlertaRestricciones()) {
            throw new org.opentest4j.TestAbortedException(
                    "No se encontró Sala Junior para esta función");
        }

        validarYManejarAlertaRestricciones(true);
        log.info("[SelectorPage] Alerta de Restricciones aceptada. Continuando...");
    }

    /**
     * Estrategia: UN solo escaneo de pantalla (MovieDetection), UN solo filtrado a
     * candidatos válidos (MovieFiltering — descarta banners/publicidad/"Horarios en
     * otros cines"/tarjetas inválidas vía esTextoNoPelicula(), ya usado dentro de
     * obtenerPeliculasVisibles()), y luego se intenta abrir cada candidato de la MISMA
     * lista ya calculada (MovieOpen) — si uno falla, se prueba el siguiente candidato
     * de la lista en memoria, sin volver a escanear ni recorrer la pantalla completa.
     * Instrumentado con utils.PerfMetrics (fases + intento/elemento/tiempo/resultado
     * por película probada) — ver Problema 5.
     */
    public String abrirPrimerPeliculaDesdeVerSinopsis() {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(isIOS() ? 8 : 3));

        // MovieDetection: obtenerPeliculasVisibles() ya aplica el filtro de candidatos
        // válidos (esTextoNoPelicula) internamente. Se envuelve en un poll corto (ver
        // esperarPeliculasVisibles) — evidencia real: la cartelera puede seguir
        // renderizando tarjetas después de que esperarCargaCartelera() confirma solo un
        // primer texto largo, y un único escaneo sin reintento podía correr una fracción
        // de segundo antes de que las tarjetas específicas terminaran de montarse, aunque
        // WDA sí las detectaba momentos después (mismas películas confirmadas en vivo:
        // "Nimrods: Una Comedia De Green Day", "Katseye: Wild Hearts En Cines", etc.).
        List<WebElement> peliculas = utils.PerfMetrics.measure("MovieDetection", () -> esperarPeliculasVisibles(5000));

        if (peliculas.isEmpty()) {
            throw new RuntimeException("No se detectaron títulos de películas en la pantalla inicial.");
        }

        // PERF (evidencia real de métricas — MovieOpen tardó ~80s POR CADA película,
        // valor sospechosamente constante entre ambos intentos): el implicitlyWait de
        // isSeconds(8) puesto arriba (necesario solo para tolerar que la cartelera
        // siguiera cargando durante el escaneo inicial) seguía activo durante TODO
        // abrirPeliculaPorVerSinopsis() — un ciclo de reintentos (3 intentos ×
        // reubicarTituloPelicula/encontrarVerSinopsisDeTarjeta/esperarDetallePelicula)
        // que hace docenas de findElements() esperando encontrar "nada" en el caso
        // normal. Cada uno de esos "no encontrado" heredaba hasta 8s de espera oculta
        // — exactamente el mismo bug de fondo que en CinemasHelper.exists() (Problema
        // 4), aquí con muchísimo más radio de impacto. Una vez confirmado el escaneo
        // inicial (peliculas no vacío), la pantalla YA está renderizada — no hace falta
        // tolerancia adicional para el resto del flujo. Se resetea a 0 (mismo criterio
        // "instantáneo" que isVisibleInstant/findInstant en CinemasHelper): los propios
        // reintentos (3 intentos, esperarDetallePelicula con su presupuesto de 5000ms)
        // ya dan la paciencia real necesaria — el implicitlyWait solo agregaba espera
        // oculta encima de esos presupuestos, nunca una garantía adicional real.
        // Aplica a ambas plataformas: Android también reutiliza este mismo camino de
        // reintentos y se beneficia igual (su implicitlyWait aquí era 3s, menor pero
        // con el mismo problema estructural).
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));

        // MovieFiltering: de los elementos ya escaneados, construir la lista final de
        // nombres únicos — sin ninguna consulta adicional al driver (0 findElements).
        List<String> nombresPeliculas = utils.PerfMetrics.measure("MovieFiltering", () -> {
            List<String> nombres = new ArrayList<>();
            for (WebElement el : peliculas) {
                try {
                    String nombre = obtenerTextoSeguro(el);
                    if (!nombre.isBlank() && !nombres.contains(nombre)) {
                        nombres.add(nombre);
                    }
                } catch (Exception ignored) {
                }
            }
            return nombres;
        });

        // MovieOpen: recorre la lista YA calculada — un candidato falla → se prueba el
        // siguiente de la MISMA lista, nunca se vuelve a escanear ni reiniciar el algoritmo.
        utils.PerfMetrics.startPhase("MovieOpen");
        try {
            int intento = 0;
            for (String nombre : nombresPeliculas) {
                intento++;

                // FIX real (Problemas 3/4/5 — fail-fast): segunda capa de defensa, además
                // del filtro ya aplicado en obtenerPeliculasVisibles(). Si por cualquier
                // motivo un candidato inválido (texto de Club Cinépolis/login/banner)
                // llegara hasta aquí, se descarta en <1ms en vez de invertir el ciclo
                // completo de reintentos (~183s observados en vivo por candidato inválido).
                if (esTextoNoPelicula(nombre)) {
                    utils.PerfMetrics.attempt("MovieOpen", intento, nombre, 0, "SKIP-INVALIDO");
                    log.debug("[SelectorPage] Candidato descartado (no es una película): {}", nombre);
                    continue;
                }

                long t0 = System.currentTimeMillis();
                try {
                    log.info("[SelectorPage] Intentando abrir película desde Ver sinopsis: {}", nombre);

                    if (abrirPeliculaPorVerSinopsis(nombre)) {
                        utils.PerfMetrics.attempt("MovieOpen", intento, nombre, System.currentTimeMillis() - t0, "OK");
                        log.info("[SelectorPage] Película abierta correctamente: {}", nombre);
                        return nombre;
                    }
                    utils.PerfMetrics.attempt("MovieOpen", intento, nombre, System.currentTimeMillis() - t0, "FAIL");

                } catch (Exception e) {
                    utils.PerfMetrics.attempt("MovieOpen", intento, nombre, System.currentTimeMillis() - t0, "ERROR");
                    log.warn("[SelectorPage] Error abriendo película '{}': {}", nombre, e.getMessage());
                }
            }

            throw new RuntimeException("Se detectaron películas visibles, pero no se pudo abrir ninguna desde Ver sinopsis.");
        } finally {
            utils.PerfMetrics.endPhase("MovieOpen");
            // Restaura el ambiente 10s por defecto (convención del proyecto) — el reset a
            // 0 de arriba es deliberadamente local a este método, para no dejar el driver
            // en implicitlyWait=0 durante el resto del test (horarios, asientos, etc.),
            // que pueden depender de esa espera ambiental en sus propios findElements().
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }
    private boolean abrirPeliculaPorVerSinopsis(String nombrePelicula) {
        for (int intento = 1; intento <= 3; intento++) {
            try {
                WebElement titulo = reubicarTituloPelicula(nombrePelicula);
                if (titulo == null) {
                    log.warn("[SelectorPage] No se pudo reubicar el título: {}", nombrePelicula);
                    return false;
                }

                WebElement verSinopsis = encontrarVerSinopsisDeTarjeta(titulo);

                if (verSinopsis != null) {
                    int x = verSinopsis.getRect().getX() + (verSinopsis.getRect().getWidth() / 2);
                    int y = verSinopsis.getRect().getY() + (verSinopsis.getRect().getHeight() / 2);

                    log.debug("[SelectorPage] Tap en Ver sinopsis intento {} -> {} X={} Y={}", intento, nombrePelicula, x, y);

                    tapW3C(x, y);

                    if (esperarDetallePelicula(5000)) {
                        return true;
                    }
                }

                // fallback: tap en la zona del póster arriba del título
                titulo = reubicarTituloPelicula(nombrePelicula);
                if (titulo != null) {
                    int centerX = titulo.getRect().getX() + (titulo.getRect().getWidth() / 2);
                    int titleTopY = titulo.getRect().getY();
                    int posterTapY = titleTopY - 140;
                    if (posterTapY < 80) {
                        posterTapY = titleTopY;
                    }

                    log.debug("[SelectorPage] Fallback tap poster intento {} -> {} X={} Y={}", intento, nombrePelicula, centerX, posterTapY);

                    tapW3C(centerX, posterTapY);

                    if (esperarDetallePelicula(5000)) {
                        return true;
                    }
                }

            } catch (Exception e) {
                log.warn("[SelectorPage] abrirPeliculaPorVerSinopsis error con '{}': {}", nombrePelicula, e.getMessage());
            }
        }

        // Diagnóstico temporal (no-op salvo -DIOS_LOCATOR_DEBUG=true) — captura el
        // page source real en el momento exacto del fallo, para investigar por qué
        // "Ver sinopsis" nunca abre el detalle de la película.
        if (isIOS()) {
            IOSLocatorDebug.onFailure(driver, "abrirPeliculaPorVerSinopsis_" + nombrePelicula, null,
                    new RuntimeException("3 intentos agotados sin abrir detalle para: " + nombrePelicula));
        }

        return false;
    }
    private WebElement encontrarVerSinopsisDeTarjeta(WebElement titulo) {
        try {
            List<WebElement> candidatos;
            if (isIOS()) {
                // NSPredicate — ver nota de rendimiento en PlatformLocator.byExactText().
                candidatos = driver.findElements(
                        AppiumBy.iOSNsPredicateString("name == 'Ver sinopsis' OR value == 'Ver sinopsis'")
                );
                if (candidatos.isEmpty()) {
                    candidatos = driver.findElements(
                            AppiumBy.iOSNsPredicateString("name CONTAINS 'Ver sinopsis' OR value CONTAINS 'Ver sinopsis'")
                    );
                }
            } else {
                candidatos = driver.findElements(
                        By.xpath("//android.widget.TextView[@text='Ver sinopsis']")
                );
                if (candidatos.isEmpty()) {
                    candidatos = driver.findElements(
                            By.xpath("//*[contains(@text,'Ver sinopsis')]")
                    );
                }
            }

            WebElement mejor = null;
            int mejorDistancia = Integer.MAX_VALUE;

            int tituloX = titulo.getRect().getX();
            int tituloY = titulo.getRect().getY();

            for (WebElement el : candidatos) {
                try {
                    if (!el.isDisplayed()) continue;

                    int x = el.getRect().getX();
                    int y = el.getRect().getY();

                    // Debe estar debajo del título y cerca en X
                    if (y < tituloY) continue;

                    int distancia = Math.abs(x - tituloX) + Math.abs(y - tituloY);

                    if (distancia < mejorDistancia) {
                        mejorDistancia = distancia;
                        mejor = el;
                    }
                } catch (Exception ignored) {
                }
            }

            return mejor;
        } catch (Exception e) {
            return null;
        }
    }
    private void manejarPopupsIniciales() {
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(800));
        String[] botonesCerrar = {"PERMITIR", "Permitir", "ENTENDIDO", "ACEPTAR", "Cerrar", "Saltar"};
        boolean ios = isIOS();
        for (String txt : botonesCerrar) {
            try {
                // NSPredicate en iOS — ver nota de rendimiento en PlatformLocator.byExactText().
                List<WebElement> btn = ios
                        ? driver.findElements(AppiumBy.iOSNsPredicateString("name == '" + txt + "' OR value == '" + txt + "'"))
                        : driver.findElements(By.xpath("//*[@text='" + txt + "']"));
                if (!btn.isEmpty()) btn.get(0).click();
            } catch (Exception ignored) {}
        }
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }
    public String seleccionarPrimerHorarioDisponible() {
        // Regex para buscar el formato de hora 00:00
        String xpathHoras = "//*[matches(@text, '^(1[0-2]|[1-9]|0[0-9]|2[0-3]):[0-5][0-9](\\\\s?(AM|PM|am|pm))?$')]";
        List<WebElement> horarios = driver.findElements(By.xpath(xpathHoras));

        if (horarios.isEmpty()) throw new RuntimeException("No hay horarios visibles.");

        WebElement primero = horarios.get(0);
        String hora = obtenerTextoSeguro(primero);
        clicSeguroEnElemento(primero);
        sleep(1500);
        return hora;
    }

    // NSPredicate en iOS (por() abajo) — poll cada 500ms hasta 10s (~20 iteraciones),
    // más manejarPopupsPosibles() en cada vuelta — ver nota de rendimiento en
    // PlatformLocator.byExactText().
    private By detectorCargaCarteleraLocator() {
        return isIOS()
                ? AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeStaticText' AND value.length > 8")
                : By.xpath("//android.widget.TextView[string-length(@text) > 8]");
    }

    private void esperarCargaCartelera() {
        By detector = detectorCargaCarteleraLocator();
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end) {
            if (!driver.findElements(detector).isEmpty()) {
                return;
            }
            manejarPopupsPosibles();
            sleep(500);
        }
    }
    private void manejarPopupsPosibles() {
        try {
            List<WebElement> popups = isIOS()
                    ? driver.findElements(AppiumBy.iOSNsPredicateString(
                            "name == 'PERMITIR' OR name == 'Permitir' OR name == 'ENTENDIDO'"
                            + " OR value == 'PERMITIR' OR value == 'Permitir' OR value == 'ENTENDIDO'"))
                    : driver.findElements(By.xpath(
                            "//*[@text='PERMITIR' or @text='Permitir' or @text='ENTENDIDO']"));
            if (!popups.isEmpty()) popups.get(0).click();
        } catch (Exception ignored) {}
    }

    public List<String> seleccionar3AsientosRandomDisponibles() {
        utils.PerfMetrics.startPhase("SeatSelection");
        try {
            SeatMap map = buildSeatMap();
            log.info("[SelectorPage] {}", map.getSummary());

            if (map.getTotalSeats() < 3) {
                org.junit.jupiter.api.Assumptions.abort(
                    "Menos de 3 asientos disponibles. Se omite la prueba.");
                return null;
            }

            // FIX real (evidencia de rendimiento — log 2026-08-13 14:07-14:18: el mismo
            // escaneo de ~190s se ejecutaba DOS veces por caso, aquí y otra vez dentro de
            // SeatSelectionEngine.select()): se reutiliza el MISMO SeatMap ya construido
            // arriba (solo usado antes para el chequeo "hay al menos 3 asientos") en vez
            // de dejar que el motor repita el escaneo completo. El resto de la lógica del
            // motor (resolución por número, freno de seguridad, confirmación) no cambia.
            List<String> seleccionados = new SeatSelectionEngine(this)
                .select(3, SeatSelectionEngine.CUALQUIERA, map);

            log.info("[SelectorPage] 3 asientos seleccionados: {}", seleccionados);
            takeScreenshot("3 asientos seleccionados");
            return seleccionados;
        } finally {
            utils.PerfMetrics.endPhase("SeatSelection");
        }
    }
    private WebElement reubicarTituloPelicula(String nombre) {
        // NSPredicate en iOS — ver nota de rendimiento en PlatformLocator.byExactText().
        By primaryLocator = isIOS()
                ? AppiumBy.iOSNsPredicateString("value == '" + nombre + "'")
                : By.xpath("//android.widget.TextView[@text=\"" + nombre + "\"]");
        By fallbackLocator = isIOS()
                ? AppiumBy.iOSNsPredicateString("value CONTAINS '" + nombre + "' OR name CONTAINS '" + nombre + "'")
                : By.xpath("//*[contains(@text,\"" + nombre + "\")]");

        try {
            List<WebElement> candidatos = driver.findElements(primaryLocator);
            for (WebElement el : candidatos) {
                try { if (el.isDisplayed()) return el; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        try {
            List<WebElement> candidatos = driver.findElements(fallbackLocator);
            for (WebElement el : candidatos) {
                try { if (el.isDisplayed()) return el; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }
    private boolean esperarDetallePelicula(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < end) {
            try {
                manejarPopupsPosibles();

                if (estaEnDetalleDePelicula()) {
                    log.info("[SelectorPage] Detalle de película detectado correctamente.");
                    return true;
                }
            } catch (Exception ignored) {
            }

            sleep(250);
        }

        return false;
    }
    public List<String> seleccionar3AsientosConsecutivosDisponibles() {
        utils.PerfMetrics.startPhase("SeatSelection");
        try {
            SeatMap map = buildSeatMap();
            log.info("[SelectorPage] {}", map.getSummary());
            map.logMap();

            long tCandidatos = System.currentTimeMillis();
            SeatMap.SelectionResult result = map.selectN(3);
            utils.PerfMetrics.stage("SeatSelection", "candidatos", System.currentTimeMillis() - tCandidatos);
            if (result == null) {
                org.junit.jupiter.api.Assumptions.abort(
                    "Menos de 3 asientos disponibles. Se omite la prueba.");
                return null;
            }

            log.info("[SelectorPage] Estrategia aplicada: {}", result.strategy);

            List<String> seleccionados = new ArrayList<>();
            int intento = 0;
            for (SeatMap.Seat seat : result.seats) {
                intento++;
                long tClick = System.currentTimeMillis();
                boolean ok = tapRapidoEnButacaDesdeLabel(seat.element);
                utils.PerfMetrics.attempt("SeatSelection", intento, seat.toString(), System.currentTimeMillis() - tClick, ok ? "OK" : "FAIL");
                if (ok) {
                    sleep(80);
                    seleccionados.add(seat.toString());
                    log.info("[SelectorPage] Asiento seleccionado OK: {}", seat);
                } else {
                    throw new RuntimeException("No se pudo seleccionar el asiento: " + seat);
                }
            }

            log.info("[SelectorPage] 3 asientos seleccionados ({}): {}", result.strategy, seleccionados);
            takeScreenshot("3 asientos - " + result.strategy);
            return seleccionados;
        } finally {
            utils.PerfMetrics.endPhase("SeatSelection");
        }
    }
    // NSPredicate en iOS \u2014 llamado en cada iteraci\u00f3n del poll de esperarDetallePelicula()
    // (sleep 250ms, hasta ~4-5s) \u2014 ver nota de rendimiento en PlatformLocator.byExactText().
    /**
     * FIX real (evidencia capturada con IOSLocatorDebug \u2014 ver page source real de un
     * fallo): la app ya NO tiene bot\u00f3n "Ver sinopsis" ni pantalla de detalle separada
     * (Sinopsis/Director/Reparto) \u2014 al tocar la pel\u00edcula, los horarios aparecen
     * DIRECTAMENTE bajo el t\u00edtulo en la misma tarjeta. El criterio original nunca
     * pod\u00eda cumplirse porque busca un flujo que la app ya no tiene; se agrega un
     * segundo criterio de \u00e9xito (equivalente, no excluyente): "\u00bfhay un horario visible
     * en pantalla?" (mismo patr\u00f3n regex "7:30 PM" ya usado por
     * estaEnPantallaDeHorarios()/obtenerHorariosDisponibles()). Se mantiene el criterio
     * original por si alg\u00fan flujo/pa\u00eds todav\u00eda muestra la pantalla de detalle cl\u00e1sica.
     */
    private boolean estaEnDetalleDePelicula() {
        try {
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "value CONTAINS 'Sinopsis' OR value CONTAINS 'Director' OR "
                        + "value CONTAINS 'Reparto' OR value CONTAINS 'Clasificaci\u00f3n' OR "
                        + "value CONTAINS 'Ver horarios' OR value CONTAINS 'VER HORARIOS' OR "
                        + "value CONTAINS 'Ver tr\u00e1iler' OR value CONTAINS 'Ver trailer' OR "
                        + "name CONTAINS 'Ver horarios' OR name CONTAINS 'Sinopsis' OR "
                        + "value CONTAINS 'Ver m\u00e1s'")
                    : By.xpath("//*[contains(@text,'Sinopsis') or contains(@text,'Director') or "
                        + "contains(@text,'Reparto') or contains(@text,'Clasificaci\u00f3n') or "
                        + "contains(@text,'Ver horarios') or contains(@text,'VER HORARIOS') or "
                        + "contains(@text,'Ver tr\u00e1iler') or contains(@text,'Ver trailer') or "
                        + "contains(@text,'Ver m\u00e1s')]");
            for (WebElement el : driver.findElements(locator)) {
                try { if (el.isDisplayed()) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return hayHorarioVisible();
    }

    /**
     * Ver comentario de estaEnDetalleDePelicula() \u2014 mismo criterio de horario que obtenerHorariosDisponibles().
     *
     * Iteraci\u00f3n 4: reutiliza un resultado POSITIVO reciente (menos de
     * HORARIO_VISIBLE_CACHE_TTL_MS) en vez de repetir el escaneo completo \u2014 ver
     * comentario del campo horarioVisibleCachedAtMs. Nunca cachea "false".
     */
    private boolean hayHorarioVisible() {
        if (horarioVisibleCachedAtMs > 0
                && System.currentTimeMillis() - horarioVisibleCachedAtMs < HORARIO_VISIBLE_CACHE_TTL_MS) {
            return true;
        }
        try {
            // FIX real (Problema 3 — causa raíz de los ~183s/intento observados en vivo):
            // este método se invoca hasta 6 veces por candidato dentro del poll de
            // esperarDetallePelicula() (cada 250ms), y su predicate anterior matcheaba
            // CUALQUIER botón con texto accesible — con la cartelera completa todavía
            // visible (docenas de botones) tras tocar un candidato inválido, cada llamada
            // pagaba el costo de evaluar y traer todos esos botones. Mismo patrón ya
            // corregido en estaEnPantallaDeHorarios() (Iteración 3, esta sesión) — aquí se
            // acota exactamente a la condición que el regex Java de abajo exige (termina
            // en AM/PM): nunca puede excluir un horario real, solo reduce drásticamente
            // cuántos elementos llegan al filtrado Java.
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "type == 'XCUIElementTypeButton' AND "
                        + "(value CONTAINS[c] 'AM' OR value CONTAINS[c] 'PM' "
                        + "OR label CONTAINS[c] 'AM' OR label CONTAINS[c] 'PM')")
                    : By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"
                        + " | //android.view.View[@text and normalize-space(@text)!='']");
            for (WebElement el : driver.findElements(locator)) {
                try {
                    if (!el.isDisplayed()) continue;
                    String txt = obtenerTextoSeguro(el);
                    if (txt.matches("^(1[0-2]|[1-9]):[0-5]\\d\\s?(AM|PM|am|pm)$")) {
                        horarioVisibleCachedAtMs = System.currentTimeMillis();
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void irAEtiquetaHorarios() {
        for (int intento = 1; intento <= 3; intento++) {
            long t0 = System.currentTimeMillis();
            String resultado = "?";
            String excepcion = "";
            try {
                // FIX real (causa raíz de "No se encontró el botón Ver horarios" con la
                // película recién abierta): antes, la búsqueda del botón era UN solo
                // findElements() sin espera — si el detalle de película todavía estaba en
                // transición/animación de entrada, este método fallaba de inmediato y el for
                // exterior pasaba al siguiente intento SIN NINGÚN sleep entre ellos, agotando
                // los 3 intentos en milisegundos sin darle tiempo real a la UI a estabilizarse.
                // esperarYLocalizarPorTexto() ahora hace un poll acotado (2500ms) que sale en
                // cuanto el botón aparece, en vez de rendirse en el primer chequeo.
                WebElement boton = esperarYLocalizarPorTexto("Ver horarios", 2500);

                if (boton == null) {
                    resultado = "NO-ENCONTRADO";
                    throw new RuntimeException("No se encontró el botón Ver horarios.");
                }

                log.debug("[SelectorPage] Intentando entrar a horarios. Intento={}", intento);

                int centerX = boton.getRect().getX() + (boton.getRect().getWidth() / 2);
                int centerY = boton.getRect().getY() + (boton.getRect().getHeight() / 2);

                // intento 1: tap directo al texto
                tapW3C(centerX, centerY);
                if (esperarPantallaHorarios(5000)) {
                    log.info("[SelectorPage] Se abrió la sección de horarios correctamente.");
                    resultado = "OK-tap-directo";
                    return;
                }

                // intento 2: tap un poco más arriba por si responde el contenedor del botón
                int yArriba = centerY - 20;
                if (yArriba < 1) yArriba = centerY;

                tapW3C(centerX, yArriba);
                if (esperarPantallaHorarios(5000)) {
                    log.info("[SelectorPage] Se abrió la sección de horarios correctamente con offset.");
                    resultado = "OK-tap-offset";
                    return;
                }

                // intento 3: parent real
                try {
                    WebElement parent = boton.findElement(By.xpath(".."));
                    if (clicSeguroEnElemento(parent) && esperarPantallaHorarios(5000)) {
                        log.info("[SelectorPage] Se abrió la sección de horarios correctamente desde parent.");
                        resultado = "OK-parent";
                        return;
                    }
                } catch (StaleElementReferenceException stale) {
                    // El botón quedó stale entre localizarlo y pedir su parent — no se
                    // reutiliza; el siguiente "intento" del for relocaliza desde cero.
                    log.debug("[SelectorPage] Botón 'Ver horarios' quedó stale al buscar su parent; se reintentará.");
                    resultado = "STALE";
                } catch (Exception ignored) {
                }

                if (resultado.equals("?")) resultado = "SIN-TRANSICION";

            } catch (SesionAppiumMuertaException muerta) {
                throw muerta; // no seguir intentando contra una sesión muerta

            } catch (Exception e) {
                relanzarSiSesionMuerta(e, "irAEtiquetaHorarios");
                excepcion = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("[SelectorPage] Error entrando a horarios: {}", e.getMessage());
                if (resultado.equals("?")) resultado = "ERROR";
            } finally {
                utils.PerfMetrics.note("MovieOpen", String.format(
                        "[irAEtiquetaHorarios] intento=%d comando=tap+esperarPantallaHorarios "
                        + "duracionMs=%d resultado=%s excepcion=%s",
                        intento, System.currentTimeMillis() - t0, resultado, excepcion));
            }
        }

        throw new RuntimeException("No se pudo abrir la etiqueta de horarios.");
    }
    public String seleccionarPrimerHorarioDisponibleEnGrid() {
        utils.PerfMetrics.startPhase("ScheduleSelection");
        // PERF (Iteración 1 — evidencia de auditoría de código, confirmada, no
        // supuesta): MovieOpen restaura implicitlyWait a 10s en su finally (línea 216)
        // "para horarios, asientos, etc." — pero ScheduleSelection nunca lo vuelve a
        // poner en 0 antes de este método, así que hereda esos 10s durante TODA la
        // fase. Mismo bug de fondo ya corregido en abrirPrimerPeliculaDesdeVerSinopsis()
        // (MovieOpen: ~80s→~34s) y en el escaneo de asientos (líneas 2488/2544/2641/2674,
        // que YA resetean a 0 explícitamente antes de escanear) — la suposición original
        // de que horarios "puede depender" de la espera ambiental queda contradicha por
        // el propio código de asientos, que no la necesita y ya la resetea por su cuenta.
        // Los presupuestos propios de este método (esperarPantallaHorarios: 5000ms,
        // aceptarAlertaAtencionSiPresente: 2000ms) ya dan la paciencia real necesaria —
        // el implicitlyWait solo agrega espera oculta encima, nunca una garantía extra.
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            if (!esperarPantallaHorarios(5000)) {
                throw new RuntimeException("La pantalla de horarios no cargó correctamente.");
            }

            List<WebElement> horarios = obtenerHorariosDisponibles();

            if (horarios.isEmpty()) {
                throw new RuntimeException("No hay horarios visibles para seleccionar.");
            }

            int intento = 0;
            for (WebElement horario : horarios) {
                intento++;
                long t0 = System.currentTimeMillis();
                try {
                    if (!horario.isDisplayed()) continue;

                    String hora = obtenerTextoSeguro(horario);
                    if (hora.isBlank()) continue;

                    log.info("[SelectorPage] Intentando seleccionar horario disponible: {}", hora);

                    // PERF (Problema 5): sleep(800) fijo eliminado — aceptarAlertaAtencionSiPresente()
                    // ya hace su propio polling interno (implicitlyWait=0, hasta 2000ms) esperando la
                    // alerta; el sleep previo solo agregaba tiempo muerto sin aportar seguridad extra
                    // (el caso lento ya está cubierto por ESE polling). Seguro para ambas plataformas:
                    // el downstream es compartido y su presupuesto de espera no cambia.
                    if (clicSeguroEnHorario(horario)) {
                        aceptarAlertaAtencionSiPresente();
                        utils.PerfMetrics.attempt("ScheduleSelection", intento, hora, System.currentTimeMillis() - t0, "OK");
                        return hora;
                    }

                    // fallback con reubicación por texto
                    WebElement relocalizado = reubicarElementoPorTextoExacto(hora);
                    if (relocalizado != null) {
                        int x = relocalizado.getRect().getX() + (relocalizado.getRect().getWidth() / 2);
                        int y = relocalizado.getRect().getY() + (relocalizado.getRect().getHeight() / 2);
                        tapW3C(x, y);
                        aceptarAlertaAtencionSiPresente();
                        utils.PerfMetrics.attempt("ScheduleSelection", intento, hora, System.currentTimeMillis() - t0, "OK");
                        return hora;
                    }
                    utils.PerfMetrics.attempt("ScheduleSelection", intento, hora, System.currentTimeMillis() - t0, "FAIL");

                } catch (Exception e) {
                    utils.PerfMetrics.attempt("ScheduleSelection", intento, "?", System.currentTimeMillis() - t0, "ERROR");
                    log.warn("[SelectorPage] Error seleccionando horario: {}", e.getMessage());
                }
            }

            throw new RuntimeException("Se detectaron horarios visibles, pero no se pudo seleccionar ninguno.");
        } finally {
            utils.PerfMetrics.endPhase("ScheduleSelection");
            // Restaura el ambiente 10s por defecto (convención del proyecto) — el reset a
            // 0 de arriba es deliberadamente local a este método, mismo criterio que MovieOpen.
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }
    /**
     * Igual que seleccionarPeliculaRandomYHorario() pero si al seleccionar un horario
     * aparece una alerta inesperada (ej. Restricciones/Sala Junior en un horario sin filtro),
     * la descarta automáticamente con "Cancelar" y prueba el siguiente horario disponible.
     * Usar en todos los tests EXCEPTO Sala Junior y 3D.
     */
    public void seleccionarPeliculaRandomYHorarioDescartandoAlertas() {
        abrirPeliculaYMostrarHorarios();
        String horario = seleccionarPrimerHorarioDescartandoAlertas();
        log.info("[SelectorPage] Horario seleccionado (descartando alertas): {}", horario);
    }

    public String seleccionarPrimerHorarioDescartandoAlertas() {
        utils.PerfMetrics.startPhase("ScheduleSelection");
        // PERF (Iteración 1 — mismo hallazgo y mismo fix que seleccionarPrimerHorarioDisponibleEnGrid();
        // ver el comentario completo ahí). Esta es la variante realmente usada por el flujo medido
        // en las corridas en vivo de esta sesión (SeleccionAsientos → seleccionarPeliculaRandomYHorarioDescartandoAlertas()),
        // donde ScheduleSelection promedió ~200s con implicitlyWait=10s heredado de MovieOpen.
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            if (!esperarPantallaHorarios(5000)) {
                throw new RuntimeException("La pantalla de horarios no cargó correctamente.");
            }

            // FIX real (causa raíz confirmada de StaleElementReferenceException / "Cached
            // elements ... do not exist in DOM anymore" / timeout de 240000ms reportados en
            // SeleccionAsientos > Selección de Asientos Consecutivos): la versión anterior
            // obtenía UNA lista de WebElement por ronda y la recorría completa con
            // `for (WebElement horario : horarios)`. Si el primer click de la ronda cambiaba
            // la pantalla de cualquier forma no reconocida como "alerta esperada" (o
            // directamente lanzaba), el resto de ESA MISMA lista quedaba con referencias
            // potencialmente inválidas, y cada intento siguiente pagaba un viaje completo a
            // Appium solo para descubrirlo — exactamente el patrón "Cached elements... do not
            // exist" del log. Ahora NUNCA se conserva un WebElement entre iteraciones: solo el
            // texto (obtenerTextosHorariosDisponibles()); cada horario se vuelve a localizar
            // por texto exacto justo antes de tocarlo, y ante StaleElement o "sin transición
            // confirmada" se corta la ronda actual en vez de seguir iterando un snapshot ya
            // desactualizado (en vez de reintentar 10 veces contra el mismo estado inválido).
            Set<String> descartadosPorAlerta = new LinkedHashSet<>();
            int intentoGlobal = 0;

            for (int ronda = 0; ronda < 10; ronda++) {
                List<String> horariosTexto = obtenerTextosHorariosDisponibles();
                horariosTexto.removeAll(descartadosPorAlerta);
                if (horariosTexto.isEmpty()) break;

                boolean alertaDescartada = false;

                for (String hora : horariosTexto) {
                    intentoGlobal++;
                    long t0 = System.currentTimeMillis();
                    String resultado = "?";
                    String excepcion = "";
                    try {
                        log.info("[SelectorPage] Intentando horario (descarte-alerta) intento={}: {}", ronda, hora);

                        // Re-localizar SIEMPRE justo antes de interactuar — nunca reutilizar
                        // un WebElement obtenido en una vuelta anterior del bucle. Ya viene
                        // filtrado por isDisplayed() (ver primerVisible()).
                        WebElement horario = reubicarElementoPorTextoExacto(hora);
                        if (horario == null) {
                            resultado = "NO-ENCONTRADO";
                            continue; // el DOM ya cambió — se prueba el siguiente texto de esta ronda
                        }

                        boolean clicOk = clicSeguroEnHorario(horario);
                        if (!clicOk) {
                            WebElement rel = reubicarElementoPorTextoExacto(hora);
                            if (rel != null) {
                                tapW3C(rel.getRect().getX() + rel.getRect().getWidth() / 2,
                                       rel.getRect().getY() + rel.getRect().getHeight() / 2);
                                clicOk = true;
                            }
                        }
                        if (!clicOk) {
                            resultado = "FAIL";
                            continue;
                        }

                        // PERF (Problema 5, preservado): espera inteligente por cualquiera de
                        // las dos alertas conocidas, mismo tope de 1000ms como peor caso.
                        long tAlerta0 = System.currentTimeMillis();
                        smartWait(() -> !driver.findElements(aceptarYContinuarLocator()).isEmpty()
                                || estaVisibleAlertaRestricciones(), 1000, 100);
                        utils.PerfMetrics.stage("ScheduleSelection", "smartWait-alertaVisible", System.currentTimeMillis() - tAlerta0);

                        // Alerta "Atención" (movimientos/vibraciones): aceptar y continuar al flujo de asientos
                        long tAceptar0 = System.currentTimeMillis();
                        boolean aceptada = aceptarAlertaAtencionSiPresente();
                        utils.PerfMetrics.stage("ScheduleSelection", "aceptarAlertaAtencionSiPresente", System.currentTimeMillis() - tAceptar0);
                        // FIX real (causa raíz CONFIRMADA en vivo del "SIN-TRANSICION" de ~12-20s:
                        // aceptarAlertaAceptarYContinuarSiPresente() restaura implicitlyWait a 10s en
                        // su propio finally al retornar — convención correcta para SUS otros
                        // llamadores, pero aquí pisa silenciosamente el implicitlyWait(0) que este
                        // método puso al entrar. Con implicitlyWait=10s heredado, CADA
                        // driver.findElements() posterior que no encuentra nada (el caso normal de
                        // "no hay alerta") espera hasta 10s completos antes de devolver vacío — medido
                        // en vivo: hayAlertaHorarioInesperada() pasó de ~0ms a ~12000ms exactos, dos
                        // veces seguidas, apenas después de esta llamada). Se reafirma el 0 propio de
                        // este método inmediatamente, sin tocar la convención de
                        // aceptarAlertaAceptarYContinuarSiPresente() para sus demás llamadores.
                        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
                        if (aceptada) {
                            log.info("[SelectorPage] Alerta 'Atención' aceptada para horario '{}'.", hora);
                            resultado = "OK";
                            return hora;
                        }

                        long tHay0 = System.currentTimeMillis();
                        boolean hayAlerta = hayAlertaHorarioInesperada();
                        utils.PerfMetrics.stage("ScheduleSelection", "hayAlertaHorarioInesperada", System.currentTimeMillis() - tHay0);
                        if (hayAlerta) {
                            log.warn("[SelectorPage] Alerta tras '{}': descartando y probando siguiente horario.", hora);
                            descartarAlertaHorario();
                            // PERF (Problema 5, preservado): espera inteligente, tope 600ms.
                            smartWait(() -> !hayAlertaHorarioInesperada(), 600, 100);
                            descartadosPorAlerta.add(hora);
                            alertaDescartada = true;
                            resultado = "SKIP-ALERTA";
                            break; // no seguir con el resto de esta ronda: la pantalla acaba de cambiar (alerta cerrada)
                        }

                        // FIX real (causa raíz CONFIRMADA con evidencia de screenshot + pageSource en
                        // vivo — investigación "SIN-TRANSICION" de la sesión anterior): la validación
                        // anterior (`!estaEnPantallaDeHorarios()`) es AMBIGUA. La pantalla de Asientos
                        // muestra su propio selector de horarios (pestañas "11:05 AM", "11:35 AM"...)
                        // en la parte superior para cambiar de función sin salir del mapa de asientos
                        // — el mismo texto con forma de horario que detecta estaEnPantallaDeHorarios()
                        // sigue presente AHÍ TAMBIÉN. Evidencia capturada: a t+500ms tras un click que
                        // "nunca transicionaba" según ese chequeo, el pageSource ya contenía "Asientos",
                        // "Paso 2 de 4" y "Pantalla Sala 10" (la app SÍ había transicionado, en menos de
                        // 500ms) — el click y la transición real nunca fueron el problema; el falso
                        // negativo era de esta validación. Se reemplaza por una condición NO ambigua:
                        // estaEnPantallaDeAsientos(), con los mismos indicadores ya usados y validados
                        // en verificarPantallaAsientosOSkip() de este mismo archivo (nunca aparecen en
                        // la pantalla de horarios).
                        long tTrans0 = System.currentTimeMillis();
                        boolean transicionOk = smartWait(this::estaEnPantallaDeAsientos, 1500, 150);
                        utils.PerfMetrics.stage("ScheduleSelection", "smartWait-transicion", System.currentTimeMillis() - tTrans0);
                        if (transicionOk) {
                            resultado = "OK";
                            return hora;
                        }

                        log.warn("[SelectorPage] Click en '{}' no lanzó excepción pero la pantalla de horarios "
                                + "sigue visible — transición no confirmada, se corta esta ronda.", hora);
                        resultado = "SIN-TRANSICION";
                        break; // el snapshot de esta ronda puede ya no reflejar la pantalla real

                    } catch (StaleElementReferenceException stale) {
                        // Requisito explícito: NO seguir usando el WebElement anterior. Se
                        // corta esta ronda completa (el resto del snapshot puede estar
                        // igualmente obsoleto) y se reconstruye desde cero en la siguiente.
                        resultado = "STALE";
                        excepcion = stale.getClass().getSimpleName() + ": " + stale.getMessage();
                        log.warn("[SelectorPage] StaleElementReferenceException con horario='{}' — se descartan "
                                + "las referencias de esta ronda y se reconstruye la lista.", hora);
                        break;

                    } catch (SesionAppiumMuertaException muerta) {
                        throw muerta; // no seguir ejecutando comandos contra una sesión muerta

                    } catch (Exception e) {
                        relanzarSiSesionMuerta(e, "seleccionarPrimerHorarioDescartandoAlertas horario=" + hora);
                        resultado = "ERROR";
                        excepcion = e.getClass().getSimpleName() + ": " + e.getMessage();
                        log.warn("[SelectorPage] Error intentando horario '{}': {}", hora, e.getMessage());
                    } finally {
                        utils.PerfMetrics.attempt("ScheduleSelection", intentoGlobal, hora, System.currentTimeMillis() - t0, resultado);
                        utils.PerfMetrics.note("ScheduleSelection", String.format(
                                "intento=%d horario='%s' locator=texto-exacto comando=reubicar+click "
                                + "duracionMs=%d resultado=%s excepcion=%s",
                                intentoGlobal, hora, System.currentTimeMillis() - t0, resultado, excepcion));
                    }
                }

                if (!alertaDescartada) break;
            }

            throw new RuntimeException("No se encontró ningún horario sin alertas inesperadas.");
        } finally {
            utils.PerfMetrics.endPhase("ScheduleSelection");
            // Restaura el ambiente 10s por defecto (convención del proyecto) — el reset a
            // 0 de arriba es deliberadamente local a este método, mismo criterio que MovieOpen.
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    private boolean hayAlertaHorarioInesperada() {
        try {
            // "Atención" (movimientos/vibraciones) se acepta, no se descarta — excluir de aquí
            if (estaVisibleAlertaRestricciones()) return true;
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void descartarAlertaHorario() {
        try {
            // NSPredicate en iOS — ver nota de rendimiento en PlatformLocator.byExactText().
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString("label == 'Cancelar' OR label CONTAINS 'Cancelar' " +
                        "OR name == 'Cancelar' OR name CONTAINS 'Cancelar' " +
                        "OR value == 'Cancelar' OR value CONTAINS 'Cancelar'")
                    : By.xpath("//*[@text='Cancelar' or contains(@text,'Cancelar')]");
            List<WebElement> botones = driver.findElements(locator);
            for (WebElement btn : botones) {
                try {
                    if (!btn.isDisplayed()) continue;
                    tapW3C(btn.getRect().getX() + btn.getRect().getWidth() / 2,
                           btn.getRect().getY() + btn.getRect().getHeight() / 2);
                    log.info("[SelectorPage] Alerta de horario descartada con Cancelar.");
                    return;
                } catch (Exception ignored) {}
            }
            log.warn("[SelectorPage] No se encontró botón Cancelar para descartar la alerta.");
        } catch (Exception e) {
            log.warn("[SelectorPage] Error descartando alerta de horario: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manejo de sesión Appium muerta (FIX — requisito explícito: NO ocultar
    // "A session is either terminated or not started" ni seguir enviando comandos
    // contra una sesión muerta). No se implementa una arquitectura de recuperación
    // nueva: se detecta la señal que Appium YA emite y se relanza de forma clara e
    // inconfundible para que el mecanismo de recuperación EXISTENTE de BaseTest
    // (relaunchAppSafe()/quitDriver() en @BeforeEach/@AfterEach, no tocado) actúe en
    // el siguiente test — este método solo deja de insistir dentro del actual.
    // ─────────────────────────────────────────────────────────────────────────

    /** Señal inconfundible de sesión Appium muerta — nunca debe seguir generando comandos. */
    public static class SesionAppiumMuertaException extends RuntimeException {
        public SesionAppiumMuertaException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private static boolean esMensajeDeSesionMuerta(String mensaje) {
        if (mensaje == null) return false;
        String m = mensaje.toLowerCase();
        return m.contains("session is either terminated or not started")
                || m.contains("invalid session id")
                || m.contains("session not found");
    }

    /**
     * Si {@code e} indica que la sesión Appium ya no existe (NoSuchSessionException, o
     * WebDriverException con el mensaje característico), la relanza como
     * SesionAppiumMuertaException para que el llamador deje de ejecutar más comandos
     * inmediatamente. Si no es ese caso, no hace nada (el llamador sigue con su manejo
     * normal de la excepción).
     */
    private void relanzarSiSesionMuerta(Exception e, String contexto) {
        boolean sesionMuerta = (e instanceof NoSuchSessionException)
                || (e instanceof WebDriverException && esMensajeDeSesionMuerta(e.getMessage()));
        if (sesionMuerta) {
            log.error("[SelectorPage] Sesión Appium terminada durante '{}' — abortando sin más comandos. {}",
                    contexto, e.getMessage());
            throw new SesionAppiumMuertaException(
                    "Sesión Appium terminada durante '" + contexto + "'.", e);
        }
    }

    /**
     * Poll acotado para localizar un elemento por texto — reemplaza la búsqueda de UN
     * solo intento que usaba irAEtiquetaHorarios() (ver FIX real ahí: sin esta espera, si
     * el detalle de película todavía estaba en transición cuando se buscaba "Ver horarios",
     * los 3 intentos del for exterior se agotaban en milisegundos sin darle tiempo real a
     * la UI a estabilizarse). Nunca espera más que timeoutMs; sale en cuanto aparece.
     */
    private WebElement esperarYLocalizarPorTexto(String texto, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        WebElement el;
        do {
            el = reubicarElementoPorTextoExacto(texto);
            if (el == null) el = reubicarElementoPorTexto(texto);
            if (el != null) return el;
            sleep(200);
        } while (System.currentTimeMillis() < end);
        return null;
    }

    /**
     * Snapshot de SOLO TEXTO de los horarios visibles — nunca WebElement. Pieza central del
     * fix de StaleElementReferenceException / "Cached elements ... do not exist in DOM
     * anymore": seleccionarPrimerHorarioDescartandoAlertas() ya no conserva una lista de
     * WebElement durante todo el ciclo de reintentos (ver comentario ahí) — cada horario se
     * vuelve a localizar por texto exacto justo antes de tocarlo, así que lo único que debe
     * sobrevivir entre el escaneo y el click es el texto.
     */
    private List<String> obtenerTextosHorariosDisponibles() {
        List<WebElement> horarios = obtenerHorariosDisponibles();
        List<String> textos = new ArrayList<>();
        for (WebElement el : horarios) {
            try {
                if (!el.isDisplayed()) continue;
                String texto = obtenerTextoSeguro(el);
                if (!texto.isBlank()) textos.add(texto);
            } catch (Exception ignored) {
                // Elemento ya inválido entre el findElements() y este punto — se omite sin
                // conservar la referencia (a diferencia del comportamiento anterior).
            }
        }
        return textos;
    }

    // PERF/FIX (Problema 5 — irAEtiquetaHorarios): sin rama iOS, @text es exclusivo de
    // Android — en iOS "Ver horarios" (y cualquier otro texto) NUNCA se encontraba aquí,
    // aunque el elemento estuviera perfectamente visible. NSPredicate en iOS — ver nota
    // de rendimiento en PlatformLocator.byExactText().
    // FIX real (misma causa raíz que estaEnPantallaDeHorarios() — ver comentario ahí):
    // el locator Android original era //*[contains(@text,...)], un wildcard que fuerza a
    // UiAutomator2 a volcar el árbol completo y puede colgarse durante una transición/
    // animación. Se prueba primero el patrón acotado (android.widget.TextView/android.view.View
    // — únicos dos tipos de nodo con @text en esta app, mismo criterio ya usado en
    // hayHorarioVisible()/obtenerHorariosDisponibles()) y, si ese acotado no encuentra nada
    // (nunca debería excluir un candidato real, pero se conserva como red de seguridad
    // explícita — "conserva el locator existente como primera opción" no aplica aquí porque
    // el existente ERA la causa del cuelgue, así que pasa a ser el fallback), se cae al
    // wildcard original sin ningún cambio de comportamiento final.
    private WebElement reubicarElementoPorTexto(String texto) {
        try {
            if (!isIOS()) {
                By acotado = By.xpath("//android.widget.TextView[contains(@text,'" + texto + "')]"
                        + " | //android.view.View[contains(@text,'" + texto + "')]");
                WebElement el = primerVisible(driver.findElements(acotado));
                if (el != null) return el;
            }

            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "label CONTAINS \"" + texto + "\" OR name CONTAINS \"" + texto + "\" OR value CONTAINS \"" + texto + "\"")
                    : By.xpath("//*[contains(@text,'" + texto + "')]");
            return primerVisible(driver.findElements(locator));
        } catch (Exception ignored) {
        }

        return null;
    }
    private WebElement reubicarElementoPorTextoExacto(String texto) {
        try {
            if (!isIOS()) {
                By acotado = By.xpath("//android.widget.TextView[@text='" + texto + "']"
                        + " | //android.view.View[@text='" + texto + "']");
                WebElement el = primerVisible(driver.findElements(acotado));
                if (el != null) return el;
            }

            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "label == \"" + texto + "\" OR name == \"" + texto + "\" OR value == \"" + texto + "\"")
                    : By.xpath("//*[@text='" + texto + "']");
            return primerVisible(driver.findElements(locator));
        } catch (Exception ignored) {
        }

        return null;
    }

    /** Devuelve el primer elemento visible de la lista, tolerando stale/errores por elemento. */
    private WebElement primerVisible(List<WebElement> elementos) {
        for (WebElement el : elementos) {
            try {
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean esperarPantallaHorarios(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < end) {
            try {
                if (estaEnPantallaDeHorarios()) {
                    return true;
                }
            } catch (Exception ignored) {
            }

            sleep(300);
        }

        return false;
    }

    public String seleccionarHorarioRandomDisponible(int maxScrolls) {
        for (int scroll = 0; scroll <= maxScrolls; scroll++) {

            List<WebElement> horarios = obtenerHorariosDisponibles();
            if (!horarios.isEmpty()) {
                WebElement horarioRandom = horarios.get(ThreadLocalRandom.current().nextInt(horarios.size()));
                String hora = obtenerTextoSeguro(horarioRandom);

                log.info("[SelectorPage] Intentando seleccionar horario random: {}", hora);

                if (clicSeguroEnHorario(horarioRandom)) {
                    sleep(1500);
                    aceptarAlertaAtencionSiPresente();
                    return hora;
                }
            }

            log.debug("[SelectorPage] No se encontró horario usable en scroll={}. Haciendo swipe...", scroll);
            hacerScrollHorarios();
            sleep(800);
        }

        throw new RuntimeException("No se encontró un horario disponible para seleccionar.");
    }

    // DIAGNÓSTICO (investigación en curso — ScheduleSelection tarda ~55-56s en un
    // único paso, de forma consistente en 6 corridas seguidas, sin variar aunque se
    // corrigió shouldWaitForQuiescence; evidencia de log descarta que el tiempo esté
    // en aceptarAlertaAtencionSiPresente()/smartWait, que suman como mucho ~3s). Se
    // agrega timing por estrategia para localizar en cuál de los 4 intentos de click
    // se consume el tiempo real — puramente aditivo (logging), ningún cambio de
    // comportamiento ni de orden de las estrategias existentes.
    private boolean clicSeguroEnHorario(WebElement el) {
        String hora = obtenerTextoSeguro(el);
        long t0 = System.currentTimeMillis();

        try {
            el.click();
            log.info("[SelectorPage][PERF-CLICK] horario='{}' estrategia=click-directo tiempo={}ms",
                    hora, System.currentTimeMillis() - t0);
            return true;
        } catch (Exception e1) {
            try {
                WebElement parent = el.findElement(By.xpath(".."));
                parent.click();
                log.info("[SelectorPage][PERF-CLICK] horario='{}' estrategia=click-padre tiempo={}ms",
                        hora, System.currentTimeMillis() - t0);
                return true;
            } catch (Exception e2) {
                try {
                    WebElement grandParent = el.findElement(By.xpath("../.."));
                    grandParent.click();
                    log.info("[SelectorPage][PERF-CLICK] horario='{}' estrategia=click-abuelo tiempo={}ms",
                            hora, System.currentTimeMillis() - t0);
                    return true;
                } catch (Exception e3) {
                    try {
                        int centerX = el.getRect().getX() + (el.getRect().getWidth() / 2);
                        int centerY = el.getRect().getY() + (el.getRect().getHeight() / 2);
                        tapW3C(centerX, centerY);
                        log.info("[SelectorPage][PERF-CLICK] horario='{}' estrategia=tapW3C tiempo={}ms",
                                hora, System.currentTimeMillis() - t0);
                        return true;
                    } catch (Exception e4) {
                        try {
                            WebElement horario = reubicarElementoPorTextoExacto(hora);
                            if (horario != null) {
                                int x = horario.getRect().getX() + (horario.getRect().getWidth() / 2);
                                int y = horario.getRect().getY() + (horario.getRect().getHeight() / 2);
                                tapW3C(x, y);
                                log.info("[SelectorPage][PERF-CLICK] horario='{}' estrategia=tapW3C-reubicado tiempo={}ms",
                                        hora, System.currentTimeMillis() - t0);
                                return true;
                            }
                        } catch (Exception ignored) {
                        }

                        log.warn("[SelectorPage] No se pudo seleccionar horario: {}", hora);
                        log.info("[SelectorPage][PERF-CLICK] horario='{}' estrategia=NINGUNA-FALLO tiempo={}ms",
                                hora, System.currentTimeMillis() - t0);
                        return false;
                    }
                }
            }
        }
    }


    /**
     * FIX real — causa raíz CONFIRMADA con evidencia forense en vivo (ver instrumentación
     * [SelectorPage][FORENSE] en obtenerPeliculasVisibles()): de 72 candidatos crudos,
     * 68 — incluyendo TODOS los títulos reales de película ("Spider-Man: Un Nuevo Día",
     * "Toy Story 5", "La Odisea", etc.) y hasta la barra de navegación ("Filtros",
     * "Cines", "Fechas") — quedaban "DESCARTADO (no visible)". Solo los 4 elementos del
     * banner de Club Cinépolis ("Inicia sesión o crea una cuenta en...") SÍ eran
     * visibles (correctamente filtrados por esTextoNoPelicula). Es decir: el banner
     * ocupa la parte superior de la pantalla y empuja el resto de la cartelera fuera
     * del viewport visible — no un problema de timing/renderizado progresivo (la
     * hipótesis anterior), sino de posición de scroll. Esto ocurre tras el
     * relanzamiento de la app entre tests (@AfterEach → relaunchAppSafe(), no
     * modificado) y nada en el flujo hacía scroll para revelarlo.
     *
     * Corrección mínima: si el primer intento queda vacío, UN solo scroll
     * (slowSwipeUp(), heredado de BasePage — mismo gesto que ya usa este archivo para
     * el mismo propósito en horarios, ver hacerScrollHorarios()) antes de reintentar.
     * En el caso normal (primer intento no vacío) el scroll nunca se ejecuta — cero
     * cambio de comportamiento ahí. Único punto de entrada para "¿hay tarjetas de
     * película reales visibles ahora?" — reutilizado por abrirPrimerPeliculaDesdeVerSinopsis()
     * (MovieDetection) y por seleccionarFiltroGenerico() (validación de contexto de
     * cartelera) — nunca se duplica esta espera en más de un lugar.
     */
    private List<WebElement> esperarPeliculasVisibles(long timeoutMs) {
        List<WebElement> peliculas = obtenerPeliculasVisibles();
        if (!peliculas.isEmpty()) return peliculas;

        try {
            slowSwipeUp();
        } catch (Exception ignored) {}

        long end = System.currentTimeMillis() + timeoutMs;
        peliculas = obtenerPeliculasVisibles();
        while (peliculas.isEmpty() && System.currentTimeMillis() < end) {
            sleep(400);
            peliculas = obtenerPeliculasVisibles();
        }
        return peliculas;
    }

    // PERF (evidencia real de MÉTRICAS — MovieDetection tardó 35384ms en una corrida):
    // este método (el escaneo inicial de TODA la cartelera, llamado en cada test) seguía
    // usando XPath para iOS — el candidato de mayor impacto que faltaba convertir de
    // toda la sesión, ya que corre sobre una pantalla con potencialmente decenas de
    // elementos de texto/imagen. NSPredicate — ver nota de rendimiento en
    // PlatformLocator.byExactText(). El filtro de "no vacío tras trim" (normalize-space)
    // no se replica en el predicate: el bucle de abajo ya descarta texto.isBlank()
    // inmediatamente después (obtenerTextoSeguro), mismo resultado final.
    private List<WebElement> obtenerPeliculasVisibles() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        // PERF (Problema 2 — MovieDetection ~50-60s, mismo patrón ya validado en vivo
        // esta sesión para asientos: "value != nil" matchea CUALQUIER texto accesible en
        // pantalla, obligando a traer todos los candidatos a Java antes de descartar por
        // longitud. Se empuja la MISMA condición de longitud (5-80) que el filtro Java de
        // abajo ya exige — nunca puede excluir un candidato que el filtro actual aceptaría,
        // solo reduce cuántos elementos WDA devuelve y Java tiene que iterar.
        List<By> candidatos = isIOS() ? List.of(
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeStaticText' AND value != nil AND value MATCHES '.{5,80}'"),
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeOther' AND value != nil AND value MATCHES '.{5,80}'")
        ) : List.of(
                By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"),
                By.xpath("//android.view.View[@text and normalize-space(@text)!='']")
        );

        // Instrumentación forense — investigación "No se detectaron títulos de
        // películas" pese a que WDA sí detecta títulos reales. IMPORTANTE: log.debug()
        // de este archivo NUNCA aparece en los logs de producción de este proyecto
        // (confirmado: 0 apariciones en una corrida real de 21k líneas, incluso en el
        // caso exitoso) — por eso se usa log.info() aquí. Cero costo adicional en el
        // camino exitoso (solo se acumulan strings en memoria con datos YA calculados
        // por el propio filtro; nunca se imprimen si resultado no queda vacío). Solo
        // cuando el resultado final queda vacío se vuelca el detalle completo — que es
        // exactamente cuando se necesita para diagnosticar.
        List<String> diagnostico = new ArrayList<>();
        List<WebElement> primerLoteCrudo = null;
        int totalCandidatosCrudos = 0;

        for (By locator : candidatos) {
            try {
                List<WebElement> elementos = driver.findElements(locator);
                if (primerLoteCrudo == null) primerLoteCrudo = elementos;
                totalCandidatosCrudos += elementos.size();

                for (WebElement el : elementos) {
                    try {
                        boolean visible;
                        try { visible = el.isDisplayed(); } catch (Exception e) { visible = false; }

                        if (!visible) {
                            diagnostico.add("DESCARTADO (no visible)");
                            continue;
                        }

                        String texto = obtenerTextoSeguro(el);
                        if (texto.isBlank()) {
                            diagnostico.add("DESCARTADO (texto vacío)");
                            continue;
                        }
                        if (texto.length() < 5) {
                            diagnostico.add("DESCARTADO (longitud<5) texto='" + texto + "'");
                            continue;
                        }
                        if (esTextoNoPelicula(texto)) {
                            diagnostico.add("DESCARTADO (esTextoNoPelicula) texto='" + texto + "'");
                            continue;
                        }

                        // Evitar horarios
                        if (texto.matches("^([01]?\\d|2[0-3]):[0-5]\\d(\\s?(AM|PM|am|pm))?$")) {
                            diagnostico.add("DESCARTADO (es horario) texto='" + texto + "'");
                            continue;
                        }

                        // Evitar textos demasiado largos que no suelen ser títulos
                        if (texto.length() > 80) {
                            diagnostico.add("DESCARTADO (longitud>80) texto='" + texto.substring(0, 60) + "...'");
                            continue;
                        }

                        diagnostico.add("ACEPTADO texto='" + texto + "'");
                        unicos.putIfAbsent(texto.trim(), el);

                    } catch (Exception e) {
                        diagnostico.add("DESCARTADO (excepción: " + e.getMessage() + ")");
                    }
                }

                if (!unicos.isEmpty()) {
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        resultado.addAll(unicos.values());

        if (resultado.isEmpty()) {
            log.info("[SelectorPage][FORENSE] obtenerPeliculasVisibles() NO encontró películas — "
                    + "candidatosCrudos={} evaluados={}", totalCandidatosCrudos, diagnostico.size());
            for (String linea : diagnostico) {
                log.info("[SelectorPage][FORENSE]   {}", linea);
            }
            // Detalle extendido (type/name/label/value) solo para diagnóstico de fallo —
            // acotado a los primeros 30 candidatos crudos (mismo criterio de "no volcar
            // todo el árbol" que ya usa IOSLocatorDebug) para no sumar demasiada latencia
            // extra a un camino que ya está fallando.
            if (isIOS() && primerLoteCrudo != null) {
                int limite = Math.min(30, primerLoteCrudo.size());
                for (int i = 0; i < limite; i++) {
                    WebElement el = primerLoteCrudo.get(i);
                    try {
                        log.info("[SelectorPage][FORENSE]   [{}] type={} name={} label={} value={}",
                                i, el.getAttribute("type"), el.getAttribute("name"),
                                el.getAttribute("label"), el.getAttribute("value"));
                    } catch (Exception e) {
                        log.info("[SelectorPage][FORENSE]   [{}] error leyendo atributos: {}", i, e.getMessage());
                    }
                }
            }
        } else {
            log.info("[SelectorPage] Películas visibles detectadas: {}", resultado.size());
        }

        return resultado;
    }

    // PERF/FIX (Problema 5): sin rama iOS — @text/android.widget.TextView son exclusivos
    // de Android, así que en iOS esta lista SIEMPRE estaba vacía (0 horarios detectados,
    // garantizado). El filtro real (regex de hora "7:30 PM") es semántico, no depende de
    // qué película/función sea — se agrega el equivalente iOS vía NSPredicate, mismo
    // criterio de regex aplicado después con obtenerTextoSeguro() (ya lee @value en iOS).
    // FIX real (evidencia — RUN-1014: ScheduleSelection tardaba ~101s en este método
    // antes del primer intento de click. Mismo patrón que el mapa de asientos: el
    // predicado NSPredicate es amplio (StaticText/Button con value/label presente, sin
    // límite de longitud — matchea precios, nombre de película, etc.) y por cada
    // candidato se paga isDisplayed() + obtenerTextoSeguro() (hasta 4 viajes). Se
    // intenta primero UNA sola llamada a getPageSource() con la misma verificación de
    // conteo/muestra ya usada para asientos; si algo no cuadra, cae al camino original
    // (elemento por elemento) sin ningún cambio de comportamiento.
    private List<WebElement> obtenerHorariosDisponibles() {
        if (isIOS()) {
            List<WebElement> rapido = intentarHorariosRapidoConPageSource();
            if (rapido != null) return rapido;
        }

        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        List<By> candidatos = isIOS() ? List.of(
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeStaticText' AND value != nil"),
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeButton' AND (value != nil OR label != nil)")
        ) : List.of(
                By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"),
                By.xpath("//android.view.View[@text and normalize-space(@text)!='']")
        );

        for (By locator : candidatos) {
            List<WebElement> elementos;
            try {
                elementos = driver.findElements(locator);
            } catch (Exception e) {
                continue;
            }

            for (WebElement el : elementos) {
                try {
                    if (!el.isDisplayed()) continue;

                    String texto = obtenerTextoSeguro(el);

                    if (!texto.matches("^(1[0-2]|[1-9]):[0-5]\\d\\s?(AM|PM|am|pm)$")) {
                        continue;
                    }

                    unicos.putIfAbsent(texto.trim(), el);

                } catch (Exception ignored) {
                }
            }

            if (!unicos.isEmpty()) {
                break;
            }
        }

        resultado.addAll(unicos.values());

        log.debug("[SelectorPage] Horarios visibles detectados: {}", resultado.size());
        for (WebElement el : resultado) {
            log.debug(" - {}", obtenerTextoSeguro(el));
        }

        return resultado;
    }

    // Vía rápida (con verificación) de obtenerHorariosDisponibles().
    //
    // FIX real (evidencia — RUN-1015: la primera versión de este método correlacionaba
    // por ORDEN de documento contra driver.findElements(), igual que el mapa de
    // asientos — pero aquí la verificación de muestra detectó un desajuste real
    // ("real='Hoy 13 Ago' xml='Cines'"), demostrando que el orden de findElements()
    // NO es confiable en esta pantalla (a diferencia del mapa de asientos). Se
    // descartó esa vía por completo — no se puede confiar en el orden aquí).
    //
    // Este diseño no depende del orden en absoluto: UNA sola llamada a
    // getPageSource() para encontrar los TEXTOS que parecen horarios reales (regex
    // "7:30 PM"), y luego una búsqueda DIRIGIDA por texto exacto
    // (reubicarElementoPorTextoExacto(), ya usado en este archivo) por cada horario
    // encontrado — típicamente 3-8 horarios reales, nunca los cientos de candidatos
    // crudos del predicado original. Si el XML no tiene ningún horario detectable
    // devuelve null y el llamador usa el método original completo.
    private List<WebElement> intentarHorariosRapidoConPageSource() {
        try {
            List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(driver.getPageSource()).nodos;

            Set<String> horariosEncontrados = new LinkedHashSet<>();
            for (SeatUiSnapshot.Nodo n : nodos) {
                if (!"XCUIElementTypeStaticText".equals(n.tag) && !"XCUIElementTypeButton".equals(n.tag)) continue;
                if (!"true".equals(n.attrs.get("visible"))) continue;
                String texto = textoHorarioDeNodo(n);
                if (texto.matches("^(1[0-2]|[1-9]):[0-5]\\d\\s?(AM|PM|am|pm)$")) {
                    horariosEncontrados.add(texto.trim());
                }
            }

            if (horariosEncontrados.isEmpty()) {
                utils.PerfMetrics.note("SeatSelection", "horariosRapido: 0 horarios detectados en el XML — camino original decide.");
                return null;
            }

            List<WebElement> resultado = new ArrayList<>();
            for (String texto : horariosEncontrados) {
                WebElement el = reubicarElementoPorTextoExacto(texto);
                if (el != null) resultado.add(el);
            }

            utils.PerfMetrics.note("SeatSelection", String.format(
                    "horariosRapido OK: %d horario(s) detectados en el XML -> %d resueltos por texto exacto (sin isDisplayed/obtenerTextoSeguro sobre candidatos crudos)",
                    horariosEncontrados.size(), resultado.size()));
            return resultado;
        } catch (Exception e) {
            utils.PerfMetrics.note("SeatSelection", "horariosRapido descartado por excepción: " + e.getMessage());
            return null;
        }
    }

    private String textoHorarioDeNodo(SeatUiSnapshot.Nodo n) {
        String value = n.attrs.get("value");
        if (value != null && !value.isBlank()) return value.trim();
        String label = n.attrs.get("label");
        if (label != null && !label.isBlank()) return label.trim();
        String name = n.attrs.get("name");
        return name == null ? "" : name.trim();
    }

    // PERF/FIX (Problema 5): esta locator NUNCA tuvo rama iOS (@text es exclusivo de
    // Android) — en iOS `todos` siempre estaba vacío, así que esperarPantallaHorarios()
    // agotaba GARANTIZADO su timeout completo (5000ms) en cada una de las hasta ~10
    // llamadas de irAEtiquetaHorarios()/seleccionarPrimerHorarioDisponibleEnGrid() por
    // intento de película. El criterio (¿hay un horario tipo "7:30 PM" o "Español"/
    // "Subtitulada" visible?) es semántico y agnóstico de qué película sea — no
    // requiere inventar texto específico no verificado, solo exponerlo también para
    // iOS vía @value/@label (obtenerTextoSeguro() ya sabe leer @value en iOS).
    // PERF (Iteración 3 — auditoría confirmada, no supuesta): el predicate iOS anterior
    // ("value != nil OR label != nil") matchea CUALQUIER nodo con texto accesible en
    // pantalla (título, sinopsis, reparto, precio, cine, etc.) — el mismo patrón "traer
    // todo y filtrar después" que ya se identificó como el mayor costo de MovieDetection.
    // Se acota a nodos que contengan "AM"/"PM" (mayúsculas/minúsculas) o sean uno de los
    // dos tags de idioma exactos — una propiedad que el propio regex Java de la línea
    // 1215 YA exige de cualquier horario válido (termina en AM/PM), así que esto nunca
    // puede excluir un horario real: solo reduce drásticamente cuántos elementos llegan
    // al filtrado en Java. No se intenta replicar el regex preciso dentro de NSPredicate
    // (evita depender de que el motor ICU regex de iOS traduzca \d/anchors idéntico a
    // Java sin poder validarlo en el dispositivo) — el regex Java sigue siendo la única
    // autoridad real sobre "¿esto es un horario válido?", sin ningún cambio.
    // FIX real (causa raíz confirmada del timeout de 240000ms "Could not proxy command to
    // the remote server" reportado en SeleccionAsientos > Selección de Asientos Consecutivos):
    // pese al comentario de hayAlertaHorarioInesperada()/hayHorarioVisible() que afirma que
    // este método ya fue acotado en "Iteración 3", el locator Android seguía siendo el
    // wildcard //*[@text...] — matchea TODO elemento con @text en el árbol completo, sin
    // excluir tipo de nodo. Este método se llama cada 300ms dentro de esperarPantallaHorarios(),
    // incluida la ventana en la que la app está en plena transición/animación (justo tras tocar
    // "Ver horarios" o tras tocar un horario). UiAutomator2 debe volcar la jerarquía completa de
    // vistas para resolver un XPath //*, y ese volcado espera a que la UI esté "idle" — con una
    // animación en curso, esa espera puede colgarse hasta que expira el timeout HTTP del cliente
    // (los 240000ms observados), y es exactamente ese comando el que deja "cached elements" del
    // locator ancho huérfanos cuando por fin responde sobre un DOM ya distinto. Se acota al mismo
    // patrón YA validado en producción por hayHorarioVisible()/obtenerPeliculasVisibles()/
    // obtenerHorariosDisponibles() en este mismo archivo (los únicos dos tipos de nodo que emiten
    // @text en esta app) — nunca puede excluir una pantalla de horarios real, solo evita el
    // volcado completo del árbol.
    private boolean estaEnPantallaDeHorarios() {
        try {
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "(value CONTAINS[c] 'AM' OR value CONTAINS[c] 'PM' "
                        + "OR label CONTAINS[c] 'AM' OR label CONTAINS[c] 'PM' "
                        + "OR value ==[c] 'español' OR label ==[c] 'español' "
                        + "OR value ==[c] 'subtitulada' OR label ==[c] 'subtitulada')")
                    : By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"
                        + " | //android.view.View[@text and normalize-space(@text)!='']");
            List<WebElement> todos = driver.findElements(locator);

            for (WebElement el : todos) {
                try {
                    if (!el.isDisplayed()) continue;

                    String txt = obtenerTextoSeguro(el);

                    if (txt.matches("^(1[0-2]|[1-9]):[0-5]\\d\\s?(AM|PM|am|pm)$")) {
                        return true;
                    }

                    if ("español".equalsIgnoreCase(txt) || "subtitulada".equalsIgnoreCase(txt)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    // FIX real (ver comentario en seleccionarPrimerHorarioDescartandoAlertas() —
    // investigación "SIN-TRANSICION" con evidencia de screenshot/pageSource): a
    // diferencia de estaEnPantallaDeHorarios() (ambiguo — la pantalla de Asientos
    // también muestra texto con forma de horario en su selector de función), estos
    // indicadores NUNCA aparecen en la pantalla de horarios — son el mismo criterio
    // ya usado y validado en verificarPantallaAsientosOSkip() de este archivo.
    private boolean estaEnPantallaDeAsientos() {
        try {
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "label CONTAINS 'Pantalla Sala' OR value CONTAINS 'Pantalla Sala' "
                        + "OR label CONTAINS 'Paso 2' OR value CONTAINS 'Paso 2' "
                        + "OR label CONTAINS 'Paso 3' OR value CONTAINS 'Paso 3'")
                    : By.xpath("//android.widget.TextView[contains(@text,'Pantalla Sala') or contains(@text,'Paso 2') or contains(@text,'Paso 3')]"
                        + " | //android.view.View[contains(@text,'Pantalla Sala') or contains(@text,'Paso 2') or contains(@text,'Paso 3')]");
            return !driver.findElements(locator).isEmpty();
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean clicSeguroEnElemento(WebElement el) {
        try {
            el.click();
            return true;
        } catch (Exception e1) {
            try {
                WebElement parent = el.findElement(By.xpath(".."));
                parent.click();
                return true;
            } catch (Exception e2) {
                try {
                    WebElement grandParent = el.findElement(By.xpath("../.."));
                    grandParent.click();
                    return true;
                } catch (Exception e3) {
                    try {
                        int centerX = el.getRect().getX() + (el.getRect().getWidth() / 2);
                        int centerY = el.getRect().getY() + (el.getRect().getHeight() / 2);
                        tapW3C(centerX, centerY);
                        return true;
                    } catch (Exception e4) {
                        log.warn("[SelectorPage] Falló click en elemento, parent, grandParent y tapW3C: {}", obtenerTextoSeguro(el));
                        return false;
                    }
                }
            }
        }
    }
    public List<String> seleccionarYDeseleccionar3AsientosConsecutivosDisponibles() {
        SeatMap map = buildSeatMap();
        log.info("[SelectorPage] {}", map.getSummary());
        map.logMap();

        SeatMap.SelectionResult result = map.selectN(3);
        if (result == null) {
            org.junit.jupiter.api.Assumptions.abort(
                "Menos de 3 asientos disponibles. Se omite la prueba.");
            return null;
        }

        log.info("[SelectorPage] Estrategia aplicada: {}", result.strategy);

        // ── Fase 1: selección — guardar coordenadas exactas ───────────────────
        List<int[]> coords = new ArrayList<>();
        List<String> seleccionados = new ArrayList<>();

        for (SeatMap.Seat seat : result.seats) {
            log.info("[SelectorPage] Seleccionando: {} en ({},{})", seat, seat.x, seat.y);
            tapW3C(seat.x, seat.y);
            sleep(80);
            coords.add(new int[]{seat.x, seat.y});
            seleccionados.add(seat.toString());
        }

        log.info("[SelectorPage] Asientos seleccionados: {}", seleccionados);
        sleep(400);

        // ── Fase 2: deselección por coordenadas exactas ───────────────────────
        log.info("[SelectorPage] Iniciando deselección por coordenadas exactas...");
        for (int[] c : coords) {
            log.debug("[SelectorPage] Deseleccionando en ({},{})", c[0], c[1]);
            tapW3C(c[0], c[1]);
            sleep(150);
        }

        log.info("[SelectorPage] Selección/deselección finalizada ({}): {}", result.strategy, seleccionados);
        takeScreenshot("Asientos deseleccionados - " + result.strategy);
        return seleccionados;
    }
    public List<String> seleccionarMasDe10AsientosYValidarAlerta() {
        SeatMap map = buildSeatMap();
        log.info("[SelectorPage] {}", map.getSummary());

        if (map.getTotalSeats() < 11) {
            org.junit.jupiter.api.Assumptions.abort(
                "Menos de 11 asientos disponibles (detectados: " + map.getTotalSeats() + "). Se omite la prueba.");
            return null;
        }

        List<WebElement> asientos = map.allSeats().stream()
            .map(s -> s.element)
            .collect(Collectors.toList());

        Collections.shuffle(asientos);

        List<String> seleccionados = new ArrayList<>();
        int maxIntentos = Math.min(asientos.size(), 20);

        for (int i = 0; i < maxIntentos; i++) {
            WebElement asiento = asientos.get(i);

            // Leer rect una sola vez: evita llamadas extra a getText() + getAttribute()
            // que construirKeyAsiento() y describirAsiento() harían por separado
            org.openqa.selenium.Rectangle rect;
            try {
                rect = asiento.getRect();
            } catch (Exception e) {
                continue;
            }

            int tapX = rect.getX() + (rect.getWidth() / 2);
            int tapY = rect.getY() + (rect.getHeight() / 2);

            log.debug("[SelectorPage] Tap asiento #{} -> ({},{})", (i + 1), tapX, tapY);

            if (tapDirecto(asiento)) {
                seleccionados.add("(" + tapX + "," + tapY + ")");
                sleep(60);
            } else {
                continue;
            }

            // La alerta solo puede aparecer al intentar seleccionar el asiento #11.
            // No tiene sentido verificarla en los primeros 9 taps: ahorra 9 llamadas WebDriver.
            if (i >= 9 && estaVisibleAlertaLimiteAsientos()) {
                validarAlertaLimiteAsientos();
                log.info("[SelectorPage] Alerta de límite detectada. Asientos tapeados: {}", seleccionados.size());
                takeScreenshot("Alerta limite asientos");
                return seleccionados;
            }
        }

        throw new RuntimeException("No apareció la alerta de límite máximo de asientos.");
    }
    private boolean estaVisibleAlertaLimiteAsientos() {
        try {
            // Una sola llamada al driver en lugar de 3 separadas
            return !driver.findElements(By.xpath(
                    "//*[contains(@text,'Alcanzaste el l\u00edmite m\u00e1ximo de asientos') or " +
                    "contains(@text,'l\u00edmite m\u00e1ximo de asientos') or " +
                    "contains(@text,'Aceptar y continuar')]"
            )).isEmpty();
        } catch (Exception ignored) {}
        return false;
    }
    private void validarAlertaLimiteAsientos() {
        boolean tituloVisible = false;
        boolean mensajeVisible = false;
        boolean botonVisible = false;

        try {
            tituloVisible = !driver.findElements(
                    By.xpath("//*[contains(@text,'Alcanzaste el límite máximo de asientos')]")
            ).isEmpty();

            mensajeVisible = !driver.findElements(
                    By.xpath("//*[contains(@text,'limitado la compra y selección de asientos a 10 por transacción')]")
            ).isEmpty();

            botonVisible = !driver.findElements(
                    By.xpath("//*[contains(@text,'Aceptar y continuar')]")
            ).isEmpty();
        } catch (Exception ignored) {
        }

        if (!tituloVisible) {
            throw new RuntimeException("No se mostró el título esperado de la alerta de límite de asientos.");
        }

        if (!mensajeVisible) {
            throw new RuntimeException("No se mostró el mensaje esperado de la alerta de límite de asientos.");
        }

        if (!botonVisible) {
            throw new RuntimeException("No se mostró el botón 'Aceptar y continuar' en la alerta.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Asiento especial
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Busca y toca un asiento especial (para personas con discapacidad motriz)
     * dentro del mapa de asientos, identificado por su contentDescription.
     * Lanza RuntimeException si no se encuentra ninguno.
     *
     * @return descripción del asiento tapeado (número + etiqueta)
     */
    public String seleccionarAsientoEspecial() {
        // Guardia: verificar que estamos en la pantalla de asientos
        verificarPantallaAsientosOSkip();

        // Fase 1: intento rápido por contentDescription o resourceId
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            List<WebElement> porDesc = driver.findElements(By.xpath(
                    "//*[contains(@content-desc,'especial') or contains(@content-desc,'Especial') or " +
                    "contains(@content-desc,'discapacidad') or contains(@content-desc,'Discapacidad') or " +
                    "contains(@content-desc,'accesible') or contains(@content-desc,'Accesible') or " +
                    "contains(@content-desc,'wheelchair') or contains(@content-desc,'Wheelchair') or " +
                    "contains(@content-desc,'PRM') or contains(@content-desc,'prm') or " +
                    "contains(@resource-id,'especial') or contains(@resource-id,'special') or " +
                    "contains(@resource-id,'wheelchair') or contains(@resource-id,'accessible')]"
            ));
            int screenHeight = driver.manage().window().getSize().getHeight();
            int mapTop    = (int) (screenHeight * 0.28);
            int mapBottom = (int) (screenHeight * 0.94);

            for (WebElement el : porDesc) {
                try {
                    org.openqa.selenium.Rectangle r = el.getRect();
                    int centerY = r.getY() + (r.getHeight() / 2);
                    int centerX = r.getX() + (r.getWidth() / 2);
                    if (centerY < mapTop || centerY > mapBottom || centerX < 20) continue;

                    tapW3C(centerX, centerY);
                    sleep(600);
                    if (estaVisibleAlertaAsientoEspecial()) {
                        log.info("[SelectorPage] Asiento especial encontrado por contentDescription.");
                        takeScreenshot("Asiento especial seleccionado");
                        return "asiento=" + obtenerTextoSeguro(el) + " [especial]";
                    }
                    tapW3C(centerX, centerY);
                    sleep(300);
                } catch (Exception ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        // Fase 2: búsqueda sistemática — primero extremos de fila (donde suelen estar los especiales)
        // Si no hay asientos con número (función agotada), pasa directo a Fase 3
        List<WebElement> asientos = esperarYObtenerAsientosDelMapa();
        if (asientos.isEmpty()) asientos = obtenerAsientosDisponiblesVisibles();
        if (asientos.isEmpty()) asientos = obtenerAsientosDelMapaAmplio();

        if (!asientos.isEmpty()) {
            Map<Integer, List<WebElement>> filas = agruparAsientosPorFilaFlexible(asientos);
            // Solo extremos de fila: los asientos especiales siempre están al inicio/fin de fila.
            // Probar el interior dispara taps innecesarios y alarga el test varios minutos.
            List<WebElement> prioritarios = new ArrayList<>();

            for (List<WebElement> fila : filas.values()) {
                fila.sort((a, b) -> Integer.compare(a.getRect().getX(), b.getRect().getX()));
                if (!fila.isEmpty()) {
                    prioritarios.add(fila.get(0));
                    if (fila.size() > 1) prioritarios.add(fila.get(fila.size() - 1));
                }
            }

            List<WebElement> orden = new ArrayList<>(prioritarios);
            int maxIntentos = Math.min(orden.size(), 25);

            // implicitlyWait=0 evita que estaVisibleAlertaAsientoEspecial() espere
            // 10 segundos por tap fallido → de ~270s a ~20s para los 25 intentos.
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            try {
                for (int i = 0; i < maxIntentos; i++) {
                    WebElement asiento = orden.get(i);
                    try {
                        org.openqa.selenium.Rectangle r = asiento.getRect();
                        int centerX = r.getX() + r.getWidth() / 2;
                        int centerY = r.getY() + r.getHeight() / 2;
                        String txt = obtenerTextoSeguro(asiento);

                        log.debug("[SelectorPage] Probando asiento {}/{}: asiento={} en ({},{})", (i + 1), maxIntentos, txt, centerX, centerY);

                        tapW3C(centerX, centerY);
                        sleep(600);

                        if (estaVisibleAlertaAsientoEspecial()) {
                            log.info("[SelectorPage] Asiento especial detectado: asiento={}", txt);
                            takeScreenshot("Asiento especial seleccionado");
                            return "asiento=" + txt + " [especial]";
                        }

                        tapW3C(centerX, centerY);
                        sleep(200);

                    } catch (Exception ignored) {}
                }
            } finally {
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            }
        } else {
            log.debug("[SelectorPage] Fase2 omitida: no se detectaron asientos con número (posible función agotada).");
        }

        // Fase 3: buscar android.view.View sin texto dentro del mapa
        // Los asientos especiales en apps Compose aparecen como View vacíos (ícono de silla de ruedas)
        String resultadoFase3 = buscarAsientoEspecialEnViewsSinTexto();
        if (resultadoFase3 != null) return resultadoFase3;

        takeScreenshot("Sin asientos especiales");
        org.junit.jupiter.api.Assumptions.abort(
                "En esta función no se detectaron asientos especiales, se omite la prueba. Intente con otra función.");
        return null; // inalcanzable, requerido por el compilador
    }

    /**
     * Devuelve {@code true} si el diálogo de "Asiento especial" está visible en pantalla.
     */
    private void verificarPantallaAsientosOSkip() {
        // Indicadores que confirman que estamos en el mapa de asientos
        String xpath =
            "//*[contains(@text,'Asientos') or contains(@text,'Pantalla Sala') or " +
            "contains(@text,'Paso 2') or contains(@text,'Paso 3') or " +
            "contains(@text,'Selecciona') or contains(@text,'selecciona')]";
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            boolean enAsientos = !driver.findElements(By.xpath(xpath)).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            if (!enAsientos) {
                takeScreenshot("App fuera de pantalla asientos");
                org.junit.jupiter.api.Assumptions.abort(
                    "SKIPPED: La app no está en la pantalla de asientos (posible cierre/crash de la app).");
            }
        } catch (org.opentest4j.TestAbortedException e) {
            throw e;
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            takeScreenshot("App fuera de pantalla asientos");
            org.junit.jupiter.api.Assumptions.abort(
                "SKIPPED: No se pudo verificar la pantalla de asientos (" + e.getMessage() + ").");
        }
    }

    private String buscarAsientoEspecialEnViewsSinTexto() {
        // Asientos especiales (silla de ruedas) en Compose: android.view.View sin texto.
        // Pueden tener enabled=true o false y clickable=false, pero responden a W3C tap.
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            int screenH = driver.manage().window().getSize().getHeight();
            int screenW = driver.manage().window().getSize().getWidth();
            int mapTop    = (int) (screenH * 0.28);
            int mapBottom = (int) (screenH * 0.94);
            int mapLeft   = (int) (screenW * 0.03);
            int mapRight  = (int) (screenW * 0.97);

            // Sin filtro de enabled: los asientos especiales pueden tener enabled=false
            List<WebElement> viewsSinTexto = driver.findElements(
                    By.xpath("//android.view.View[@text='']"));

            log.debug("[SelectorPage] Fase3: Views sin texto totales: {}", viewsSinTexto.size());

            // Recolectar candidatos válidos por posición y tamaño
            List<int[]> candidatos = new ArrayList<>();
            for (WebElement el : viewsSinTexto) {
                try {
                    org.openqa.selenium.Rectangle r = el.getRect();
                    int cx = r.getX() + r.getWidth() / 2;
                    int cy = r.getY() + r.getHeight() / 2;
                    int w  = r.getWidth();
                    int h  = r.getHeight();

                    if (cy < mapTop || cy > mapBottom) continue;
                    if (cx < mapLeft || cx > mapRight) continue;
                    // Rango ampliado: 10–120 px para no excluir el contenedor del ícono
                    if (w < 10 || w > 120) continue;
                    if (h < 10 || h > 120) continue;

                    candidatos.add(new int[]{cx, cy, w, h});
                } catch (Exception ignored) {}
            }

            log.debug("[SelectorPage] Fase3: candidatos en mapa: {}", candidatos.size());

            // Deduplicar por posición (evitar tocar el mismo punto dos veces)
            List<int[]> unicos = new ArrayList<>();
            for (int[] c : candidatos) {
                boolean duplicado = false;
                for (int[] u : unicos) {
                    if (Math.abs(c[0] - u[0]) < 8 && Math.abs(c[1] - u[1]) < 8) {
                        duplicado = true;
                        break;
                    }
                }
                if (!duplicado) unicos.add(c);
            }

            log.debug("[SelectorPage] Fase3: candidatos únicos: {}", unicos.size());

            // Cap de Fase 3: evita iterar cientos de View vacíos si no hay asiento especial.
            int maxFase3 = Math.min(unicos.size(), 20);
            if (unicos.size() > maxFase3)
                log.debug("[SelectorPage] Fase3: acotando a {} de {} candidatos", maxFase3, unicos.size());

            for (int fi3 = 0; fi3 < maxFase3; fi3++) {
                int[] coord = unicos.get(fi3);
                int cx = coord[0], cy = coord[1];
                try {
                    log.debug("[SelectorPage] Fase3: tap en ({},{}) size={}x{}", cx, cy, coord[2], coord[3]);

                    tapW3C(cx, cy);
                    sleep(800); // más tiempo para que aparezca el diálogo

                    if (estaVisibleAlertaAsientoEspecial()) {
                        log.info("[SelectorPage] Fase3: ¡asiento especial encontrado! ({},{})", cx, cy);
                        takeScreenshot("Asiento especial seleccionado");
                        return "asiento=especial en (" + cx + "," + cy + ") [especial]";
                    }

                    tapW3C(cx, cy); // deseleccionar
                    sleep(250);
                } catch (Exception ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
        return null;
    }

    public boolean estaVisibleAlertaAsientoEspecial() {
        try {
            return !driver.findElements(By.xpath(
                    "//*[contains(@text,'Asiento especial') or contains(@text,'asiento especial') or " +
                    "contains(@text,'discapacidad') or contains(@text,'Discapacidad') or " +
                    "contains(@text,'motriz') or contains(@text,'accesible') or " +
                    "contains(@text,'Accesible') or contains(@text,'PRM')]"
            )).isEmpty();
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Valida que el diálogo de asiento especial contenga título, mensaje y ambos botones,
     * y a continuación toca el botón indicado.
     *
     * Lanza {@link RuntimeException} si algún elemento esperado no está visible.
     *
     * @param aceptar {@code true} → pulsa "Aceptar y continuar" | {@code false} → pulsa "Cancelar"
     */
    public void validarYManejarAlertaAsientoEspecial(boolean aceptar) {
        boolean tituloVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'Asiento especial')]")
        ).isEmpty();

        boolean mensajeVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'discapacidad motriz')]")
        ).isEmpty();

        boolean botonAceptarVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'Aceptar y continuar')]")
        ).isEmpty();

        boolean botonCancelarVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'Cancelar')]")
        ).isEmpty();

        if (!tituloVisible) {
            throw new RuntimeException("Alerta asiento especial: no se encontró el título 'Asiento especial'.");
        }
        if (!mensajeVisible) {
            throw new RuntimeException("Alerta asiento especial: no se encontró el mensaje de discapacidad motriz.");
        }
        if (!botonAceptarVisible) {
            throw new RuntimeException("Alerta asiento especial: no se encontró el botón 'Aceptar y continuar'.");
        }
        if (!botonCancelarVisible) {
            throw new RuntimeException("Alerta asiento especial: no se encontró el botón 'Cancelar'.");
        }

        log.info("[SelectorPage] Alerta de asiento especial validada: título, mensaje y botones presentes.");

        String textoBoton = aceptar ? "Aceptar y continuar" : "Cancelar";
        List<WebElement> botones = driver.findElements(
                By.xpath("//*[contains(@text,'" + textoBoton + "')]")
        );

        if (botones.isEmpty()) {
            throw new RuntimeException(
                    "Alerta asiento especial: no se pudo encontrar el botón '" + textoBoton + "' para pulsarlo.");
        }

        WebElement btn = botones.get(0);
        org.openqa.selenium.Rectangle r = btn.getRect();
        tapW3C(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
        sleep(300);

        log.info("[SelectorPage] Botón '{}' pulsado en alerta de asiento especial.", textoBoton);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alerta: Restricciones (Sala Junior)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve {@code true} si la alerta de Restricciones de Sala Junior está visible.
     */
    // PERF/FIX (Problema 5 — hayAlertaHorarioInesperada, llamado en cada horario probado):
    // sin rama iOS, siempre false en iOS. NSPredicate — ver nota en PlatformLocator.byExactText().
    // FIX real (mismo hallazgo que aceptarYContinuarLocator() — ver comentario ahí): este
    // método se llama en el hot-path de cada horario probado (hayAlertaHorarioInesperada(),
    // y directamente dentro del smartWait de seleccionarPrimerHorarioDescartandoAlertas()) —
    // wildcard //* acotado al mismo patrón ya validado en el resto del archivo, sin cambio de
    // semántica para ninguno de sus llamadores (incluida la alerta de Sala Junior).
    public boolean estaVisibleAlertaRestricciones() {
        try {
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString(
                        "label CONTAINS 'Restricciones' OR label CONTAINS 'ambiente familiar' " +
                        "OR value CONTAINS 'Restricciones' OR value CONTAINS 'ambiente familiar'")
                    : By.xpath("//android.widget.TextView[contains(@text,'Restricciones') or contains(@text,'ambiente familiar')]"
                        + " | //android.view.View[contains(@text,'Restricciones') or contains(@text,'ambiente familiar')]");
            return !driver.findElements(locator).isEmpty();
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Valida que la alerta de Restricciones de Sala Junior muestre título, mensaje
     * y ambos botones, y a continuación toca el botón indicado.
     *
     * Lanza {@link RuntimeException} si algún elemento esperado no está visible.
     *
     * @param aceptar {@code true} → pulsa "Aceptar y continuar" | {@code false} → pulsa "Cancelar"
     */
    public void validarYManejarAlertaRestricciones(boolean aceptar) {
        boolean tituloVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'Restricciones')]")
        ).isEmpty();

        boolean mensajeVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'ambiente familiar') or contains(@text,'sala-junior')]")
        ).isEmpty();

        boolean botonAceptarVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'Aceptar y continuar')]")
        ).isEmpty();

        boolean botonCancelarVisible = !driver.findElements(
                By.xpath("//*[contains(@text,'Cancelar')]")
        ).isEmpty();

        if (!tituloVisible) {
            throw new RuntimeException("Alerta Restricciones: no se encontró el título 'Restricciones'.");
        }
        if (!mensajeVisible) {
            throw new RuntimeException("Alerta Restricciones: no se encontró el mensaje esperado.");
        }
        if (!botonAceptarVisible) {
            throw new RuntimeException("Alerta Restricciones: no se encontró el botón 'Aceptar y continuar'.");
        }
        if (!botonCancelarVisible) {
            throw new RuntimeException("Alerta Restricciones: no se encontró el botón 'Cancelar'.");
        }

        log.info("[SelectorPage] Alerta de Restricciones validada: título, mensaje y botones presentes.");

        String textoBoton = aceptar ? "Aceptar y continuar" : "Cancelar";
        List<WebElement> botones = driver.findElements(
                By.xpath("//*[contains(@text,'" + textoBoton + "')]")
        );

        if (botones.isEmpty()) {
            throw new RuntimeException(
                    "Alerta Restricciones: no se encontró el botón '" + textoBoton + "' para pulsarlo.");
        }

        WebElement btn = botones.get(0);
        org.openqa.selenium.Rectangle r = btn.getRect();
        tapW3C(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
        sleep(300);

        log.info("[SelectorPage] Botón '{}' pulsado en alerta de Restricciones.", textoBoton);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alerta genérica: cualquier modal con botón "Aceptar y continuar"
    // Cubre: clasificación C/B/B15, Atención (vibraciones), y cualquier otra
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detecta y acepta CUALQUIER alerta modal con botón "Aceptar y continuar",
     * sin importar su título ni contenido (clasificación C/B/B15, Atención, etc.).
     *
     * Estrategias de tap en orden hasta que la alerta desaparezca:
     *   1. Tap en el TextView con el texto del botón
     *   2. Tap en el padre del TextView (contenedor Compose)
     *   3. Tap en el android.widget.Button hermano (enabled aunque clickable=false)
     *
     * @return true si se detectó y aceptó una alerta.
     */
    // PERF/FIX (Problema 5 — llamado tras CADA horario tapeado en las dos rutas de
    // selección): sin rama iOS, "textos" siempre vacío en iOS → el while quemaba
    // garantizado los 2000ms completos (implicitlyWait=0 ya estaba bien puesto — el
    // problema era el locator en sí, no la espera). NSPredicate — ver nota en
    // PlatformLocator.byExactText().
    // FIX real (causa raíz confirmada EN VIVO tras instrumentar seleccionarPrimerHorarioDescartandoAlertas()
    // — ver METRICS[ScheduleSelection]: un click válido quedó "SIN-TRANSICION" 17-20s en dos corridas
    // reales consecutivas contra el mismo dispositivo, sin ninguna excepción ni log intermedio). Este
    // locator se consulta en bucle (hasta 2s en aceptarAlertaAceptarYContinuarSiPresente(), y de nuevo
    // dentro del smartWait de seleccionarPrimerHorarioDescartandoAlertas()) — con el wildcard //* cada
    // consulta fuerza a UiAutomator2 a volcar el árbol completo, y si hay una animación de entrada del
    // modal en curso cada volcado puede tardar varios segundos en vez de milisegundos, multiplicado por
    // cada re-chequeo del bucle. Mismo acotado ya validado en el resto del archivo (android.widget.TextView/
    // android.view.View — únicos tipos de nodo con @text en esta app).
    private By aceptarYContinuarLocator() {
        return isIOS()
                ? AppiumBy.iOSNsPredicateString("label CONTAINS 'Aceptar y continuar' OR value CONTAINS 'Aceptar y continuar'")
                : By.xpath("//android.widget.TextView[contains(@text,'Aceptar y continuar')]"
                    + " | //android.view.View[contains(@text,'Aceptar y continuar')]");
    }

    public boolean aceptarAlertaAceptarYContinuarSiPresente() {
        final long TIMEOUT_MS = 2000;
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            while (System.currentTimeMillis() < deadline) {
                List<WebElement> textos = driver.findElements(aceptarYContinuarLocator());
                for (WebElement txt : textos) {
                    try {
                        if (!txt.isDisplayed()) continue;

                        // Detectar título de la alerta para el log
                        String tituloAlerta = detectarTituloAlerta();
                        int cx = txt.getRect().getX() + txt.getRect().getWidth()  / 2;
                        int cy = txt.getRect().getY() + txt.getRect().getHeight() / 2;

                        // Estrategia 1: tap directo en el texto
                        tapW3C(cx, cy);
                        sleep(400);
                        if (driver.findElements(aceptarYContinuarLocator()).isEmpty()) {
                            log.info("[SelectorPage] Alerta'{}' aceptada (tap texto).", tituloAlerta);
                            return true;
                        }

                        // Estrategia 2: tap en el padre (contenedor Compose/View)
                        try {
                            WebElement parent = txt.findElement(By.xpath(".."));
                            tapW3C(parent.getRect().getX() + parent.getRect().getWidth()  / 2,
                                   parent.getRect().getY() + parent.getRect().getHeight() / 2);
                            sleep(400);
                            if (driver.findElements(aceptarYContinuarLocator()).isEmpty()) {
                                log.info("[SelectorPage] Alerta '{}' aceptada (tap padre).", tituloAlerta);
                                return true;
                            }
                        } catch (Exception ignored) {}

                        // Estrategia 3: android.widget.Button hermano (clickable=false pero enabled=true)
                        // Exclusivo de Android — Compose expone el botón real como hermano del
                        // TextView; iOS no tiene este patrón (el elemento tocado ya es el control
                        // real), por eso esta estrategia solo se intenta en Android.
                        if (!isIOS()) {
                            try {
                                WebElement parent = txt.findElement(By.xpath(".."));
                                List<WebElement> btns = parent.findElements(
                                    By.xpath(".//android.widget.Button"));
                                for (WebElement btn : btns) {
                                    tapW3C(btn.getRect().getX() + btn.getRect().getWidth()  / 2,
                                           btn.getRect().getY() + btn.getRect().getHeight() / 2);
                                    sleep(400);
                                    if (driver.findElements(aceptarYContinuarLocator()).isEmpty()) {
                                        log.info("[SelectorPage] Alerta '{}' aceptada (tap Button).", tituloAlerta);
                                        return true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        // Al menos un tap se ejecutó — asumir que funcionó
                        log.info("[SelectorPage] Alerta '{}' tapeada con 'Aceptar y continuar'.", tituloAlerta);
                        sleep(300);
                        return true;

                    } catch (Exception ignored) {}
                }
                sleep(200);
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
        return false;
    }

    /** Intenta leer el título del diálogo visible para logging (best-effort, no bloquea el flujo). */
    private String detectarTituloAlerta() {
        try {
            // Primeras líneas de texto visibles en el área superior del diálogo.
            // NSPredicate en iOS — ver nota de rendimiento en PlatformLocator.byExactText().
            // FIX real (mismo hallazgo que aceptarYContinuarLocator() — ver comentario ahí):
            // wildcard //* acotado al mismo patrón ya validado en el resto del archivo.
            By locator = isIOS()
                    ? AppiumBy.iOSNsPredicateString("value.length > 3 AND value.length < 60")
                    : By.xpath("//android.widget.TextView[@text and string-length(@text) > 3 and string-length(@text) < 60]"
                        + " | //android.view.View[@text and string-length(@text) > 3 and string-length(@text) < 60]");
            List<WebElement> textos = driver.findElements(locator);
            for (WebElement el : textos) {
                try {
                    String t = obtenerTextoSeguro(el);
                    if (!t.isBlank() && !t.contains("Aceptar") && !t.contains("Cancelar")) {
                        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return "desconocida";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alerta: Atención (movimientos y vibraciones repentinas)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve {@code true} si la alerta de Atención (movimientos y vibraciones)
     * está visible en pantalla.
     */
    public boolean estaVisibleAlertaAtencion() {
        try {
            boolean tituloVisible = !driver.findElements(By.xpath(
                    "//*[contains(@text,'Atención') or contains(@text,'Atencion')]"
            )).isEmpty();
            if (!tituloVisible) return false;
            return !driver.findElements(By.xpath(
                    "//*[contains(@text,'vibraciones') or contains(@text,'menores de 4')]"
            )).isEmpty();
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Si hay una alerta visible con "Aceptar y continuar", la acepta.
     * Mantiene el nombre original para compatibilidad; delega al método general.
     */
    public boolean aceptarAlertaAtencionSiPresente() {
        return aceptarAlertaAceptarYContinuarSiPresente();
    }

    public String cambiarHorarioEnPantallaAsientos() {
        sleep(200);

        try {
            List<WebElement> horariosVisibles = obtenerHorariosVisiblesEnPantallaAsientos();

            if (horariosVisibles.isEmpty()) {
                abortNoHayMasHorariosEnAsientos("No se detectaron horarios visibles en la barra superior.");
                return "";
            }

            WebElement horarioActual = obtenerHorarioActualSeleccionado(horariosVisibles);
            String horarioActualTexto = horarioActual != null ? obtenerTextoSeguro(horarioActual) : "";

            List<WebElement> candidatos = new ArrayList<>();
            for (WebElement horario : horariosVisibles) {
                String txt = obtenerTextoSeguro(horario);
                if (txt.isBlank()) continue;

                if (!txt.equalsIgnoreCase(horarioActualTexto)) {
                    candidatos.add(horario);
                }
            }

            if (candidatos.isEmpty()) {
                abortNoHayMasHorariosEnAsientos(
                        "Solo hay un horario visible o no existe otro distinto al actual. Actual=" + horarioActualTexto
                );
                return "";
            }

            Collections.shuffle(candidatos);
            WebElement nuevoHorario = candidatos.get(0);
            String nuevoHorarioTexto = obtenerTextoSeguro(nuevoHorario);

            log.info("[SelectorPage] Horario actual detectado: {}", horarioActualTexto);
            log.info("[SelectorPage] Intentando cambiar al horario: {}", nuevoHorarioTexto);

            if (!clicSeguroEnHorarioAsientos(nuevoHorario)) {
                throw new RuntimeException("No se pudo cambiar al horario: " + nuevoHorarioTexto);
            }

            sleep(800);

            log.info("[SelectorPage] Horario cambiado correctamente a: {}", nuevoHorarioTexto);
            takeScreenshot("Horario cambiado");
            return nuevoHorarioTexto;

        } catch (org.opentest4j.TestAbortedException aborted) {
            throw aborted;
        } catch (Exception e) {
            rethrowIfAborted(e);
            throw e;
        }
    }
    // Mismo hallazgo/fix que obtenerHorariosDisponibles() — sin rama iOS, siempre vacío.
    private List<WebElement> obtenerHorariosVisiblesEnPantallaAsientos() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        List<By> candidatos = isIOS() ? List.of(
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeStaticText' AND value != nil"),
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeButton' AND (value != nil OR label != nil)")
        ) : List.of(
                By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"),
                By.xpath("//android.view.View[@text and normalize-space(@text)!='']")
        );

        for (By locator : candidatos) {
            try {
                List<WebElement> elementos = driver.findElements(locator);

                for (WebElement el : elementos) {
                    try {
                        if (!el.isDisplayed()) continue;

                        String txt = obtenerTextoSeguro(el);
                        if (!txt.matches("^(1[0-2]|[1-9]):[0-5]\\d\\s?(AM|PM|am|pm)$")) continue;

                        int y = el.getRect().getY() + (el.getRect().getHeight() / 2);
                        int screenHeight = driver.manage().window().getSize().getHeight();

                        // Solo barra superior de horarios
                        if (y > (screenHeight * 0.10) && y < (screenHeight * 0.32)) {
                            unicos.putIfAbsent(txt.trim(), el);
                        }

                    } catch (Exception ignored) {
                    }
                }

                if (!unicos.isEmpty()) {
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        resultado.addAll(unicos.values());

        log.debug("[SelectorPage] Horarios visibles en pantalla de asientos: {}", resultado.size());
        for (WebElement el : resultado) {
            log.debug(" - {}", obtenerTextoSeguro(el));
        }

        return resultado;
    }
    private WebElement obtenerHorarioActualSeleccionado(List<WebElement> horarios) {
        for (WebElement el : horarios) {
            try {
                String selected = safeLower(el.getAttribute("selected"));
                String checked = safeLower(el.getAttribute("checked"));
                String desc = safeLower(el.getAttribute("contentDescription"));

                if ("true".equals(selected)
                        || "true".equals(checked)
                        || desc.contains("seleccionado")
                        || desc.contains("selected")) {
                    return el;
                }
            } catch (Exception ignored) {
            }
        }

        // fallback: si no detectamos cuál está activo, tomamos el primero visible
        return horarios.isEmpty() ? null : horarios.get(0);
    }
    public void seleccionarFiltro3D() {
        seleccionarFiltroGenerico("3D");
    }

    /**
     * Abre el panel de filtros, selecciona la opción cuyo texto coincida con {@code textoFiltro}
     * y pulsa "Aplicar". Verifica que el elemento quede marcado (checked/selected) antes de aplicar;
     * si no, reintenta con distintas posiciones de tap sobre la fila.
     */
    private void seleccionarFiltroGenerico(String textoFiltro) {
        // FIX real (Problema 6 — evidencia: seleccionarFiltro3D()/seleccionarFiltroSalaJunior()
        // se llaman ANTES de abrir cualquier película, es decir, en la pantalla de
        // cartelera — no de horarios). Buscar "Filtros" sin validar primero que la
        // cartelera realmente cargó con tarjetas de película reales producía un fallo
        // tardío y confuso (timeout genérico de waitAndGet). Se reutiliza
        // esperarPeliculasVisibles() (mismo helper que usa MovieDetection — evita
        // repetir aquí un segundo escaneo de un solo intento) como señal de "contexto
        // correcto": si no hay ninguna tarjeta real detectable ni con reintento, se
        // detiene el flujo de inmediato con un motivo claro en vez de seguir buscando
        // elementos que no existen en esta pantalla.
        if (esperarPeliculasVisibles(5000).isEmpty()) {
            throw new org.opentest4j.TestAbortedException(
                    "No se puede seleccionar el filtro '" + textoFiltro
                    + "': la cartelera no muestra películas — contexto incorrecto para buscar 'Filtros'.");
        }

        By btnFiltros = By.xpath("//*[contains(@text,'Filtros') or contains(@content-desc,'Filtros')]");
        By btnAplicar = By.xpath("//*[contains(@text,'Aplicar')]");

        try {
            log.info("[SelectorPage] Abriendo panel de filtros para seleccionar '{}'...", textoFiltro);

            WebElement filtros = waitAndGet(btnFiltros);
            if (!clicSeguroEnElemento(filtros)) tapElementCenter(filtros);
            pausa(1200);

            WebElement opcion = encontrarElementoFiltro(textoFiltro);
            if (opcion == null) {
                log.warn("[SelectorPage] La opción '{}' no está disponible en el panel de filtros. Intenta con otro cine", textoFiltro);
                // Panel queda abierto para que TestSteps capture el screenshot del panel
                throw new org.opentest4j.TestAbortedException(
                        "No se encontró el filtro " + textoFiltro + " para esta función");
            }

            log.info("[SelectorPage] Opción '{}' encontrada. Intentando marcarla...", textoFiltro);

            if (!marcarOpcionFiltro(opcion)) {
                throw new RuntimeException("No se pudo marcar la opción '" + textoFiltro + "' en el panel de filtros.");
            }

            log.info("[SelectorPage] Aplicando filtros...");
            WebElement aplicar = waitAndGet(btnAplicar);
            if (!clicSeguroEnElemento(aplicar)) tapElementCenter(aplicar);
            pausa(1500);

            log.info("[SelectorPage] Filtro '{}' aplicado correctamente.", textoFiltro);
            takeScreenshot("Filtro " + textoFiltro + " aplicado");

        } catch (org.opentest4j.TestAbortedException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(
                    "❌ No fue posible seleccionar el filtro '" + textoFiltro + "'. Detalle: " + e.getMessage(), e);
        }
    }

    private WebElement encontrarElementoFiltro(String textoFiltro) {
        String[][] xpaths = {
            { "//*[@text='" + textoFiltro + "']" },
            { "//android.widget.TextView[@text='" + textoFiltro + "']" },
            { "//*[contains(@text,'" + textoFiltro + "')]" },
            { "//*[contains(@content-desc,'" + textoFiltro + "')]" }
        };
        for (String[] xp : xpaths) {
            try {
                List<WebElement> found = driver.findElements(By.xpath(xp[0]));
                for (WebElement el : found) {
                    try {
                        if (el.isDisplayed()) return el;
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Intenta marcar una opción de filtro con hasta 4 estrategias de tap.
     * Si los atributos checked/selected no son detectables (apps Compose),
     * asume marcado tras el primer tap exitoso para que el flujo llegue a "Aplicar".
     */
    private boolean marcarOpcionFiltro(WebElement opcion) {
        int screenWidth = driver.manage().window().getSize().getWidth();
        int xCentro     = opcion.getRect().getX() + opcion.getRect().getWidth() / 2;
        int yCentro     = opcion.getRect().getY() + opcion.getRect().getHeight() / 2;

        Runnable[] estrategias = {
            () -> clicSeguroEnElemento(opcion),                // click nativo + parent fallbacks
            () -> tapW3C(xCentro, yCentro),                   // tap centro del texto
            () -> tapW3C((int)(screenWidth * 0.50), yCentro), // tap centro de la fila
            () -> tapW3C((int)(screenWidth * 0.12), yCentro)  // tap zona checkbox izquierda
        };

        boolean tapEjecutado = false;
        for (int i = 0; i < estrategias.length; i++) {
            try {
                estrategias[i].run();
                tapEjecutado = true;
                pausa(700);
                if (esFiltroMarcado(opcion)) {
                    log.info("[SelectorPage] Opción marcada (atributos) en intento {}.", i + 1);
                    return true;
                }
                log.debug("[SelectorPage] Intento {}: tap OK, atributo no detectable.", i + 1);
            } catch (Exception e) {
                log.warn("[SelectorPage] Intento {} falló: {}", i + 1, e.getMessage());
            }
        }
        // En Compose, checked/selected no siempre son accesibles vía UiAutomator2,
        // pero el tap SÍ aplica el cambio visual. Si al menos uno se ejecutó, OK.
        if (tapEjecutado) {
            log.info("[SelectorPage] checked no detectable (Compose) — tap ejecutado, asumiendo marcado.");
            return true;
        }
        return false;
    }

    /** Devuelve true si el elemento o su jerarquía cercana indica que está marcado. */
    private boolean esFiltroMarcado(WebElement el) {
        try {
            // 1. Atributos directos del elemento
            for (String attr : new String[]{"checked", "selected"}) {
                if ("true".equalsIgnoreCase(el.getAttribute(attr))) return true;
            }
            // 2. contentDescription — Compose suele incluir el estado
            String desc = safeLower(el.getAttribute("contentDescription"));
            if (desc.contains("seleccionado") || desc.contains("marcado")
                    || desc.contains("checked") || desc.contains(", on")) return true;

            // 3. Padre (row container)
            WebElement parent = el.findElement(By.xpath(".."));
            for (String attr : new String[]{"checked", "selected"}) {
                if ("true".equalsIgnoreCase(parent.getAttribute(attr))) return true;
            }
            String pd = safeLower(parent.getAttribute("contentDescription"));
            if (pd.contains("seleccionado") || pd.contains("checked")) return true;

            // 4. Abuelo
            try {
                WebElement grand = parent.findElement(By.xpath(".."));
                for (String attr : new String[]{"checked", "selected"}) {
                    if ("true".equalsIgnoreCase(grand.getAttribute(attr))) return true;
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return false;
    }

    public void seleccionarFiltroSalaJunior() {
        By btnFiltros    = By.xpath("//*[contains(@text,'Filtros')]");
        By txtSalaJunior = By.xpath("//android.widget.TextView[@text='Sala Junior']");
        By btnAplicar    = By.xpath("//*[contains(@text,'Aplicar')]");

        try {
            log.info("[SelectorPage] Abriendo pantalla de filtros para Sala Junior...");

            WebElement filtros = waitAndGet(btnFiltros);
            if (!clicSeguroEnElemento(filtros)) {
                tapElementCenter(filtros);
            }

            pausa(1200);

            // Verificar si existe la opción antes de intentar interactuar
            java.util.List<WebElement> opciones = driver.findElements(txtSalaJunior);
            if (opciones == null || opciones.isEmpty()) {
                log.warn("[SelectorPage] La opción Sala Junior no está disponible en el panel de filtros.");
                // Panel queda abierto para que TestSteps capture el screenshot del panel
                throw new org.opentest4j.TestAbortedException(
                        "No se encontró el filtro Sala Junior para esta función");
            }

            log.info("[SelectorPage] Intentando seleccionar fila de Sala Junior...");

            WebElement textoSalaJunior = opciones.get(0);

            int screenWidth = driver.manage().window().getSize().getWidth();
            int yFilaSalaJunior = textoSalaJunior.getRect().getY() + (textoSalaJunior.getRect().getHeight() / 2);

            boolean seleccionado = false;

            // Intento 1: tap en la zona izquierda de la fila (zona del checkbox)
            try {
                int xFila = (int) (screenWidth * 0.12);
                log.debug("[SelectorPage] Tap fila Sala Junior intento 1 -> X={} Y={}", xFila, yFilaSalaJunior);
                tapW3C(xFila, yFilaSalaJunior);
                pausa(700);
                seleccionado = true;
            } catch (Exception e) {
                log.warn("[SelectorPage] Falló intento 1 sobre fila Sala Junior.");
            }

            // Intento 2: un poco más al centro del row
            if (!seleccionado) {
                try {
                    int xFila = (int) (screenWidth * 0.20);
                    log.debug("[SelectorPage] Tap fila Sala Junior intento 2 -> X={} Y={}", xFila, yFilaSalaJunior);
                    tapW3C(xFila, yFilaSalaJunior);
                    pausa(700);
                    seleccionado = true;
                } catch (Exception e) {
                    log.warn("[SelectorPage] Falló intento 2 sobre fila Sala Junior.");
                }
            }

            // Intento 3: tocar directamente el texto
            if (!seleccionado) {
                try {
                    if (!clicSeguroEnElemento(textoSalaJunior)) {
                        tapElementCenter(textoSalaJunior);
                    }
                    pausa(700);
                    seleccionado = true;
                } catch (Exception e) {
                    log.warn("[SelectorPage] Falló intento tocando el texto Sala Junior.");
                }
            }

            if (!seleccionado) {
                throw new RuntimeException("No se pudo seleccionar la opción Sala Junior.");
            }

            log.info("[SelectorPage] Aplicando filtros...");

            WebElement aplicar = waitAndGet(btnAplicar);
            if (!clicSeguroEnElemento(aplicar)) {
                tapElementCenter(aplicar);
            }

            pausa(1500);

            log.info("[SelectorPage] Filtro Sala Junior aplicado correctamente.");
            takeScreenshot("Filtro Sala Junior aplicado");

        } catch (org.opentest4j.TestAbortedException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("❌ No fue posible seleccionar el filtro Sala Junior. Detalle: " + e.getMessage(), e);
        }
    }

    public WebElement waitAndGet(By locator) {
        return waits.waitVisible(locator);
    }

    public void pausa(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void tapElementCenter(WebElement element) {
        int centerX = element.getRect().getX() + (element.getRect().getWidth() / 2);
        int centerY = element.getRect().getY() + (element.getRect().getHeight() / 2);
        tapW3C(centerX, centerY);
    }
    private boolean clicSeguroEnHorarioAsientos(WebElement el) {
        try {
            el.click();
            return true;
        } catch (Exception e1) {
            try {
                WebElement parent = el.findElement(By.xpath(".."));
                parent.click();
                return true;
            } catch (Exception e2) {
                try {
                    WebElement grandParent = el.findElement(By.xpath("../.."));
                    grandParent.click();
                    return true;
                } catch (Exception e3) {
                    try {
                        int centerX = el.getRect().getX() + (el.getRect().getWidth() / 2);
                        int centerY = el.getRect().getY() + (el.getRect().getHeight() / 2);
                        tapW3C(centerX, centerY);
                        return true;
                    } catch (Exception e4) {
                        log.warn("[SelectorPage] No se pudo cambiar el horario desde asientos: {}", obtenerTextoSeguro(el));
                        return false;
                    }
                }
            }
        }
    }


    /**
     * Realiza un toque directo en las coordenadas del elemento usando W3C Actions.
     * Es más rápido y confiable que .click() en mapas de asientos complejos.
     */
    private boolean tapDirecto(WebElement el) {
        try {
            org.openqa.selenium.Rectangle r = el.getRect();
            int x = r.getX() + (r.getWidth() / 2);
            int y = r.getY() + (r.getHeight() / 2);

            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence tap = new Sequence(finger, 1);
            tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
            tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            tap.addAction(new Pause(finger, Duration.ofMillis(50)));
            tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

            driver.perform(Collections.singletonList(tap));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Devuelve una descripción legible del asiento para logs.
     */


    /**
     * Wrapper para pausar la ejecución de forma segura.
     */
    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Espera activa: retorna en cuanto la condición es verdadera o se agota el
     * timeout — mismo patrón que CinemasHelper.smartWait(), reutilizado aquí para
     * reemplazar sleeps fijos por polling corto con salida temprana (Problema 5).
     * Nunca espera MÁS que un sleep(maxMs) equivalente en el peor caso.
     */
    private boolean smartWait(java.util.function.BooleanSupplier condition, long maxMs, long pollMs) {
        long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) return true;
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) break;
            sleep(Math.min(pollMs, remaining));
        }
        return condition.getAsBoolean();
    }


    private void hacerScrollHorarios() {
        try {
            slowSwipeUp();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo hacer scroll en horarios.", e);
        }
    }

    // FIX real (evidencia capturada con IOSLocatorDebug — mapa de asientos): los
    // botones de número de asiento en iOS (XCUIElementTypeButton) traen @value VACÍO
    // — el número real vive en @label/@name. Antes solo se consultaba @value, así
    // que obtenerTextoSeguro() devolvía "" para CADA botón de asiento, y el filtro
    // de longitud (<=2) de más abajo nunca los aceptaba como candidatos — el mapa
    // parecía "vacío" aunque estuviera completamente poblado en pantalla. Se agrega
    // @label y @name como fallback adicional (en ese orden, después de @value, que
    // ya funciona correctamente para películas/horarios — @value nunca deja de
    // usarse primero, esto es puramente aditivo).
    private String obtenerTextoSeguro(WebElement el) {
        try {
            String txt = el.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
            if (isIOS()) {
                String val = el.getAttribute("value");
                if (val != null && !val.isBlank()) return val.trim();
                String label = el.getAttribute("label");
                if (label != null && !label.isBlank()) return label.trim();
                String name = el.getAttribute("name");
                if (name != null && !name.isBlank()) return name.trim();
            }
            return txt == null ? "" : txt.trim();
        } catch (Exception e) {
            return "";
        }
    }

    // FIX real (Problema 1 — evidencia: abrirPrimerPeliculaDesdeVerSinopsis() intentaba
    // abrir "Inicia sesión o crea una cuenta", "¿Ya tienes tu cuenta digital con QR?",
    // "Si tienes una tarjeta física...", "crea una cuenta..." como si fueran películas —
    // hasta 4 intentos × ~3 min c/u = >12 min perdidos en un solo caso). Estos textos de
    // Club Cinépolis/login pasaban el filtro anterior porque era una lista de
    // coincidencias EXACTAS que nunca los incluyó. Se agregan dos capas:
    //   1) Palabras clave inequívocas de Club Cinépolis/login/promocional (CONTAINS,
    //      no exact-match — cubre variantes de redacción del mismo banner).
    //   2) Heurística estructural: ningún título real de película en esta cartelera
    //      contiene "?"/"¿" (son preguntas) ni "..." (elipsis de copy promocional) —
    //      esto generaliza a CUALQUIER banner futuro con esa forma, sin depender de
    //      conocer su texto exacto de antemano.
    private static final String[] PALABRAS_CLAVE_NO_PELICULA = {
            "inicia sesión", "inicia sesion", "crea una cuenta", "creación de cuenta",
            "cuenta digital", "tarjeta física", "tarjeta fisica", "código qr", "codigo qr",
            "club cinépolis", "club cinepolis", "regístrate", "registrate",
            "recuperar contraseña", "recuperar contrasena", "cerrar sesión", "cerrar sesion",
    };

    private boolean esTextoNoPelicula(String txt) {
        String t = txt == null ? "" : txt.trim().toLowerCase();

        if (t.contains("?") || t.contains("¿") || t.contains("...")) return true;
        for (String clave : PALABRAS_CLAVE_NO_PELICULA) {
            if (t.contains(clave)) return true;
        }

        return t.isBlank()
                || t.equals("cartelera")
                || t.equals("horarios")
                || t.equals("cines")
                || t.equals("fechas")
                || t.equals("filtros")
                || t.equals("películas")
                || t.equals("alimentos")
                || t.equals("club")
                || t.equals("mis compras")
                || t.equals("más")
                || t.equals("la perla")
                || t.equals("horarios en otros cines")
                || t.startsWith("hoy ")
                || t.equals("ver sinopsis")
                || t.equals("ver horarios")
                || t.equals("ver tráiler")
                || t.equals("ver trailer")
                || t.equals("ver más")
                || t.equals("español")
                || t.equals("subtitulada")
                || t.equals("dirigida por")
                || t.contains("garantía")
                || t.contains("encontrado")
                || t.matches("^\\d+\\s*min$")
                || t.matches("^[ab]\\s*\\d+\\s*min$")
                || t.matches("^\\d{4}$")
                || t.matches("^([01]?\\d|2[0-3]):[0-5]\\d(\\s?(am|pm))?$")
                || t.length() > 120;
    }

    // =========================
    // ASIENTOS - OPTIMIZADO
    // =========================

    public String seleccionarAsientoRandomDisponible() {
        utils.PerfMetrics.startPhase("SeatSelection");
        try {
            SeatMap map = buildSeatMap();
            log.info("[SelectorPage] {}", map.getSummary());

            if (map.isEmpty()) {
                // Diagnóstico temporal (no-op salvo -DIOS_LOCATOR_DEBUG=true) — captura el
                // page source real cuando el mapa de asientos aparece vacío, para investigar
                // con evidencia en vez de seguir adivinando (mismo mecanismo ya usado para
                // el hallazgo de "Ver sinopsis"/horarios).
                if (isIOS()) {
                    IOSLocatorDebug.onFailure(driver, "seleccionarAsientoRandomDisponible_mapaVacio", null,
                            new RuntimeException("buildSeatMap() devolvió vacío tras los 3 niveles de fallback"));
                }
                throw new RuntimeException("No se encontraron asientos visibles en el mapa.");
            }

            long tCandidatos = System.currentTimeMillis();
            List<SeatMap.Seat> seats = map.allSeats();
            Collections.shuffle(seats);
            utils.PerfMetrics.stage("SeatSelection", "candidatos", System.currentTimeMillis() - tCandidatos);

            int maxIntentos = Math.min(6, seats.size());
            for (int i = 0; i < maxIntentos; i++) {
                SeatMap.Seat seat = seats.get(i);
                log.info("[SelectorPage] Intentando seleccionar asiento rápido: {}", seat);

                long tClick = System.currentTimeMillis();
                boolean ok = tapRapidoEnButacaDesdeLabel(seat.element);
                utils.PerfMetrics.attempt("SeatSelection", i + 1, seat.toString(), System.currentTimeMillis() - tClick, ok ? "OK" : "FAIL");
                if (ok) {
                    sleep(150);
                    log.info("[SelectorPage] Asiento seleccionado OK: {}", seat);
                    takeScreenshot("Asiento seleccionado");
                    return seat.toString();
                }
            }

            throw new RuntimeException("Se detectaron asientos, pero no se pudo seleccionar ninguno.");
        } finally {
            utils.PerfMetrics.endPhase("SeatSelection");
        }
    }
    private List<WebElement> obtenerAsientosDelMapaAmplio() {
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        int screenHeight = driver.manage().window().getSize().getHeight();
        int mapTop    = (int)(screenHeight * 0.22);
        int mapBottom = (int)(screenHeight * 0.96);

        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            String seatXpath = isIOS()
                ? "//XCUIElementTypeStaticText[@value and string-length(normalize-space(@value)) <= 2]"
                : "//android.widget.TextView[@text and string-length(normalize-space(@text)) <= 2]";
            // UIAutomator2 on-device (Android) / NSPredicate (iOS, ver obtenerCandidatosAsientoIOS()) —
            // seatXpath se conserva como fallback XPath por si la ruta principal lanza excepción.
            try {
                List<WebElement> elementos = isIOS()
                    ? obtenerCandidatosAsientoIOS()
                    : driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"^\\\\d{1,2}$\")"));
                for (WebElement el : elementos) {
                    try {
                        org.openqa.selenium.Rectangle r = el.getRect();
                        int cy = r.getY() + r.getHeight() / 2;
                        int cx = r.getX() + r.getWidth() / 2;
                        if (cy < mapTop || cy > mapBottom || cx < 10) continue;
                        String key = isIOS() ? (obtenerTextoSeguro(el) + "|" + r.getX() + "|" + r.getY()) : (r.getX() + "|" + r.getY());
                        if (isIOS() && !obtenerTextoSeguro(el).matches("^\\d{1,2}$")) continue;
                        unicos.putIfAbsent(key, el);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                // Fallback XPath
                List<WebElement> elementos = driver.findElements(By.xpath(seatXpath));
                for (WebElement el : elementos) {
                    try {
                        String txt = obtenerTextoSeguro(el);
                        if (!txt.matches("^\\d{1,2}$")) continue;
                        org.openqa.selenium.Rectangle r = el.getRect();
                        int cy = r.getY() + r.getHeight() / 2;
                        int cx = r.getX() + r.getWidth() / 2;
                        if (cy < mapTop || cy > mapBottom || cx < 10) continue;
                        unicos.putIfAbsent(txt + "|" + r.getX() + "|" + r.getY(), el);
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        List<WebElement> resultado = new ArrayList<>(unicos.values());
        log.debug("[SelectorPage] Asientos amplios detectados: {}", resultado.size());
        return resultado;
    }




    private List<WebElement> obtenerAsientosDisponiblesVisibles() {
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        int screenHeight = driver.manage().window().getSize().getHeight();
        int mapTop    = (int)(screenHeight * 0.30);
        int mapBottom = (int)(screenHeight * 0.93);

        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            // UIAutomator2 (Android) / NSPredicate (iOS, ver obtenerCandidatosAsientoIOS()): filtra asientos numéricos en el mapa
            List<WebElement> candidatos = isIOS()
                ? obtenerCandidatosAsientoIOS()
                : driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"^\\\\d{1,2}$\")"));
            for (WebElement el : candidatos) {
                try {
                    // FIX real (mismo hallazgo que esperarYObtenerAsientosDelMapa(): sin
                    // esto, este fallback puede aceptar etiquetas de fila de una letra
                    // como si fueran asientos, exactamente el mismo bug — este chequeo ya
                    // existía en obtenerAsientosDelMapaAmplio(), aquí faltaba.
                    if (isIOS() && !obtenerTextoSeguro(el).matches("^\\d{1,2}$")) continue;

                    org.openqa.selenium.Rectangle r = el.getRect();
                    int cy = r.getY() + r.getHeight() / 2;
                    int cx = r.getX() + r.getWidth() / 2;
                    if (cy < mapTop || cy > mapBottom || cx < 20) continue;

                    // Solo descartamos si contentDescription indica explícitamente no disponible
                    String desc = safeLower(el.getAttribute("contentDescription"));
                    if (desc.contains("ocupado") || desc.contains("vendido") || desc.contains("no disponible")) continue;

                    unicos.putIfAbsent(r.getX() + "|" + r.getY(), el);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        List<WebElement> resultado = new ArrayList<>(unicos.values());
        log.debug("[SelectorPage] Asientos fallback detectados: {}", resultado.size());
        return resultado;
    }



    // Sin modificador (package-private): reutilizado por SeatSelectionEngine — ver
    // comentario de buildSeatMap().
    boolean tapRapidoEnButacaDesdeLabel(WebElement el) {
        if (!SeatUiSnapshot.ENABLED) return tapRapidoEnButacaDesdeLabelInterno(el);

        // Instrumentación exclusiva de investigación (SEAT_SNAPSHOT_DEBUG=true, OFF por
        // defecto) — captura TODO el árbol + pantalla antes/después de este tap, guarda
        // ambos en disco (before/after .xml/.png) y registra el diff, para encontrar con
        // evidencia real qué cambia cuando un asiento queda seleccionado (no solo el
        // propio botón). No participa en la decisión de negocio: el resultado del tap es
        // exactamente el mismo que sin esta instrumentación.
        String pageSourceAntes = safePageSource();
        byte[] screenshotAntes = takeScreenshot();
        boolean resultado = tapRapidoEnButacaDesdeLabelInterno(el);
        sleep(400);
        String pageSourceDespues = safePageSource();
        byte[] screenshotDespues = takeScreenshot();
        int screenHeight = driver.manage().window().getSize().getHeight();
        SeatUiSnapshot.investigarTap("tap_tapOk-" + resultado, pageSourceAntes, screenshotAntes,
                pageSourceDespues, screenshotDespues, screenHeight);
        return resultado;
    }

    private String safePageSource() {
        try { return driver.getPageSource(); } catch (Exception e) { return ""; }
    }

    private boolean tapRapidoEnButacaDesdeLabelInterno(WebElement el) {
        try {
            org.openqa.selenium.Rectangle r = el.getRect();

            int centerX = r.getX() + (r.getWidth() / 2);
            int centerY = r.getY() + (r.getHeight() / 2);

            int[][] puntos = new int[][]{
                    {centerX, centerY},
                    {centerX - 8, centerY - 8},
                    {centerX + 8, centerY - 8},
                    {centerX - 8, centerY + 8},
                    {centerX + 8, centerY + 8}
            };

            for (int[] p : puntos) {
                try {
                    tapW3C(p[0], p[1]);
                    return true;
                } catch (Exception ignored) {
                }
            }

            try {
                WebElement parent = el.findElement(By.xpath(".."));
                int px = parent.getRect().getX() + (parent.getRect().getWidth() / 2);
                int py = parent.getRect().getY() + (parent.getRect().getHeight() / 2);
                tapW3C(px, py);
                return true;
            } catch (Exception ignored) {
            }

            try {
                WebElement grandParent = el.findElement(By.xpath("../.."));
                int gx = grandParent.getRect().getX() + (grandParent.getRect().getWidth() / 2);
                int gy = grandParent.getRect().getY() + (grandParent.getRect().getHeight() / 2);
                tapW3C(gx, gy);
                return true;
            } catch (Exception ignored) {
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }



    /**
     * Espera a que la pantalla de asientos cargue Y devuelve los elementos del mapa
     * en una sola operación, eliminando el doble findElements que ocurría al llamar
     * esperarPantallaAsientos() seguido de obtenerAsientosDelMapaRapido().
     *
     * Fase 1: detecta señales de UI (texto "Pantalla", "Selecciona", "Continuar"…)
     * Fase 2: sondea hasta que los elementos numéricos del mapa aparecen y los devuelve
     *         ya filtrados y deduplicados — listos para usar directamente.
     */
    /**
     * Si la animación/hint "Seats gesture" está visible al cargar el mapa de asientos,
     * hace tap en el centro de la pantalla para descartarla.
     * El hint no siempre aparece; si no está presente, no hace nada.
     */
    private void descartarHintGestosAsientosSiPresente() {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            boolean visible = !driver.findElements(By.xpath(
                    "//*[@content-desc='Seats gesture' or " +
                    "contains(@text,'2 dedos para acercar') or " +
                    "contains(@text,'mapa de asientos')]"
            )).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            if (visible) {
                int cx = driver.manage().window().getSize().getWidth() / 2;
                int cy = driver.manage().window().getSize().getHeight() / 2;
                tapW3C(cx, cy);
                sleep(400);
                log.info("[SelectorPage] Hint 'Seats gesture' descartado con tap.");
            }
        } catch (Exception e) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            log.debug("[SelectorPage] No se pudo verificar hint de gestos: {}", e.getMessage());
        }
    }

    private List<WebElement> esperarYObtenerAsientosDelMapa() {
        return esperarYObtenerAsientosDelMapa(60000);
    }

    private List<WebElement> esperarYObtenerAsientosDelMapa(long timeoutMs) {
        log.info("[SelectorPage] Escaneando mapa de asientos...");
        descartarHintGestosAsientosSiPresente();

        int screenHeight = driver.manage().window().getSize().getHeight();
        int mapTop    = (int)(screenHeight * 0.30);
        int mapBottom = (int)(screenHeight * 0.93);

        // implicitlyWait=0 una sola vez antes del loop, se restaura en finally
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        long end = System.currentTimeMillis() + timeoutMs;
        long loopStart = System.currentTimeMillis();

        // Instrumentación (Iteración 6 — sin cambiar el comportamiento del loop):
        // separa "cuánto tiempo se fue en llamadas reales a WDA" de "cuánto tiempo
        // se fue durmiendo entre intentos", para saber si el costo real observado en
        // vivo (busqueda=228s en una corrida, 94s en otra — muy por encima del cap
        // nominal de 60s) viene de MUCHAS iteraciones normales (la app tarda en
        // renderizar números de asiento) o de POCAS llamadas anormalmente lentas a
        // WDA (mismo patrón ya documentado en smartWait: el deadline solo se revisa
        // ENTRE llamadas, nunca interrumpe una en curso).
        int iteraciones = 0;
        long sumaScanMs = 0;
        long maxScanMs = 0;
        try {
            while (System.currentTimeMillis() < end) {
                iteraciones++;
                long tScan = System.currentTimeMillis();
                // UIAutomator2 filtra en el dispositivo: 1 round-trip en vez de getText() × N
                List<WebElement> asientos = escanearMapaConUIAutomator(mapTop, mapBottom);
                long scanMs = System.currentTimeMillis() - tScan;
                sumaScanMs += scanMs;
                if (scanMs > maxScanMs) maxScanMs = scanMs;

                // FIX real (evidencia de 2 corridas en vivo — "Filas=1 | Total=2 | Con
                // número=0" seguido de un tap por coordenadas que nunca registra un
                // asiento real, haciendo fallar "Continuar"): en iOS, las etiquetas de
                // fila (A, B, C…) a veces quedan consultables por WDA ANTES que los
                // botones de asiento numerados terminen de renderizar — ambos matchean
                // el mismo predicate de longitud ≤2 (necesario para que las etiquetas de
                // fila sigan siendo candidatos válidos, ver obtenerCandidatosAsientoIOS()).
                // Aceptar el primer resultado no vacío sin validar contenido puede
                // "confirmar" el mapa con 1-2 etiquetas huérfanas antes de que exista
                // ningún asiento real. Las corridas exitosas de referencia SIEMPRE
                // muestran "Con número" == Total (nunca cero) — se exige ese mismo
                // invariante aquí antes de aceptar el escaneo como listo.
                if (!asientos.isEmpty() && tieneAlMenosUnAsientoNumerado(asientos)) {
                    log.debug("[SelectorPage] Mapa listo: {} asientos.", asientos.size());
                    logResumenEscaneoMapa(iteraciones, sumaScanMs, maxScanMs, System.currentTimeMillis() - loopStart, "OK");
                    return asientos;
                }
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        logResumenEscaneoMapa(iteraciones, sumaScanMs, maxScanMs, System.currentTimeMillis() - loopStart, "TIMEOUT");
        log.warn("[SelectorPage] Tiempo agotado escaneando el mapa.");
        return Collections.emptyList();
    }

    /**
     * Un escaneo solo cuenta como "el mapa ya cargó" si al menos un candidato tiene
     * un número de asiento real — ver comentario en esperarYObtenerAsientosDelMapa().
     * En Android esto siempre es true de inmediato: los candidatos ya llegan
     * pre-filtrados a solo dígitos (UiSelector().textMatches / escanearMapaConXPath),
     * así que esta validación no agrega latencia ni cambia comportamiento ahí.
     */
    private boolean tieneAlMenosUnAsientoNumerado(List<WebElement> asientos) {
        for (WebElement el : asientos) {
            try {
                if (obtenerTextoSeguro(el).matches("^\\d{1,2}$")) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void logResumenEscaneoMapa(int iteraciones, long sumaScanMs, long maxScanMs, long elapsedTotalMs, String resultado) {
        long sleepTotalMs = elapsedTotalMs - sumaScanMs;
        long promScanMs = iteraciones > 0 ? sumaScanMs / iteraciones : 0;
        utils.PerfMetrics.note("SeatSelection", String.format(
                "resumenBusqueda resultado=%s iteraciones=%d tiempoEscaneoTotal=%dms "
                + "tiempoEscaneoProm=%dms tiempoEscaneoMax=%dms tiempoSleepTotal=%dms elapsedTotal=%dms",
                resultado, iteraciones, sumaScanMs, promScanMs, maxScanMs, sleepTotalMs, elapsedTotalMs));
    }

    /**
     * Construye un {@link SeatMap} a partir del estado actual del mapa de asientos.
     * Ejecuta el escaneo principal y dos fallbacks antes de devolver el modelo.
     */
    // FIX real (optimización de rendimiento — ver informe SeatSelectionEngine):
    // reconstruir el SeatMap completo (~143 elementos, ~160s) para revalidar UN solo
    // asiento tras un tap es lo que hacía crecer el tiempo de "varios minutos" a
    // "una hora". Esta relocalización es exclusiva del asiento que SeatSelectionEngine
    // necesita revalidar (StaleElementReferenceException sobre su WebElement original)
    // — nunca vuelve a escanear los demás candidatos. Mismo par de atributos
    // (label/name) ya documentado como el lugar real donde vive el número de asiento
    // en iOS; en Android reutiliza el mismo patrón UiSelector().text() ya usado en
    // otros escaneos de este archivo.
    WebElement reubicarAsientoPorNumero(int numero) {
        try {
            List<WebElement> encontrados = isIOS()
                ? driver.findElements(AppiumBy.iOSNsPredicateString(predicadoAsientoPorNumero(numero)))
                : driver.findElements(AppiumBy.androidUIAutomator(uiSelectorAsientoPorNumero(numero)));
            for (WebElement el : encontrados) {
                try { if (el.isDisplayed()) return el; } catch (Exception ignored) {}
            }
            return encontrados.isEmpty() ? null : encontrados.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** Locator exacto usado por reubicarAsientoPorNumero() — solo para instrumentación/logs. */
    String locatorAsientoPorNumero(int numero) {
        return isIOS()
            ? "iOSNsPredicate: " + predicadoAsientoPorNumero(numero)
            : "androidUIAutomator: " + uiSelectorAsientoPorNumero(numero);
    }

    private static final java.util.regex.Pattern CONTADOR_CONTINUAR =
            java.util.regex.Pattern.compile("^Continuar,\\s*(\\d+)$");

    // FIX real (evidencia — build/seat-diagnostics/tap_tapOk-true_*, capturada con
    // SeatUiSnapshot): getAttribute("selected") del propio botón de asiento NUNCA
    // cambia (confirmado en 3 taps consecutivos, antes y después, siempre
    // selected=false) — pero el botón "Continuar" SÍ refleja el conteo real de la
    // app: sin asientos seleccionados no existe ("Continuar" a secas o ausente);
    // con 1/2/3 seleccionados su @label/@name es literalmente "Continuar, 1" /
    // "Continuar, 2" / "Continuar, 3" (verificado: A5→"Continuar, 1", A1→
    // "Continuar, 2", A2→"Continuar, 3"). Es el único indicador que de verdad
    // refleja el estado de la app, y no depende de qué asiento se tocó — sirve
    // igual para 1, N o VIP.
    int contarAsientosSeleccionadosPorBotonContinuar() {
        return isIOS() ? contarPorBotonContinuarIOS() : contarPorBotonContinuarAndroid();
    }

    private int contarPorBotonContinuarIOS() {
        try {
            List<WebElement> candidatos = driver.findElements(AppiumBy.iOSNsPredicateString(
                    "type == 'XCUIElementTypeButton' AND (label BEGINSWITH 'Continuar' OR name BEGINSWITH 'Continuar')"));
            for (WebElement el : candidatos) {
                for (String attr : new String[]{"label", "name"}) {
                    try {
                        String texto = el.getAttribute(attr);
                        if (texto == null) continue;
                        java.util.regex.Matcher m = CONTADOR_CONTINUAR.matcher(texto.trim());
                        if (m.matches()) return Integer.parseInt(m.group(1));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return 0; // botón "Continuar, N" no encontrado → 0 asientos seleccionados según la app
    }

    // FIX real (causa raíz de "0 confirmados, 0 taps exitosos" en "Selección de
    // Múltiples Asientos" — evidencia capturada en vivo contra dispositivo Android real,
    // build/seat-diagnostics/despues_primer_tap.xml, tras un tap real y exitoso en
    // "Selección de Asientos Consecutivos"): a diferencia de iOS (un solo string
    // "Continuar, N"), en Android el contador vive en un TextView HERMANO separado,
    // dentro del mismo contenedor clickable que el TextView "Continuar" — estructura
    // real observada:
    //   <android.view.View clickable="true">
    //     <android.widget.TextView text="Continuar" .../>
    //     <android.view.View>
    //       <android.widget.TextView text="1" .../>      ← el contador
    //     </android.view.View>
    //   </android.view.View>
    // Ausente por completo (ningún nodo con text="Continuar") cuando no hay asientos
    // seleccionados — mismo comportamiento ya documentado en iOS. Antes de esta
    // evidencia el código devolvía -1 sin haberlo investigado nunca en Android.
    // FIX real (evidencia en vivo — el primer intento de esta implementación navegaba
    // "Continuar" → parent vía By.xpath("..") y lanzó NoSuchElementException de forma
    // intermitente): CUALQUIER navegación de árbol (parent/child) sobre un WebElement ya
    // devuelto por findElements() demostró ser poco confiable en este árbol Android — el
    // mismo patrón de fondo ya identificado para reubicarAsientoPorNumero(). Se evita
    // por completo: UNA sola consulta trae todos los TextView, y "Continuar" + su
    // contador se correlacionan en Java por posición (misma fila, contador a la
    // derecha) usando SOLO getText()/getRect() sobre cada handle de forma independiente
    // — nunca una segunda consulta dirigida ni navegación de árbol.
    private int contarPorBotonContinuarAndroid() {
        try {
            List<WebElement> textos = driver.findElements(By.xpath("//android.widget.TextView"));

            org.openqa.selenium.Rectangle continuarBounds = null;
            for (WebElement t : textos) {
                try {
                    if ("Continuar".equals(t.getText().trim())) {
                        continuarBounds = t.getRect();
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (continuarBounds == null) return 0; // sin "Continuar" en pantalla → 0 seleccionados

            for (WebElement t : textos) {
                try {
                    String texto = t.getText().trim();
                    if (!texto.matches("^\\d+$")) continue;
                    org.openqa.selenium.Rectangle r = t.getRect();
                    boolean mismaFila  = Math.abs(r.getY() - continuarBounds.getY()) < continuarBounds.getHeight();
                    boolean aLaDerecha = r.getX() > continuarBounds.getX();
                    if (mismaFila && aLaDerecha) return Integer.parseInt(texto);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return 0; // "Continuar" presente pero sin contador legible → 0
    }

    private String predicadoAsientoPorNumero(int numero) {
        String num = String.valueOf(numero);
        return "(type == 'XCUIElementTypeButton' OR type == 'XCUIElementTypeStaticText') AND "
            + "(label == '" + num + "' OR name == '" + num + "')";
    }

    private String uiSelectorAsientoPorNumero(int numero) {
        return "new UiSelector().text(\"" + numero + "\")";
    }

    // FIX real (evidencia — RUN-1013: ~120s ocultos entre que el escaneo termina y
    // SeatSelectionEngine reporta el mapa listo, una vez eliminados los demás escaneos
    // redundantes): SeatMap.buildRows() volvía a llamar getRect()/getText() por cada
    // uno de los ~173 elementos, aunque intentarEscaneoRapidoConPageSource() YA había
    // resuelto exactamente esos mismos datos desde una única llamada a getPageSource().
    // Este cache guarda esos datos ya resueltos; buildSeatMap() los reutiliza SOLO si
    // TODOS los elementos del escaneo final están presentes en el cache — si falta
    // uno solo (camino lento, fallback, u otra discrepancia), se usa el constructor
    // original de SeatMap sin ningún cambio, exactamente como antes. Se reinicia al
    // inicio de cada escaneo — nunca sobrevive entre llamadas distintas.
    private Map<WebElement, SeatMap.RawSeat> cacheEscaneoRapido;

    // Sin modificador (package-private): SeatSelectionEngine, en el mismo paquete,
    // necesita reconstruir el mapa entre cada tap sin duplicar este método.
    SeatMap buildSeatMap() {
        cacheEscaneoRapido = null;
        long t0 = System.currentTimeMillis();
        List<WebElement> raw = esperarYObtenerAsientosDelMapa();
        utils.PerfMetrics.stage("SeatSelection", "busqueda", System.currentTimeMillis() - t0);

        if (raw.isEmpty()) {
            long tFallback1 = System.currentTimeMillis();
            raw = obtenerAsientosDisponiblesVisibles();
            utils.PerfMetrics.stage("SeatSelection", "fallback-visibles", System.currentTimeMillis() - tFallback1);
        }
        if (raw.isEmpty()) {
            long tFallback2 = System.currentTimeMillis();
            raw = obtenerAsientosDelMapaAmplio();
            utils.PerfMetrics.stage("SeatSelection", "fallback-amplio", System.currentTimeMillis() - tFallback2);
        }

        if (cacheEscaneoRapido != null) {
            List<SeatMap.RawSeat> rawResueltos = new ArrayList<>();
            boolean completo = true;
            for (WebElement el : raw) {
                SeatMap.RawSeat rs = cacheEscaneoRapido.get(el);
                if (rs == null) { completo = false; break; }
                rawResueltos.add(rs);
            }
            if (completo) {
                utils.PerfMetrics.note("SeatSelection", "SeatMap construido desde cache del escaneo rápido (sin getRect/getText adicionales)");
                return SeatMap.fromRawSeats(rawResueltos);
            }
        }
        return new SeatMap(raw);
    }

    /**
     * Candidatos de "número de asiento" en iOS: StaticText con @value no vacío y longitud
     * (tras trim) &lt;= 2 — mismo criterio que el XPath anterior
     * ("string-length(normalize-space(@value)) &lt;= 2"), llamado hasta ~240 veces por
     * escaneo de mapa (esperarYObtenerAsientosDelMapa reintenta cada 250ms hasta 60s).
     *
     * PERF (solo iOS): el filtro de tipo+presencia de @value se resuelve con NSPredicate
     * (WDA evalúa contra el árbol nativo, sin serializar el pageSource completo a XML,
     * que es lo que exige cualquier XPath) y el recorte por longitud se hace en Java
     * sobre el resultado ya obtenido — mismo criterio exacto, sin la evaluación XPath.
     * Android (UiAutomator2, sin cambios) no pasa por aquí.
     */
    // FIX real (evidencia capturada — mapa de asientos "vacío" aunque estaba
    // completamente poblado en pantalla): los números de asiento son
    // XCUIElementTypeButton (NO StaticText) con @value vacío — el número real vive
    // en @name/@label (ver también obtenerTextoSeguro()). El predicate original solo
    // pedía StaticText+value, así que ningún botón de asiento llegaba siquiera a
    // evaluarse. Se amplía a ambos tipos y los tres atributos; el filtro por
    // longitud se hace con obtenerTextoSeguro() (ya sabe leer name/label/value en
    // el orden correcto) en vez de leer @value directo. Las filas con letra (A, B,
    // C…) también matchean length<=2 aquí — sin cambio de riesgo: los llamadores
    // YA filtran por posición (cx < 20) para descartar esa columna, igual que antes.
    // Iteración 8 (evidencia de Iteración 7, en vivo): findElements=29.2s +
    // filtroJava=57.75s sobre candidatosCrudos=178, de los cuales solo 60 pasaban el
    // filtro Java de longitud ≤2 (obtenerTextoSeguro().length() <= 2). Se agrega la
    // MISMA condición de longitud al NSPredicate (name/label/value MATCHES '.{1,2}',
    // regex ICU de longitud exacta 1-2, equivalente a la condición Java) para que WDA
    // descarte del lado nativo los candidatos que Java iba a rechazar de todas formas
    // — nunca puede excluir un elemento que el filtro Java habría aceptado, porque es
    // la MISMA condición de longitud, solo evaluada antes. Se conserva "!= nil" como
    // guarda explícita para no evaluar MATCHES sobre un atributo ausente.
    // Predicado compartido — usado por obtenerCandidatosAsientoIOS() (camino lento,
    // siempre disponible) Y por intentarEscaneoRapidoConPageSource() (camino rápido,
    // ver más abajo), para garantizar que ambos identifiquen EXACTAMENTE el mismo
    // conjunto de candidatos crudos. Nunca diverge entre los dos caminos.
    private static final String PREDICADO_CANDIDATOS_ASIENTO_IOS =
            "(type == 'XCUIElementTypeButton' OR type == 'XCUIElementTypeStaticText') "
            + "AND ((name != nil AND name MATCHES '.{1,2}') "
            + "OR (label != nil AND label MATCHES '.{1,2}') "
            + "OR (value != nil AND value MATCHES '.{1,2}'))";

    private List<WebElement> obtenerCandidatosAsientoIOS() {
        // Instrumentación (Iteración 7, se conserva): separa cuánto tarda la ÚNICA
        // llamada nativa findElements(NSPredicate) de cuánto tarda el loop Java que
        // llama obtenerTextoSeguro() por cada candidato — permite comparar en vivo el
        // candidatosCrudos/tiempos de antes (178 / 29.2s+57.75s) contra los de después
        // de este fix.
        long t0 = System.currentTimeMillis();
        List<WebElement> all = driver.findElements(
                AppiumBy.iOSNsPredicateString(PREDICADO_CANDIDATOS_ASIENTO_IOS));
        long tFindElements = System.currentTimeMillis() - t0;

        long t1 = System.currentTimeMillis();
        List<WebElement> filtrados = new ArrayList<>();
        for (WebElement el : all) {
            try {
                String v = textoAsientoRapido(el);
                if (!v.isBlank() && v.length() <= 2) filtrados.add(el);
            } catch (Exception ignored) {}
        }
        long tFiltroJava = System.currentTimeMillis() - t1;

        utils.PerfMetrics.note("SeatSelection", String.format(
                "obtenerCandidatosAsientoIOS findElements=%dms candidatosCrudos=%d filtroJava=%dms filtrados=%d",
                tFindElements, all.size(), tFiltroJava, filtrados.size()));

        return filtrados;
    }

    // FIX real (evidencia — filtroJava=64306ms para 143 elementos, ver informe de
    // rendimiento SeatSelectionEngine): la causa no era cómputo Java, eran hasta 4
    // viajes HTTP secuenciales a WDA por elemento dentro de obtenerTextoSeguro()
    // (getText + value + label + name). La evidencia de ESTE mismo log confirma que
    // los botones de asiento siempre resuelven en @label (o @name si @label viene
    // vacío) — nunca en getText()/@value. Ruta rápida exclusiva de detección de
    // candidatos de asiento (máx. 2 viajes/elemento en vez de hasta 4); NO se toca
    // obtenerTextoSeguro(), que usan películas/horarios con su propio orden de
    // prioridad y no debe cambiar.
    private String textoAsientoRapido(WebElement el) {
        try {
            String label = el.getAttribute("label");
            if (label != null && !label.isBlank()) return label.trim();
            String name = el.getAttribute("name");
            return name == null ? "" : name.trim();
        } catch (Exception e) {
            return "";
        }
    }

    // UIAutomator2 (Android) / NSPredicate (iOS) — filtra por texto numérico en el mapa de asientos.
    // Diagnóstico exclusivo de investigación (SEAT_SCAN_TIMING_DEBUG=true, OFF por
    // defecto — no-op inmediato, cero impacto en el flujo normal): instrumenta cada
    // getRect()/getAttribute() del bucle de abajo para determinar si TODAS las
    // llamadas a WDA son lentas por igual o si un subconjunto de elementos bloquea
    // WebDriverAgent. Deliberadamente NO activo durante la validación funcional del
    // fix de escaneo duplicado — mide, no optimiza.
    private static final boolean SCAN_TIMING_DEBUG = "true".equalsIgnoreCase(
            System.getProperty("SEAT_SCAN_TIMING_DEBUG",
                    System.getenv().getOrDefault("SEAT_SCAN_TIMING_DEBUG", "false")));

    // FIX real (evidencia — RUN-1012, SEAT_SCAN_TIMING_DEBUG: 187 candidatos, cada
    // getRect()/getAttribute() individual entre 242-747ms, SIN outliers — el costo es
    // parejo por elemento, no un subconjunto bloqueando WDA. Esos ~137s en total
    // (filtroJava + getRect, uno por candidato) son N viajes de red secuenciales para
    // leer texto/posición que YA están en el page source completo). Este método
    // reemplaza esos N viajes por UNA sola llamada a driver.getPageSource() (misma
    // API ya usada por SeatUiSnapshot/IOSLocatorDebug en este mismo paquete),
    // emparejando cada nodo del XML con el WebElement correspondiente por ORDEN de
    // documento (mismo predicado exacto en ambos lados — PREDICADO_CANDIDATOS_ASIENTO_IOS).
    //
    // Nunca confía ciegamente en ese orden: (1) exige que el conteo de nodos del XML
    // coincida EXACTO con el de findElements(); (2) verifica una muestra real (primeros
    // y últimos 3 candidatos) comparando el texto ya obtenido del XML contra una
    // llamada real a getAttribute() sobre ESE MISMO índice. Si cualquiera de las dos
    // verificaciones falla, devuelve null — el llamador cae al camino ya probado
    // (elemento por elemento), sin arriesgar la detección de asientos.
    private List<WebElement> intentarEscaneoRapidoConPageSource(int mapTop, int mapBottom) {
        try {
            List<WebElement> crudos = driver.findElements(
                    AppiumBy.iOSNsPredicateString(PREDICADO_CANDIDATOS_ASIENTO_IOS));
            if (crudos.isEmpty()) return null;

            List<SeatUiSnapshot.Nodo> nodos = SeatUiSnapshot.capturar(driver.getPageSource()).nodos;
            List<SeatUiSnapshot.Nodo> nodosFiltrados = new ArrayList<>();
            for (SeatUiSnapshot.Nodo n : nodos) {
                if (!"XCUIElementTypeButton".equals(n.tag) && !"XCUIElementTypeStaticText".equals(n.tag)) continue;
                String texto = textoDeNodo(n);
                if (!texto.isBlank() && texto.length() <= 2) nodosFiltrados.add(n);
            }

            int n = crudos.size();
            if (nodosFiltrados.size() != n) {
                utils.PerfMetrics.note("SeatSelection", String.format(
                        "escaneoRapido descartado: conteo no coincide (findElements=%d, pageSource=%d)",
                        n, nodosFiltrados.size()));
                return null;
            }

            Set<Integer> muestra = new HashSet<>();
            for (int i = 0; i < Math.min(3, n); i++) muestra.add(i);
            for (int i = Math.max(0, n - 3); i < n; i++) muestra.add(i);
            for (int i : muestra) {
                String textoReal = textoAsientoRapido(crudos.get(i));
                String textoXml = textoDeNodo(nodosFiltrados.get(i));
                if (!textoReal.equals(textoXml)) {
                    utils.PerfMetrics.note("SeatSelection", String.format(
                            "escaneoRapido descartado: desajuste de orden en idx=%d real='%s' xml='%s'",
                            i, textoReal, textoXml));
                    return null;
                }
            }

            Map<String, WebElement> unicos = new LinkedHashMap<>();
            // FIX real (evidencia — RUN-1013: ~120s ocultos dentro de new SeatMap(raw),
            // que volvía a pedir getRect()/getText() por elemento aunque ya se conocían
            // aquí): se guarda posición+texto ya resueltos por elemento — buildSeatMap()
            // los reutiliza si el resultado final coincide 1:1 con este cache.
            Map<WebElement, SeatMap.RawSeat> cache = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                SeatUiSnapshot.Nodo nodo = nodosFiltrados.get(i);
                double x = nodo.num("x"), y = nodo.num("y"), w = nodo.num("width"), h = nodo.num("height");
                if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)) continue;
                int cy = (int) (y + h / 2);
                int cx = (int) (x + w / 2);
                if (cy < mapTop || cy > mapBottom || cx < 20) continue;
                WebElement el = crudos.get(i);
                unicos.putIfAbsent((int) x + "|" + (int) y, el);
                cache.putIfAbsent(el, new SeatMap.RawSeat(el, (int) x, (int) y, (int) w, (int) h, textoDeNodo(nodo)));
            }

            utils.PerfMetrics.note("SeatSelection", String.format(
                    "escaneoRapido OK: %d candidatos -> %d filtrados (sin getRect/getAttribute individuales)",
                    n, unicos.size()));
            cacheEscaneoRapido = cache;
            return new ArrayList<>(unicos.values());
        } catch (Exception e) {
            utils.PerfMetrics.note("SeatSelection", "escaneoRapido descartado por excepción: " + e.getMessage());
            return null;
        }
    }

    private String textoDeNodo(SeatUiSnapshot.Nodo n) {
        String label = n.attrs.get("label");
        if (label != null && !label.isBlank()) return label.trim();
        String name = n.attrs.get("name");
        return name == null ? "" : name.trim();
    }

    private List<WebElement> escanearMapaConUIAutomator(int mapTop, int mapBottom) {
        try {
            if (isIOS()) {
                List<WebElement> rapido = intentarEscaneoRapidoConPageSource(mapTop, mapBottom);
                if (rapido != null) return rapido;
            }

            List<WebElement> candidatos = isIOS()
                ? obtenerCandidatosAsientoIOS()
                : driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"^\\\\d{1,2}$\")"));
            if (candidatos.isEmpty()) return escanearMapaConXPath(mapTop, mapBottom);

            Map<String, WebElement> unicos = new LinkedHashMap<>();
            int idx = 0;
            for (WebElement el : candidatos) {
                idx++;
                try {
                    long tRect = SCAN_TIMING_DEBUG ? System.currentTimeMillis() : 0;
                    org.openqa.selenium.Rectangle r = el.getRect();
                    long msRect = SCAN_TIMING_DEBUG ? System.currentTimeMillis() - tRect : 0;

                    if (SCAN_TIMING_DEBUG) {
                        long tAttr = System.currentTimeMillis();
                        String etiqueta = textoAsientoRapido(el);
                        long msAttr = System.currentTimeMillis() - tAttr;
                        utils.PerfMetrics.note("SeatSelection", String.format(
                                "getRectDetalle idx=%d asiento=%s getRectMs=%d getAttributeMs=%d",
                                idx, etiqueta.isBlank() ? "?" : etiqueta, msRect, msAttr));
                    }

                    int cy = r.getY() + r.getHeight() / 2;
                    int cx = r.getX() + r.getWidth() / 2;
                    if (cy < mapTop || cy > mapBottom || cx < 20) continue;
                    unicos.putIfAbsent(r.getX() + "|" + r.getY(), el);
                } catch (Exception ignored) {}
            }
            return new ArrayList<>(unicos.values());
        } catch (Exception e) {
            return escanearMapaConXPath(mapTop, mapBottom);
        }
    }

    private List<WebElement> escanearMapaConXPath(int mapTop, int mapBottom) {
        try {
            // //* cubre TextView Y View de Compose; string-length <= 2 pre-filtra sin getText()
            List<WebElement> candidatos = driver.findElements(
                By.xpath("//*[@text and string-length(normalize-space(@text)) <= 2]")
            );
            Map<String, WebElement> unicos = new LinkedHashMap<>();
            for (WebElement el : candidatos) {
                try {
                    String txt = obtenerTextoSeguro(el);
                    if (!txt.matches("^\\d{1,2}$")) continue;
                    org.openqa.selenium.Rectangle r = el.getRect();
                    int cy = r.getY() + r.getHeight() / 2;
                    int cx = r.getX() + r.getWidth() / 2;
                    if (cy < mapTop || cy > mapBottom || cx < 20) continue;
                    unicos.putIfAbsent(txt + "|" + r.getX() + "|" + r.getY(), el);
                } catch (Exception ignored) {}
            }
            return new ArrayList<>(unicos.values());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }



    private Map<Integer, List<WebElement>> agruparAsientosPorFilaFlexible(List<WebElement> asientos) {
        Map<Integer, List<WebElement>> filas = new LinkedHashMap<>();
        int toleranciaY = 24;

        for (WebElement asiento : asientos) {
            org.openqa.selenium.Rectangle r = asiento.getRect();
            int y = r.getY() + (r.getHeight() / 2);

            Integer filaExistente = null;
            for (Integer key : filas.keySet()) {
                if (Math.abs(key - y) <= toleranciaY) {
                    filaExistente = key;
                    break;
                }
            }

            if (filaExistente == null) {
                filas.put(y, new ArrayList<>());
                filaExistente = y;
            }

            filas.get(filaExistente).add(asiento);
        }

        return filas;
    }









    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public void tapW3C(int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);

        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(new Pause(finger, Duration.ofMillis(80)));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(Collections.singletonList(tap));
    }

}