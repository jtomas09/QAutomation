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
 * Único responsable de refrescar el mapa de asientos entre cada tap.
 *
 * FIX real (evidencia forense — ejecución 2026-08-11 11:18:58, log
 * automationqa-runner.log líneas ~1705900-1706193): antes, cada método de
 * selección múltiple construía el SeatMap UNA sola vez y reutilizaba los
 * mismos WebElement para los N taps. El log de WebDriverAgent muestra que,
 * tras el primer tap, el árbol XCUI se invalida ("Find the '2' Button
 * (retry 1)", "(retry 2)", fallback "Get all elements bound by index") y los
 * WebElement restantes ya no se pueden resolver de forma confiable. Además,
 * "tap sin excepción" nunca implicó "asiento seleccionado" — no existía
 * ninguna verificación posterior.
 *
 * Este motor reconstruye el mapa antes de elegir cada candidato y vuelve a
 * reconstruirlo después del tap para confirmar el estado "selected" real del
 * mismo número de asiento — nunca reutiliza un WebElement de un escaneo
 * anterior. Es el único lugar donde vive esta lógica de refresco; cualquier
 * método de SelectorPage que seleccione N asientos (random, consecutivos,
 * VIP, etc.) debe apoyarse en {@link #select(int, SeatPicker)} en vez de
 * repetirla.
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
     * Selecciona {@code count} asientos, reconstruyendo el mapa antes de cada
     * candidato y confirmando la selección real antes de continuar con el
     * siguiente. Nunca reintenta un número ya confirmado o descartado.
     *
     * @throws RuntimeException si se agotan los candidatos disponibles antes
     *         de reunir {@code count} asientos confirmados.
     */
    List<String> select(int count, SeatPicker picker) {
        List<String> seleccionados = new ArrayList<>();
        Set<Integer> excluidos = new HashSet<>();
        int intento = 0;

        while (seleccionados.size() < count) {
            SeatMap mapaActual = page.buildSeatMap();
            SeatMap.Seat candidato = picker.pick(mapaActual, excluidos);
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
            long tiempoMs = System.currentTimeMillis() - tClick;

            boolean confirmado = false;
            if (tapOk) {
                page.sleep(150);
                WebElement reubicado = localizarPorNumero(page.buildSeatMap(), candidato.number);
                confirmado = reubicado != null && estaSeleccionado(reubicado);
                log.info("[SeatSelectionEngine] Después del tap A{} → {}", candidato.number,
                    reubicado != null ? describir(reubicado) : "no se pudo relocalizar el asiento");
            }

            utils.PerfMetrics.attempt("SeatSelection", intento, "A" + candidato.number, tiempoMs,
                confirmado ? "OK" : "FAIL");

            if (confirmado) {
                seleccionados.add(candidato.toString());
                log.info("[SeatSelectionEngine] Asiento confirmado: A{}", candidato.number);
            } else {
                log.warn("[SeatSelectionEngine] Asiento A{} descartado (tapOk={}, confirmado=false) — "
                    + "se reconstruye el mapa y se intenta otro candidato.", candidato.number, tapOk);
            }
        }

        if (seleccionados.size() < count) {
            throw new RuntimeException(
                "Solo se pudieron seleccionar " + seleccionados.size() + " de " + count + " asientos.");
        }
        return seleccionados;
    }

    private static WebElement localizarPorNumero(SeatMap map, int numero) {
        return map.allNumberedSeats().stream()
            .filter(s -> s.number == numero)
            .map(s -> s.element)
            .findFirst()
            .orElse(null);
    }

    private static boolean estaSeleccionado(WebElement el) {
        try {
            return "true".equalsIgnoreCase(el.getAttribute("selected"));
        } catch (Exception e) {
            return false;
        }
    }

    private static String describir(WebElement el) {
        return String.format("label=%s value=%s enabled=%s selected=%s frame=%s",
            safe(() -> el.getAttribute("label")),
            safe(() -> el.getAttribute("value")),
            safe(() -> String.valueOf(el.isEnabled())),
            safe(() -> el.getAttribute("selected")),
            safe(() -> String.valueOf(el.getRect())));
    }

    private static String safe(Supplier<String> fn) {
        try { return fn.get(); } catch (Exception e) { return "N/D"; }
    }
}
