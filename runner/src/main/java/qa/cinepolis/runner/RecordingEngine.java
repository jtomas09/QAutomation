package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import qa.cinepolis.runner.accessibility.AccessibilityInspector;
import qa.cinepolis.runner.accessibility.InspectorFactory;
import qa.cinepolis.runner.accessibility.UIElement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recording engine — manages device recording sessions with intelligent
 * event classification, powered by the platform-agnostic AccessibilityInspector.
 *
 * Classification rules (unchanged):
 *  - Tap on EditText + keyboard appears  → "tap" + starts InputWatcher
 *  - InputWatcher: polls until keyboard gone → "input" step
 *  - Swipe |dy| > |dx|                   → "scroll"
 *  - Swipe |dx| >= |dy|                  → "swipe"
 *  - Duration > 600 ms                   → "long_press"
 *  - Two taps < 250 ms at same spot      → "double_tap"
 *  - KEY_BACK (Android getevent)         → "back"
 *  - KEY_HOME (Android getevent)         → "home"
 *
 * Platform dispatch:
 *  - InspectorFactory.create(udid, adbPath) auto-detects Android vs iOS and
 *    returns an AccessibilityInspector.  This class never calls ADB or WDA
 *    directly — all device interaction goes through the inspector.
 *  - getevent listener is started only for Android inspectors.
 *
 * Thread safety: same guarantees as before (ConcurrentHashMap, volatile,
 * CopyOnWriteArrayList, ScheduledExecutorService).
 */
public final class RecordingEngine {

    private static final ObjectMapper MAPPER                  = new ObjectMapper();
    private static final int          SWIPE_THRESHOLD_PX      = 50;
    private static final int          DOUBLE_TAP_WINDOW_MS    = 250;
    private static final int          LONG_PRESS_THRESHOLD_MS = 600;
    private static final int          UI_SETTLE_MS            = 350;

    private final String adbPath;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "recording-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Session>       sessions     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    public RecordingEngine(String adbPath) {
        this.adbPath = adbPath;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public static final class StartResult {
        public final String sessionId;
        public final int    deviceWidth;
        public final int    deviceHeight;

        StartResult(String id, int w, int h) {
            sessionId    = id;
            deviceWidth  = w;
            deviceHeight = h;
        }
    }

    public StartResult start(String udid) {
        String sessionId = "rec-" + System.currentTimeMillis();

        // Auto-detect platform and create the right inspector
        AccessibilityInspector inspector = InspectorFactory.create(udid, adbPath);
        int[] dims = inspector.getScreenSize();

        Session session = new Session(udid, dims[0], dims[1], inspector);
        sessions.put(sessionId, session);
        stepCounters.put(sessionId, new AtomicInteger(0));

        // getevent listener only runs on Android (it will fail gracefully on iOS)
        session.startGeteventListener(sessionId);

        System.out.println("[RecordingEngine] Session started: " + sessionId
                + " udid=" + udid + " " + dims[0] + "x" + dims[1]);
        return new StartResult(sessionId, dims[0], dims[1]);
    }

    public boolean stop(String sessionId) {
        Session s = sessions.remove(sessionId);
        stepCounters.remove(sessionId);
        if (s == null) return false;
        s.stop();
        System.out.println("[RecordingEngine] Session stopped: " + sessionId);
        return true;
    }

    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    // ── Actions (overlay-triggered) ───────────────────────────────────────────

    public String executeTap(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 1400;

        boolean kbBefore = s.inspector.isKeyboardVisible();
        UIElement el     = s.inspector.findElementAt(x, y);

        s.inspector.performTap(x, y);
        sleep(UI_SETTLE_MS);
        s.inspector.invalidateCache();

        boolean kbAfter = s.inspector.isKeyboardVisible();

        if (!kbBefore && kbAfter && el != null && "input".equals(el.elType)) {
            String preText = el.text != null ? el.text : "";
            s.activeInputWatcher = new InputWatcher(s, sessionId, el.getBoundsString(), preText);
            s.activeInputWatcher.start();
            System.out.println("[RecordingEngine] InputWatcher started for " + el.shortId);
        }

        return buildStepFromEl(sessionId, s, "tap", el, null, null);
    }

