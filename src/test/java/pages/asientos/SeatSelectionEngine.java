package pages.asientos;

import org.openqa.selenium.StaleElementReferenceException;
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
 * (no por asiento). Los WebElement de los asientos NO tocados no tienen
 * motivo para invalidarse solo porque se tocó otro; el estado del asiento
 * recién tocado se revalida leyendo sus atributos directamente sobre el
 * mismo handle (sin findElements). Solo si esa lectura lanza
 * {@link StaleElementReferenceException} se hace una relocalización DIRIGIDA
 * a ese único número (nunca un reescaneo de los ~143 candidatos) vía
 * {@link SelectorPage#reubicarAsientoPorNumero(int)}.
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

        while (seleccionados.size() < count) {
            SeatMap.Seat candidato = picker.pick(mapa, excluidos);
            if (candidato == null) {
                log.warn("[SeatSelectionEngine] Sin más candidatos ({}/{} confirmados, {} descartados).",
                    seleccionados.size(), count, excluidos.size());
                break;
            }

            intento++;
            excluidos.add(candidato.number);
            log.info("[SeatSelectionEngine] Intento {} → candidato A{} antes del tap: {}",
                intento, candidato.number, describir(candidato.element));

            long tClick = System.currentTimeMillis();
            boolean tapOk = page.tapRapidoEnButacaDesdeLabel(candidato.element);
            long tiempoTap = System.currentTimeMillis() - tClick;

            long tValidacion = System.currentTimeMillis();
            boolean reubicado = false;
            boolean confirmado = false;
            String estadoFinal = "no se validó (tap falló)";
            if (tapOk) {
                page.sleep(400);
                WebElement objetivo = candidato.element;
                try {
                    confirmado = estaSeleccionado(objetivo);
                    estadoFinal = describir(objetivo);
                } catch (StaleElementReferenceException stale) {
                    reubicado = true;
                    objetivo = page.reubicarAsientoPorNumero(candidato.number);
                    if (objetivo != null) {
                        confirmado = estaSeleccionado(objetivo);
                        estadoFinal = describir(objetivo);
                    } else {
                        estadoFinal = "no se pudo relocalizar A" + candidato.number + " (búsqueda dirigida, sin escaneo completo)";
                    }
                }
            }
            long tiempoValidacion = System.currentTimeMillis() - tValidacion;

            log.info("[SeatSelectionEngine] Después del tap A{} → {} (relocalizado={})",
                candidato.number, estadoFinal, reubicado);
            utils.PerfMetrics.note("SeatSelection", String.format(
                "intento=%d asiento=A%d tap=%dms validacion=%dms relocalizado=%s",
                intento, candidato.number, tiempoTap, tiempoValidacion, reubicado));
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
