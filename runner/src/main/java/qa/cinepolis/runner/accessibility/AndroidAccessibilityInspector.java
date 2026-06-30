package qa.cinepolis.runner.accessibility;

import qa.cinepolis.runner.UiHierarchyParser;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android implementation of {@link AccessibilityInspector}.
 *
 * Inspection  — delegates to {@link UiHierarchyParser} (ADB uiautomator dump).
 *               Uses {@link HierarchyCache} to avoid redundant dumps;
 *               the cache is keyed on the current foreground Activity, which is
 *               obtained cheaply via "adb dumpsys window windows".
 *
 * Actions     — delegates to "adb input" commands (same as before).
 *
 * No Appium session is required; everything runs over raw ADB.
 */
public final class AndroidAccessibilityInspector implements AccessibilityInspector {

    private static final int     ADB_TIMEOUT_S = 8;
    private static final Pattern FOCUS_PAT     =
            Pattern.compile("mCurrentFocus.*?\\{.*?\\s+(\\S+/\\S+)\\}");

    private final String         adbPath;
    private final String         udid;
    private final HierarchyCache cache = new HierarchyCache();

    public AndroidAccessibilityInspector(String adbPath, String udid) {
        this.adbPath = adbPath;
        this.udid    = udid;
    }

    // ── Inspection ────────────────────────────────────────────────────────────

    @Override
    public UIElement findElementAt(int tapX, int tapY) {
        System.out.printf("[AndroidInspector] Touch: (%d,%d)%n", tapX, tapY);

        String xml = getXml();
        if (xml == null) {
            System.out.println("[AndroidInspector] FAIL: Hierarchy not updated — uiautomator dump returned null");
            return null;
        }

        // Quick node count for debug output (count "<node" occurrences — fast, no second parse)
        int totalNodes = countSubstring(xml, "<node");

        UiHierarchyParser.ElementInfo ei = UiHierarchyParser.findElementAt(xml, tapX, tapY);

        if (ei == null) {
            System.out.printf("[AndroidInspector] Nodes parsed: %d  |  No node contains (%d,%d)%n",
                    totalNodes, tapX, tapY);
            return null;
        }

        UIElement uel = toUIElement(ei);
        System.out.printf(
            "[AndroidInspector] Nodes parsed: %d%n" +
            "[AndroidInspector] Selected node:%n" +
            "  resource-id=%s%n" +
            "  class=%s%n" +
            "  bounds=%s%n" +
            "  text=%s%n" +
            "  content-desc=%s%n" +
            "  Locator: strategy=%s  value=%s%n",
            totalNodes,
            ei.resourceId, ei.className, ei.bounds,
            ei.text, ei.accessId,
            uel.locatorStrategy, uel.locatorValue
        );
        return uel;
    }

