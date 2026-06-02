package pages.asientos;

import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Modelo del estado actual del mapa de asientos capturado desde pantalla.
 *
 * Responsabilidades:
 *  - Agrupar los WebElements crudos en filas y asientos estructurados.
 *  - Ofrecer múltiples estrategias de selección sin depender de posiciones fijas.
 *  - Centralizar toda la inteligencia de búsqueda para que todos los métodos
 *    de SelectorPage usen exactamente el mismo algoritmo.
 *
 * Estrategias de selectN(count):
 *  S1 – count asientos consecutivos (numeración secuencial)
 *  S2 – (count-1) consecutivos + 1 adicional en la misma fila  [solo count == 3]
 *  S3 – cualquier count asientos numerados disponibles
 *  S4 – cualquier count asientos (incluso sin número)
 *  null – menos de count asientos en total (prueba debe omitirse)
 */
public final class SeatMap {

    private static final Logger log = LoggerFactory.getLogger(SeatMap.class);

    private static final int ROW_TOLERANCE_PX = 24;

    // ─────────────────────────────────────────────────────────────────────────
    // Seat — un asiento individual
    // ─────────────────────────────────────────────────────────────────────────

    public static final class Seat {
        public final int        number;   // etiqueta numérica visible; -1 si no tiene número
        public final int        x;        // centro X en pantalla
        public final int        y;        // centro Y en pantalla
        public final int        width;
        public final int        height;
        public final WebElement element;

        Seat(int number, int x, int y, int width, int height, WebElement element) {
            this.number  = number;
            this.x       = x;
            this.y       = y;
            this.width   = width;
            this.height  = height;
            this.element = element;
        }

