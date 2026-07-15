package qa.cinepolis.runner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Árbitro de quién tiene actualmente el derecho a lanzar/gestionar el ciclo de
 * vida de WDA: una ejecución de test real (JobExecutor) o un lanzamiento
 * on-demand del Mirror (IOSMirrorProvider).
 *
 * Reemplaza el antiguo WdaManager.testExecutionActive — un único AtomicBoolean
 * compartido por ambos flujos, sin dueño explícito. Ese diseño violaba SRP: el
 * mismo flag representaba "hay una ejecución real en curso" y "el Mirror está
 * intentando levantar WDA", y solo el flujo de ejecución real lo liberaba
 * (IOSExecutionCleanupManager.cleanup() → markTestExecutionEnd()). Un
 * lanzamiento on-demand del Mirror lo dejaba en true para siempre, porque
 * IOSMirrorProvider nunca liberaba lo que IosPreflightManager.runPreflight()
 * marcaba internamente en su nombre — a partir de ahí, IOSMirrorProvider.start()
 * creía indefinidamente que una ejecución real estaba en curso y nunca volvía
 * a intentar levantar WDA.
 *
 * Diseño: un único slot de propiedad (Owner), con dos formas de adquisición
 * deliberadamente distintas y no intercambiables:
 *
 *   - EXECUTION (beginExecutionSession/endExecutionSession): una ejecución
 *     real SIEMPRE gana — desplaza incondicionalmente cualquier sesión de
 *     Mirror existente. Nunca espera, nunca reintenta: una ejecución real no
 *     debe depender de que el Mirror termine (requisito: "un lanzamiento del
 *     Mirror NO debe impedir futuras ejecuciones reales").
 *
 *   - MIRROR (tryAcquireForMirror): solo adquiere si el slot está libre; nunca
 *     desplaza a EXECUTION. Devuelve un ticket {@link MirrorSession} — el
 *     ÚNICO medio de liberar esa sesión es llamar a release()/close() sobre
 *     ESE objeto. No existe ningún método estático "genérico" que el Mirror
 *     pudiera invocar por error para liberar una sesión de ejecución (el bug
 *     original), porque los tipos son distintos y no hay overlap posible.
 *
 * Sin timeouts, sin TTLs, sin sleeps: la liberación es siempre por referencia
 * explícita (quién adquirió, libera), nunca por expiración de tiempo.
 */
public final class WdaLaunchCoordinator {

    public enum Owner { EXECUTION, MIRROR }

    private static final AtomicReference<Owner> CURRENT = new AtomicReference<>(null);

    /** Epoch ms de la sesión EXECUTION actual — usado solo por IOSExecutionCleanupManager
     *  para acotar la ventana de búsqueda en appium.log de ESA ejecución, nunca contaminado
     *  por un lanzamiento on-demand del Mirror (que ya lleva su propio timestamp local). */
    private static volatile long executionStartedAtMs = 0L;

    private WdaLaunchCoordinator() {}

    // ── Execution (JobExecutor / IosPreflightManager / IOSExecutionCleanupManager) ──

    /**
     * Una ejecución real SIEMPRE adquiere control, incondicionalmente — desplaza
     * cualquier sesión de Mirror activa en ese momento. Llamar antes de
     * IosPreflightManager.runPreflight() en el flujo de JobExecutor.
     */
    public static void beginExecutionSession() {
        CURRENT.set(Owner.EXECUTION);
        executionStartedAtMs = System.currentTimeMillis();
    }

    /**
     * Libera la sesión de ejecución. Llamar SIEMPRE desde
     * IOSExecutionCleanupManager.cleanup(), incluso si algo falló — mismo
     * patrón de "llamar siempre desde cleanup" que tenía markTestExecutionEnd().
     * CAS defensivo: si por alguna razón el slot ya no es EXECUTION, no toca nada.
     */
    public static void endExecutionSession() {
        CURRENT.compareAndSet(Owner.EXECUTION, null);
    }

    /** Epoch ms en que arrancó la sesión de ejecución actual — 0 si nunca se llamó. */
    public static long executionStartedAtMs() {
        return executionStartedAtMs;
    }

    // ── Mirror (IOSMirrorProvider) ────────────────────────────────────────────

    /**
     * Ticket de sesión del Mirror. Solo quien lo obtiene de
     * {@link #tryAcquireForMirror()} puede liberarlo — no hay otro camino.
     * Idempotente: llamar release()/close() más de una vez no tiene efecto
     * adicional.
     */
    public static final class MirrorSession implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean(false);

        private MirrorSession() {}

        /** Libera esta sesión — SIEMPRE debe llamarse desde el finally de quien la adquirió. */
        public void release() {
            if (released.compareAndSet(false, true)) {
                CURRENT.compareAndSet(Owner.MIRROR, null);
            }
        }

        @Override
        public void close() { release(); }
    }

    /**
     * El Mirror intenta adquirir control solo si el slot está completamente
     * libre — nunca desplaza a EXECUTION (requisito: "una ejecución real debe
     * impedir que el Mirror lance WDA") ni a otro intento de Mirror ya en curso.
     *
     * @return el ticket de sesión si se adquirió, o null si ya hay un dueño activo.
     */
    public static MirrorSession tryAcquireForMirror() {
        if (CURRENT.compareAndSet(null, Owner.MIRROR)) {
            return new MirrorSession();
        }
        return null;
    }

    // ── Consulta ───────────────────────────────────────────────────────────────

    /** ¿Hay actualmente una ejecución real usando/levantando WDA? */
    public static boolean isExecutionActive() {
        return CURRENT.get() == Owner.EXECUTION;
    }

    /** Solo para logs/diagnóstico — quién tiene el control ahora mismo, o null si nadie. */
    public static Owner currentOwner() {
        return CURRENT.get();
    }
}
