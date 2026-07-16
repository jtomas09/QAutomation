package qa.cinepolis.runner;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Marca cuándo una ejecución de test real tiene el control de WDA — usado
 * solo para diagnóstico/ventanas de log (ver executionStartedAtMs()) y para
 * que el Mirror (DeviceStreamServer) sepa si alguien sigue trabajando en WDA
 * antes de rendirse (ver su propio comentario en el loop de captura).
 *
 * El Mirror ya NUNCA inicia WDA (ver IOSMirrorProvider) — por lo tanto esta
 * clase ya no arbitra una condición de carrera Mirror-vs-ejecución (no puede
 * existir: solo una ejecución real construye WDA, a través de la única
 * autoridad WdaLifecycleOwner, que además garantiza con un Future compartido
 * por UDID que nunca haya dos compilaciones simultáneas). Lo que queda aquí
 * es simple bookkeeping de "hay una ejecución real usando WDA ahora mismo".
 */
public final class WdaLaunchCoordinator {

    public enum Owner { EXECUTION }

    private static final AtomicReference<Owner> CURRENT = new AtomicReference<>(null);

    /** Epoch ms de la sesión EXECUTION actual — usado solo por IOSExecutionCleanupManager
     *  para acotar la ventana de búsqueda en appium.log de ESA ejecución. */
    private static volatile long executionStartedAtMs = 0L;

    private WdaLaunchCoordinator() {}

    /** Llamar antes de IosPreflightManager.runPreflight() en el flujo de JobExecutor. */
    public static void beginExecutionSession() {
        CURRENT.set(Owner.EXECUTION);
        executionStartedAtMs = System.currentTimeMillis();
    }

    /** Llamar SIEMPRE desde IOSExecutionCleanupManager.cleanup(), incluso si algo falló. */
    public static void endExecutionSession() {
        CURRENT.compareAndSet(Owner.EXECUTION, null);
    }

    /** Epoch ms en que arrancó la sesión de ejecución actual — 0 si nunca se llamó. */
    public static long executionStartedAtMs() {
        return executionStartedAtMs;
    }

    /** ¿Hay actualmente una ejecución real usando/levantando WDA? */
    public static boolean isExecutionActive() {
        return CURRENT.get() == Owner.EXECUTION;
    }

    /** Solo para logs/diagnóstico — quién tiene el control ahora mismo, o null si nadie. */
    public static Owner currentOwner() {
        return CURRENT.get();
    }
}