    public String executeDoubleTap(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 1600;

        UIElement el = s.inspector.findElementAt(x, y);
        s.inspector.performTap(x, y);
        sleep(120);
        s.inspector.performTap(x, y);
        sleep(UI_SETTLE_MS);
        s.inspector.invalidateCache();

        return buildStepFromEl(sessionId, s, "double_tap", el, null, null);
    }

    public String executeLongPress(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 2200;

        UIElement el = s.inspector.findElementAt(x, y);
        s.inspector.performLongPress(x, y, 800);
        sleep(UI_SETTLE_MS);
        s.inspector.invalidateCache();

        return buildStepFromEl(sessionId, s, "long_press", el, null, null);
    }

    public String executeSwipe(String sessionId, int x1, int y1, int x2, int y2) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 1200;

        s.inspector.performSwipe(x1, y1, x2, y2, 300);
        sleep(UI_SETTLE_MS);
        s.inspector.invalidateCache();

        int    dx   = x2 - x1;
        int    dy   = y2 - y1;
        String dir  = (Math.abs(dy) >= Math.abs(dx))
                ? (dy > 0 ? "down" : "up") : (dx > 0 ? "right" : "left");
        String type = (Math.abs(dy) >= Math.abs(dx)) ? "scroll" : "swipe";

        return buildStepFromEl(sessionId, s, type, null, dir, null);
    }

    public String executeInput(String sessionId, String text) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.inspector.performText(text);
        sleep(UI_SETTLE_MS);
        s.inspector.invalidateCache();

        UIElement el = s.inspector.findFocusedInputElement();
        return buildStepFromEl(sessionId, s, "input", el, null, text);
    }

    public String executeKey(String sessionId, String key) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        if ("back".equalsIgnoreCase(key) || "home".equalsIgnoreCase(key)
                || "enter".equalsIgnoreCase(key) || "done".equalsIgnoreCase(key)) {
            stopInputWatcher(s);
        }

        s.inspector.performKey(key);
        s.inspector.invalidateCache();

        String type;
        switch (key.toLowerCase()) {
            case "back":   type = "back";          break;
            case "home":   type = "home";           break;
            case "enter":
            case "done":   type = "hide_keyboard";  break;
            default:       type = "tap";            break;
        }
        return buildStepFromEl(sessionId, s, type, null, null, null);
    }

    // ── SSE client registry ───────────────────────────────────────────────────

    public void registerSseClient(String sessionId, OutputStream out) {
        Session s = sessions.get(sessionId);
        if (s != null) s.sseClients.add(out);
    }

    public void unregisterSseClient(String sessionId, OutputStream out) {
        Session s = sessions.get(sessionId);
        if (s != null) s.sseClients.remove(out);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildStepFromEl(String sessionId, Session s, String type,
                                    UIElement el, String dir, String inputText) {
        try {
            AtomicInteger counter = stepCounters.get(sessionId);
            if (counter == null) return null;
            int n = counter.incrementAndGet();

            ObjectNode node = MAPPER.createObjectNode();
            node.put("id",   "step-" + System.currentTimeMillis() + "-" + n);
            node.put("n",    n);
            node.put("type", type);
            if (inputText != null) node.put("inputVal", inputText);
            if (dir != null)       node.put("dir",      dir);
            appendElement(node, el);
            appendScreenName(node, s, el);
            appendTime(node, s);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            System.err.println("[RecordingEngine] buildStepFromEl error: " + e.getMessage());
            return null;
        }
    }

    /** Used by the getevent path (Android): dumps hierarchy and finds element at (x,y). */
    private String buildStep(String sessionId, Session s, String type,
                              int x, int y, String dir, String inputText) {
        try {
            AtomicInteger counter = stepCounters.get(sessionId);
            if (counter == null) return null;
            int n = counter.incrementAndGet();

            UIElement el = (x >= 0 && y >= 0) ? s.inspector.findElementAt(x, y) : null;

            ObjectNode node = MAPPER.createObjectNode();
            node.put("id",   "step-" + System.currentTimeMillis() + "-" + n);
            node.put("n",    n);
            node.put("type", type);
            if (inputText != null) node.put("inputVal", inputText);
            if (dir != null)       node.put("dir",      dir);
            appendElement(node, el);
            appendScreenName(node, s, el);
            appendTime(node, s);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            System.err.println("[RecordingEngine] buildStep error: " + e.getMessage());
            return null;
        }
    }

    private void appendElement(ObjectNode node, UIElement rawEl) {
        if (rawEl == null) { node.putNull("el"); return; }
        // Enrich with varName and pageObjectAnnotation before serializing
        UIElement el = qa.cinepolis.runner.accessibility.ElementResolver.enrich(rawEl);
        if (el == null) el = rawEl; // safety fallback
        ObjectNode e = MAPPER.createObjectNode();
        // Core fields
        e.put("platform",              el.platform);
        e.put("className",             el.className);
        e.put("locatorStrategy",       el.locatorStrategy);
        e.put("locatorValue",          el.locatorValue);
        e.put("accessibilityLabel",    el.accessibilityLabel);
        e.put("packageName",           el.packageName);
        e.put("bundleId",              el.bundleId);
        e.put("enabled",               el.enabled);
        e.put("clickable",             el.clickable);
        e.put("visible",               el.visible);
        // ElementResolver + SemanticAnalyzer output
        e.put("varName",               el.varName);
        e.put("semanticName",          el.semanticName);
        e.put("pageObjectAnnotation",  el.pageObjectAnnotation);
        // Backward-compat fields (required by existing code generators)
        e.put("shortId",    el.shortId);
        e.put("resourceId", el.resourceId);
        e.put("accessId",   el.accessId);
        e.put("text",       el.text);
        e.put("elType",     el.elType);
        e.put("bounds",     el.getBoundsString());
        node.set("el", e);
    }

    private void appendTime(ObjectNode node, Session s) {
        long elapsedSec = (System.currentTimeMillis() - s.startedAtMs) / 1000;
        node.put("timeStr", String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60));
    }

    private void appendScreenName(ObjectNode node, Session s, UIElement el) {
        if (el == null) return;
        try {
            String screen = s.inspector.getCurrentScreenName();
            if (screen != null && !screen.isBlank()) {
                node.put("screenName", screen);
            }
        } catch (Exception ignored) {}
    }

    private void stopInputWatcher(Session s) {
        if (s.activeInputWatcher != null) {
            s.activeInputWatcher.stop();
            s.activeInputWatcher = null;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── InputWatcher ──────────────────────────────────────────────────────────

    /**
     * Polls the device hierarchy every 600 ms after a tap on a text field.
     * Emits an "input" step via SSE when the keyboard is dismissed and the
     * text content has changed.  Timeout: 45 seconds.
     */
    private class InputWatcher {
        private final Session  s;
        private final String   sessionId;
        private final String   preBounds;
        private final String   preText;
        private final Thread   thread;
        private volatile boolean stopped = false;

        InputWatcher(Session s, String sessionId, String preBounds, String preText) {
            this.s         = s;
            this.sessionId = sessionId;
            this.preBounds = preBounds != null ? preBounds : "";
            this.preText   = preText   != null ? preText   : "";
            this.thread    = new Thread(this::run, "input-watcher-" + s.udid);
            this.thread.setDaemon(true);
        }

        void start() { thread.start(); }
        void stop()  { stopped = true; thread.interrupt(); }

        private void run() {
            long deadline = System.currentTimeMillis() + 45_000;

            while (!stopped && System.currentTimeMillis() < deadline) {
                sleep(600);
                if (stopped) break;

                s.inspector.invalidateCache();

                if (!s.inspector.isKeyboardVisible()) {
                    // Keyboard gone — read the element again and get its current text
                    int[] center   = parseBoundsCenter(preBounds);
                    UIElement el   = (center[0] >= 0 && center[1] >= 0)
                                     ? s.inspector.findElementAt(center[0], center[1])
                                     : s.inspector.findFocusedInputElement();
                    String finalText = (el != null && el.text != null) ? el.text : "";

                    if (!finalText.equals(preText) && !finalText.isEmpty()) {
                        System.out.println("[InputWatcher] Text changed: '"
                                + preText + "' → '" + finalText + "'");
                        String json = buildStepFromEl(sessionId, s, "input", el, null, finalText);
                        if (json != null) s.pushSseEvent(json);
                    }
                    break;
                }
            }
            if (s.activeInputWatcher == this) s.activeInputWatcher = null;
        }

        private int[] parseBoundsCenter(String bounds) {
            try {
                String b = bounds.replace("][", ",").replace("[", "").replace("]", "");
                String[] p = b.split(",");
                int x1 = Integer.parseInt(p[0].trim()), y1 = Integer.parseInt(p[1].trim());
                int x2 = Integer.parseInt(p[2].trim()), y2 = Integer.parseInt(p[3].trim());
                return new int[]{(x1 + x2) / 2, (y1 + y2) / 2};
            } catch (Exception e) { return new int[]{-1, -1}; }
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private class Session {
        final String               udid;
        final int                  deviceWidth;
        final int                  deviceHeight;
        final long                 startedAtMs;
        final AccessibilityInspector inspector;

        volatile long               suppressUntilMs  = 0;
        final List<OutputStream>    sseClients       = new CopyOnWriteArrayList<>();

        // getevent movement tracking (Android only)
        volatile int     touchStartX = -1;
        volatile int     touchStartY = -1;
        volatile int     touchLastX  = -1;
        volatile int     touchLastY  = -1;
        volatile boolean touchDown   = false;

        // Double-tap detection (250 ms window)
        volatile long              lastTapMs  = 0;
        volatile int               lastTapX   = -1;
        volatile int               lastTapY   = -1;
        volatile ScheduledFuture<?> pendingTap = null;

        volatile InputWatcher      activeInputWatcher = null;

        private volatile Process geteventProcess;
        private volatile Thread  geteventThread;

        Session(String udid, int w, int h, AccessibilityInspector inspector) {
            this.udid        = udid;
            this.deviceWidth = w;
            this.deviceHeight= h;
            this.startedAtMs = System.currentTimeMillis();
            this.inspector   = inspector;
        }

        void startGeteventListener(String sessionId) {
            geteventThread = new Thread(() -> {
                try {
                    geteventProcess = new ProcessBuilder(
                            adbPath, "-s", udid, "shell", "getevent", "-lt")
                            .redirectErrorStream(false).start();

                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            geteventProcess.getInputStream(), StandardCharsets.UTF_8));

                    long   btnTouchDownMs = -1;
                    String line;

                    while (!Thread.currentThread().isInterrupted()
                            && (line = br.readLine()) != null) {

                        if (line.contains("ABS_MT_POSITION_X")) {
                            int v = parseHexToken(line);
                            if (v >= 0) {
                                touchLastX = v;
                                if (touchDown && touchStartX < 0) touchStartX = v;
                            }
                        } else if (line.contains("ABS_MT_POSITION_Y")) {
                            int v = parseHexToken(line);
                            if (v >= 0) {
                                touchLastY = v;
                                if (touchDown && touchStartY < 0) touchStartY = v;
                            }
                        } else if (line.contains("BTN_TOUCH") && line.contains("DOWN")) {
                            btnTouchDownMs = System.currentTimeMillis();
                            touchDown  = true;
                            touchStartX = touchStartY = -1;
                        } else if (line.contains("BTN_TOUCH") && line.contains("UP")) {
                            touchDown = false;
                            handleTouchUp(sessionId, btnTouchDownMs);
                            btnTouchDownMs = -1;
                            touchStartX = touchStartY = touchLastX = touchLastY = -1;

                        } else if (line.contains("KEY_BACK") && line.contains("UP")) {
                            stopInputWatcher(this);
                            String json = buildStepFromEl(sessionId, this, "back", null, null, null);
                            if (json != null) pushSseEvent(json);
                        } else if (line.contains("KEY_HOME") && line.contains("UP")) {
                            stopInputWatcher(this);
                            String json = buildStepFromEl(sessionId, this, "home", null, null, null);
                            if (json != null) pushSseEvent(json);
                        }
                    }
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        // iOS or disconnected device: failure is expected and harmless
                        System.out.println("[RecordingEngine] getevent unavailable for "
                                + udid + " (" + e.getMessage() + ")");
                    }
                }
            }, "getevent-" + udid);
            geteventThread.setDaemon(true);
            geteventThread.start();
        }

        private void handleTouchUp(String sessionId, long btnTouchDownMs) {
            int fx = touchLastX, fy = touchLastY;
            int sx = touchStartX, sy = touchStartY;
            if (fx < 0 || fy < 0) return;
            if (System.currentTimeMillis() < suppressUntilMs) return;

            long  duration = btnTouchDownMs > 0 ? System.currentTimeMillis() - btnTouchDownMs : 0;
            int   dx       = (sx >= 0) ? (fx - sx) : 0;
            int   dy       = (sy >= 0) ? (fy - sy) : 0;
            float dist     = (float) Math.sqrt((double)(dx * dx + dy * dy));

            if (dist >= SWIPE_THRESHOLD_PX) {
                String dir  = (Math.abs(dy) >= Math.abs(dx))
                        ? (dy > 0 ? "down" : "up") : (dx > 0 ? "right" : "left");
                String type = (Math.abs(dy) >= Math.abs(dx)) ? "scroll" : "swipe";
                String json = buildStep(sessionId, this, type,
                        sx >= 0 ? sx : fx, sy >= 0 ? sy : fy, dir, null);
                if (json != null) pushSseEvent(json);
                return;
            }

            if (duration >= LONG_PRESS_THRESHOLD_MS) {
                String json = buildStep(sessionId, this, "long_press", fx, fy, null, null);
                if (json != null) pushSseEvent(json);
                lastTapMs = 0;
                return;
            }

            long now = System.currentTimeMillis();
            if (lastTapMs > 0 && (now - lastTapMs) < DOUBLE_TAP_WINDOW_MS
                    && Math.abs(fx - lastTapX) < 60 && Math.abs(fy - lastTapY) < 60) {
                ScheduledFuture<?> pending = pendingTap;
                if (pending != null) { pending.cancel(false); pendingTap = null; }
                lastTapMs = 0;
                String json = buildStep(sessionId, this, "double_tap", fx, fy, null, null);
                if (json != null) pushSseEvent(json);
                return;
            }

            final int finalX = fx, finalY = fy;
            ScheduledFuture<?> existing = pendingTap;
            if (existing != null) existing.cancel(false);
            lastTapMs = now;
            lastTapX  = fx;
            lastTapY  = fy;
            pendingTap = scheduler.schedule(() -> {
                pendingTap = null;
                lastTapMs  = 0;
                inspector.invalidateCache();
                UIElement el = inspector.findElementAt(finalX, finalY);
                if (el != null && "input".equals(el.elType)) {
                    String preText = el.text != null ? el.text : "";
                    activeInputWatcher = new InputWatcher(this, sessionId,
                            el.getBoundsString(), preText);
                    activeInputWatcher.start();
                }
                String json = buildStep(sessionId, this, "tap", finalX, finalY, null, null);
                if (json != null) pushSseEvent(json);
            }, DOUBLE_TAP_WINDOW_MS, TimeUnit.MILLISECONDS);
        }

        void stop() {
            if (geteventThread  != null) geteventThread.interrupt();
            if (geteventProcess != null) geteventProcess.destroyForcibly();
            if (pendingTap      != null) pendingTap.cancel(false);
            stopInputWatcher(this);
            inspector.close();
            for (OutputStream out : sseClients) {
                try { out.close(); } catch (Exception ignored) {}
            }
            sseClients.clear();
        }

        void pushSseEvent(String json) {
            byte[] data = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
            List<OutputStream> dead = new ArrayList<>();
            for (OutputStream out : sseClients) {
                try { out.write(data); out.flush(); }
                catch (Exception e) { dead.add(out); }
            }
            sseClients.removeAll(dead);
        }

        private int parseHexToken(String line) {
            String[] parts = line.trim().split("\\s+");
            String hex = parts[parts.length - 1];
            try { return Integer.parseUnsignedInt(hex, 16); }
            catch (Exception e) { return -1; }
        }
    }
}
