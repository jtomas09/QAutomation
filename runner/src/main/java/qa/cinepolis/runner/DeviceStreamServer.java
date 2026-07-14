package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import qa.cinepolis.runner.mirror.DeviceMirrorProvider;
import qa.cinepolis.runner.mirror.MirrorProviderRegistry;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.*;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Device Stream Service — Live Preview Engine (Phase 10)
 *
 * Exposes a lightweight HTTP server on STREAM_PORT (default 8082).
 * Architecture is designed to evolve from PNG polling → MJPEG → H264/scrcpy
 * without modifying the Backend or Frontend contracts.
 *
 * CURRENT MODE: Live Preview + Device Mirror — un frame por captura, vía la
 * abstracción DeviceMirrorProvider (ver paquete qa.cinepolis.runner.mirror).
 * MirrorProviderRegistry elige el provider correcto (ADB para Android, WDA
 * para iOS) según la forma del UDID — este servidor no conoce ni le importa
 * QUÉ herramienta produjo cada frame, solo que llega como PNG.
 *
 * Security:
 *   - UDID validated against [a-zA-Z0-9\-_.] before delegating a la captura
 *   - Cada provider gestiona su propia concurrencia por dispositivo
 *   - Device existence verificado antes de capturar (provider.isDeviceConnected)
 */
public class DeviceStreamServer {

    private static final String PATH_PREFIX       = "/api/device-stream/";
    private static final String PATH_MIRROR       = "/api/device-mirror/";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int                    port;
    private final RecordingEngine        recordingEngine;
    private final MirrorProviderRegistry providers;

    private HttpServer server;

    public DeviceStreamServer(int port, String adbPath) {
        this.port            = port;
        this.recordingEngine = new RecordingEngine(adbPath);
        this.providers       = new MirrorProviderRegistry(adbPath);
    }

    /** Resuelve el provider de captura para este UDID, o null si su plataforma no está soportada en este host. */
    private DeviceMirrorProvider resolveProvider(String udid) {
        return providers.resolve(udid);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 32);
        server.createContext(PATH_PREFIX, new StreamHandler());
        server.createContext(PATH_MIRROR, new MirrorHandler());

        // Health check — frontend uses this to detect Runner reachability
        server.createContext("/health", ex -> {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            byte[] body = "{\"status\":\"ok\",\"service\":\"device-stream\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(body); }
        });

