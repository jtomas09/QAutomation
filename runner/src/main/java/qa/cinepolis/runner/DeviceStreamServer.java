package qa.cinepolis.runner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Device Stream Service — Live Preview Engine (Phase 10)
 *
 * Exposes a lightweight HTTP server on STREAM_PORT (default 8082).
 * Architecture is designed to evolve from PNG polling → MJPEG → H264/scrcpy
 * without modifying the Backend or Frontend contracts.
 *
 * CURRENT MODE: Live Preview — one PNG per request via ADB screencap
 * FUTURE MODE:  Device Mirror — replace captureScreenshot() with MJPEG/scrcpy stream
 *
 * Security:
 *   - UDID validated against [a-zA-Z0-9\-_.] before ADB invocation
 *   - Per-device Semaphore prevents concurrent ADB capture on the same device
 *   - Device existence verified before capture (adb get-state)
 */
public class DeviceStreamServer {

    private static final String PATH_PREFIX       = "/api/device-stream/";
    private static final int    CAPTURE_TIMEOUT_S = 5;
    private static final int    MIN_PNG_BYTES      = 1_024;

    private final int    port;
    private final String adbPath;

    private HttpServer server;

    /** One semaphore per device — prevents concurrent ADB screencap on the same device. */
    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public DeviceStreamServer(int port, String adbPath) {
        this.port    = port;
        this.adbPath = adbPath;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 32);
        server.createContext(PATH_PREFIX, new StreamHandler());
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "device-stream-worker");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        System.out.println("[DeviceStream] Server started → http://localhost:" + port + PATH_PREFIX + "{udid}");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[DeviceStream] Server stopped.");
        }
    }

    public int getPort() { return port; }

    // ── HTTP handler ──────────────────────────────────────────────────────────

    private class StreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                addCorsHeaders(ex);

                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(204, -1);
                    return;
                }
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    sendText(ex, 405, "Method Not Allowed");
                    return;
                }

                String path = ex.getRequestURI().getPath();
                if (!path.startsWith(PATH_PREFIX)) {
                    sendText(ex, 404, "Not Found");
                    return;
                }

                String udid = path.substring(PATH_PREFIX.length()).trim();

                // Security: reject path traversal and invalid characters
                if (udid.isEmpty() || udid.contains("/") || udid.contains("..") ||
                    !udid.matches("[a-zA-Z0-9\\-_.]+")) {
                    sendText(ex, 400, "Invalid device identifier");
                    return;
                }

                if (!isDeviceConnected(udid)) {
                    sendText(ex, 404, "Device not connected: " + udid);
                    return;
                }

                byte[] png = captureScreenshot(udid);
                if (png == null) {
                    sendText(ex, 503, "Screenshot capture failed or device busy");
                    return;
                }

                ex.getResponseHeaders().set("Content-Type",  "image/png");
                ex.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                ex.getResponseHeaders().set("Pragma",        "no-cache");
                ex.getResponseHeaders().set("Expires",       "0");
                ex.sendResponseHeaders(200, png.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(png);
                }

            } catch (Exception e) {
                System.err.println("[DeviceStream] Handler error: " + e.getMessage());
                try { sendText(ex, 500, "Internal error"); } catch (Exception ignored) {}
            }
        }
    }

    // ── ADB helpers ───────────────────────────────────────────────────────────

    /** Verify device is still authorized and connected. */
    private boolean isDeviceConnected(String udid) {
        try {
            ProcessBuilder pb = new ProcessBuilder(adbPath, "-s", udid, "get-state");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            String  out  = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.destroyForcibly();
            return done && "device".equalsIgnoreCase(out);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Capture a PNG screenshot entirely in memory.
     * Runs: adb -s {udid} exec-out screencap -p
     *
     * Design note: this method is intentionally synchronous and blocking.
     * Callers run on a daemon thread from the cached thread pool.
     * The per-device Semaphore ensures only one screencap runs per device at a time.
     *
     * Future evolution: replace this method body with MJPEG/H264 frame extraction
     * without changing any caller or interface.
     */
    private byte[] captureScreenshot(String udid) {
        Semaphore lock = locks.computeIfAbsent(udid, k -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            return null; // Another capture already in progress
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(adbPath, "-s", udid, "exec-out", "screencap", "-p");
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream(1 << 20); // 1 MB initial
            byte[] buf  = new byte[8_192];
            int    read;
            long   deadline = System.currentTimeMillis() + (CAPTURE_TIMEOUT_S * 1_000L);

            try (InputStream in = proc.getInputStream()) {
                while ((read = in.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                    if (System.currentTimeMillis() > deadline) {
                        proc.destroyForcibly();
                        return null;
                    }
                }
            }

            boolean finished = proc.waitFor(1, TimeUnit.SECONDS);
            proc.destroyForcibly();

            if (!finished || proc.exitValue() != 0) return null;

            byte[] data = baos.toByteArray();
            return data.length >= MIN_PNG_BYTES ? data : null;

        } catch (Exception e) {
            System.err.println("[DeviceStream] ADB capture error [" + udid + "]: " + e.getMessage());
            return null;
        } finally {
            lock.release();
        }
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendText(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }
}
