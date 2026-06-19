package qa.cinepolis.runner;

import java.util.concurrent.*;

/**
 * Monitors ADB availability and auto-heals by re-downloading platform-tools
 * every 5 minutes until the binary is functional.
 *
 * When ADB transitions from non-functional → functional the healCallback is
 * invoked once so RunnerAgent can rescan devices and update its heartbeat
 * status from DEGRADED to ONLINE without restarting the process.
 */
public class SelfHealingManager {

    private static final int CHECK_INTERVAL_MINUTES = 5;

    private final PlatformToolsManager     platformTools;
    private final ScheduledExecutorService scheduler;
    private final Runnable                 healCallback;

    private volatile boolean adbWasFunctional;

    public SelfHealingManager(PlatformToolsManager platformTools,
                               boolean initialAdbState,
                               Runnable healCallback) {
        this.platformTools    = platformTools;
        this.adbWasFunctional = initialAdbState;
        this.healCallback     = healCallback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "self-healing");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the background healing loop. */
    public void start() {
        if (adbWasFunctional) {
            // ADB is healthy — still schedule periodic probe to detect regressions
            System.out.println("[SelfHealing] ADB operativo. Monitoreo activo cada "
                    + CHECK_INTERVAL_MINUTES + " min.");
        } else {
            System.out.println("[SelfHealing] ADB no disponible. Auto-reparacion activa cada "
                    + CHECK_INTERVAL_MINUTES + " min.");
        }
        scheduler.scheduleAtFixedRate(
                this::checkAndHeal,
                CHECK_INTERVAL_MINUTES,
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    /** Stops the background loop (called from shutdown hook). */
    public void stop() {
        scheduler.shutdownNow();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void checkAndHeal() {
        try {
            boolean functional = platformTools.isAdbFunctional();

            if (!functional) {
                System.out.println("[SelfHealing] ADB no funcional — intentando auto-reparacion...");

                // Reset cache so resolveAdb() re-attempts the full download sequence
                platformTools.reset();
                String adbPath = platformTools.resolveAdb();
                functional = platformTools.isAdbFunctional();

                if (functional) {
                    System.out.println("[SelfHealing] ADB reparado exitosamente: " + adbPath);
                    System.out.println("[SelfHealing] Version: " + platformTools.getAdbVersion());
                    updateSystemProperties(adbPath);
                    if (!adbWasFunctional) {
                        adbWasFunctional = true;
                        invokeHealCallback();
                    }
                } else {
                    System.out.println("[SelfHealing] Reparacion fallida. Reintentando en "
                            + CHECK_INTERVAL_MINUTES + " minutos.");
                }

            } else if (!adbWasFunctional) {
                // ADB became functional between scheduled checks (e.g. user installed manually)
                adbWasFunctional = true;
                updateSystemProperties(platformTools.resolveAdb());
                invokeHealCallback();
            }

        } catch (Exception e) {
            System.err.println("[SelfHealing] Error en ciclo de verificacion: " + e.getMessage());
        }
    }

    private void updateSystemProperties(String adbPath) {
        System.setProperty("ADB_PATH",    adbPath);
        System.setProperty("ADB_VERSION", platformTools.getAdbVersion());
        System.setProperty("ADB_OK",      "true");
    }

    private void invokeHealCallback() {
        try {
            System.out.println("[SelfHealing] Disparando callback de re-registro de dispositivos...");
            healCallback.run();
        } catch (Exception e) {
            System.err.println("[SelfHealing] Error en heal callback: " + e.getMessage());
        }
    }
}
