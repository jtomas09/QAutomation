package qa.cinepolis.runner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Única autoridad del ciclo de vida de PROCESOS LARGOS del Runner (git, Gradle, y —
 * a través de la cancelación que cada llamador provee — WDA), por executionId.
 *
 * ── Causa raíz que resuelve (evidencia real) ─────────────────────────────────────
 * El mecanismo de abort solo vigilaba el proceso Gradle: un abort durante
 * `git clone`/`git fetch`, o durante el Preflight de WDA, no detenía nada — el
 * proceso quedaba huérfano corriendo en segundo plano mientras el backend ya
 * marcaba la ejecución como ABORTED. Reproducido en vivo: un `git clone` seguía
 * activo minutos después de que el Dashboard mostrara la ejecución como abortada,
 * y esa carrera (un clone nuevo borrando el directorio de uno huérfano todavía
 * escribiendo en él) es lo que dejaba el workspace en un estado que forzaba un
 * re-clonado completo en la siguiente ejecución.
 *
 * ── Diseño ────────────────────────────────────────────────────────────────────
 * Este registro NO decide qué significa "cancelar" — cada llamador lo define:
 *   - Git / Gradle: {@link #registerProcess} — cancelar = matar el árbol de
 *     procesos del sistema operativo (proceso exclusivo de esta ejecución, nunca
 *     compartido).
 *   - WDA: el llamador (JobExecutor) registra un {@link Cancelable} que invoca
 *     {@code WdaLifecycleOwner.release(Consumer.JOB_EXECUTION, ...)} — WDA es un
 *     recurso COMPARTIDO (el Mirror puede seguir usándolo), así que su cancelación
 *     correcta es liberar la referencia de este consumidor, no matar el proceso a
 *     ciegas. WdaLifecycleOwner sigue siendo la única autoridad de su ciclo de
 *     vida; este registro solo dispara esa liberación más temprano (en el
 *     instante del abort, no solo al final de la ejecución).
 *
 * Un mismo executionId puede tener varias entradas activas a la vez (poco común,
 * pero no se asume lo contrario); {@link #killAll} cancela todas.
 */
public final class ProcessRegistry {

    @FunctionalInterface
    public interface Cancelable {
        void cancel();
    }

    private record Entry(String label, Cancelable cancelable, long pid, long registeredAtMs) {}

    private static final Map<String, Map<String, Entry>> BY_EXECUTION = new ConcurrentHashMap<>();
    private static final AtomicLong TOKEN_SEQ = new AtomicLong();

    private ProcessRegistry() {}

    /** Registra cualquier recurso cancelable bajo un executionId. Devuelve el token para desregistrarlo. */
    public static String register(String executionId, String label, Cancelable cancelable) {
        return register(executionId, label, cancelable, -1);
    }

    private static String register(String executionId, String label, Cancelable cancelable, long pid) {
        String token = label + "#" + TOKEN_SEQ.incrementAndGet();
        BY_EXECUTION.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
                .put(token, new Entry(label, cancelable, pid, System.currentTimeMillis()));
        return token;
    }

    /** Conveniencia para procesos del SO (git, Gradle) — cancelar = matar el árbol de procesos. */
    public static String registerProcess(String executionId, String label, Process process) {
        long pid = safePid(process);
        return register(executionId, label, () -> killProcessTree(process), pid);
    }

    /** Quita una entrada sin cancelarla — usar cuando el proceso ya terminó por su cuenta. */
    public static void unregister(String executionId, String token) {
        if (token == null) return;
        Map<String, Entry> m = BY_EXECUTION.get(executionId);
        if (m == null) return;
        Entry e = m.remove(token);
        if (e != null) {
            System.out.println("[ProcessRegistry] Process terminated — executionId=" + executionId
                    + " label=" + e.label() + " token=" + token);
        }
        if (m.isEmpty()) BY_EXECUTION.remove(executionId, m);
    }

    /**
     * Cancela TODO lo registrado ahora mismo para este executionId — idempotente
     * (no hace nada si ya no queda nada registrado). Se llama repetidamente desde
     * el abort-watcher de ExecutionContext mientras la ejecución siga marcada como
     * abortada, para alcanzar también un reintento posterior (p.ej. un segundo
     * intento de git clone) sin depender de que WorkspaceManager sepa nada de abort.
     */
    public static void killAll(String executionId) {
        Map<String, Entry> m = BY_EXECUTION.remove(executionId);
        if (m == null || m.isEmpty()) return;
        System.out.println("[ProcessRegistry] Killing process tree — executionId=" + executionId
                + " count=" + m.size());
        for (Map.Entry<String, Entry> en : m.entrySet()) {
            try {
                en.getValue().cancelable().cancel();
            } catch (Exception ignored) {}
            System.out.println("[ProcessRegistry] Process terminated — executionId=" + executionId
                    + " label=" + en.getValue().label() + " token=" + en.getKey());
        }
    }

    /** @return true si queda algún proceso/recurso registrado (vivo) para este executionId. */
    public static boolean hasActive(String executionId) {
        Map<String, Entry> m = BY_EXECUTION.get(executionId);
        return m != null && !m.isEmpty();
    }

    private static long safePid(Process p) {
        try { return p.pid(); } catch (Exception e) { return -1; }
    }

    /**
     * Mata el árbol de procesos de forma confiable en Windows y Linux/Mac — misma
     * estrategia que ya usaba JobExecutor.forceKillProcessTree() (ahora delega aquí),
     * compartida para que Git y Gradle se cancelen exactamente igual.
     */
    public static void killProcessTree(Process process) {
        long pid = safePid(process);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            process.toHandle().descendants().forEach(h -> {
                try { h.destroyForcibly(); } catch (Exception ignored) {}
            });
            process.destroyForcibly();
        } catch (Exception ignored) {}
        if (isWindows) {
            try {
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                        .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }
}