        @Override
        public String toString() {
            return number > 0 ? "A" + number : "(" + x + "," + y + ")";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row — una fila del mapa, ordenada de izquierda a derecha
    // ─────────────────────────────────────────────────────────────────────────

    public static final class Row {
        public final int        y;      // Y representativa de la fila (píxeles)
        public final List<Seat> seats;  // ordenados por X ascendente

        Row(int y, List<Seat> seats) {
            this.y     = y;
            this.seats = Collections.unmodifiableList(new ArrayList<>(seats));
        }

        /**
         * Devuelve todos los bloques de exactamente {@code count} asientos
         * con números estrictamente consecutivos en esta fila.
         */
        public List<List<Seat>> consecutiveBlocks(int count) {
            List<Seat> numbered = seats.stream()
                .filter(s -> s.number > 0)
                .sorted(Comparator.comparingInt(s -> s.x))
                .collect(Collectors.toList());

            List<List<Seat>> blocks = new ArrayList<>();
            for (int i = 0; i <= numbered.size() - count; i++) {
                boolean ok = true;
                for (int j = 1; j < count; j++) {
                    if (numbered.get(i + j).number != numbered.get(i + j - 1).number + 1) {
                        ok = false;
                        break;
                    }
                }
                if (ok) blocks.add(new ArrayList<>(numbered.subList(i, i + count)));
            }
            return blocks;
        }

        /** Línea de diagnóstico: "Fila Y=### [1,2,?,4,5]" */
        public String diagnose() {
            StringBuilder sb = new StringBuilder("Fila Y=").append(y).append(" [");
            for (Seat s : seats) sb.append(s.number > 0 ? s.number : "?").append(",");
            if (!seats.isEmpty()) sb.setLength(sb.length() - 1);
            return sb.append("]").toString();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SelectionResult — qué se va a seleccionar y con qué estrategia
    // ─────────────────────────────────────────────────────────────────────────

    public static final class SelectionResult {
        public final List<Seat> seats;
        public final String     strategy;

        SelectionResult(List<Seat> seats, String strategy) {
            this.seats    = Collections.unmodifiableList(new ArrayList<>(seats));
            this.strategy = strategy;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado del mapa
    // ─────────────────────────────────────────────────────────────────────────

    private final List<Row> rows;
    private final int       totalSeats;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public SeatMap(List<WebElement> raw) {
        this.rows       = buildRows(raw);
        this.totalSeats = rows.stream().mapToInt(r -> r.seats.size()).sum();
    }

    private static List<Row> buildRows(List<WebElement> elements) {
        Map<Integer, List<Seat>> grouped = new LinkedHashMap<>();

        for (WebElement el : elements) {
            try {
                org.openqa.selenium.Rectangle r = el.getRect();
                int cx = r.getX() + r.getWidth()  / 2;
                int cy = r.getY() + r.getHeight() / 2;

                int number = -1;
                try {
                    String txt = el.getText().trim();
                    if (txt.matches("^\\d{1,2}$")) number = Integer.parseInt(txt);
                } catch (Exception ignored) {}

                Seat seat = new Seat(number, cx, cy, r.getWidth(), r.getHeight(), el);

                Integer rowKey = null;
                for (Integer k : grouped.keySet()) {
                    if (Math.abs(k - cy) <= ROW_TOLERANCE_PX) { rowKey = k; break; }
                }
                if (rowKey == null) { rowKey = cy; grouped.put(rowKey, new ArrayList<>()); }
                grouped.get(rowKey).add(seat);

            } catch (Exception ignored) {}
        }

        List<Row> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Seat>> e : grouped.entrySet()) {
            List<Seat> sorted = e.getValue().stream()
                .sorted(Comparator.comparingInt(s -> s.x))
                .collect(Collectors.toList());
            result.add(new Row(e.getKey(), sorted));
        }
        result.sort(Comparator.comparingInt(r -> r.y));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    public boolean   isEmpty()       { return totalSeats == 0; }
    public int       getTotalSeats() { return totalSeats; }
    public List<Row> getRows()       { return Collections.unmodifiableList(rows); }

    /** Todos los bloques de N asientos consecutivos en toda la sala. */
    public List<List<Seat>> allConsecutiveBlocks(int count) {
        List<List<Seat>> all = new ArrayList<>();
        for (Row row : rows) all.addAll(row.consecutiveBlocks(count));
        return all;
    }

    /** Todos los asientos que tienen etiqueta numérica visible. */
    public List<Seat> allNumberedSeats() {
        return rows.stream()
            .flatMap(r -> r.seats.stream())
            .filter(s -> s.number > 0)
            .collect(Collectors.toList());
    }

    /** Todos los asientos detectados (con o sin número). */
    public List<Seat> allSeats() {
        return rows.stream()
            .flatMap(r -> r.seats.stream())
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estrategia principal: selectN — elige la mejor forma de obtener N asientos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve la mejor combinación posible de {@code count} asientos
     * usando la estrategia más favorable disponible.
     *
     * <pre>
     * S1 – count consecutivos (numeración secuencial estricta)
     * S2 – (count-1) consecutivos + 1 en la misma fila          [solo count == 3]
     * S3 – cualquier count asientos numerados
     * S4 – cualquier count asientos (sin número)
     * </pre>
     *
     * Devuelve {@code null} solo cuando hay menos de {@code count} asientos totales
     * → la prueba debe omitirse con {@code Assumptions.abort()}.
     */
    public SelectionResult selectN(int count) {
        if (totalSeats < count) {
            log.warn("[SeatMap] Solo {} asientos disponibles; se necesitan {}.", totalSeats, count);
            return null;
        }

        // ── S1: count consecutivos ────────────────────────────────────────────
        List<List<Seat>> blocks = allConsecutiveBlocks(count);
        if (!blocks.isEmpty()) {
            Collections.shuffle(blocks);
            List<Seat> chosen = blocks.get(0);
            log.info("[SeatMap] S1: {} consecutivos — {} bloque(s) encontrados, usando {}",
                count, blocks.size(), chosen);
            return new SelectionResult(chosen, "S1: " + count + " consecutivos");
        }
        log.debug("[SeatMap] S1: sin bloques de {} consecutivos.", count);

        // ── S2: (count-1) consecutivos + 1 cercano en la misma fila ─────────
        if (count == 3) {
            for (Row row : rows) {
                List<List<Seat>> pairs = row.consecutiveBlocks(2);
                if (pairs.isEmpty()) continue;

                List<Seat> pair = pairs.get(0);
                Set<Seat> pairSet = new HashSet<>(pair);

                for (Seat candidate : row.seats) {
                    if (pairSet.contains(candidate) || candidate.number <= 0) continue;
                    List<Seat> chosen = new ArrayList<>(pair);
                    chosen.add(candidate);
                    log.info("[SeatMap] S2: par {} + adicional {} en fila Y={}", pair, candidate, row.y);
                    return new SelectionResult(chosen, "S2: 2 consecutivos + 1 adicional");
                }
            }
            log.debug("[SeatMap] S2: sin pares consecutivos disponibles.");
        }

        // ── S3: cualquier N numerados ─────────────────────────────────────────
        List<Seat> numbered = allNumberedSeats();
        if (numbered.size() >= count) {
            Collections.shuffle(numbered);
            List<Seat> chosen = new ArrayList<>(numbered.subList(0, count));
            log.info("[SeatMap] S3: {} numerados aleatorios — {}", count, chosen);
            return new SelectionResult(chosen, "S3: " + count + " numerados");
        }
        log.debug("[SeatMap] S3: solo {} asientos numerados (< {}).", numbered.size(), count);

        // ── S4: cualquier N (sin número) ──────────────────────────────────────
        List<Seat> all = allSeats();
        if (all.size() >= count) {
            Collections.shuffle(all);
            List<Seat> chosen = new ArrayList<>(all.subList(0, count));
            log.info("[SeatMap] S4: {} asientos sin número — {}", count, chosen);
            return new SelectionResult(chosen, "S4: " + count + " sin número");
        }

        log.warn("[SeatMap] Sin suficientes asientos para ninguna estrategia.");
        return null;
    }

    /**
     * Selecciona {@code count} asientos cualquiera (sin preferencia de consecutividad).
     * Usado para selecciones aleatorias que no requieren orden espacial.
     */
    public SelectionResult selectAnyN(int count) {
        if (totalSeats < count) return null;

        List<Seat> numbered = allNumberedSeats();
        if (numbered.size() >= count) {
            Collections.shuffle(numbered);
            return new SelectionResult(
                new ArrayList<>(numbered.subList(0, count)),
                "Aleatorio " + count + " numerados");
        }

        List<Seat> all = allSeats();
        if (all.size() >= count) {
            Collections.shuffle(all);
            return new SelectionResult(
                new ArrayList<>(all.subList(0, count)),
                "Aleatorio " + count + " cualquiera");
        }

        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diagnóstico / logging
    // ─────────────────────────────────────────────────────────────────────────

    public String getSummary() {
        long numbered = rows.stream()
            .flatMap(r -> r.seats.stream())
            .filter(s -> s.number > 0)
            .count();
        return String.format("Filas=%d | Total=%d | Con número=%d",
            rows.size(), totalSeats, numbered);
    }

    public void logMap() {
        if (rows.isEmpty()) {
            log.info("[SeatMap] Mapa vacío — sin asientos detectados.");
            return;
        }

        StringBuilder sb = new StringBuilder("[SeatMap] Estado del mapa:\n");
        for (Row row : rows) {
            sb.append("  ").append(row.diagnose()).append("\n");
        }

        List<List<Seat>> b3 = allConsecutiveBlocks(3);
        sb.append(String.format("  Libres detectados: %d | Filas: %d | Bloques de 3 consecutivos: %d",
            totalSeats, rows.size(), b3.size()));

        if (!b3.isEmpty()) {
            String preview = b3.stream()
                .limit(3)
                .map(b -> b.stream().map(Seat::toString).collect(Collectors.joining("-")))
                .collect(Collectors.joining(", "));
            sb.append(" → ").append(preview);
        }

        log.info(sb.toString().trim());
    }
}
