package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recording engine — manages Android action recording sessions.
 *
 * Each session runs a getevent listener thread to detect physical device taps
 * and pushes recorded steps to registered SSE clients.
 *
 * Actions triggered via the overlay (RecordingActionHandler) are also
 * executed here. A suppression window prevents getevent from double-counting
 * ADB-injected events.
 *
 * Thread safety: sessions map is ConcurrentHashMap; per-session state
 * uses volatile for the suppress flag and CopyOnWriteArrayList for SSE clients.
 */
public final class RecordingEngine {

    private static final ObjectMapper MAPPER        = new ObjectMapper();
    private static final int          ADB_TIMEOUT_S = 8;

    private final String adbPath;

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

    // ── Actions ───────────────────────────────────────────────────────────────

    public String executeTap(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;
        s.suppressUntilMs = System.currentTimeMillis() + 1200;
        runAdb(s.udid, "input", "tap", str(x), str(y));
        return buildStep(sessionId, s, "tap", x, y, null, null, null, null);
    }

    public String executeDoubleTap(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;
        s.suppressUntilMs = System.currentTimeMillis() + 1500;
        runAdb(s.udid, "input", "tap", str(x), str(y));
        try { Thread.sleep(120); } catch (InterruptedException ignored) {}
        runAdb(s.udid, "input", "tap", str(x), str(y));
        return buildStep(sessionId, s, "double_tap", x, y, null, null, null, null);
    }

    public String executeLongPress(String sessionId, int x, int y) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;
        s.suppressUntilMs = System.currentTimeMillis() + 2000;
        // Long press = swipe from/to same point over 800ms
        runAdb(s.udid, "input", "swipe", str(x), str(y), str(x), str(y), "800");
        return buildStep(sessionId, s, "long_press", x, y, null, null, null, null);
    }

    public String executeSwipe(String sessionId, int x1, int y1, int x2, int y2) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;
        s.suppressUntilMs = System.currentTimeMillis() + 1000;
        runAdb(s.udid, "input", "swipe", str(x1), str(y1), str(x2), str(y2), "300");
        int dx = x2 - x1;
        int dy = y2 - y1;
        String dir = (Math.abs(dy) >= Math.abs(dx))
                ? (dy > 0 ? "down" : "up")
                : (dx > 0 ? "right" : "left");
        return buildStep(sessionId, s, "swipe", x1, y1, x2, y2, dir, null);
    }

    public String executeInput(String sessionId, String text) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;
        // ADB input text requires spaces as %s
        runAdb(s.udid, "input", "text", text.replace(" ", "%s"));
        return buildStep(sessionId, s, "input", null, null, null, null, null, text);
    }

    public String executeKey(String sessionId, String key) {
        Session s = sessions.get(sessionId);
        if (s == null) return null;
        runAdb(s.udid, "input", "keyevent", toKeycode(key));
        String type = "back".equalsIgnoreCase(key) || "home".equalsIgnoreCase(key)
                ? "tap" : "hide_keyboard";
        return buildStep(sessionId, s, type, null, null, null, null, null, null);
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

    private String buildStep(String sessionId, Session s, String type,
                             Integer x, Integer y, Integer x2, Integer y2,
                             String dir, String inputText) {
        try {
            int n = stepCounters.get(sessionId).incrementAndGet();

            UiHierarchyParser.ElementInfo el = null;
            if (x != null && x >= 0 && y != null && y >= 0) {
                String xml = UiHierarchyParser.dumpHierarchy(adbPath, s.udid);
                el = UiHierarchyParser.findElementAt(xml, x, y);
            }

            ObjectNode node = MAPPER.createObjectNode();
            node.put("id",   "step-" + System.currentTimeMillis() + "-" + n);
            node.put("n",    n);
            node.put("type", type);
            if (inputText != null) node.put("inputVal", inputText);
            if (dir != null)       node.put("dir", dir);

            if (el != null) {
                ObjectNode elNode = MAPPER.createObjectNode();
                elNode.put("shortId",    el.shortId);
                elNode.put("resourceId", el.resourceId);
                elNode.put("accessId",   el.accessId);
                elNode.put("text",       el.text);
                elNode.put("elType",     el.elType);
                elNode.put("className",  el.className);
                elNode.put("bounds",     el.bounds);
                node.set("el", elNode);
            } else {
                node.putNull("el");
            }

            long elapsedSec = (System.currentTimeMillis() - s.startedAtMs) / 1000;
            node.put("timeStr", String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60));

            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            System.err.println("[RecordingEngine] buildStep error: " + e.getMessage());
            return null;
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

    // ── Session ───────────────────────────────────────────────────────────────

    private class Session {
        final String udid;
        final int    deviceWidth;
        final int    deviceHeight;
        final long   startedAtMs;
        volatile long suppressUntilMs = 0;

        final List<OutputStream> sseClients = new CopyOnWriteArrayList<>();

        private volatile Process geteventProcess;
        private volatile Thread  geteventThread;

        Session(String udid, int w, int h) {
            this.udid        = udid;
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

                    int  rawX = -1, rawY = -1;
                    long btnTouchDownMs = -1;

                    String line;
                    while (!Thread.currentThread().isInterrupted() && (line = br.readLine()) != null) {
                        // Typical lines:
                        // [  123.456] /dev/input/event1: EV_ABS  ABS_MT_POSITION_X  000001f4
                        // [  123.456] /dev/input/event1: EV_KEY  BTN_TOUCH          DOWN

                        if (line.contains("ABS_MT_POSITION_X")) {
                            rawX = parseHexToken(line);
                        } else if (line.contains("ABS_MT_POSITION_Y")) {
                            rawY = parseHexToken(line);
                        } else if (line.contains("BTN_TOUCH") && line.contains("DOWN")) {
                            btnTouchDownMs = System.currentTimeMillis();
                        } else if (line.contains("BTN_TOUCH") && line.contains("UP")) {
                            if (rawX < 0 || rawY < 0) {
                                rawX = -1; rawY = -1; btnTouchDownMs = -1;
                                continue;
                            }
                            if (System.currentTimeMillis() < suppressUntilMs) {
                                // This event was triggered by an ADB command from the overlay
                                rawX = -1; rawY = -1; btnTouchDownMs = -1;
                                continue;
                            }
                            long duration = btnTouchDownMs > 0
                                    ? System.currentTimeMillis() - btnTouchDownMs : 0;
                            String type = duration > 600 ? "long_press" : "tap";
                            int fx = rawX, fy = rawY;
                            rawX = -1; rawY = -1; btnTouchDownMs = -1;

                            String json = buildStep(sessionId, this, type, fx, fy, null, null, null, null);
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

        void stop() {
            if (geteventThread != null)  geteventThread.interrupt();
            if (geteventProcess != null) geteventProcess.destroyForcibly();
            for (OutputStream out : sseClients) {
                try { out.close(); } catch (Exception ignored) {}
            }
            sseClients.clear();
        }

        void pushSseEvent(String json) {
            byte[] data = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
            List<OutputStream> dead = new ArrayList<>();
            for (OutputStream out : sseClients) {
                try {
                    out.write(data);
                    out.flush();
                } catch (Exception e) {
                    dead.add(out);
                }
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
