package utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BaseTestStatusRegistry {

    private BaseTestStatusRegistry() {}

    // Estado por test (uniqueId)
    // true = falló alguna vez (latch), false = no ha fallado
    private static final Map<String, Boolean> FAILED_BY_ID = new ConcurrentHashMap<>();

    // Conteos globales de la ejecución
    private static final AtomicInteger TOTAL = new AtomicInteger(0);
    private static final AtomicInteger PASSED = new AtomicInteger(0);
    private static final AtomicInteger FAILED = new AtomicInteger(0);

    // Nombre de ejecución/proyecto (para PDF + email)
    private static volatile String executionName = "Cinépolis";

    // =========================
    // Inicialización / reset
    // =========================

    public static void resetForRun(String executionNameFromCaller) {
        FAILED_BY_ID.clear();
        TOTAL.set(0);
        PASSED.set(0);
        FAILED.set(0);
        executionName = (executionNameFromCaller == null || executionNameFromCaller.isBlank())
                ? "Cinépolis"
                : executionNameFromCaller.trim();
    }

    public static String getExecutionName() {
        return executionName;
    }

    // =========================
    // Ciclo de vida del test
    // =========================

    /** Llamar al iniciar un test (1 vez por test). */
    public static void onTestStart(String uniqueId) {
        // inicializa el estado si no existe
        FAILED_BY_ID.putIfAbsent(uniqueId, false);
        TOTAL.incrementAndGet();
    }

    /** Llamar cuando JUnit marca el test exitoso. */
    public static void markPassed(String uniqueId) {
        // Si ya falló antes, NO se puede volver a pasar
        if (FAILED_BY_ID.getOrDefault(uniqueId, false)) {
            return;
        }
        // Evita doble conteo si el watcher llama 2 veces por alguna razón
        // (sólo cuenta passed si estaba en false y lo dejamos en false)
        PASSED.incrementAndGet();
    }

    /** Llamar cuando JUnit marca el test fallido. */
    public static void markFailed(String uniqueId, Throwable t) {
        boolean alreadyFailed = FAILED_BY_ID.getOrDefault(uniqueId, false);
        FAILED_BY_ID.put(uniqueId, true); // latch

        // Solo incrementa failed la primera vez que pasa a true
        if (!alreadyFailed) {
            FAILED.incrementAndGet();
        }
    }

    public static boolean isFailed(String uniqueId) {
        return FAILED_BY_ID.getOrDefault(uniqueId, false);
    }

    /** Limpieza opcional (si corres muchas suites en el mismo JVM). */
    public static void clear(String uniqueId) {
        FAILED_BY_ID.remove(uniqueId);
    }

    // =========================
    // Datos para PDF / email
    // =========================

    public static int getTotal() {
        return TOTAL.get();
    }

    public static int getPassed() {
        return PASSED.get();
    }

    public static int getFailed() {
        return FAILED.get();
    }

    /** “PASSED” solo si failed == 0 */
    public static boolean runPassed() {
        return getFailed() == 0;
    }
}
