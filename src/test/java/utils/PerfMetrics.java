package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Instrumentación de rendimiento por fase — puramente observabilidad (solo logging),
 * cero impacto funcional: no agrega esperas, no cambia ningún resultado, no afecta
 * qué elemento se busca o se toca. Por eso es seguro usarla en código COMPARTIDO por
 * Android e iOS (ver disciplina "¿Este cambio afecta Android?" — la respuesta aquí es
 * "no": añadir una línea de log no puede romper ninguna aserción ni flujo existente).
 *
 * Uso:
 *   PerfMetrics.startPhase("PromosGuard");
 *   ...
 *   PerfMetrics.endPhase("PromosGuard");
 *
 *   PerfMetrics.attempt("MovieOpen", 1, "La Odisea", 842, "OK");
 *
 * Estado por-hilo (ThreadLocal): cada test JUnit corre en su propio hilo ("Test worker"),
 * y REUSE_DRIVER reutiliza ese mismo hilo secuencialmente entre tests — un ThreadLocal
 * es correcto aquí (no hay fases concurrentes dentro del mismo test) y evita que fases
 * de un hilo interfieran con las de otro si alguna vez se paraleliza la ejecución.
 */
public final class PerfMetrics {

    private static final Logger log = LoggerFactory.getLogger(PerfMetrics.class);

    private static final ThreadLocal<Map<String, Long>> STARTS =
            ThreadLocal.withInitial(HashMap::new);

    private PerfMetrics() {}

    /** Marca el inicio de una fase (p. ej. "PromosGuard", "MovieDetection"). */
    public static void startPhase(String phase) {
        STARTS.get().put(phase, System.currentTimeMillis());
        log.info("[METRICS][{}] INICIO", phase);
    }

    /**
     * Marca el fin de una fase iniciada con {@link #startPhase(String)} y registra su
     * duración total. Si la fase nunca se inició (o ya se cerró), registra total=-1 en
     * vez de lanzar — la instrumentación nunca debe poder romper el flujo del test.
     *
     * @return la duración en ms, o -1 si no había un inicio registrado para esta fase.
     */
    public static long endPhase(String phase) {
        Long t0 = STARTS.get().remove(phase);
        long elapsed = (t0 != null) ? (System.currentTimeMillis() - t0) : -1;
        log.info("[METRICS][{}] FIN total={}ms", phase, elapsed);
        return elapsed;
    }

    /**
     * Registra un intento individual dentro de una fase (p. ej. cada película probada
     * dentro de MovieOpen, cada horario probado dentro de ScheduleSelection).
     *
     * @param phase     fase a la que pertenece el intento (debe coincidir con el nombre
     *                  usado en startPhase/endPhase para poder correlacionarlos en logs).
     * @param intento   número de intento, 1-based.
     * @param elemento  identificador legible del elemento probado (nombre de película,
     *                  texto del horario, etc.) — nunca null (usar "" si no aplica).
     * @param tiempoMs  duración de ESTE intento en ms.
     * @param resultado resultado corto y consistente (p. ej. "OK", "FAIL", "SKIP").
     */
    public static void attempt(String phase, int intento, String elemento, long tiempoMs, String resultado) {
        log.info("[METRICS][{}] intento={} elemento='{}' tiempo={}ms resultado={}",
                phase, intento, elemento == null ? "" : elemento, tiempoMs, resultado);
    }

    /**
     * Envuelve una fase completa (start/end automático) alrededor de un bloque de código
     * que devuelve un valor — evita olvidar el endPhase() en algún camino de retorno o
     * excepción. El endPhase() se ejecuta siempre, incluso si el bloque lanza.
     */
    public static <T> T measure(String phase, java.util.function.Supplier<T> block) {
        startPhase(phase);
        try {
            return block.get();
        } finally {
            endPhase(phase);
        }
    }

    /** Variante sin valor de retorno de {@link #measure(String, java.util.function.Supplier)}. */
    public static void measure(String phase, Runnable block) {
        startPhase(phase);
        try {
            block.run();
        } finally {
            endPhase(phase);
        }
    }
}
