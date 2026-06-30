package qa.cinepolis.runner.accessibility;

import qa.cinepolis.runner.WdaManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Creates the correct {@link AccessibilityInspector} for a given device UDID.
 *
 * Platform detection:
 *   1. Run "adb -s {udid} get-state": if it returns "device", the UDID belongs
 *      to an Android device.
 *   2. Otherwise, assume iOS and use the WDA URL resolved by WdaManager.
 *
 * No caller needs to know the platform: InspectorFactory encapsulates the
 * detection and returns a uniform AccessibilityInspector.
 */
public final class InspectorFactory {

    private InspectorFactory() {}

    /**
     * Creates and returns an inspector for the given device.
     *
     * @param udid    the device serial (ADB serial for Android, UDID for iOS)
     * @param adbPath path to the adb executable (used for Android detection)
     * @return        a ready-to-use AccessibilityInspector
     */
    public static AccessibilityInspector create(String udid, String adbPath) {
        if (isAndroidDevice(adbPath, udid)) {
            System.out.println("[InspectorFactory] Android detected for " + udid);
            return new AndroidAccessibilityInspector(adbPath, udid);
        }
        // iOS — use WDA URL discovered by WdaManager
        String wdaUrl = WdaManager.getWdaBaseUrl();
        System.out.println("[InspectorFactory] iOS detected for " + udid + " → WDA @ " + wdaUrl);
        return new IOSAccessibilityInspector(udid, wdaUrl);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Returns true when ADB reports the UDID as an attached "device".
     * Fails fast (3 s timeout) so iOS detection doesn't add perceptible delay.
     */
    private static boolean isAndroidDevice(String adbPath, String udid) {
        if (adbPath == null || adbPath.isBlank()) return false;
        try {
            Process p = new ProcessBuilder(adbPath, "-s", udid, "get-state")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(3, TimeUnit.SECONDS);
            p.destroyForcibly();
            return "device".equalsIgnoreCase(out);
        } catch (Exception e) {
            return false;
        }
    }
}
