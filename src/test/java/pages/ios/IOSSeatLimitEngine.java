package pages.ios;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.junit.jupiter.api.Assumptions;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TAREA — motor de asientos EXCLUSIVO de iOS para el caso "Selección de más de 10
 * Asientos con Mensaje de Alerta" (único caso de la suite portado en esta tarea, por
 * decisión explícita del alcance).
 *
 * Independiente por completo de {@link pages.asientos.SelectorPage}: no llama a
 * ninguno de sus métodos, no reutiliza ningún locator Android (XPath/@text/
 * UiAutomator2), no comparte ninguna rama {@code isIOS()}. Toda búsqueda de elementos
 * usa exclusivamente {@link AppiumBy#iOSNsPredicateString(String)} sobre atributos
 * reales de XCUITest (type/label/name/value). Solo reutiliza infraestructura genérica
 * ya compartida por TODA la familia de Page Objects de este proyecto (Android e iOS
 * por igual) — heredada de {@link pages.common.BasePage} vía {@link IOSBasePage}:
 * {@code tapW3C()} (W3C Actions estándar), {@code sleep()} y {@code takeScreenshot()}
 * — ninguno de los tres contiene lógica de selección de asientos ni locators.
 *
 * Comportamiento funcional replicado de Android (referencia:
 * {@link pages.asientos.SelectorPage#seleccionarMasDe10AsientosYValidarAlerta()}), NO
 * su código: acumular exactamente 10 asientos reales confirmados por contador (nunca
 * "tap ejecutado" = "asiento seleccionado"), realizar EXACTAMENTE un intento adicional
 * del 11º asiento, y verificar la alerta de límite INMEDIATAMENTE después — sin
 * reintentar con otro candidato si la alerta no aparece.
 *
 * IMPORTANTE — locators pendientes de confirmación con hardware real: el predicate de
 * candidatos de asiento y el de la alerta reutilizan el patrón NSPredicate ya usado
 * (con evidencia histórica real) en las ramas iOS de SelectorPage para el mismo
 * propósito — no fueron re-verificados contra una captura fresca del árbol XCUITest en
 * esta tarea porque el único iPhone físico registrado estaba en estado DISCOVERED (túnel
 * CoreDevice desconectado), no AVAILABLE. Ver sección "Problemas pendientes" del
 * reporte de esta tarea.
 */
public class IOSSeatLimitEngine extends IOSBasePage {

    private static final Logger log = LoggerFactory.getLogger(IOSSeatLimitEngine.class);

    // Regla de negocio (misma que Android, ver SelectorPage — no es lógica de
    // plataforma, es una constante de negocio; se redefine aquí para que esta clase
    // sea 100% autocontenida, sin importar nada de SelectorPage).
    private static final int MAX_ASIENTOS_CONFIRMABLES = 10;
    private static final int ASIENTOS_MINIMOS_SALA = 11;

    // Tolerancias espaciales — mismos valores que la referencia funcional de Android
    // (SelectorPage.TOLERANCIA_FILA_PX / TOLERANCIA_MISMO_ASIENTO_PX), reimplementadas
    // aquí de forma independiente (misma idea: "menor que el tamaño típico de un botón
    // de asiento, para que dos asientos reales distintos nunca caigan dentro de esta
    // distancia entre sí").
    private static final int TOLERANCIA_FILA_PX = 40;
    private static final double TOLERANCIA_MISMO_ASIENTO_PX = 30.0;

    private static final int RETRY_BUDGET = 10;
    private static final int SEGUNDA_OLA = 15;

    // NSPredicate — candidato de asiento: un botón o texto estático con un valor
    // accesible de 1-2 caracteres (número de butaca), en cualquiera de los 3 atributos
    // reales que XCUITest expone (name/label/value) — nunca @text (eso no existe en
    // XCUITest).
    private static final String SEAT_CANDIDATE_PREDICATE =
            "(type == 'XCUIElementTypeButton' OR type == 'XCUIElementTypeStaticText') AND "
            + "((name != nil AND name MATCHES '.{1,2}') OR (label != nil AND label MATCHES '.{1,2}') "
            + "OR (value != nil AND value MATCHES '.{1,2}'))";

    // NSPredicate — botón "Continuar, N" (fuente de verdad del contador real de
    // asientos seleccionados, nunca "tap ejecutado").
    private static final String CONTINUAR_PREDICATE =
            "type == 'XCUIElementTypeButton' AND (label BEGINSWITH 'Continuar' OR name BEGINSWITH 'Continuar')";
    private static final Pattern CONTINUAR_COUNT_PATTERN = Pattern.compile("^Continuar,\\s*(\\d+)$");

    // NSPredicate — texto real de la alerta de límite (título/botón), buscado sobre
    // label/name/value — nunca @text.
    private static final String ALERT_TEXT_PREDICATE =
            "label CONTAINS 'límite máximo de asientos' OR value CONTAINS 'límite máximo de asientos' "
            + "OR name CONTAINS 'límite máximo de asientos' "
            + "OR label CONTAINS 'Aceptar y continuar' OR value CONTAINS 'Aceptar y continuar' "
            + "OR name CONTAINS 'Aceptar y continuar'";

    public IOSSeatLimitEngine(AppiumDriver driver) {
        super(driver);
    }

    /** Identidad física de un candidato — número + posición real en pantalla en el momento del escaneo. */
    private record CandidatoIOS(int number, int x, int y) {
        String candidateId() { return number + "@" + x + "," + y; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Escaneo del mapa
    // ─────────────────────────────────────────────────────────────────────────

    private List<WebElement> escanearCandidatosCrudos() {
        try {
            return driver.findElements(AppiumBy.iOSNsPredicateString(SEAT_CANDIDATE_PREDICATE));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Integer numeroDeAsiento(WebElement el) {
        for (String attr : new String[]{"name", "label", "value"}) {
            try {
                String v = el.getAttribute(attr);
                Integer numero = parseNumeroAsiento(v);
                if (numero != null) return numero;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Algoritmo puro (sin WDA) que decide si un texto de atributo (name/label/value)
     * representa un número de asiento válido (1-2 dígitos) — extraído para poder
     * testearse sin dispositivo/instancia, mismo patrón ya usado en
     * {@code SelectorPageTest} para {@code indiceMasCercanoPorY}.
     */
    static Integer parseNumeroAsiento(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        if (!t.matches("\\d{1,2}")) return null;
        return Integer.parseInt(t);
    }

    /**
     * Algoritmo puro (sin WDA) que extrae el contador real del botón "Continuar, N" a
     * partir del texto crudo de su label/name — null si el texto no calza con el
     * formato esperado.
     */
    static Integer parseContadorContinuar(String texto) {
        if (texto == null) return null;
        Matcher m = CONTINUAR_COUNT_PATTERN.matcher(texto.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : null;
    }

    private List<CandidatoIOS> construirPool() {
        List<CandidatoIOS> pool = new ArrayList<>();
        for (WebElement el : escanearCandidatosCrudos()) {
            try {
                if (!el.isDisplayed()) continue;
                Integer numero = numeroDeAsiento(el);
                if (numero == null || numero <= 0) continue;
                Rectangle r = el.getRect();
                int cx = r.getX() + r.getWidth() / 2;
                int cy = r.getY() + r.getHeight() / 2;
                pool.add(new CandidatoIOS(numero, cx, cy));
            } catch (Exception ignored) {
            }
        }
        Collections.shuffle(pool);
        return pool;
    }

    /** Re-localiza un candidato por número, eligiendo — entre TODOS los que comparten
     *  ese número (asientos duplicados entre filas) — el más cercano a la Y esperada.
     *  Equivalente funcional de SelectorPage.reubicarAsientoPorNumero(), reimplementado
     *  sobre NSPredicate/XCUITest, sin compartir código con la versión Android. */
    private WebElement reubicarPorNumero(int numero, int expectedY) {
        String predicate = "(type == 'XCUIElementTypeButton' OR type == 'XCUIElementTypeStaticText') AND "
                + "(name == '" + numero + "' OR label == '" + numero + "' OR value == '" + numero + "')";
        try {
            List<WebElement> encontrados = driver.findElements(AppiumBy.iOSNsPredicateString(predicate));
            WebElement mejor = null;
            int mejorDistancia = Integer.MAX_VALUE;
            for (WebElement el : encontrados) {
                try {
                    Rectangle r = el.getRect();
                    int cy = r.getY() + r.getHeight() / 2;
                    int distancia = Math.abs(cy - expectedY);
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

    /** Algoritmo puro (sin WDA) — mismo criterio ya usado como referencia funcional
     *  Android (SelectorPage.mismoAsientoFisico), reimplementado de forma independiente. */
    static boolean mismaPosicionFisica(int x1, int y1, int x2, int y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy) <= TOLERANCIA_MISMO_ASIENTO_PX;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Contador real (fuente de verdad — nunca "tap ejecutado" = "seleccionado")
    // ─────────────────────────────────────────────────────────────────────────

    private int contarSeleccionados() {
        try {
            List<WebElement> candidatos = driver.findElements(AppiumBy.iOSNsPredicateString(CONTINUAR_PREDICATE));
            for (WebElement el : candidatos) {
                for (String attr : new String[]{"label", "name"}) {
                    try {
                        Integer contador = parseContadorContinuar(el.getAttribute(attr));
                        if (contador != null) return contador;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0; // botón "Continuar, N" ausente → 0 asientos seleccionados según la app
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alerta de límite
    // ─────────────────────────────────────────────────────────────────────────

    private WebElement buscarAlertaLimite() {
        try {
            for (WebElement el : driver.findElements(AppiumBy.iOSNsPredicateString(ALERT_TEXT_PREDICATE))) {
                try {
                    if (el.isDisplayed()) return el;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean hayElementoConTexto(String fragmento) {
        String predicate = "label CONTAINS '" + fragmento + "' OR value CONTAINS '" + fragmento
                + "' OR name CONTAINS '" + fragmento + "'";
        try {
            for (WebElement el : driver.findElements(AppiumBy.iOSNsPredicateString(predicate))) {
                try {
                    if (el.isDisplayed()) return true;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caso principal
    // ─────────────────────────────────────────────────────────────────────────

    public List<String> seleccionarMasDe10AsientosYValidarAlerta() {
        List<CandidatoIOS> pool = construirPool();
        if (pool.size() < ASIENTOS_MINIMOS_SALA) {
            Assumptions.abort("iOS: menos de 11 asientos disponibles (detectados: " + pool.size()
                    + "). Se omite la prueba.");
            return null;
        }

        int contadorActual = contarSeleccionados();
        List<String> seleccionados = new ArrayList<>();
        List<CandidatoIOS> confirmados = new ArrayList<>();
        Set<String> descartados = new HashSet<>();

        int idx = 0, attempt = 0, failedCount = 0;
        int maxIntentos = Math.min(pool.size(), MAX_ASIENTOS_CONFIRMABLES + RETRY_BUDGET);
        boolean sourceRefreshed = false;

        // ── FASE 1 — acumular exactamente 10 asientos reales confirmados ────────
        while (true) {
            if (idx >= pool.size() || attempt >= maxIntentos) {
                if (sourceRefreshed) break; // único re-scan permitido ya usado
                sourceRefreshed = true;
                List<CandidatoIOS> fresh = construirPool();
                List<CandidatoIOS> finalConfirmados = confirmados;
                fresh.removeIf(c -> finalConfirmados.stream().anyMatch(cf ->
                        cf.number() == c.number() && mismaPosicionFisica(cf.x(), cf.y(), c.x(), c.y())));
                log.info("[SeatLimit-iOS] sourceRefresh=true — presupuesto agotado sin llegar a {}, "
                        + "re-escaneando estado actual del mapa. candidatosNuevos={}",
                        MAX_ASIENTOS_CONFIRMABLES, fresh.size());
                pool = fresh;
                idx = 0;
                maxIntentos = attempt + Math.min(pool.size(), SEGUNDA_OLA);
                continue;
            }

            CandidatoIOS c = pool.get(idx++);
            if (descartados.contains(c.candidateId())) continue;
            boolean yaConfirmado = confirmados.stream().anyMatch(cf ->
                    cf.number() == c.number() && mismaPosicionFisica(cf.x(), cf.y(), c.x(), c.y()));
            if (yaConfirmado) continue;

            attempt++;
            int beforeCount = contadorActual;

            int tapX = c.x(), tapY = c.y();
            try {
                WebElement fresco = reubicarPorNumero(c.number(), c.y());
                if (fresco != null) {
                    Rectangle r = fresco.getRect();
                    int freshY = r.getY() + r.getHeight() / 2;
                    if (Math.abs(freshY - c.y()) <= TOLERANCIA_FILA_PX) {
                        tapX = r.getX() + r.getWidth() / 2;
                        tapY = freshY;
                    }
                }
            } catch (Exception ignored) {
                // se conservan las coordenadas cacheadas del candidato
            }

            // Nunca tapear una coordenada (revalidada o cacheada) que coincida
            // físicamente con un asiento ya confirmado — mismo riesgo real ya
            // documentado en la referencia funcional Android.
            final int fx = tapX, fy = tapY;
            boolean coincideConfirmado = confirmados.stream().anyMatch(cf ->
                    mismaPosicionFisica(cf.x(), cf.y(), fx, fy));

            boolean confirmed = false;
            boolean tapOk = false;
            if (!coincideConfirmado) {
                try {
                    tapW3C(tapX, tapY);
                    tapOk = true;
                    sleep(400);
                    contadorActual = contarSeleccionados();
                    confirmed = contadorActual == beforeCount + 1;
                    if (confirmed) {
                        seleccionados.add("A" + c.number());
                        confirmados.add(c);
                    }
                } catch (Exception ignored) {
                }
            }
            if (!confirmed) {
                descartados.add(c.candidateId());
                failedCount++;
            }

            log.info("[SeatLimit-iOS] attempt={} beforeCount={} afterCount={} confirmed={} candidate={} tap={} "
                    + "sourceRefresh={}",
                    attempt, beforeCount, contadorActual, confirmed, c.candidateId(), tapOk, sourceRefreshed);

            if (contadorActual >= MAX_ASIENTOS_CONFIRMABLES) break;
        }

        if (contadorActual < MAX_ASIENTOS_CONFIRMABLES) {
            log.warn("[SeatLimit-iOS] TARGET_NOT_REACHABLE confirmed={} target={} attempts={} failedCount={}",
                    contadorActual, MAX_ASIENTOS_CONFIRMABLES, attempt, failedCount);
            takeScreenshot("iOS - No se alcanzaron 10 asientos reales");
            throw new RuntimeException("iOS: solo se pudieron seleccionar " + contadorActual + " de "
                    + MAX_ASIENTOS_CONFIRMABLES + " asientos reales (límite real de negocio). intentos=" + attempt
                    + " fallidos=" + failedCount);
        }

        // ── FASE 2 — intento ÚNICO del asiento adicional (11º) ──────────────────
        CandidatoIOS candidato11 = null;
        int tapX11 = 0, tapY11 = 0;
        boolean refrescoEnFase2 = false;

        while (true) {
            CandidatoIOS c = null;
            while (idx < pool.size()) {
                CandidatoIOS cand = pool.get(idx++);
                if (descartados.contains(cand.candidateId())) continue;
                List<CandidatoIOS> finalConfirmados1 = confirmados;
                boolean yaConfirmado = finalConfirmados1.stream().anyMatch(cf ->
                        cf.number() == cand.number() && mismaPosicionFisica(cf.x(), cf.y(), cand.x(), cand.y()));
                if (yaConfirmado) continue;
                c = cand;
                break;
            }

            if (c == null) {
                if (refrescoEnFase2 || sourceRefreshed) break;
                refrescoEnFase2 = true;
                sourceRefreshed = true;
                List<CandidatoIOS> fresh = construirPool();
                List<CandidatoIOS> finalConfirmados2 = confirmados;
                fresh.removeIf(cand -> finalConfirmados2.stream().anyMatch(cf ->
                        cf.number() == cand.number() && mismaPosicionFisica(cf.x(), cf.y(), cand.x(), cand.y())));
                log.info("[SeatLimit-iOS] sourceRefresh=true (fase 11º asiento) — pool agotado, re-escaneando. "
                        + "candidatosNuevos={}", fresh.size());
                pool = fresh;
                idx = 0;
                continue;
            }

            int tapXTmp = c.x(), tapYTmp = c.y();
            try {
                WebElement fresco = reubicarPorNumero(c.number(), c.y());
                if (fresco != null) {
                    Rectangle r = fresco.getRect();
                    int freshY = r.getY() + r.getHeight() / 2;
                    if (Math.abs(freshY - c.y()) <= TOLERANCIA_FILA_PX) {
                        tapXTmp = r.getX() + r.getWidth() / 2;
                        tapYTmp = freshY;
                    }
                }
            } catch (Exception ignored) {
            }

            final int fxTmp = tapXTmp, fyTmp = tapYTmp;
            List<CandidatoIOS> finalConfirmados3 = confirmados;
            boolean coincide = finalConfirmados3.stream().anyMatch(cf ->
                    mismaPosicionFisica(cf.x(), cf.y(), fxTmp, fyTmp));
            if (coincide) {
                log.info("[SeatLimit-iOS] candidato11Descartado=true candidate={} motivo=coincideConConfirmado",
                        c.candidateId());
                descartados.add(c.candidateId());
                continue;
            }

            candidato11 = c;
            tapX11 = tapXTmp;
            tapY11 = tapYTmp;
            break;
        }

        if (candidato11 == null) {
            takeScreenshot("iOS - Sin candidato disponible para el 11o asiento");
            throw new RuntimeException("iOS: no fue posible intentar un 11º asiento: no hay ningún candidato real "
                    + "disponible tras confirmar " + contadorActual + " asientos reales.");
        }

        int beforeCount11 = contadorActual;
        boolean tap11Ok;
        try {
            tapW3C(tapX11, tapY11);
            tap11Ok = true;
        } catch (Exception e) {
            tap11Ok = false;
        }

        sleep(400);
        int afterCount11 = contarSeleccionados();
        if (afterCount11 > MAX_ASIENTOS_CONFIRMABLES) {
            log.warn("[SeatLimit-iOS] UNDECIMO_CONFIRMADO=true — el intento del 11º asiento SÍ incrementó el "
                    + "contador (afterCount={}), contradice el límite de 10. Se continúa verificando la alerta.",
                    afterCount11);
        }

        // Verificación INMEDIATA — poll acotado (5s, cada 200ms), nunca una consulta
        // instantánea, pero tampoco más intentos de tap.
        WebElement alertEl = null;
        boolean alertFound = false;
        long finEspera = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < finEspera) {
            alertEl = buscarAlertaLimite();
            if (alertEl != null) {
                alertFound = true;
                break;
            }
            sleep(200);
        }

        String alertType = "n/a", alertLabel = "n/a", alertName = "n/a", alertValue = "n/a";
        if (alertFound) {
            try { alertType = String.valueOf(alertEl.getAttribute("type")); } catch (Exception ignored) { }
            try { alertLabel = String.valueOf(alertEl.getAttribute("label")); } catch (Exception ignored) { }
            try { alertName = String.valueOf(alertEl.getAttribute("name")); } catch (Exception ignored) { }
            try { alertValue = String.valueOf(alertEl.getAttribute("value")); } catch (Exception ignored) { }
        }
        String navigationState = alertFound ? "alertaVisible" : "sinAlertaSobrePantallaDeAsientos";
        String result = alertFound ? "PASS" : "FAIL";

        log.info("[SeatLimit-iOS] beforeCount={} afterCount={} candidate={} tap={} alertSearch=NSPredicate "
                + "alertFound={} alertType={} alertLabel={} alertName={} alertValue={} navigationState={} "
                + "result={}",
                beforeCount11, afterCount11, candidato11.candidateId(), tap11Ok, alertFound, alertType, alertLabel,
                alertName, alertValue, navigationState, result);

        if (!alertFound) {
            takeScreenshot("iOS - Sin alerta tras el intento del 11o asiento");
            throw new RuntimeException(String.format(
                    "iOS: la alerta de límite máximo de asientos no apareció tras el único intento del 11º "
                    + "asiento (candidate=%s, beforeCount=%d, afterCount=%d).",
                    candidato11.candidateId(), beforeCount11, afterCount11));
        }

        // No declarar PASS solo porque ALGÚN nodo coincidió con el predicate amplio de
        // arriba — validar título, mensaje y botón por separado, cada uno con su propio
        // predicate, igual de estricto que la referencia funcional Android.
        boolean tituloVisible = hayElementoConTexto("límite máximo de asientos");
        boolean mensajeVisible = hayElementoConTexto("10 por transacción");
        boolean botonVisible = hayElementoConTexto("Aceptar y continuar");

        log.info("[SeatLimit-iOS] alertType={} titlePresent={} messagePresent={} buttonPresent={}",
                alertType, tituloVisible, mensajeVisible, botonVisible);

        if (!tituloVisible) {
            throw new RuntimeException("iOS: no se mostró el título esperado de la alerta de límite de asientos.");
        }
        if (!mensajeVisible) {
            throw new RuntimeException("iOS: no se mostró el mensaje esperado de la alerta de límite de asientos.");
        }
        if (!botonVisible) {
            throw new RuntimeException("iOS: no se mostró el botón 'Aceptar y continuar' en la alerta.");
        }

        takeScreenshot("iOS - Alerta limite asientos");
        return seleccionados;
    }
}
