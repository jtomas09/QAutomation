package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recording engine — manages Android action recording sessions with intelligent
 * event classification.
 *
 * Classification rules:
 *  - Tap on EditText + keyboard appears  → emits "tap" step, then starts InputWatcher
 *  - InputWatcher: polls every 600 ms until keyboard dismissed → emits "input" step
 *  - Swipe with |dy| > |dx|             → "scroll" (vertical content change)
 *  - Swipe with |dx| >= |dy|            → "swipe" (horizontal navigation)
 *  - Touch duration > 600 ms            → "long_press"
 *  - Two taps at same location < 250 ms → "double_tap" (with 250 ms buffer)
 *  - KEY_BACK from getevent             → "back"
 *  - KEY_HOME from getevent             → "home"
 *
 * Thread safety:
 *  - sessions/stepCounters: ConcurrentHashMap
 *  - per-session volatile fields for cross-thread communication
 *  - CopyOnWriteArrayList for SSE clients
 *  - Shared ScheduledExecutorService for double-tap buffer timers
 */
public final class RecordingEngine {

    private static final ObjectMapper MAPPER                 = new ObjectMapper();
    private static final int          ADB_TIMEOUT_S          = 8;
    private static final int          SWIPE_THRESHOLD_PX     = 50;
    private static final int          DOUBLE_TAP_WINDOW_MS   = 250;
    private static final int          LONG_PRESS_THRESHOLD_MS = 600;
    private static final int          UI_SETTLE_MS           = 350;

    private final String adbPath;

    // Shared timer for 250 ms double-tap detection windows
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
        int[]  dims      = UiHierarchyParser.getScreenSize(adbPath, udid);
        Session session  = new Session(udid, dims[0], dims[1]);
        sessions.put(sessionId, session);
        stepCounters.put(sessionId, new AtomicInteger(0));
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

    /**
     * Executes a tap, then classifies the result by comparing pre/post hierarchy:
     * - EditText tapped + keyboard appeared → emit "tap" step, start InputWatcher
     * - Otherwise → emit "tap"
     */
    public String executeTap(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 1400;

        String preXml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
        UiHierarchyParser.ElementInfo el = (preXml != null)
                ? UiHierarchyParser.findElementAt(preXml, x, y) : null;

        runAdb(s.udid, "input", "tap", str(x), str(y));

        sleep(UI_SETTLE_MS);

        // Classify: check if keyboard appeared after tapping an EditText
        String postXml  = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
        boolean kbBefore = UiHierarchyParser.isKeyboardVisible(preXml);
        boolean kbAfter  = UiHierarchyParser.isKeyboardVisible(postXml);

        if (!kbBefore && kbAfter && el != null && "input".equals(el.elType)) {
            // Keyboard just appeared on an EditText tap — start watching for text
            String preText = el.text != null ? el.text : "";
            s.activeInputWatcher = new InputWatcher(s, sessionId, el.bounds, preText);
            s.activeInputWatcher.start();
            System.out.println("[RecordingEngine] InputWatcher started for bounds=" + el.bounds);
        }

        return buildStepFromEl(sessionId, s, "tap", el, null, null);
    }

    public String executeDoubleTap(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 1600;

        String preXml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
        UiHierarchyParser.ElementInfo el = (preXml != null)
                ? UiHierarchyParser.findElementAt(preXml, x, y) : null;

        runAdb(s.udid, "input", "tap", str(x), str(y));
        sleep(120);
        runAdb(s.udid, "input", "tap", str(x), str(y));
        sleep(UI_SETTLE_MS);

        return buildStepFromEl(sessionId, s, "double_tap", el, null, null);
    }

    public String executeLongPress(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 2200;

        String preXml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
        UiHierarchyParser.ElementInfo el = (preXml != null)
                ? UiHierarchyParser.findElementAt(preXml, x, y) : null;

        runAdb(s.udid, "input", "swipe", str(x), str(y), str(x), str(y), "800");
        sleep(UI_SETTLE_MS);

        return buildStepFromEl(sessionId, s, "long_press", el, null, null);
    }

    /**
     * Executes a swipe and classifies it:
     * - |dy| > |dx| → "scroll" (vertical, changes content position)
     * - |dx| >= |dy| → "swipe" (horizontal, navigates between screens)
     */
    public String executeSwipe(String sessionId, int x1, int y1, int x2, int y2) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        s.suppressUntilMs = System.currentTimeMillis() + 1200;

        runAdb(s.udid, "input", "swipe", str(x1), str(y1), str(x2), str(y2), "300");
        sleep(UI_SETTLE_MS);