        // Device mirror status — GET /api/device/status?udid={udid}
        server.createContext("/api/device/status", ex -> {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            String query = ex.getRequestURI().getQuery();
            String udid = "";
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("udid=")) {
                        udid = java.net.URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            DeviceMirrorProvider statusProvider = !udid.isBlank() ? resolveProvider(udid) : null;
            boolean connected = statusProvider != null && statusProvider.isDeviceConnected(udid);
            MirrorService.DeviceMirrorState state = MirrorService.getState(udid, connected);

            // Fase observable del Mirror — desacoplada de "connected" (que solo dice si el
            // dispositivo responde, no si WDA realmente produce frames). Android no tiene
            // fases WDA: "WDA" es el nombre exclusivo de IOSMirrorProvider.
            boolean iosDevice = statusProvider != null && "WDA".equals(statusProvider.name());
            qa.cinepolis.runner.mirror.IOSMirrorStateTracker.Snapshot snapshot =
                    qa.cinepolis.runner.mirror.IOSMirrorStateTracker.get(udid, connected, iosDevice);

            String json = String.format(
                "{\"connected\":%b,\"deviceId\":\"%s\",\"isStreaming\":%b,\"resolution\":\"auto\",\"fps\":%d,"
                + "\"mirrorPhase\":\"%s\",\"reason\":%s}",
                state.connected(), state.deviceId(), state.isStreaming(), state.fps(),
                snapshot.phase().name(), jsonStringOrNull(snapshot.reason())
            );
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(body); }
        });

        // Recording engine endpoints
        server.createContext("/api/recording/start",   new RecordingStartHandler());
        server.createContext("/api/recording/stop/",   new RecordingStopHandler());
        server.createContext("/api/recording/action/", new RecordingActionHandler());
        server.createContext("/api/recording/events/", new RecordingEventsHandler());

        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "device-stream-worker");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        System.out.println("[DeviceStream] Server started → http://localhost:" + port + PATH_PREFIX + "{udid}");
        System.out.println("[DeviceMirror] MJPEG endpoint  → http://localhost:" + port + PATH_MIRROR + "{udid}");
        System.out.println("[Recording]   Engine endpoint  → http://localhost:" + port + "/api/recording/{start|stop|action|events}");
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
                    System.out.println("[DeviceStream][AUDIT] Rejected invalid UDID: " + udid);
                    sendText(ex, 400, "Invalid device identifier");
                    return;
                }

                System.out.println("[DeviceStream][AUDIT] Preview request for UDID: " + udid
                        + " | from: " + ex.getRemoteAddress());

                DeviceMirrorProvider provider = resolveProvider(udid);
                boolean connected = provider != null && provider.isDeviceConnected(udid);
                System.out.println("[DeviceStream][AUDIT] Device connected: " + connected
                        + " | provider: " + (provider != null ? provider.name() : "none") + " | UDID: " + udid);
                if (!connected) {
                    sendText(ex, 404, "Device not connected: " + udid);
                    return;
                }

                long t0  = System.currentTimeMillis();
                byte[] png = provider.captureFrame(udid);
                long ms  = System.currentTimeMillis() - t0;

                if (png == null) {
                    System.out.println("[DeviceStream][AUDIT] Capture FAILED for " + udid + " (" + ms + "ms)");
                    sendText(ex, 503, "Screenshot capture failed or device busy");
                    return;
                }

                System.out.println("[DeviceStream][AUDIT] Capture OK: " + png.length + " bytes in " + ms + "ms | UDID: " + udid);

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

    // ── MJPEG mirror handler ──────────────────────────────────────────────────

    private class MirrorHandler implements HttpHandler {

        private static final String BOUNDARY  = "frameBound";
        private static final int    TARGET_FPS = 20;
        private static final long   FRAME_MS   = 1000L / TARGET_FPS; // 50 ms per frame

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
                if (!path.startsWith(PATH_MIRROR)) {
                    sendText(ex, 404, "Not Found");
                    return;
                }

                String udid = path.substring(PATH_MIRROR.length()).trim();
                if (udid.isEmpty() || udid.contains("/") || udid.contains("..") ||
                        !udid.matches("[a-zA-Z0-9\\-_.]+")) {
                    sendText(ex, 400, "Invalid device identifier");
                    return;
                }

                DeviceMirrorProvider provider = resolveProvider(udid);
                if (provider != null) provider.start(udid);

                System.out.println("[DeviceMirror] Stream opened: " + udid
                        + " | provider: " + (provider != null ? provider.name() : "none")
                        + " | client: " + ex.getRemoteAddress());

                ex.getResponseHeaders().set("Content-Type",       "multipart/x-mixed-replace; boundary=" + BOUNDARY);
                ex.getResponseHeaders().set("Cache-Control",      "no-cache, no-store, must-revalidate");
                ex.getResponseHeaders().set("Pragma",             "no-cache");
                ex.getResponseHeaders().set("Connection",         "keep-alive");
                ex.getResponseHeaders().set("X-Accel-Buffering", "no"); // disable nginx buffering
                ex.sendResponseHeaders(200, 0);                         // 0 = streaming / unknown length

                MirrorService.registerStream(udid);
                // Fase 6 — optimización de latencia: un único ImageWriter JPEG vive
                // durante toda la conexión en vez de buscarse vía SPI en cada frame
                // (ImageIO.getImageWritersByFormatName recorre los proveedores
                // registrados cada vez que se llama). No cambia el formato de salida,
                // solo evita repetir esa búsqueda ~20 veces por segundo.
                ImageWriter jpegWriter = createJpegWriter();
                try (OutputStream out = ex.getResponseBody()) {
                    if (jpegWriter == null) {
                        System.err.println("[DeviceMirror] No hay ImageWriter JPEG disponible en este JVM.");
                        return;
                    }
                    final byte[] crLf = "\r\n".getBytes(StandardCharsets.UTF_8);
                    int missCount = 0;

                    while (!Thread.currentThread().isInterrupted()) {
                        long t0 = System.currentTimeMillis();

                        byte[] png = provider != null ? provider.captureFrame(udid) : null;
                        if (png == null) {
                            // Fase 6 — optimización de latencia: antes se llamaba a
                            // isDeviceConnected() (spawns un proceso/HTTP roundtrip
                            // completo) ANTES de cada captura, sin importar si el
                            // dispositivo estaba sano — es decir, ~20 veces/segundo.
                            // La propia falla de captura ya es la señal de que algo anda
                            // mal; solo entonces vale la pena pagar el costo de verificar
                            // conectividad real. Se conserva exactamente la misma ventana
                            // de detección de desconexión (12 intentos ≈ 6 s). Si no hay
                            // provider soportado para este UDID (p.ej. iOS en un Runner
                            // no-macOS), esto se cumple de inmediato y el stream cierra
                            // tras el mismo margen, sin caso especial.
                            if (provider == null || !provider.isDeviceConnected(udid)) {
                                if (++missCount > 12) break; // device gone for ~6 s
                                Thread.sleep(500);
                                continue;
                            }
                            missCount = 0;
                            Thread.sleep(80);
                            continue;
                        }
                        missCount = 0;

                        byte[] jpeg = pngToJpeg(png, 0.78f, jpegWriter);
                        if (jpeg == null) continue;

                        byte[] header = ("--" + BOUNDARY + "\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: " + jpeg.length + "\r\n\r\n"
                        ).getBytes(StandardCharsets.UTF_8);

                        out.write(header);
                        out.write(jpeg);
                        out.write(crLf);
                        out.flush();

                        long elapsed = System.currentTimeMillis() - t0;
                        long sleep   = FRAME_MS - elapsed;
                        if (sleep > 0) Thread.sleep(sleep);
                    }
                } catch (Exception ignored) {
                    // Normal: client closed connection or device disconnected
                } finally {
                    if (jpegWriter != null) jpegWriter.dispose();
                    if (provider != null) provider.stop(udid);
                    MirrorService.deregisterStream(udid);
                    System.out.println("[DeviceMirror] Stream closed: " + udid);
                }
            } catch (Exception e) {
                System.err.println("[DeviceMirror] Handler error: " + e.getMessage());
                try { sendText(ex, 500, "Internal error"); } catch (Exception ignored) {}
            }
        }
    }

    // ── PNG → JPEG conversion ─────────────────────────────────────────────────

    /** Crea un ImageWriter JPEG nuevo. Llamar UNA vez por conexión de stream, no por frame. */
    private static ImageWriter createJpegWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        return writers.hasNext() ? writers.next() : null;
    }

    /**
     * Convierte PNG a JPEG reutilizando el ImageWriter provisto por el llamador
     * (ver createJpegWriter) — evita la búsqueda SPI de un writer nuevo en cada
     * frame. El writer se resetea tras cada uso para poder reutilizarse; el
     * llamador es responsable de invocar writer.dispose() al cerrar el stream.
     */
    private static byte[] pngToJpeg(byte[] png, float quality, ImageWriter writer) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return null;

            // JPEG does not support alpha — convert to RGB
            if (img.getColorModel().hasAlpha()) {
                BufferedImage rgb = new BufferedImage(
                        img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.drawImage(img, 0, 0, null);
                g.dispose();
                img = rgb;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(png.length / 3);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), params);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try { writer.reset(); } catch (Exception ignored) {}
        }
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    /** Serializa un String como literal JSON (comillas + escapado) o el literal "null" si es null. */
    private static String jsonStringOrNull(String value) {
        if (value == null) return "null";
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "null";
        }
    }

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendText(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }

    // ── Recording handlers ────────────────────────────────────────────────────

    /** POST /api/recording/start  body: {"udid":"xxx"}  → {"sessionId","deviceWidth","deviceHeight"} */
    private class RecordingStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                addCorsHeaders(ex);
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))   { sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = MAPPER.readTree(body);
                String udid = json.path("udid").asText("").trim();
                if (udid.isEmpty() || !udid.matches("[a-zA-Z0-9\\-_.]+")) {
                    sendJson(ex, 400, "{\"error\":\"Invalid or missing udid\"}"); return;
                }

                RecordingEngine.StartResult result = recordingEngine.start(udid);
                String resp = String.format(
                    "{\"sessionId\":\"%s\",\"deviceWidth\":%d,\"deviceHeight\":%d}",
                    result.sessionId, result.deviceWidth, result.deviceHeight);
                sendJson(ex, 200, resp);
            } catch (Exception e) {
                System.err.println("[RecordingStart] " + e.getMessage());
                try { sendJson(ex, 500, "{\"error\":\"Internal error\"}"); } catch (Exception ignored) {}
            }
        }
    }

    /** POST /api/recording/stop/{sessionId} */
    private class RecordingStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                addCorsHeaders(ex);
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))   { sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

                String path      = ex.getRequestURI().getPath();
                String sessionId = path.substring("/api/recording/stop/".length()).trim();
                if (sessionId.isEmpty()) { sendJson(ex, 400, "{\"error\":\"sessionId required\"}"); return; }

                boolean stopped = recordingEngine.stop(sessionId);
                sendJson(ex, 200, "{\"stopped\":" + stopped + "}");
            } catch (Exception e) {
                System.err.println("[RecordingStop] " + e.getMessage());
                try { sendJson(ex, 500, "{\"error\":\"Internal error\"}"); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * POST /api/recording/action/{sessionId}
     * body: {"action":"tap","x":500,"y":800} | {"action":"swipe","x1":..,"y1":..,"x2":..,"y2":..}
     *       {"action":"double_tap","x":..,"y":..} | {"action":"long_press","x":..,"y":..}
     *       {"action":"input","text":"..."} | {"action":"key","key":"back"}
     * Returns: step JSON or 404/400.
     */
    private class RecordingActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                addCorsHeaders(ex);
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))   { sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

                String path      = ex.getRequestURI().getPath();
                String sessionId = path.substring("/api/recording/action/".length()).trim();
                if (sessionId.isEmpty()) { sendJson(ex, 400, "{\"error\":\"sessionId required\"}"); return; }
                if (!recordingEngine.sessionExists(sessionId)) { sendJson(ex, 404, "{\"error\":\"session not found\"}"); return; }

                String  body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode req = MAPPER.readTree(body);
                String action = req.path("action").asText("").toLowerCase();

                String result = null;
                switch (action) {
                    case "tap":
                        result = recordingEngine.executeTap(sessionId, req.path("x").asInt(), req.path("y").asInt());
                        break;
                    case "double_tap":
                        result = recordingEngine.executeDoubleTap(sessionId, req.path("x").asInt(), req.path("y").asInt());
                        break;
                    case "long_press":
                        result = recordingEngine.executeLongPress(sessionId, req.path("x").asInt(), req.path("y").asInt());
                        break;
                    case "swipe":
                        result = recordingEngine.executeSwipe(sessionId,
                                req.path("x1").asInt(), req.path("y1").asInt(),
                                req.path("x2").asInt(), req.path("y2").asInt());
                        break;
                    case "input":
                        result = recordingEngine.executeInput(sessionId, req.path("text").asText(""));
                        break;
                    case "key":
                        result = recordingEngine.executeKey(sessionId, req.path("key").asText("back"));
                        break;
                    default:
                        sendJson(ex, 400, "{\"error\":\"Unknown action: " + action + "\"}");
                        return;
                }

                if (result == null) {
                    sendJson(ex, 500, "{\"error\":\"Action failed\"}");
                } else {
                    sendJson(ex, 200, result);
                }
            } catch (Exception e) {
                System.err.println("[RecordingAction] " + e.getMessage());
                try { sendJson(ex, 500, "{\"error\":\"Internal error\"}"); } catch (Exception ignored) {}
            }
        }
    }

    /** GET /api/recording/events/{sessionId} — SSE stream for physical device events */
    private class RecordingEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                addCorsHeaders(ex);
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod()))    { sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

                String path      = ex.getRequestURI().getPath();
                String sessionId = path.substring("/api/recording/events/".length()).trim();
                if (sessionId.isEmpty()) { sendJson(ex, 400, "{\"error\":\"sessionId required\"}"); return; }
                if (!recordingEngine.sessionExists(sessionId)) { sendJson(ex, 404, "{\"error\":\"session not found\"}"); return; }

                ex.getResponseHeaders().set("Content-Type",       "text/event-stream; charset=utf-8");
                ex.getResponseHeaders().set("Cache-Control",      "no-cache");
                ex.getResponseHeaders().set("Connection",         "keep-alive");
                ex.getResponseHeaders().set("X-Accel-Buffering", "no");
                ex.sendResponseHeaders(200, 0); // streaming / unknown length

                OutputStream out = ex.getResponseBody();
                recordingEngine.registerSseClient(sessionId, out);
                try {
                    // Initial connected event
                    out.write("data: {\"type\":\"connected\"}\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    // Keep-alive loop until session ends or client disconnects
                    while (!Thread.currentThread().isInterrupted() && recordingEngine.sessionExists(sessionId)) {
                        Thread.sleep(20_000);
                        out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                } catch (Exception ignored) {
                    // Client closed connection or session ended
                } finally {
                    recordingEngine.unregisterSseClient(sessionId, out);
                    try { out.close(); } catch (Exception ignored2) {}
                }
            } catch (Exception e) {
                System.err.println("[RecordingEvents] " + e.getMessage());
                try { sendJson(ex, 500, "{\"error\":\"Internal error\"}"); } catch (Exception ignored) {}
            }
        }
    }
}