    /** Counts non-overlapping occurrences of {@code needle} in {@code haystack}. */
    private static int countSubstring(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) { count++; idx += needle.length(); }
        return count;
    }

    @Override
    public UIElement findFocusedInputElement() {
        String xml = getXml();
        if (xml == null) return null;
        UiHierarchyParser.ElementInfo ei = UiHierarchyParser.findFocusedEditText(xml);
        return toUIElement(ei);
    }

    @Override
    public boolean isKeyboardVisible() {
        return UiHierarchyParser.isKeyboardVisible(getXml());
    }

    @Override
    public String getHierarchyXml() {
        return getXml();
    }

    @Override
    public void invalidateCache() {
        cache.invalidate();
    }

    @Override
    public int[] getScreenSize() {
        return UiHierarchyParser.getScreenSize(adbPath, udid);
    }

    // ── Action injection ──────────────────────────────────────────────────────

    @Override
    public void performTap(int x, int y) {
        runAdb("input", "tap", str(x), str(y));
    }

    @Override
    public void performSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        runAdb("input", "swipe", str(x1), str(y1), str(x2), str(y2), str(durationMs));
    }

    @Override
    public void performLongPress(int x, int y, int durationMs) {
        runAdb("input", "swipe", str(x), str(y), str(x), str(y), str(durationMs));
    }

    @Override
    public void performKey(String keyName) {
        runAdb("input", "keyevent", toKeycode(keyName));
    }

    @Override
    public void performText(String text) {
        runAdb("input", "text", text.replace(" ", "%s"));
    }

    @Override
    public void close() {
        cache.invalidate();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Returns XML from cache if valid, otherwise dumps hierarchy and caches it. */
    private String getXml() {
        String activity = getCurrentActivity();
        String cached   = cache.get(activity);
        if (cached != null) return cached;

        String xml = UiHierarchyParser.dumpHierarchy(adbPath, udid);
        if (xml != null) cache.put(xml, activity);
        return xml;
    }

    /** Returns the foreground Activity name (e.g. "com.pkg/.MainActivity"), or null. */
    private String getCurrentActivity() {
        try {
            Process p = new ProcessBuilder(adbPath, "-s", udid, "shell",
                    "dumpsys", "window", "windows")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(3, TimeUnit.SECONDS);
            p.destroyForcibly();
            Matcher m = FOCUS_PAT.matcher(out);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Converts UiHierarchyParser.ElementInfo → UIElement. */
    private UIElement toUIElement(UiHierarchyParser.ElementInfo ei) {
        if (ei == null) return null;

        // Parse bounds "[x1,y1][x2,y2]" → rect
        int[] rect = parseBounds(ei.bounds);
        int bx = 0, by = 0, bw = 0, bh = 0;
        if (rect != null) { bx = rect[0]; by = rect[1]; bw = rect[2] - rect[0]; bh = rect[3] - rect[1]; }

        // Resolve best locator
        String strategy, value;
        if (ei.resourceId != null && !ei.resourceId.isBlank()) {
            strategy = "id";
            value    = ei.resourceId;
        } else if (ei.accessId != null && !ei.accessId.isBlank()) {
            strategy = "accessibility_id";
            value    = ei.accessId;
        } else if (ei.text != null && !ei.text.isBlank()) {
            strategy = "text";
            value    = ei.text;
        } else {
            strategy = "xpath";
            value    = "//*[@class='" + ei.className + "']";
        }

        // Extract package from resourceId  ("com.pkg:id/btn" → "com.pkg")
        String pkg = "";
        if (ei.resourceId != null && ei.resourceId.contains(":")) {
            pkg = ei.resourceId.substring(0, ei.resourceId.indexOf(':'));
        }

        return UIElement.builder()
                .platform("android")
                .className(ei.className)
                .locatorStrategy(strategy)
                .locatorValue(value)
                .text(ei.text)
                .accessibilityLabel(ei.accessId)
                .resourceId(ei.resourceId)
                .packageName(pkg)
                .bundleId("")
                .rect(bx, by, bw, bh)
                .enabled(true)
                .clickable(true)
                .visible(true)
                // backward compat
                .shortId(ei.shortId)
                .accessId(ei.accessId)
                .elType(ei.elType)
                .build();
    }

    private static int[] parseBounds(String bounds) {
        if (bounds == null || bounds.isBlank()) return null;
        try {
            String s = bounds.replace("][", ",").replace("[", "").replace("]", "");
            String[] p = s.split(",");
            return new int[]{
                Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())
            };
        } catch (Exception e) { return null; }
    }

    private void runAdb(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(adbPath); cmd.add("-s"); cmd.add(udid);
            Collections.addAll(cmd, args);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            p.waitFor(ADB_TIMEOUT_S, TimeUnit.SECONDS);
            p.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[AndroidInspector] ADB error: " + e.getMessage());
        }
    }

    private static String str(int n) { return Integer.toString(n); }

    private static String toKeycode(String key) {
        switch (key.toLowerCase()) {
            case "back":   return "KEYCODE_BACK";
            case "home":   return "KEYCODE_HOME";
            case "enter":  return "KEYCODE_ENTER";
            case "done":   return "KEYCODE_ENTER";
            case "tab":    return "KEYCODE_TAB";
            case "delete": return "KEYCODE_DEL";
            default:
                String k = key.toUpperCase();
                return k.startsWith("KEYCODE_") ? k : "KEYCODE_" + k;
        }
    }
}
