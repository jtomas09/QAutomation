package pages.asientos;

import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Único responsable de la lógica de selección incremental de asientos.
 *
 * Historia (evidencia forense — ejecución 2026-08-11 11:18:58, log
 * automationqa-runner.log líneas ~1705900-1706193): la versión ORIGINAL
 * construía el SeatMap UNA sola vez y reutilizaba los mismos WebElement para
 * los N taps — tras el primer tap el árbol XCUI se invalidaba y los taps
 * siguientes fallaban ("Find the 'N' Button (retry 1/2)" + fallback antes de
 * fallar). Se corrigió reconstruyendo el mapa completo antes y después de
 * CADA asiento — pero esa versión introdujo una regresión de rendimiento
 * severa: cada reconstrucción cuesta ~160s con ~143 candidatos (evidencia:
 * "obtenerCandidatosAsientoIOS findElements=40596ms ... filtroJava=64306ms"),
 * y con 2 reconstrucciones por asiento un caso de 3 asientos pasaba de
 * minutos a más de una hora.
 *
 * Esta versión hace UN solo escaneo completo por llamada a {@link #select}
 * (no por asiento) — pero ESE escaneo solo sirve para enumerar qué números
 * de asiento existen. El {@code WebElement} que trae cada {@code Seat} del
 * escaneo inicial NUNCA se usa directamente para tapear ni para leer sus
 * atributos: evidencia en vivo (log 2026-08-11 17:36-17:38) demostró que, en
 * cuanto se tapea el primer asiento, el árbol de accesibilidad se invalida
 * por completo y CUALQUIER WebElement restante de ese mismo escaneo queda
 * muerto — usarlo directamente hacía que "antes del tap" ya apareciera como
 * label=N/D value=N/D name=N/D... (el método describir() atrapa la
 * StaleElementReferenceException real y la disfraza de "N/D" en vez de
 * reportarla). Por eso cada asiento se resuelve de nuevo, por número, contra
 * el árbol ACTUAL vía {@link SelectorPage#reubicarAsientoPorNumero(int)} —
 * una consulta dirigida a un solo número, nunca un reescaneo de los ~143
 * candidatos — inmediatamente antes de tapear y otra vez después, para
 * confirmar la selección real.
 *
 * Es el único lugar donde vive esta lógica; cualquier método de SelectorPage
 * que seleccione N asientos (random, consecutivos, VIP, etc.) debe apoyarse
 * en {@link #select(int, SeatPicker)} en vez de repetirla.
 */
final class SeatSelectionEngine {

    private static final Logger log = LoggerFactory.getLogger(SeatSelectionEngine.class);

    /**
     * Decide el próximo candidato a partir del mapa recién reconstruido,
     * excluyendo los números ya confirmados o descartados. Devuelve
     * {@code null} cuando no queda ningún candidato viable.
     */
    @FunctionalInterface
    interface SeatPicker {
        SeatMap.Seat pick(SeatMap map, Set<Integer> excluidos);
    }

    /** Cualquier asiento numerado disponible, al azar. */
    static final SeatPicker CUALQUIERA = (map, excluidos) -> {
        List<SeatMap.Seat> disponibles = map.allNumberedSeats().stream()
            .filter(s -> !excluidos.contains(s.number))
            .collect(Collectors.toList());
        if (disponibles.isEmpty()) return null;
        Collections.shuffle(disponibles);
        return disponibles.get(0);
    };

    private final SelectorPage page;

    SeatSelectionEngine(SelectorPage page) {
        this.page = page;
    }

    /**
     * Selecciona {@code count} asientos con UN solo escaneo completo inicial.
     * Cada asiento se revalida leyendo directamente su propio WebElement (sin
     * volver a escanear toda la pantalla); solo se relocaliza de forma
     * dirigida (un único número, nunca los ~143 candidatos) si ese handle
     * quedó obsoleto. Nunca reintenta un número ya confirmado o descartado.
     *
     * @throws RuntimeException si se agotan los candidatos disponibles antes
     *         de reunir {@code count} asientos confirmados.
     */
    List<String> select(int count, SeatPicker picker) {
        long tEscaneo = System.currentTimeMillis();
        SeatMap mapa = page.buildSeatMap();                 // ÚNICO escaneo completo de toda la selección
        utils.PerfMetrics.stage("SeatSelection", "escaneoInicial", System.currentTimeMillis() - tEscaneo);
        log.info("[SeatSelectionEngine] Escaneo inicial: {} asiento(s) numerado(s) disponibles.",
            mapa.allNumberedSeats().size());

        List<String> seleccionados = new ArrayList<>();
        Set<Integer> excluidos = new HashSet<>();
        int intento = 0;
        // Freno de seguridad INDEPENDIENTE de `confirmado`/`selected` — evidencia (log
        // 2026-08-12, 3 corridas consecutivas): tapOk=true en el 100% de los intentos
        // (12/12, 12/13, 20/20) mientras confirmado=false en el 100% — el motor seguía
        // tapeando candidato tras candidato hasta agotar los ~15-20 numerados del mapa,
        // ejecutando muchos más taps reales en la app que los `count` solicitados
        // (observado visualmente: la app terminaba con más de 3 asientos seleccionados).
        // Este contador NO decide qué cuenta como "seleccionado" (eso sigue siendo
        // exclusivamente `confirmado`) — solo acota cuántos taps reales se permiten
        // como máximo, mientras el verdadero indicador de selección (SeatUiSnapshot)
        // sigue en investigación.
        int tapsExitosos = 0;

        while (seleccionados.size() < count) {
            if (tapsExitosos >= count) {
                log.warn("[SeatSelectionEngine] DETENIDO por freno de seguridad: ya se ejecutaron {} "
                    + "tap(s) exitoso(s) (>= {} solicitados) aunque el motor solo confirmó {} — no se "
                    + "intentan más candidatos para no seguir seleccionando asientos reales de más en "
                    + "la app mientras el indicador de confirmación sigue en investigación.",
                    tapsExitosos, count, seleccionados.size());
                break;
            }

            int candidatosRestantes = mapa.allNumberedSeats().size() - excluidos.size();
            log.info("[SeatSelectionEngine] Estado antes del intento {}: confirmados(motor)={} "
                + "descartados={} candidatosRestantes={} tapsExitosos={} objetivo={}",
                intento + 1, seleccionados.size(), intento - seleccionados.size(),
                candidatosRestantes, tapsExitosos, count);

            SeatMap.Seat candidato = picker.pick(mapa, excluidos);
            if (candidato == null) {
                log.warn("[SeatSelectionEngine] Sin más candidatos ({}/{} confirmados, {} descartados, {} taps exitosos).",
                    seleccionados.size(), count, excluidos.size(), tapsExitosos);
                break;
            }

            intento++;
            excluidos.add(candidato.number);
            String locator = page.locatorAsientoPorNumero(candidato.number);

            // Resolución OBLIGATORIA contra el árbol actual — nunca candidato.element
            // (proviene del escaneo inicial y puede llevar minutos/varios taps de
            // antigüedad). Consulta dirigida a UN número, jamás un reescaneo completo.
            long tResolver = System.currentTimeMillis();
            WebElement objetivo = page.reubicarAsientoPorNumero(candidato.number);
            long tiempoResolver = System.currentTimeMillis() - tResolver;

            if (objetivo == null) {
                log.warn("[SeatSelectionEngine] Intento {} → A{} NO se pudo resolver (locator={}, {}ms) — "
                    + "se descarta SIN tapear.", intento, candidato.number, locator, tiempoResolver);
                utils.PerfMetrics.attempt("SeatSelection", intento, "A" + candidato.number, tiempoResolver, "FAIL-NO-RESUELTO");
                continue;
            }
            if (!respondeAtributosBasicos(objetivo)) {
                log.warn("[SeatSelectionEngine] Intento {} → A{} se encontró pero no respondió label/enabled "
                    + "(locator={}, {}ms) — elemento no confiable, se descarta SIN tapear.",
                    intento, candidato.number, locator, tiempoResolver);
                utils.PerfMetrics.attempt("SeatSelection", intento, "A" + candidato.number, tiempoResolver, "FAIL-ATRIBUTOS-INVALIDOS");
                continue;
            }

            log.info("[SeatSelectionEngine] Intento {} → A{} resuelto (locator={}, {}ms) antes del tap: {}",
                intento, candidato.number, locator, tiempoResolver, describir(objetivo));

            long tClick = System.currentTimeMillis();
            boolean tapOk = page.tapRapidoEnButacaDesdeLabel(objetivo);
            long tiempoTap = System.currentTimeMillis() - tClick;
            if (tapOk) tapsExitosos++;

            long tValidacion = System.currentTimeMillis();
            boolean confirmado = false;
            String estadoFinal = "no se validó (tap falló)";
            if (tapOk) {
                page.sleep(400);
                // Se vuelve a resolver — NUNCA se reutiliza `objetivo` para la
                // validación post-tap, por la misma razón por la que no se reutiliza
                // candidato.element: el tap puede haber invalidado el árbol de nuevo.
                WebElement revalidado = page.reubicarAsientoPorNumero(candidato.number);
                if (revalidado != null) {
                    confirmado = estaSeleccionado(revalidado);
                    estadoFinal = describir(revalidado);
                } else {
                    estadoFinal = "no se pudo revalidar A" + candidato.number + " tras el tap (búsqueda dirigida, sin escaneo completo)";
                }
            }
            long tiempoValidacion = System.currentTimeMillis() - tValidacion;

            log.info("[SeatSelectionEngine] Después del tap A{} → {}", candidato.number, estadoFinal);
            utils.PerfMetrics.note("SeatSelection", String.format(
                "intento=%d asiento=A%d locator=%s resolverMs=%d tapMs=%d validacionMs=%d",
                intento, candidato.number, locator, tiempoResolver, tiempoTap, tiempoValidacion));
            utils.PerfMetrics.attempt("SeatSelection", intento, "A" + candidato.number, tiempoTap,
                confirmado ? "OK" : "FAIL");

            if (confirmado) {
                seleccionados.add(candidato.toString());
                log.info("[SeatSelectionEngine] Asiento confirmado: A{}", candidato.number);
            } else {
                log.warn("[SeatSelectionEngine] Asiento A{} descartado (tapOk={}, confirmado=false) — "
                    + "se intenta otro candidato del mismo escaneo inicial (sin reescanear).", candidato.number, tapOk);
            }
        }

        if (seleccionados.size() < count) {
            throw new RuntimeException(
                "Solo se pudieron seleccionar " + seleccionados.size() + " de " + count + " asientos.");
        }
        return seleccionados;
    }

    private static boolean estaSeleccionado(WebElement el) {
        return "true".equalsIgnoreCase(el.getAttribute("selected"));
    }

    /**
     * Valida que el elemento resuelto responde de verdad contra el árbol ACTUAL
     * antes de tapear — si label/enabled lanzan excepción (StaleElementReferenceException
     * u otra), el handle no es confiable aunque reubicarAsientoPorNumero() lo haya
     * devuelto no-nulo un instante antes.
     */
    private static boolean respondeAtributosBasicos(WebElement el) {
        try {
            el.getAttribute("label");
            el.isEnabled();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String describir(WebElement el) {
        return String.format("label=%s value=%s name=%s type=%s enabled=%s selected=%s frame=%s",
            safe(() -> el.getAttribute("label")),
            safe(() -> el.getAttribute("value")),
            safe(() -> el.getAttribute("name")),
            safe(() -> el.getAttribute("type")),
            safe(() -> String.valueOf(el.isEnabled())),
            safe(() -> el.getAttribute("selected")),
            safe(() -> String.valueOf(el.getRect())));
    }

    private static String safe(Supplier<String> fn) {
        try { return fn.get(); } catch (Exception e) { return "N/D"; }
    }
}
