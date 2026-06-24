package qa.cinepolis.runner;

import qa.cinepolis.runner.model.RunnerConfig;

import java.util.*;
import java.util.concurrent.*;

/**
 * Device-level self-healing watchdog (V5).
 *
 * Complements DependencySelfHealingManager (which polls every 5 min) by reacting
 * to runtime events immediately:
 *
 *   • AppiumManager watchdog fires onRestart → onAppiumRestarted() re-registers devices
 *   • DependencySelfHealingManager heals ADB → onAdbHealed() re-registers devices
 *   • startMonitor() polls every 30s for device-count / component-state changes
 *     and forces an extra registerDevices() when something shifts
 *
 * Does NOT send heartbeats — that remains the heartbeat scheduler's responsibility.
 */
public class DeviceSelfHealingManager {

    private final PlatformToolsManager platformTools;
    private final AppiumManager        appiumMgr;
    private final BackendClient        client;
    private final RunnerConfig         config;

    private volatile boolean lastAdbOk     = false;
    private volatile boolean lastAppiumOk  = false;
    private volatile int     lastDevices   = 0;
    private volatile int     lastIosCount  = 0;

    public DeviceSelfHealingManager(
            PlatformToolsManager platformTools,
            AppiumManager appiumMgr,
            BackendClient client,
            RunnerConfig config) {
        this.platformTools = platformTools;
        this.appiumMgr     = appiumMgr;
        this.client        = client;
        this.config        = config;
    }

    /** Seed state mirrors so first-tick delta detection is correct. */
    public void init(boolean adbOk, boolean appiumOk, int deviceCount) {
        this.lastAdbOk    = adbOk;
        this.lastAppiumOk = appiumOk;
        this.lastDevices  = deviceCount;
        this.lastIosCount = (int) discoverDevices().stream()
                .filter(d -> "IOS".equals(d.get("platform"))).count();
    }

    // ── Reactive callbacks ────────────────────────────────────────────────────

    /**
     * Called by AppiumManager watchdog after a successful restart.
     * Updates system properties and immediately re-registers devices.
     */
    public void onAppiumRestarted() {
        System.out.println("[DeviceHealer] Appium reiniciado — reescaneando dispositivos...");
        System.setProperty("APPIUM_OK",      "true");
        System.setProperty("APPIUM_VERSION", appiumMgr.getAppiumVersion());
        lastAppiumOk = true;
        rescannAndUpdate("APPIUM_RESTART");
    }

    /**
     * Called by DependencySelfHealingManager after ADB repair succeeds.
     * Enables androidSupported and immediately re-registers devices.
     */
    public void onAdbHealed() {
        System.out.println("[DeviceHealer] ADB reparado — reescaneando dispositivos...");
        config.androidSupported = true;
        lastAdbOk = true;
        rescannAndUpdate("ADB_HEALED");
    }

    // ── Proactive monitor ─────────────────────────────────────────────────────

    /**
     * Starts a 30-second poll that detects device-count or component-state changes
     * and pushes an updated device list to the backend when something shifts.
     */
    public void startMonitor(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean adbOk    = platformTools.isAdbFunctional();
                boolean appiumOk = appiumMgr.isAlive();
                List<Map<String, String>> devices = discoverDevices();
                int count    = devices.size();
                int iosCount = (int) devices.stream()
                        .filter(d -> "IOS".equals(d.get("platform"))).count();

                boolean changed = count    != lastDevices
                        || iosCount  != lastIosCount
                        || adbOk     != lastAdbOk
                        || appiumOk  != lastAppiumOk;

                if (changed) {
                    System.out.printf(
                            "[DeviceHealer] Cambio — total: %d→%d  iOS: %d→%d  ADB: %s→%s  Appium: %s→%s%n",
                            lastDevices, count, lastIosCount, iosCount,
                            okStr(lastAdbOk), okStr(adbOk),
                            okStr(lastAppiumOk), okStr(appiumOk));
                    lastDevices  = count;
                    lastIosCount = iosCount;
                    lastAdbOk    = adbOk;
                    lastAppiumOk = appiumOk;
                    client.syncDevices(config.runnerId, devices);
                }
            } catch (Exception e) {
                System.err.println("[DeviceHealer] Monitor error: " + e.getMessage());
            }
        }, 15, 30, TimeUnit.SECONDS);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void rescannAndUpdate(String reason) {
        try {
            List<Map<String, String>> devices = discoverDevices();
            lastDevices = devices.size();
            client.syncDevices(config.runnerId, devices);
            System.out.printf("[DeviceHealer] %s → %d dispositivo(s) sincronizado(s).%n",
                    reason, devices.size());
        } catch (Exception e) {
            System.err.println("[DeviceHealer] Rescan error (" + reason + "): " + e.getMessage());
        }
    }

    private List<Map<String, String>> discoverDevices() {
        List<Map<String, String>> devices = new ArrayList<>(BackendClient.discoverAndroidDevices());
        if (config.iosSupported) devices.addAll(IOSDeviceScanner.scan());
        return devices;
    }

    private static String okStr(boolean v) { return v ? "OK" : "FAIL"; }
}
