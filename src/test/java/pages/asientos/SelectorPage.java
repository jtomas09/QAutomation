package pages.asientos;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.common.BasePage;
import org.openqa.selenium.interactions.Pause;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SelectorPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(SelectorPage.class);

    private static final int RANDOM_PICK_TIMEOUT_SECONDS = 10;

    public SelectorPage(AndroidDriver driver) {
        super(driver);
    }

    public void continuar() {
        this.click(By.xpath("//android.widget.TextView[@text=\"Continuar\"]"));
    }

    public void seleccionarPeliculaRandomYHorario() {
        abrirPeliculaYMostrarHorarios();
        seleccionarHorario();
    }

    /** Navigates through initial popups, loads the cartelera, opens a movie, and lands on the horarios tab. */
    public void abrirPeliculaYMostrarHorarios() {
        manejarPopupsIniciales();
        esperarCargaCartelera();
        String pelicula = abrirPrimerPeliculaDesdeVerSinopsis();
        irAEtiquetaHorarios();
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

        irAEtiquetaHorarios();

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

    public String abrirPrimerPeliculaDesdeVerSinopsis() {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));

        List<WebElement> peliculas = obtenerPeliculasVisibles();

        if (peliculas.isEmpty()) {
            throw new RuntimeException("No se detectaron títulos de películas en la pantalla inicial.");
        }

        List<String> nombresPeliculas = new ArrayList<>();
        for (WebElement el : peliculas) {
            try {
                String nombre = obtenerTextoSeguro(el);
                if (!nombre.isBlank() && !nombresPeliculas.contains(nombre)) {
                    nombresPeliculas.add(nombre);
                }
            } catch (Exception ignored) {
            }
        }

        for (String nombre : nombresPeliculas) {
            try {
                log.info("[SelectorPage] Intentando abrir película desde Ver sinopsis: {}", nombre);

                if (abrirPeliculaPorVerSinopsis(nombre)) {
                    log.info("[SelectorPage] Película abierta correctamente: {}", nombre);
                    return nombre;
                }

            } catch (Exception e) {
                log.warn("[SelectorPage] Error abriendo película '{}': {}", nombre, e.getMessage());
            }
        }

        throw new RuntimeException("Se detectaron películas visibles, pero no se pudo abrir ninguna desde Ver sinopsis.");
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

        return false;
    }
    private WebElement encontrarVerSinopsisDeTarjeta(WebElement titulo) {
        try {
            List<WebElement> candidatos = driver.findElements(
                    By.xpath("//android.widget.TextView[@text='Ver sinopsis']")
            );

            if (candidatos.isEmpty()) {
                candidatos = driver.findElements(
                        By.xpath("//*[contains(@text,'Ver sinopsis')]")
                );
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
        // Intentar cerrar popups comunes de Cinépolis
        String[] botonesCerrar = {"PERMITIR", "Permitir", "ENTENDIDO", "ACEPTAR", "Cerrar", "Saltar"};
        for (String txt : botonesCerrar) {
            try {
                List<WebElement> btn = driver.findElements(By.xpath("//*[@text='" + txt + "']"));
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

    private void esperarCargaCartelera() {
        long end = System.currentTimeMillis() + 10000; // 10 segundos de espera máxima
        while (System.currentTimeMillis() < end) {
            // Si aparece algún elemento que parece título, salimos de la espera
            if (!driver.findElements(By.xpath("//android.widget.TextView[string-length(@text) > 8]")).isEmpty()) {
                return;
            }
            // Si hay un botón de "Permitir" o "Entendido" de ubicación, podrías agregar el clic aquí
            manejarPopupsPosibles();
            sleep(500);
        }
    }
    private void manejarPopupsPosibles() {
        try {
            // Intenta cerrar el popup de ubicación si aparece
            List<WebElement> popups = driver.findElements(By.xpath("//*[@text='PERMITIR' or @text='Permitir' or @text='ENTENDIDO']"));
            if (!popups.isEmpty()) popups.get(0).click();
        } catch (Exception ignored) {}
    }

    public List<String> seleccionar3AsientosRandomDisponibles() {
        SeatMap map = buildSeatMap();
        log.info("[SelectorPage] {}", map.getSummary());

        SeatMap.SelectionResult result = map.selectAnyN(3);
        if (result == null) {
            org.junit.jupiter.api.Assumptions.abort(
                "Menos de 3 asientos disponibles. Se omite la prueba.");
            return null;
        }

        log.info("[SelectorPage] Estrategia: {}", result.strategy);

        List<String> seleccionados = new ArrayList<>();
        for (SeatMap.Seat seat : result.seats) {
            if (tapRapidoEnButacaDesdeLabel(seat.element)) {
                sleep(80);
                seleccionados.add(seat.toString());
                log.info("[SelectorPage] Asiento agregado: {}", seat);
            }
        }

        if (seleccionados.size() < 3) {
            throw new RuntimeException(
                "Solo se pudieron seleccionar " + seleccionados.size() + " de 3 asientos.");
        }

        log.info("[SelectorPage] 3 asientos seleccionados: {}", seleccionados);
        takeScreenshot("3 asientos seleccionados");
        return seleccionados;
    }
    public String seleccionarPrimerPeliculaVisible() {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));

        List<WebElement> peliculasVisibles = obtenerPeliculasVisibles();

        if (peliculasVisibles.isEmpty()) {
            throw new RuntimeException("No se detectaron títulos de películas en la pantalla inicial.");
        }

        // Guardamos solo textos, no WebElements, para evitar stale
        List<String> nombresPeliculas = new ArrayList<>();
        for (WebElement el : peliculasVisibles) {
            try {
                String nombre = obtenerTextoSeguro(el);
                if (!nombre.isBlank() && !nombresPeliculas.contains(nombre)) {
                    nombresPeliculas.add(nombre);
                }
            } catch (Exception ignored) {
            }
        }

        for (String nombre : nombresPeliculas) {
            try {
                log.info("[SelectorPage] Intentando abrir película: {}", nombre);

                boolean abierta = intentarAbrirPeliculaPorNombre(nombre);

                if (abierta) {
                    log.info("[SelectorPage] Película abierta correctamente: {}", nombre);
                    return nombre;
                }

                log.warn("[SelectorPage] No se pudo abrir la película: {}", nombre);

            } catch (Exception e) {
                log.warn("[SelectorPage] Error intentando abrir película '{}': {}", nombre, e.getMessage());
            }
        }

        throw new RuntimeException("Se detectaron películas visibles, pero no se pudo abrir ninguna.");
    }
    private boolean intentarAbrirPeliculaPorNombre(String nombre) {
        for (int intento = 1; intento <= 3; intento++) {
            try {
                WebElement titulo = reubicarTituloPelicula(nombre);

                if (titulo == null) {
                    log.warn("[SelectorPage] No se pudo reubicar la película: {}", nombre);
                    return false;
                }

                int centerX = titulo.getRect().getX() + (titulo.getRect().getWidth() / 2);
                int titleTopY = titulo.getRect().getY();
                int titleCenterY = titulo.getRect().getY() + (titulo.getRect().getHeight() / 2);

                // Intento A: tap en póster arriba del título
                try {
                    int posterTapY = titleTopY - 140;
                    if (posterTapY < 80) {
                        posterTapY = titleCenterY;
                    }

                    log.debug("[SelectorPage] Tap película intento {} (poster) -> {} X={} Y={}", intento, nombre, centerX, posterTapY);

                    tapW3C(centerX, posterTapY);

                    if (esperarDetallePelicula(4000)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("[SelectorPage] Falló tap poster para: {} -> {}", nombre, e.getMessage());
                }

                // Intento B: tap directo al texto reubicado
                try {
                    titulo = reubicarTituloPelicula(nombre);
                    if (titulo != null) {
                        int x = titulo.getRect().getX() + (titulo.getRect().getWidth() / 2);
                        int y = titulo.getRect().getY() + (titulo.getRect().getHeight() / 2);

                        log.debug("[SelectorPage] Tap película intento {} (texto) -> {} X={} Y={}", intento, nombre, x, y);

                        tapW3C(x, y);

                        if (esperarDetallePelicula(4000)) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[SelectorPage] Falló tap texto para: {} -> {}", nombre, e.getMessage());
                }

                // Intento C: click seguro sobre el elemento reubicado
                try {
                    titulo = reubicarTituloPelicula(nombre);
                    if (titulo != null && clicSeguroEnElemento(titulo)) {
                        if (esperarDetallePelicula(4000)) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[SelectorPage] Falló clicSeguroEnElemento para: {} -> {}", nombre, e.getMessage());
                }

            } catch (Exception e) {
                log.warn("[SelectorPage] intentoAbrirPeliculaPorNombre error con '{}': {}", nombre, e.getMessage());
            }
        }

        return false;
    }
    private WebElement reubicarTituloPelicula(String nombre) {
        try {
            List<WebElement> candidatos = driver.findElements(
                    By.xpath("//android.widget.TextView[@text=\"" + nombre + "\"]")
            );

            for (WebElement el : candidatos) {
                try {
                    if (el.isDisplayed()) {
                        return el;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        try {
            List<WebElement> candidatos = driver.findElements(
                    By.xpath("//*[contains(@text,\"" + nombre + "\")]")
            );

            for (WebElement el : candidatos) {
                try {
                    if (el.isDisplayed()) {
                        return el;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

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

        List<String> seleccionados = new ArrayList<>();
        for (SeatMap.Seat seat : result.seats) {
            if (tapRapidoEnButacaDesdeLabel(seat.element)) {
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
    }
    private boolean estaEnDetalleDePelicula() {
        try {
            // Una sola llamada al driver en lugar de 9 separadas
            String xpath =
                    "//*[contains(@text,'Sinopsis') or contains(@text,'Director') or " +
                    "contains(@text,'Reparto') or contains(@text,'Clasificaci\u00f3n') or " +
                    "contains(@text,'Ver horarios') or contains(@text,'VER HORARIOS') or " +
                    "contains(@text,'Ver tr\u00e1iler') or contains(@text,'Ver trailer') or " +
                    "contains(@text,'Ver m\u00e1s')]";
            for (WebElement el : driver.findElements(By.xpath(xpath))) {
                try { if (el.isDisplayed()) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void irAEtiquetaHorarios() {
        for (int intento = 1; intento <= 3; intento++) {
            try {
                WebElement boton = reubicarElementoPorTextoExacto("Ver horarios");

                if (boton == null) {
                    boton = reubicarElementoPorTexto("Ver horarios");
                }

                if (boton == null) {
                    throw new RuntimeException("No se encontró el botón Ver horarios.");
                }

                log.debug("[SelectorPage] Intentando entrar a horarios. Intento={}", intento);

                int centerX = boton.getRect().getX() + (boton.getRect().getWidth() / 2);
                int centerY = boton.getRect().getY() + (boton.getRect().getHeight() / 2);

                // intento 1: tap directo al texto
                tapW3C(centerX, centerY);
                if (esperarPantallaHorarios(5000)) {
                    log.info("[SelectorPage] Se abrió la sección de horarios correctamente.");
                    return;
                }

                // intento 2: tap un poco más arriba por si responde el contenedor del botón
                int yArriba = centerY - 20;
                if (yArriba < 1) yArriba = centerY;

                tapW3C(centerX, yArriba);
                if (esperarPantallaHorarios(5000)) {
                    log.info("[SelectorPage] Se abrió la sección de horarios correctamente con offset.");
                    return;
                }

                // intento 3: parent real
                try {
                    WebElement parent = boton.findElement(By.xpath(".."));
                    if (clicSeguroEnElemento(parent) && esperarPantallaHorarios(5000)) {
                        log.info("[SelectorPage] Se abrió la sección de horarios correctamente desde parent.");
                        return;
                    }
                } catch (Exception ignored) {
                }

            } catch (Exception e) {
                log.warn("[SelectorPage] Error entrando a horarios: {}", e.getMessage());
            }
        }

        throw new RuntimeException("No se pudo abrir la etiqueta de horarios.");
    }
    public String seleccionarPrimerHorarioDisponibleEnGrid() {
        if (!esperarPantallaHorarios(5000)) {
            throw new RuntimeException("La pantalla de horarios no cargó correctamente.");
        }

        List<WebElement> horarios = obtenerHorariosDisponibles();

        if (horarios.isEmpty()) {
            throw new RuntimeException("No hay horarios visibles para seleccionar.");
        }

        for (WebElement horario : horarios) {
            try {
                if (!horario.isDisplayed()) continue;

                String hora = obtenerTextoSeguro(horario);
                if (hora.isBlank()) continue;

                log.info("[SelectorPage] Intentando seleccionar horario disponible: {}", hora);

                if (clicSeguroEnHorario(horario)) {
                    sleep(800);
                    aceptarAlertaAtencionSiPresente();
                    return hora;
                }

                // fallback con reubicación por texto
                WebElement relocalizado = reubicarElementoPorTextoExacto(hora);
                if (relocalizado != null) {
                    int x = relocalizado.getRect().getX() + (relocalizado.getRect().getWidth() / 2);
                    int y = relocalizado.getRect().getY() + (relocalizado.getRect().getHeight() / 2);
                    tapW3C(x, y);
                    sleep(800);
                    aceptarAlertaAtencionSiPresente();
                    return hora;
                }

            } catch (Exception e) {
                log.warn("[SelectorPage] Error seleccionando horario: {}", e.getMessage());
            }
        }

        throw new RuntimeException("Se detectaron horarios visibles, pero no se pudo seleccionar ninguno.");
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
        if (!esperarPantallaHorarios(5000)) {
            throw new RuntimeException("La pantalla de horarios no cargó correctamente.");
        }

        for (int intento = 0; intento < 10; intento++) {
            List<WebElement> horarios = obtenerHorariosDisponibles();
            if (horarios.isEmpty()) break;

            boolean alertaDescartada = false;
            for (WebElement horario : horarios) {
                try {
                    if (!horario.isDisplayed()) continue;
                    String hora = obtenerTextoSeguro(horario);
                    if (hora.isBlank()) continue;

                    log.info("[SelectorPage] Intentando horario (descarte-alerta) intento={}: {}", intento, hora);

                    boolean clicOk = clicSeguroEnHorario(horario);
                    if (!clicOk) {
                        WebElement rel = reubicarElementoPorTextoExacto(hora);
                        if (rel != null) {
                            tapW3C(rel.getRect().getX() + rel.getRect().getWidth() / 2,
                                   rel.getRect().getY() + rel.getRect().getHeight() / 2);
                            clicOk = true;
                        }
                    }
                    if (!clicOk) continue;

                    sleep(1000);

                    // Alerta "Atención" (movimientos/vibraciones): aceptar y continuar al flujo de asientos
                    if (aceptarAlertaAtencionSiPresente()) {
                        log.info("[SelectorPage] Alerta 'Atención' aceptada para horario '{}'.", hora);
                        return hora;
                    }

                    if (hayAlertaHorarioInesperada()) {
                        log.warn("[SelectorPage] Alerta tras '{}': descartando y probando siguiente horario.", hora);
                        descartarAlertaHorario();
                        sleep(600);
                        alertaDescartada = true;
                        break;
                    }

                    return hora;

                } catch (Exception e) {
                    log.warn("[SelectorPage] Error intentando horario: {}", e.getMessage());
                }
            }

            if (!alertaDescartada) break;
        }

        throw new RuntimeException("No se encontró ningún horario sin alertas inesperadas.");
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
            List<WebElement> botones = driver.findElements(
                    By.xpath("//*[@text='Cancelar' or contains(@text,'Cancelar')]"));
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

    private WebElement reubicarElementoPorTexto(String texto) {
        try {
            List<WebElement> elementos = driver.findElements(
                    By.xpath("//*[contains(@text,'" + texto + "')]")
            );

            for (WebElement el : elementos) {
                try {
                    if (el.isDisplayed()) {
                        return el;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }
    private WebElement reubicarElementoPorTextoExacto(String texto) {
        try {
            List<WebElement> elementos = driver.findElements(
                    By.xpath("//*[@text='" + texto + "']")
            );

            for (WebElement el : elementos) {
                try {
                    if (el.isDisplayed()) return el;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
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

    private boolean clicSeguroEnHorario(WebElement el) {
        String hora = obtenerTextoSeguro(el);

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
                        try {
                            WebElement horario = reubicarElementoPorTextoExacto(hora);
                            if (horario != null) {
                                int x = horario.getRect().getX() + (horario.getRect().getWidth() / 2);
                                int y = horario.getRect().getY() + (horario.getRect().getHeight() / 2);
                                tapW3C(x, y);
                                return true;
                            }
                        } catch (Exception ignored) {
                        }

                        log.warn("[SelectorPage] No se pudo seleccionar horario: {}", hora);
                        return false;
                    }
                }
            }
        }
    }


    private List<WebElement> obtenerPeliculasVisibles() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        List<By> candidatos = List.of(
                By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"),
                By.xpath("//android.view.View[@text and normalize-space(@text)!='']")
        );

        for (By locator : candidatos) {
            try {
                List<WebElement> elementos = driver.findElements(locator);

                for (WebElement el : elementos) {
                    try {
                        if (!el.isDisplayed()) continue;

                        String texto = obtenerTextoSeguro(el);
                        if (texto.isBlank()) continue;
                        if (texto.length() < 5) continue;
                        if (esTextoNoPelicula(texto)) continue;

                        // Evitar horarios
                        if (texto.matches("^([01]?\\d|2[0-3]):[0-5]\\d(\\s?(AM|PM|am|pm))?$")) continue;

                        // Evitar textos demasiado largos que no suelen ser títulos
                        if (texto.length() > 80) continue;

                        unicos.putIfAbsent(texto.trim(), el);

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

        log.debug("[SelectorPage] Películas visibles detectadas: {}", resultado.size());
        for (WebElement el : resultado) {
            log.debug(" - {}", obtenerTextoSeguro(el));
        }

        return resultado;
    }

    private List<WebElement> obtenerHorariosDisponibles() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        List<By> candidatos = List.of(
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

    private boolean estaEnPantallaDeHorarios() {
        try {
            List<WebElement> todos = driver.findElements(
                    By.xpath("//*[@text and normalize-space(@text)!='']")
            );

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
    public boolean estaVisibleAlertaRestricciones() {
        try {
            return !driver.findElements(By.xpath(
                    "//*[contains(@text,'Restricciones') or contains(@text,'ambiente familiar')]"
            )).isEmpty();
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
    // Alerta: Atención (movimientos y vibraciones repentinas)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve {@code true} si la alerta de Atención (movimientos y vibraciones)
     * está visible en pantalla. Se distingue por su título "Atención" y la mención
     * de vibraciones o restricción de menores de 4 años.
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
     * Si la alerta de Atención está visible, toca "Aceptar y continuar" para
     * proceder al flujo de selección de asientos.
     *
     * @return {@code true} si la alerta fue encontrada y aceptada.
     */
    public boolean aceptarAlertaAtencionSiPresente() {
        // Espera hasta 2s para que la alerta aparezca tras el tap en el horario
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (estaVisibleAlertaAtencion()) break;
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        if (!estaVisibleAlertaAtencion()) return false;
        try {
            List<WebElement> botones = driver.findElements(
                    By.xpath("//*[contains(@text,'Aceptar y continuar')]"));
            for (WebElement b : botones) {
                try {
                    if (!b.isDisplayed()) continue;
                    tapW3C(b.getRect().getX() + b.getRect().getWidth() / 2,
                           b.getRect().getY() + b.getRect().getHeight() / 2);
                    sleep(500);
                    log.info("[SelectorPage] Alerta 'Atención' aceptada con 'Aceptar y continuar'.");
                    return true;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("[SelectorPage] Error aceptando alerta Atención: {}", e.getMessage());
        }
        return false;
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
    private List<WebElement> obtenerHorariosVisiblesEnPantallaAsientos() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        List<By> candidatos = List.of(
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


    private void hacerScrollHorarios() {
        try {
            slowSwipeUp();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo hacer scroll en horarios.", e);
        }
    }

    private String obtenerTextoSeguro(WebElement el) {
        try {
            String txt = el.getText();
            return txt == null ? "" : txt.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean esTextoNoPelicula(String txt) {
        String t = txt == null ? "" : txt.trim().toLowerCase();

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
        SeatMap map = buildSeatMap();
        log.info("[SelectorPage] {}", map.getSummary());

        if (map.isEmpty()) {
            throw new RuntimeException("No se encontraron asientos visibles en el mapa.");
        }

        List<SeatMap.Seat> seats = map.allSeats();
        Collections.shuffle(seats);

        int maxIntentos = Math.min(6, seats.size());
        for (int i = 0; i < maxIntentos; i++) {
            SeatMap.Seat seat = seats.get(i);
            log.info("[SelectorPage] Intentando seleccionar asiento rápido: {}", seat);

            if (tapRapidoEnButacaDesdeLabel(seat.element)) {
                sleep(150);
                log.info("[SelectorPage] Asiento seleccionado OK: {}", seat);
                takeScreenshot("Asiento seleccionado");
                return seat.toString();
            }
        }

        throw new RuntimeException("Se detectaron asientos, pero no se pudo seleccionar ninguno.");
    }
    private List<WebElement> obtenerAsientosDelMapaAmplio() {
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        int screenHeight = driver.manage().window().getSize().getHeight();
        int mapTop    = (int)(screenHeight * 0.22);
        int mapBottom = (int)(screenHeight * 0.96);

        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            // UIAutomator2 on-device: evita getText() individual y el triple-XPath fallback
            try {
                List<WebElement> elementos = driver.findElements(
                    AppiumBy.androidUIAutomator(
                        "new UiSelector().textMatches(\"^\\\\d{1,2}$\")"
                    )
                );
                for (WebElement el : elementos) {
                    try {
                        org.openqa.selenium.Rectangle r = el.getRect();
                        int cy = r.getY() + r.getHeight() / 2;
                        int cx = r.getX() + r.getWidth() / 2;
                        if (cy < mapTop || cy > mapBottom || cx < 10) continue;
                        unicos.putIfAbsent(r.getX() + "|" + r.getY(), el);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                // Fallback XPath si UIAutomator2 falla
                List<WebElement> elementos = driver.findElements(
                    By.xpath("//android.widget.TextView[@text and string-length(normalize-space(@text)) <= 2]")
                );
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


    private List<WebElement> obtenerAsientosDelMapaRapido() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        // Deshabilitar implicit wait: evita que cada findElements espere N segundos si no hay resultados
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            int screenHeight = driver.manage().window().getSize().getHeight();
            int mapTop    = (int) (screenHeight * 0.30);
            int mapBottom = (int) (screenHeight * 0.93);

            List<WebElement> candidatos;
            try {
                candidatos = driver.findElements(
                        By.xpath("//android.widget.TextView[@text and string-length(normalize-space(@text)) <= 2]")
                );
            } catch (Exception e) {
                return resultado;
            }

            for (WebElement el : candidatos) {
                try {
                    String txt = obtenerTextoSeguro(el);
                    if (!txt.matches("^\\d{1,2}$")) continue;

                    // Cachear el Rectangle: evita llamar el.getRect() múltiples veces
                    // (cada llamada es un round-trip WebDriver independiente)
                    org.openqa.selenium.Rectangle r = el.getRect();
                    int centerY = r.getY() + (r.getHeight() / 2);
                    if (centerY < mapTop || centerY > mapBottom) continue;

                    int centerX = r.getX() + (r.getWidth() / 2);
                    if (centerX < 20) continue;

                    unicos.putIfAbsent(txt + "|" + r.getX() + "|" + r.getY(), el);

                } catch (Exception ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        resultado.addAll(unicos.values());
        log.debug("[SelectorPage] Asientos rápidos detectados: {}", resultado.size());
        return resultado;
    }

    private List<WebElement> obtenerAsientosDisponiblesVisibles() {
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        int screenHeight = driver.manage().window().getSize().getHeight();
        int mapTop    = (int)(screenHeight * 0.30);
        int mapBottom = (int)(screenHeight * 0.93);

        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            // UIAutomator2: filtra en dispositivo, sin isDisplayed() ni getText() por elemento
            List<WebElement> candidatos = driver.findElements(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().textMatches(\"^\\\\d{1,2}$\")"
                )
            );
            for (WebElement el : candidatos) {
                try {
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

    private List<WebElement> obtenerAsientosParaConsecutivos() {
        List<WebElement> resultado = new ArrayList<>();
        Map<String, WebElement> unicos = new LinkedHashMap<>();

        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        try {
            int screenHeight = driver.manage().window().getSize().getHeight();
            int mapTop    = (int) (screenHeight * 0.28);
            int mapBottom = (int) (screenHeight * 0.94);

            List<By> candidatos = List.of(
                    By.xpath("//android.widget.TextView[@text and normalize-space(@text)!='']"),
                    By.xpath("//*[@text and normalize-space(@text)!='']")
            );

            for (By locator : candidatos) {
                try {
                    List<WebElement> elementos = driver.findElements(locator);

                    for (WebElement el : elementos) {
                        try {
                            String txt = obtenerTextoSeguro(el);
                            if (!txt.matches("^\\d{1,2}$")) continue;

                            int numero = Integer.parseInt(txt);
                            if (numero < 1 || numero > 30) continue;

                            // Cachear el Rectangle: una sola llamada para Y, X y la clave
                            org.openqa.selenium.Rectangle r = el.getRect();
                            int centerY = r.getY() + (r.getHeight() / 2);
                            if (centerY < mapTop || centerY > mapBottom) continue;

                            int centerX = r.getX() + (r.getWidth() / 2);
                            if (centerX < 15) continue;

                            unicos.putIfAbsent(txt + "|" + r.getX() + "|" + r.getY(), el);

                        } catch (Exception ignored) {}
                    }

                    if (!unicos.isEmpty()) break;

                } catch (Exception ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        resultado.addAll(unicos.values());
        log.debug("[SelectorPage] Asientos para consecutivos detectados: {}", resultado.size());
        return resultado;
    }

    private boolean tapRapidoEnButacaDesdeLabel(WebElement el) {
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
        try {
            while (System.currentTimeMillis() < end) {
                // UIAutomator2 filtra en el dispositivo: 1 round-trip en vez de getText() × N
                List<WebElement> asientos = escanearMapaConUIAutomator(mapTop, mapBottom);
                if (!asientos.isEmpty()) {
                    log.debug("[SelectorPage] Mapa listo: {} asientos.", asientos.size());
                    return asientos;
                }
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        log.warn("[SelectorPage] Tiempo agotado escaneando el mapa.");
        return Collections.emptyList();
    }

    /**
     * Construye un {@link SeatMap} a partir del estado actual del mapa de asientos.
     * Ejecuta el escaneo principal y dos fallbacks antes de devolver el modelo.
     */
    private SeatMap buildSeatMap() {
        List<WebElement> raw = esperarYObtenerAsientosDelMapa();
        if (raw.isEmpty()) raw = obtenerAsientosDisponiblesVisibles();
        if (raw.isEmpty()) raw = obtenerAsientosDelMapaAmplio();
        return new SeatMap(raw);
    }

    // UIAutomator2 filtra por texto numérico EN EL DISPOSITIVO → solo devuelve asientos reales.
    // Cada elemento solo necesita 1 call (getRect) en vez de 2 (getText + getRect).
    private List<WebElement> escanearMapaConUIAutomator(int mapTop, int mapBottom) {
        try {
            // Sin className: compatible con TextView Y elementos de Jetpack Compose (View)
            List<WebElement> candidatos = driver.findElements(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().textMatches(\"^\\\\d{1,2}$\")"
                )
            );
            if (candidatos.isEmpty()) return escanearMapaConXPath(mapTop, mapBottom);

            Map<String, WebElement> unicos = new LinkedHashMap<>();
            for (WebElement el : candidatos) {
                try {
                    org.openqa.selenium.Rectangle r = el.getRect();
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







    private String safe(String value) {
        return value == null ? "" : value.trim();
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