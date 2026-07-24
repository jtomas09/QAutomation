package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import qa.cinepolis.runner.mirror.DeviceMirrorProvider;
import qa.cinepolis.runner.mirror.MirrorProviderRegistry;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Iterator;
import java.util.concurrent.*;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

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

    // Contraseña del keystore HTTPS local (certificado autofirmado de solo desarrollo,
    // generado localmente en {agentDataDir}/certs/runner.p12 — nunca un secreto real,
    // nunca sale de esta máquina, no protege nada más allá de ese archivo local).
    private static final String HTTPS_KEYSTORE_PASSWORD = "automationqa";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int                    port;
    private final int                    httpsPort;
    private final String                 agentDataDir;
    private final RecordingEngine        recordingEngine;
    private final MirrorProviderRegistry providers;

    private HttpServer  server;
    private HttpsServer httpsServer;

    public DeviceStreamServer(int port, String adbPath) {
        this(port, adbPath, null);
    }

    /**
     * @param agentDataDir raíz de datos del Runner (p.ej. AGENT_DATA_DIR) — usada
     *                      únicamente para localizar un keystore HTTPS local opcional
     *                      en {agentDataDir}/certs/runner.p12 (ver {@link #startHttpsIfAvailable()}).
     *                      Puede ser null — en ese caso HTTPS simplemente no se habilita,
     *                      sin afectar en nada el servidor HTTP existente.
     */
    public DeviceStreamServer(int port, String adbPath, String agentDataDir) {
        this.port            = port;
        this.httpsPort       = port + 1;
        this.agentDataDir    = agentDataDir;
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
        registerContexts(server);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "device-stream-worker");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        System.out.println("[DeviceStream] Server started → http://localhost:" + port + PATH_PREFIX + "{udid}");
        System.out.println("[DeviceMirror] MJPEG endpoint  → http://localhost:" + port + PATH_MIRROR + "{udid}");
        System.out.println("[Recording]   Engine endpoint  → http://localhost:" + port + "/api/recording/{start|stop|action|events}");

        startHttpsIfAvailable();
    }

    /**
     * Registra EXACTAMENTE los mismos contextos/handlers en el servidor dado — se
     * usa tanto para el HttpServer plano (existente, sin cambios de comportamiento)
     * como para el HttpsServer opcional de {@link #startHttpsIfAvailable()}.
     * HttpsServer extiende HttpServer, así que createContext() funciona igual en
     * ambos sin duplicar ninguna lógica de los handlers.
     */
    private void registerContexts(HttpServer target) {
        target.createContext(PATH_PREFIX, new StreamHandler());
        target.createContext(PATH_MIRROR, new MirrorHandler());

        // Health check — frontend uses this to detect Runner reachability
        target.createContext("/health", ex -> {
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
        target.createContext("/api/device/status", ex -> {
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

        // Autoridad viva de WebDriverAgentUrl — GET /api/wda/url
        //
        // Causa raíz corregida aquí: el pipeline anterior descubría esta URL una sola
        // vez en el Runner (WdaManager) y la transportaba congelada como propiedad JVM
        // (-DwebDriverAgentUrl=...) hacia el proceso Gradle de test. Entre ese instante
        // y la creación real de IOSDriver (arranque de Gradle sin daemon, JUnit
        // discovery) el transporte CoreDevice podía cambiar y esa copia quedaba
        // obsoleta sin que nada lo detectara a tiempo (ver IOSPreSessionRevalidator).
        //
        // Este endpoint no descubre nada nuevo — expone la MISMA autoridad que ya
        // existe (WdaManager, que sigue viva durante toda la ejecución en este mismo
        // proceso Runner) para que quien la necesite la consulte en el instante exacto
        // en que la va a usar, en vez de confiar en una copia hecha minutos antes.
        target.createContext("/api/wda/url", ex -> {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendText(ex, 405, "Method Not Allowed");
                return;
            }
            if (!WdaManager.isWdaRunning()) {
                sendText(ex, 404, "WDA no activo");
                return;
            }
            String url = WdaManager.getDetectedWdaUrl();
            if (url == null || url.isBlank()) url = WdaManager.getWdaBaseUrl();
            byte[] body = url.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(body); }
        });

        // Autoridad viva de estado de bloqueo — GET /api/device/unlock-status?udid={udid}
        //
        // Mismo patrón que /api/wda/url de arriba: expone vía HTTP la MISMA autoridad
        // que ya existe en este proceso (DeviceScreenLockChecker, ya usada por
        // IosPreflightManager y JobExecutor) para que IOSPreSessionRevalidator (JVM de
        // test, proceso separado, sin acceso directo a las clases del Runner) pueda
        // consultar el estado de unlock REAL en el último instante antes de crear
        // IOSDriver — sin duplicar ninguna lógica de detección de bloqueo.
        target.createContext("/api/device/unlock-status", ex -> {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendText(ex, 405, "Method Not Allowed");
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
            DeviceScreenLockChecker.LockState state = DeviceScreenLockChecker.check(udid);
            sendText(ex, 200, Boolean.toString(state.unlocked));
        });

        // Recording engine endpoints
        target.createContext("/api/recording/start",   new RecordingStartHandler());
        target.createContext("/api/recording/stop/",   new RecordingStopHandler());
        target.createContext("/api/recording/action/", new RecordingActionHandler());
        target.createContext("/api/recording/events/", new RecordingEventsHandler());
    }

    /**
     * Habilita HTTPS en un puerto adicional (port + 1) SOLO si existe un keystore
     * local en {agentDataDir}/certs/runner.p12 — causa raíz que resuelve: el
     * Dashboard servido por HTTPS (Railway) no puede conectarse al Runner en
     * http://localhost:{port} porque los navegadores modernos auto-suben (o
     * bloquean, sin fallback) cualquier recurso http:// solicitado desde una
     * página https:// ("mixed content"). El Mirror pedía ese recurso y el
     * navegador lo bloqueaba en silencio — nunca llegaba una sola conexión real
     * de navegador al Runner (confirmado: el log solo mostraba conexiones desde
     * ::1, es decir, pruebas locales, nunca el Dashboard).
     *
     * El servidor HTTP existente en {@code port} NO se toca — este listener es
     * puramente aditivo. Si el keystore no existe (instalación sin certificado
     * local generado), HTTPS simplemente no se habilita y todo sigue funcionando
     * exactamente igual que antes para cualquier acceso por HTTP.
     */
    private void startHttpsIfAvailable() {
        if (agentDataDir == null || agentDataDir.isBlank()) return;
        File keystoreFile = new File(agentDataDir, "certs" + File.separator + "runner.p12");
        if (!keystoreFile.isFile()) {
            System.out.println("[DeviceStream] HTTPS deshabilitado — no se encontró keystore local en "
                    + keystoreFile.getAbsolutePath());
            return;
        }
        try {
            char[] password = HTTPS_KEYSTORE_PASSWORD.toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(keystoreFile)) {
                keyStore.load(in, password);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            httpsServer = HttpsServer.create(new InetSocketAddress(httpsPort), 32);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            registerContexts(httpsServer);
            httpsServer.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "device-stream-https-worker");
                t.setDaemon(true);
                return t;
            }));
            httpsServer.start();
            System.out.println("[DeviceStream] HTTPS habilitado → https://localhost:" + httpsPort
                    + PATH_MIRROR + "{udid} (keystore: " + keystoreFile.getAbsolutePath() + ")");
        } catch (Exception e) {
            System.err.println("[DeviceStream] No se pudo iniciar HTTPS (se continúa solo con HTTP): "
                    + e.getMessage());
            httpsServer = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[DeviceStream] Server stopped.");
        }
        if (httpsServer != null) {
            httpsServer.stop(0);
            System.out.println("[DeviceStream] HTTPS server stopped.");
        }
    }

    public int getPort() { return port; }

    /** @return el puerto HTTPS opcional (port + 1), habilitado solo si hay un keystore local. */
    public int getHttpsPort() { return httpsPort; }

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

                String path = ex.getRequestURI().getPath();
                if (!path.startsWith(PATH_MIRROR)) {
                    sendText(ex, 404, "Not Found");
                    return;
                }
                String rest = path.substring(PATH_MIRROR.length());

                // POST /api/device-mirror/{udid}/retry — única forma de sacar a un UDID
                // del estado terminal ERROR de WdaLifecycleOwner. Debe ser una acción
                // explícita del usuario (botón "Reintentar") — nunca disparada por el
                // watchdog de reconexión automática del frontend, que solo hace GET.
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    handleRetry(ex, rest);
                    return;
                }
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    sendText(ex, 405, "Method Not Allowed");
                    return;
                }

                String udid = rest.trim();
                if (udid.isEmpty() || udid.contains("/") || udid.contains("..") ||
                        !udid.matches("[a-zA-Z0-9\\-_.]+")) {
                    sendText(ex, 400, "Invalid device identifier");
                    return;
                }

                // TEMP LOG (auditoría Mirror — remover tras validar Problema 1)
                System.out.println("[MirrorStream][TEMP] Mirror connection accepted — udid=" + udid
                        + " | client: " + ex.getRemoteAddress());

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

                // TEMP LOG (auditoría Mirror — remover tras validar Problema 1)
                System.out.println("[MirrorStream][TEMP] MJPEG endpoint initialized — udid=" + udid);

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
                    // TEMP (auditoría Mirror — remover tras validar Problema 1): solo
                    // instrumenta los primeros frames de cada conexión, para localizar
                    // en cuál etapa (captura/codificación/envío) se rompe el flujo, sin
                    // inundar el log durante el resto de la sesión de streaming.
                    int tempFrameCount = 0;

                    while (!Thread.currentThread().isInterrupted()) {
                        long t0 = System.currentTimeMillis();

                        if (tempFrameCount < 3) {
                            System.out.println("[MirrorStream][TEMP] CaptureFrame() invoked — udid=" + udid);
                        }
                        byte[] png = provider != null ? provider.captureFrame(udid) : null;
                        if (png == null) {
                            // Fase 6 — optimización de latencia: antes se llamaba a
                            // isDeviceConnected() (spawns un proceso/HTTP roundtrip
                            // completo) ANTES de cada captura, sin importar si el
                            // dispositivo estaba sano — es decir, ~20 veces/segundo.
                            // La propia falla de captura ya es la señal de que algo anda
                            // mal; solo entonces vale la pena pagar el costo de verificar
                            // conectividad real.
                            boolean deviceGone = (provider == null || !provider.isDeviceConnected(udid));

                            // missCount cuenta TODA falla de captureFrame(), no solo la
                            // rama "dispositivo desconectado": antes se reseteaba a 0 en
                            // cada iteración mientras el dispositivo siguiera presente, así
                            // que si WDA estaba caído pero el iPhone seguía conectado (el
                            // caso normal), este loop reintentaba contra :8100 a ritmo casi
                            // real-time (~12.5/seg) INDEFINIDAMENTE — causa confirmada de
                            // las decenas de "RemoteXPC connect/socket error" en los logs
                            // al terminar una ejecución (WDA ya derribado, el Mirror del
                            // Dashboard seguía golpeando el puerto).
                            if (++missCount > 12) {
                                if (deviceGone) break; // dispositivo realmente desconectado (~6s)

                                // Condición objetiva de "WDA no se recuperará dentro de esta
                                // sesión de stream": se consulta a WdaLifecycleOwner —única
                                // autoridad del ciclo de vida— si hay un intento de construir/
                                // verificar WDA en curso para este UDID AHORA MISMO, sin
                                // importar quién lo haya solicitado (una ejecución real o una
                                // solicitud del propio Mirror vía requestForMirror(), que corre
                                // en su propio hilo de fondo, ver IOSMirrorProvider.start()).
                                // WdaLaunchCoordinator.isExecutionActive() se conserva además
                                // porque cubre la ventana entre "ejecución real terminó de
                                // construir" y "IOSExecutionCleanupManager todavía no llamó
                                // release()" — un instante en el que INFLIGHT ya está vacío pero
                                // la ejecución real sigue usando la sesión. provider.start(udid)
                                // solo se invoca UNA VEZ, al abrir esta conexión (arriba, antes
                                // del while) — nunca de nuevo dentro de este loop. Si ninguna de
                                // las dos condiciones se cumple, ningún mecanismo de este sistema
                                // va a revivir WDA para ESTE stream — seguir reintentando sería
                                // indefinido por definición. Se termina el stream limpiamente (el
                                // finally de abajo ya libera jpegWriter/provider/MirrorService);
                                // una nueva petición del cliente abre un Mirror nuevo que vuelve a
                                // llamar provider.start(udid) y reevalúa desde cero.
                                if (WdaLaunchCoordinator.currentOwner() == null
                                        && !WdaLifecycleOwner.isBuildInFlight(udid)) break;

                                // Alguien tiene el control (ejecución real usando WDA, o una
                                // construcción en curso de cualquier consumidor, incluido el
                                // propio Mirror) — WDA puede seguir llegando; se mantiene la
                                // misma espera ya validada, sin agregar ningún mecanismo nuevo.
                                Thread.sleep(2_000);
                                continue;
                            }
                            Thread.sleep(deviceGone ? 500 : 80);
                            continue;
                        }
                        missCount = 0;
                        tempFrameCount++;
                        if (tempFrameCount <= 3) {
                            System.out.println("[MirrorStream][TEMP] Frame #" + tempFrameCount
                                    + " captured — udid=" + udid + " (" + png.length + " bytes PNG)");
                        }

                        byte[] jpeg = pngToJpeg(png, 0.78f, jpegWriter);
                        if (jpeg == null) continue;
                        if (tempFrameCount <= 3) {
                            System.out.println("[MirrorStream][TEMP] Frame #" + tempFrameCount
                                    + " encoded — udid=" + udid + " (" + jpeg.length + " bytes JPEG)");
                        }

                        byte[] header = ("--" + BOUNDARY + "\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: " + jpeg.length + "\r\n\r\n"
                        ).getBytes(StandardCharsets.UTF_8);

                        out.write(header);
                        out.write(jpeg);
                        out.write(crLf);
                        out.flush();
                        if (tempFrameCount <= 3) {
                            System.out.println("[MirrorStream][TEMP] Frame #" + tempFrameCount
                                    + " sent — udid=" + udid);
                        }

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

        /** Espera "{udid}/retry" — separado del parseo GET porque el sufijo "/retry" contiene "/". */
        private void handleRetry(HttpExchange ex, String rest) throws IOException {
            int slash = rest.indexOf('/');
            String udid   = (slash >= 0 ? rest.substring(0, slash) : rest).trim();
            String suffix = slash >= 0 ? rest.substring(slash + 1) : "";

            if (!"retry".equals(suffix) || udid.isEmpty() || udid.contains("..")
                    || !udid.matches("[a-zA-Z0-9\\-_.]+")) {
                sendText(ex, 400, "Invalid retry request");
                return;
            }

            WdaLifecycleOwner.resetForRetry(udid);
            System.out.println("[DeviceMirror] Retry solicitado explícitamente por el usuario: " + udid);

            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(body); }
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
