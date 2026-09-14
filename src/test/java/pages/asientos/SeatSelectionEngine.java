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

        // FIX real (TAREA performance/failure — diagnóstico RUN-1003, "Selección de
        // Múltiples Asientos": A6 confirmado, luego A1 y A15 con describir()=N/D en
        // TODOS los atributos — el WebElement de candidato.element, cacheado en el
        // escaneo inicial, queda obsoleto tras el primer tap real que muta el árbol
        // XCUITest). El freno de seguridad anterior (`tapsExitosos >= count`) no dejaba
        // NINGÚN margen de reintento: si 2 de los primeros 3 taps caían en un handle
        // obsoleto, el motor fallaba sin remedio aunque quedaran 180+ candidatos
        // viables. Se reemplaza por un presupuesto acotado de intentos totales
        // (objetivo + margen fijo) — sigue habiendo un límite duro de taps reales
        // sobre la app (no crece sin control), pero permite descartar unos pocos
        // candidatos obsoletos y seguir con el siguiente sin fallar de inmediato.
        final int RETRY_BUDGET = 6;
        final int maxIntentosTotales = count + RETRY_BUDGET;

        while (seleccionados.size() < count) {
            if (intento >= maxIntentosTotales) {
                log.warn("[SeatSelectionEngine] DETENIDO por freno de seguridad: se alcanzó el presupuesto "
                    + "de {} intento(s) totales (objetivo={} + margen={}) con solo {} confirmado(s) — no se "
                    + "intentan más candidatos.", maxIntentosTotales, count, RETRY_BUDGET, seleccionados.size());
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

            int beforeCount = contadorPrevio;

            // FIX real (ver comentario del RETRY_BUDGET arriba): antes de tapear, se
            // intenta revalidar/relocalizar el candidato con una consulta DIRIGIDA por
            // número (reubicarAsientoPorNumero — un solo elemento, no un re-escaneo
            // completo del mapa) para obtener una coordenada fresca. Es exactamente el
            // mecanismo que el historial de este archivo documentó como "arregla iOS
            // pero es intermitente en Android" cuando se usaba como ÚNICA fuente de
            // verdad (incluida la confirmación por atributos) — aquí se usa SOLO para
            // refrescar x/y antes del tap, nunca para confirmar la selección (eso sigue
            // siendo 100% el contador de "Continuar"), y con fallback inmediato a
            // candidato.element/candidato.x/y si la relocalización falla — nunca peor
            // que el comportamiento anterior.
            // FIX real (evidencia real — RUN-1005, ">10 Asientos" con el mismo
            // mecanismo: el contador osciló hacia abajo varias veces porque
            // reubicarAsientoPorNumero(numero) puede devolver el asiento de OTRA FILA
            // con el mismo número visible — cada fila tiene su propio "7", "9", etc. —
            // y tapear ese duplicado DESELECCIONA un asiento ya confirmado en vez de
            // seleccionar uno nuevo. Se descarta la relocalización si su Y queda lejos
            // del Y cacheado del candidato original (filas distintas están separadas
            // por decenas de px; la misma fila real nunca se mueve tanto).
            final int TOLERANCIA_FILA_PX = 40;
            boolean revalidated = false;
            WebElement objetivo = candidato.element;
            try {
                WebElement fresco = page.reubicarAsientoPorNumero(candidato.number);
                if (fresco != null) {
                    int freshY = fresco.getRect().getY() + fresco.getRect().getHeight() / 2;
                    if (Math.abs(freshY - candidato.y) <= TOLERANCIA_FILA_PX) {
                        objetivo = fresco;
                        revalidated = true;
                    }
                    // Y lejano del esperado → probable colisión de número entre filas;
                    // se conserva candidato.element (coordenadas cacheadas) sin cambio.
                }
            } catch (Exception ignored) {
                // se conserva candidato.element / coordenadas cacheadas sin cambio
            }

            if (objetivo == null) {
                log.warn("[SeatSelectionEngine] Intento {} → A{} sin elemento en el escaneo original — "
                    + "se descarta SIN tapear.", intento, candidato.number);
                utils.PerfMetrics.attempt("SeatSelection", intento, "A" + candidato.number, 0, "FAIL-SIN-ELEMENTO");
                continue;
            }
            String locator = revalidated
                    ? "relocalizado (reubicarAsientoPorNumero)"
                    : "scan-original (candidato.element, x=" + candidato.x + " y=" + candidato.y + ")";
            long tiempoResolver = 0;

            log.info("[SeatSelectionEngine] Intento {} → A{} usa {}: {}",
                intento, candidato.number, locator, describir(objetivo));

            long tClick = System.currentTimeMillis();
            boolean tapOk = revalidated
                    ? tapDirectoSobreElemento(objetivo, candidato)
                    : page.tapRapidoEnButacaDesdeLabel(candidato);
            long tiempoTap = System.currentTimeMillis() - tClick;
            if (tapOk) tapsExitosos++;

            long tValidacion = System.currentTimeMillis();
            boolean confirmado = false;
            int afterCount = beforeCount;
            String estadoFinal = "no se validó (tap falló)";
            if (tapOk) {
                page.sleep(400);
                // Única fuente de verdad: el contador real de "Continuar" — nunca se
                // interpreta un tap ejecutado como selección exitosa por sí solo.
                afterCount = page.contarAsientosSeleccionadosPorBotonContinuar();
                confirmado = afterCount == beforeCount + 1;
                estadoFinal = String.format("contador Continuar %d -> %d (esperado %d)",
                    beforeCount, afterCount, beforeCount + 1);
                if (!confirmado && afterCount != beforeCount) {
                    log.warn("[SeatSelectionEngine] Anomalía: contador Continuar cambió de forma "
                        + "inesperada ({} -> {}) tras A{}.", beforeCount, afterCount, candidato.number);
                }
                contadorPrevio = afterCount; // resincroniza siempre con el valor real observado
            }
            long tiempoValidacion = System.currentTimeMillis() - tValidacion;

            log.info("[SeatSelectionEngine] Después del tap A{} → {}", candidato.number, estadoFinal);
            log.info("[SeatSelection] requested={} beforeCount={} candidate=A{} tap={} afterCount={} "
                    + "confirmed={} attempt={} revalidated={}",
                    count, beforeCount, candidato.number, tapOk, afterCount, confirmado, intento, revalidated);
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
                    + "se intenta el siguiente candidato (relocalizado).", candidato.number, tapOk);
            }
        }

        if (seleccionados.size() < count) {
            throw new RuntimeException(
                "Solo se pudieron seleccionar " + seleccionados.size() + " de " + count + " asientos reales confirmados por el contador de \"Continuar\".");
        }
        return seleccionados;
    }

    /** Tap directo por getRect() de un elemento recién relocalizado, con fallback a las coordenadas cacheadas del escaneo original. */
    private boolean tapDirectoSobreElemento(WebElement fresco, SeatMap.Seat candidatoOriginal) {
        try {
            org.openqa.selenium.Rectangle r = fresco.getRect();
            page.tapW3C(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
            return true;
        } catch (Exception e) {
            return page.tapRapidoEnButacaDesdeLabel(candidatoOriginal);
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