        int dx = x2 - x1;
        int dy = y2 - y1;
        // Direction label always reflects the finger movement direction
        String dir = (Math.abs(dy) >= Math.abs(dx))
                ? (dy > 0 ? "down" : "up")
                : (dx > 0 ? "right" : "left");
        // Vertical finger movement → content scrolls → "scroll"
        // Horizontal → page/item change → "swipe"
        String type = (Math.abs(dy) >= Math.abs(dx)) ? "scroll" : "swipe";

        return buildStepFromEl(sessionId, s, type, null, dir, null);
    }

    public String executeInput(String sessionId, String text) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        stopInputWatcher(s);
        runAdb(s.udid, "input", "text", text.replace(" ", "%s"));
        sleep(UI_SETTLE_MS);

        // Find currently-focused element
        String postXml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
        UiHierarchyParser.ElementInfo el = (postXml != null)
                ? UiHierarchyParser.findFocusedEditText(postXml) : null;

        return buildStepFromEl(sessionId, s, "input", el, null, text);
    }

    public String executeKey(String sessionId, String key) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;

        // Key events stop any active text-input watch
        if ("back".equalsIgnoreCase(key) || "home".equalsIgnoreCase(key)
                || "enter".equalsIgnoreCase(key) || "done".equalsIgnoreCase(key)) {
            stopInputWatcher(s);
        }

        runAdb(s.udid, "input", "keyevent", toKeycode(key));

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

    /** Builds a step JSON from a pre-found element (no extra hierarchy dump). */
    private String buildStepFromEl(String sessionId, Session s, String type,
                                    UiHierarchyParser.ElementInfo el,
                                    String dir, String inputText) {
        try {
            AtomicInteger counter = stepCounters.get(sessionId);
            if (counter == null) return null;
            int n = counter.incrementAndGet();

            ObjectNode node = MAPPER.createObjectNode();
            node.put("id",   "step-" + System.currentTimeMillis() + "-" + n);
            node.put("n",    n);
            node.put("type", type);
            if (inputText != null) node.put("inputVal", inputText);
            if (dir != null)       node.put("dir", dir);
            appendElement(node, el);
            appendTime(node, s);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            System.err.println("[RecordingEngine] buildStepFromEl error: " + e.getMessage());
            return null;
        }
    }

    /** Builds a step by dumping hierarchy and finding element at (x, y). Used by getevent path. */
    private String buildStep(String sessionId, Session s, String type,
                              int x, int y, String dir, String inputText) {
        try {
            AtomicInteger counter = stepCounters.get(sessionId);
            if (counter == null) return null;
            int n = counter.incrementAndGet();

            String xml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
            UiHierarchyParser.ElementInfo el = (xml != null && x >= 0 && y >= 0)
                    ? UiHierarchyParser.findElementAt(xml, x, y) : null;

            ObjectNode node = MAPPER.createObjectNode();
            node.put("id",   "step-" + System.currentTimeMillis() + "-" + n);
            node.put("n",    n);
            node.put("type", type);
            if (inputText != null) node.put("inputVal", inputText);
            if (dir != null)       node.put("dir", dir);
            appendElement(node, el);
            appendTime(node, s);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            System.err.println("[RecordingEngine] buildStep error: " + e.getMessage());
            return null;
        }
    }

    private void appendElement(ObjectNode node, UiHierarchyParser.ElementInfo el) {
        if (el != null) {
            ObjectNode e = MAPPER.createObjectNode();
            e.put("shortId",    el.shortId);
            e.put("resourceId", el.resourceId);
            e.put("accessId",   el.accessId);
            e.put("text",       el.text);
            e.put("elType",     el.elType);
            e.put("className",  el.className);
            e.put("bounds",     el.bounds);
            node.set("el", e);
        } else {
            node.putNull("el");
        }
    }

    private void appendTime(ObjectNode node, Session s) {
        long elapsedSec = (System.currentTimeMillis() - s.startedAtMs) / 1000;
        node.put("timeStr", String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60));
    }

    private void stopInputWatcher(Session s) {
        if (s.activeInputWatcher != null) {
            s.activeInputWatcher.stop();
            s.activeInputWatcher = null;
        }
    }

    private void runAdb(String udid, String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(adbPath);
            cmd.add("-s");
            cmd.add(udid);
            Collections.addAll(cmd, args);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            p.waitFor(ADB_TIMEOUT_S, TimeUnit.SECONDS);
            p.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[RecordingEngine] ADB error: " + e.getMessage());
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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

    // ── InputWatcher ──────────────────────────────────────────────────────────

    /**
     * Polls the device hierarchy every 600 ms after a tap on an EditText.
     * When the on-screen keyboard is dismissed, reads the final text and
     * emits an "input" step via SSE.
     * Timeout: 45 seconds.
     */
    private class InputWatcher {
        private final Session  s;
        private final String   sessionId;
        private final String   preBounds;   // bounds of the EditText at tap time
        private final String   preText;     // text content before the tap
        private final Thread   thread;
        private volatile boolean stopped = false;

        InputWatcher(Session s, String sessionId, String preBounds, String preText) {
            this.s          = s;
            this.sessionId  = sessionId;
            this.preBounds  = preBounds != null ? preBounds : "";
            this.preText    = preText  != null ? preText   : "";
            this.thread     = new Thread(this::run, "input-watcher-" + s.udid);
            this.thread.setDaemon(true);
        }

        void start() { thread.start(); }

        void stop()  {
            stopped = true;
            thread.interrupt();
        }

        private void run() {
            long deadline = System.currentTimeMillis() + 45_000;

            while (!stopped && System.currentTimeMillis() < deadline) {
                sleep(600);
                if (stopped) break;

                String xml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
                if (xml == null) continue;

                boolean kbVisible = UiHierarchyParser.isKeyboardVisible(xml);

                if (!kbVisible) {
                    // Keyboard gone — get final text
                    String finalText = UiHierarchyParser.getElementTextAtBounds(xml, preBounds);
                    if (finalText == null) {
                        // Try finding by focused EditText
                        UiHierarchyParser.ElementInfo focused = UiHierarchyParser.findFocusedEditText(xml);
                        if (focused != null) finalText = focused.text;
                    }
                    // Only emit if text actually changed
                    if (finalText != null && !finalText.equals(preText) && !finalText.isEmpty()) {
                        System.out.println("[InputWatcher] Text changed: '" + preText + "' → '" + finalText + "'");
                        String json = buildStepFromEl(sessionId, s, "input",
                                UiHierarchyParser.getElementTextAtBounds(xml, preBounds) != null
                                        ? UiHierarchyParser.findElementAt(xml,
                                                parseBoundsCenter(preBounds)[0],
                                                parseBoundsCenter(preBounds)[1])
                                        : UiHierarchyParser.findFocusedEditText(xml),
                                null, finalText);
                        if (json != null) s.pushSseEvent(json);
                    }
                    break;
                }
            }
            // Mark self as inactive
            if (s.activeInputWatcher == this) s.activeInputWatcher = null;
        }

        /** Returns center point of "[x1,y1][x2,y2]" bounds, or [-1,-1] on failure. */
        private int[] parseBoundsCenter(String bounds) {
            try {
                String b = bounds.replace("][", ",").replace("[", "").replace("]", "");
                String[] p = b.split(",");
                int x1 = Integer.parseInt(p[0].trim()), y1 = Integer.parseInt(p[1].trim());
                int x2 = Integer.parseInt(p[2].trim()), y2 = Integer.parseInt(p[3].trim());
                return new int[]{(x1 + x2) / 2, (y1 + y2) / 2};
            } catch (Exception e) {
                return new int[]{-1, -1};
            }
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private class Session {
        final String udid;
        final int    deviceWidth;
        final int    deviceHeight;
        final long   startedAtMs;

        // Suppress getevent events triggered by ADB input commands
        volatile long suppressUntilMs = 0;

        // SSE clients
        final List<OutputStream> sseClients = new CopyOnWriteArrayList<>();

        // getevent movement tracking
        volatile int     touchStartX = -1;
        volatile int     touchStartY = -1;
        volatile int     touchLastX  = -1;
        volatile int     touchLastY  = -1;
        volatile boolean touchDown   = false;

        // Double-tap detection (250 ms window)
        volatile long                  lastTapMs     = 0;
        volatile int                   lastTapX      = -1;
        volatile int                   lastTapY      = -1;
        volatile ScheduledFuture<?>    pendingTap    = null;

        // EditText input monitoring
        volatile InputWatcher          activeInputWatcher = null;

        // getevent process
        private volatile Process geteventProcess;
        private volatile Thread  geteventThread;

        Session(String udid, int w, int h) {
            this.udid         = udid;
            this.deviceWidth  = w;
            this.deviceHeight = h;
            this.startedAtMs  = System.currentTimeMillis();
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

                    while (!Thread.currentThread().isInterrupted() && (line = br.readLine()) != null) {
                        // Sample lines (whitespace-normalised for parsing):
                        // [t] /dev/input/eventX: EV_ABS ABS_MT_POSITION_X  000001f4
                        // [t] /dev/input/eventX: EV_KEY BTN_TOUCH  DOWN
                        // [t] /dev/input/eventX: EV_KEY KEY_BACK  DOWN
                        // [t] /dev/input/eventX: EV_KEY KEY_HOME  DOWN

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
                            touchStartX = -1;
                            touchStartY = -1;
                        } else if (line.contains("BTN_TOUCH") && line.contains("UP")) {
                            touchDown = false;
                            handleTouchUp(sessionId, btnTouchDownMs);
                            btnTouchDownMs = -1;
                            touchStartX = touchStartY = touchLastX = touchLastY = -1;

                        // System key events (not filtered by suppress window)
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
                        System.err.println("[RecordingEngine] getevent error [" + udid + "]: " + e.getMessage());
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

            if (System.currentTimeMillis() < suppressUntilMs) return; // overlay-triggered

            long duration = btnTouchDownMs > 0 ? System.currentTimeMillis() - btnTouchDownMs : 0;

            // Calculate movement
            int  dx   = (sx >= 0) ? (fx - sx) : 0;
            int  dy   = (sy >= 0) ? (fy - sy) : 0;
            float dist = (float) Math.sqrt((double)(dx * dx + dy * dy));

            if (dist >= SWIPE_THRESHOLD_PX) {
                // Significant movement — classify as scroll or swipe
                String dir  = (Math.abs(dy) >= Math.abs(dx))
                        ? (dy > 0 ? "down" : "up")
                        : (dx > 0 ? "right" : "left");
                String type = (Math.abs(dy) >= Math.abs(dx)) ? "scroll" : "swipe";
                String json = buildStep(sessionId, this, type, sx >= 0 ? sx : fx, sy >= 0 ? sy : fy, dir, null);
                if (json != null) pushSseEvent(json);
                return;
            }

            // Small movement = tap-family
            if (duration >= LONG_PRESS_THRESHOLD_MS) {
                // Long press
                String json = buildStep(sessionId, this, "long_press", fx, fy, null, null);
                if (json != null) pushSseEvent(json);
                lastTapMs = 0;
                return;
            }

            // Potential tap — check for double tap
            long now = System.currentTimeMillis();
            if (lastTapMs > 0 && (now - lastTapMs) < DOUBLE_TAP_WINDOW_MS
                    && Math.abs(fx - lastTapX) < 60 && Math.abs(fy - lastTapY) < 60) {
                // Double tap detected — cancel the buffered single tap and emit double tap
                ScheduledFuture<?> pending = pendingTap;
                if (pending != null) { pending.cancel(false); pendingTap = null; }
                lastTapMs = 0;
                String json = buildStep(sessionId, this, "double_tap", fx, fy, null, null);
                if (json != null) pushSseEvent(json);
                return;
            }

            // Buffer this tap for DOUBLE_TAP_WINDOW_MS before emitting single tap
            final int finalX = fx, finalY = fy;
            ScheduledFuture<?> existing = pendingTap;
            if (existing != null) existing.cancel(false);
            lastTapMs = now;
            lastTapX  = fx;
            lastTapY  = fy;
            pendingTap = scheduler.schedule(() -> {
                pendingTap = null;
                lastTapMs  = 0;

                // Check if tapped element is EditText — if so, start input watcher
                String preXml = UiHierarchyParser.dumpHierarchy(adbPath, udid);
                String type   = "tap";

                if (preXml != null) {
                    UiHierarchyParser.ElementInfo el = UiHierarchyParser.findElementAt(preXml, finalX, finalY);
                    if (el != null && "input".equals(el.elType)) {
                        // Physical tap on EditText — may trigger keyboard; start watcher
                        String preText = el.text != null ? el.text : "";
                        activeInputWatcher = new InputWatcher(this, sessionId, el.bounds, preText);
                        activeInputWatcher.start();
                    }
                }

                String json = buildStep(sessionId, this, type, finalX, finalY, null, null);
                if (json != null) pushSseEvent(json);
            }, DOUBLE_TAP_WINDOW_MS, TimeUnit.MILLISECONDS);
        }

        void stop() {
            if (geteventThread != null)  geteventThread.interrupt();
            if (geteventProcess != null) geteventProcess.destroyForcibly();
            if (pendingTap != null)      pendingTap.cancel(false);
            stopInputWatcher(this);
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
