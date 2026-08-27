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
 * automationqa-runner.log líneas ~1705900-1706193, SOLO iOS): la versión ORIGINAL
 * construía el SeatMap UNA sola vez y reutilizaba los mismos WebElement para los N
 * taps — tras el primer tap el árbol XCUI se invalidaba y los taps siguientes
 * fallaban. La versión que siguió (re-resolver cada asiento por número vía
 * {@code SelectorPage#reubicarAsientoPorNumero(int)}, antes y después de cada tap)
 * "arregló" ese caso de iOS, pero introdujo una regresión distinta en ANDROID: esa
 * misma re-resolución (androidUIAutomator {@code UiSelector().text(N)}) demostró ser
 * intermitentemente poco confiable (evidencia — diagnóstico en vivo contra
 * dispositivo Android real, candidato A7: el elemento re-resuelto y
 * {@code candidato.element} tenían atributos IDÉNTICOS y sin excepción justo después
 * del escaneo — descartando "nodo incorrecto" como causa — pero la MISMA consulta,
 * repetida segundos después sin que nada se hubiera tocado todavía, sí falló con
 * "no respondió label/enabled"), provocando que "Selección de Múltiples Asientos"
 * descartara el 100% de los candidatos sin llegar a tapear ninguno.
 *
 * Diseño actual (confirmado con evidencia real en ambas rutas): {@code select()}
 * hace UN solo escaneo completo (no por asiento) y usa {@code candidato.element}
 * DIRECTAMENTE para tapear — sin re-resolver por número — exactamente la misma
 * mecánica que ya usan con éxito, en Android, "Selección de Asientos Consecutivos" y
 * "...y Deselección de los Asientos" (tocan varios {@code seat.element} distintos del
 * mismo escaneo inicial, en secuencia, sin ningún problema). La confirmación de que
 * el tap realmente seleccionó el asiento no depende de re-leer atributos del propio
 * botón (getAttribute("selected") nunca cambia, evidencia ya documentada en
 * {@code contarAsientosSeleccionadosPorBotonContinuar()}) sino del contador real que
 * expone el botón "Continuar" de la app — confirmado ahora también en Android
 * (contador en un TextView hermano separado, distinto del formato "Continuar, N" de
 * iOS) en vez de asumirlo sin evidencia.
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
        return select(count, picker, mapa);
    }

    /**
     * Igual que {@link #select(int, SeatPicker)} pero reutilizando un {@link SeatMap}
     * ya construido por el llamador (p. ej. el que usó para validar "¿hay al menos
     * N asientos?" antes de invocar este método) — evita un segundo escaneo completo
     * idéntico. Evidencia (log 2026-08-13 14:07-14:18): sin este overload, el mismo
     * escaneo (~190s con ~190 candidatos) se ejecutaba dos veces seguidas por cada
     * caso, duplicando innecesariamente más de 3 minutos por corrida.
     */
    List<String> select(int count, SeatPicker picker, SeatMap mapa) {
        log.info("[SeatSelectionEngine] Escaneo inicial: {} asiento(s) numerado(s) disponibles.",
            mapa.allNumberedSeats().size());

        List<String> seleccionados = new ArrayList<>();
        Set<Integer> excluidos = new HashSet<>();
        int intento = 0;
        // FIX real (evidencia — build/seat-diagnostics/tap_tapOk-true_*, capturada con
        // SeatUiSnapshot en 3 taps consecutivos: A5→"Continuar, 1", A1→"Continuar, 2",
        // A2→"Continuar, 3"): getAttribute("selected") del propio botón de asiento
        // NUNCA cambia — pero el botón "Continuar" sí refleja el conteo REAL de
        // asientos seleccionados en la app. -1 significa "sin evidencia en esta
        // plataforma" (solo se investigó iOS) — en ese caso se conserva el criterio
        // anterior (selected) como fallback, sin asumir el mismo indicador sin prueba.
        int contadorPrevio = page.contarAsientosSeleccionadosPorBotonContinuar();
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

            // FIX real (causa raíz CONFIRMADA con diagnóstico en vivo contra dispositivo
            // Android real — evidencia: log [DIAG-ASIENTO], candidato A7. El elemento
            // re-resuelto por reubicarAsientoPorNumero() [UiSelector().text("7")] y
            // candidato.element [el mismo TextView del escaneo inicial] tenían EXACTAMENTE
            // los mismos atributos — className=android.widget.TextView, text=7, enabled=true,
            // clickable=false, focusable=false, displayed=true — sin ninguna excepción al
            // consultarlos justo después del escaneo. La hipótesis "el locator encuentra el
            // TextView en vez del contenedor interactivo" queda descartada: candidato.element
            // ES ese mismo TextView, y es exactamente lo que "Selección de Asientos
            // Consecutivos"/"...Deselección" tocan con éxito (tapRapidoEnButacaDesdeLabel()
            // tapea por COORDENADAS via getRect(), nunca depende de clickable/enabled). La
            // causa real es que reubicarAsientoPorNumero() — una consulta AndroidUIAutomator
            // fresca por texto — es intermitentemente poco confiable en este árbol (evidencia:
            // la MISMA consulta resuelta segundos antes sin problema falló luego con
            // "no respondió label/enabled" sin que nada se hubiera tocado todavía). Se elimina
            // esa re-resolución y su chequeo label/enabled asociado (que solo protegía contra
            // un handle roto por esa MISMA re-resolución) y se reutiliza candidato.element
            // directamente — la mecánica ya validada en Consecutivos/Deselección — para
            // Android y iOS por igual, ya que ambos flujos ya prueban que tocar varios
            // candidato.element distintos del mismo escaneo, en secuencia, funciona.
            WebElement objetivo = candidato.element;

            if (objetivo == null) {
                log.warn("[SeatSelectionEngine] Intento {} → A{} sin elemento en el escaneo original — "
                    + "se descarta SIN tapear.", intento, candidato.number);
                utils.PerfMetrics.attempt("SeatSelection", intento, "A" + candidato.number, 0, "FAIL-SIN-ELEMENTO");
                continue;
            }
            String locator = "scan-original (candidato.element, x=" + candidato.x + " y=" + candidato.y + ")";
            long tiempoResolver = 0; // no hay resolución adicional — se reutiliza el handle del escaneo

            log.info("[SeatSelectionEngine] Intento {} → A{} usa el elemento del escaneo original ({}): {}",
                intento, candidato.number, locator, describir(objetivo));

            long tClick = System.currentTimeMillis();
            boolean tapOk = page.tapRapidoEnButacaDesdeLabel(objetivo);
            long tiempoTap = System.currentTimeMillis() - tClick;
            if (tapOk) tapsExitosos++;

            long tValidacion = System.currentTimeMillis();
            boolean confirmado = false;
            String estadoFinal = "no se validó (tap falló)";
            if (tapOk) {
                page.sleep(400);
                if (contadorPrevio >= 0) {
                    // Mecanismo real: el contador del botón "Continuar" debe subir
                    // exactamente en 1 — no depende de qué asiento se tocó.
                    int contadorNuevo = page.contarAsientosSeleccionadosPorBotonContinuar();
                    confirmado = contadorNuevo == contadorPrevio + 1;
                    estadoFinal = String.format("contador Continuar %d -> %d (esperado %d)",
                        contadorPrevio, contadorNuevo, contadorPrevio + 1);
                    if (!confirmado && contadorNuevo != contadorPrevio) {
                        log.warn("[SeatSelectionEngine] Anomalía: contador Continuar cambió de forma "
                            + "inesperada ({} -> {}) tras A{}.", contadorPrevio, contadorNuevo, candidato.number);
                    }
                    contadorPrevio = contadorNuevo; // resincroniza siempre con el valor real observado
                } else {
                    // Sin evidencia del indicador en esta plataforma (no-iOS) — se conserva
                    // el criterio anterior en vez de asumir el mismo indicador sin prueba.
                    WebElement revalidado = page.reubicarAsientoPorNumero(candidato.number);
                    if (revalidado != null) {
                        confirmado = estaSeleccionado(revalidado);
                        estadoFinal = describir(revalidado);
                    } else {
                        estadoFinal = "no se pudo revalidar A" + candidato.number + " tras el tap (búsqueda dirigida, sin escaneo completo)";
                    }
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
