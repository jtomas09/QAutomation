package qa.cinepolis.runner.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.AppiumXcodebuildLogForwarder;
import qa.cinepolis.runner.BackendClient;
import qa.cinepolis.runner.IosPreflightManager;
import qa.cinepolis.runner.RunnerAgent;
import qa.cinepolis.runner.WdaManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captura de pantalla para dispositivos iOS físicos, reutilizando el
 * WebDriverAgent (WDA) que WdaManager ya administra como puente de
 * automatización de Appium.
 *
 * Formato de salida: PNG (igual que AndroidMirrorProvider), vía el endpoint
 * WDA GET /screenshot, que WDA expone sin necesitar una sesión Appium activa.
 *
 * ── Modo on-demand ───────────────────────────────────────────────────────
 * Si WDA no está corriendo, start() dispara IosPreflightManager.runPreflight()
 * en un hilo daemon — EXACTAMENTE el mismo camino que usa una ejecución de
 * test real (detección de Team ID, túnel CoreDevice, caché de WDA, y
 * finalmente WdaManager.ensureWdaRunning) — para no duplicar esa lógica.
 * start() devuelve de inmediato (no bloquea la apertura del stream); las
 * capturas fallarán hasta que WDA quede listo, lo cual el propio bucle de
 * reintento del frontend (useMirrorStream) ya tolera reabriendo el stream
 * cada pocos segundos.
 *
 * Salvaguardas para no interferir con una ejecución de test real:
 *  1. Si WdaManager.isTestExecutionActive() es true, este provider NO lanza
 *     nada — una ejecución real ya es dueña del ciclo de vida de WDA en este
 *     momento (ventana marcada por IosPreflightManager/IOSExecutionCleanupManager).
 *  2. ensureWdaRunning() es `synchronized` en WdaManager, así que aunque el
 *     mirror y una ejecución real inicien su preflight casi al mismo tiempo,
 *     solo uno de los dos termina lanzando xcodebuild; el otro ve WDA ya
 *     arriba (fast path) y no hace nada.
 *  3. launchInProgress evita lanzar múltiples preflights on-demand en paralelo
 *     desde el propio mirror (p.ej. varias pestañas del Dashboard abiertas).
 *  4. stop() sigue sin detener WDA nunca — pertenece al ciclo de vida de la
 *     ejecución de test, no al del mirror.
 */
public final class IOSMirrorProvider implements DeviceMirrorProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /** Evita disparar varios preflights on-demand en paralelo desde el propio mirror. */
    private static final AtomicBoolean launchInProgress = new AtomicBoolean(false);

    @Override
    public String name() { return "WDA"; }

    @Override
    public boolean isSupported() {
        // WDA/xcrun/xcodebuild solo existen en macOS — en cualquier otro host
        // este provider se autodescarta y MirrorProviderRegistry no lo usará.
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public boolean isDeviceConnected(String udid) {
        // WdaManager administra un único proceso WDA global (ver su javadoc) —
        // es la misma señal de "listo" que usa el resto del Runner antes de
        // crear una sesión Appium para iOS.
        return WdaManager.isWdaRunning();
    }

    @Override
    public boolean start(String udid) {
        if (WdaManager.isWdaRunning()) {
            // Ya arriba — captureFrame() promoverá a MIRROR_ACTIVE en el primer frame real.
            return true;
        }

        if (WdaManager.isTestExecutionActive()) {
            // Una ejecución de test real ya está usando/levantando WDA — el mirror
            // no debe competir. Las capturas fallarán hasta que ese WDA quede
            // arriba; el reintento del frontend recogerá los frames en cuanto exista.
            IOSMirrorStateTracker.markInitializing(udid);
            System.out.println("[IOSMirrorProvider] WDA no activo y hay una ejecución de test en curso — "
                    + "el mirror esperará en vez de lanzar WDA por su cuenta. UDID: " + udid);
            return false;
        }

        IOSMirrorStateTracker.markInitializing(udid);
        triggerOnDemandLaunch(udid);
        // El stream se abre igual — los frames empezarán a llegar cuando WDA esté listo.
        return true;
    }

    private void triggerOnDemandLaunch(String udid) {
        if (!launchInProgress.compareAndSet(false, true)) {
            return; // ya hay un intento on-demand en curso para este Runner
        }
        long attemptStartedAtMs = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            try {
                BackendClient client = RunnerAgent.getClient();
                if (client == null) {
                    String reason = "BackendClient no disponible todavía — no se pudo lanzar WDA.";
                    System.err.println("[IOSMirrorProvider] " + reason + " UDID: " + udid);
                    IOSMirrorStateTracker.markError(udid, reason);
                    return;
                }
                String mirrorExecutionId = "mirror-" + udid;
                System.out.println("[IOSMirrorProvider] WDA no activo — lanzando bajo demanda "
                        + "(puede tardar varios minutos la primera vez) para " + udid + "...");
                IosPreflightManager.IosPreflightResult result =
                        IosPreflightManager.runPreflight(client, mirrorExecutionId, udid);

                boolean wdaUp = result.wdaReady || WdaManager.isWdaRunning();
                System.out.println("[IOSMirrorProvider] Preflight on-demand terminado para " + udid
                        + " — WDA activo: " + wdaUp);

                if (!wdaUp) {
                    String realError = AppiumXcodebuildLogForwarder.findRealXcodeError(attemptStartedAtMs);
                    String reason = realError != null
                            ? realError
                            : "WebDriverAgent no pudo iniciarse (motivo no capturado en appium.log).";
                    IOSMirrorStateTracker.markError(udid, reason);
                    System.err.println("[IOSMirrorProvider] WDA on-demand falló para " + udid + ": " + reason);
                }
                // Si wdaUp==true, se deja el estado en INITIALIZING_WDA — captureFrame()
                // promueve a MIRROR_ACTIVE en cuanto de verdad llegue un frame, evitando
                // reportar "activo" antes de que realmente haya imagen.
            } catch (Exception e) {
                String reason = "Error inesperado lanzando WDA: " + e.getMessage();
                System.err.println("[IOSMirrorProvider] " + reason + " UDID: " + udid);
                IOSMirrorStateTracker.markError(udid, reason);
            } finally {
                launchInProgress.set(false);
            }
        }, "ios-mirror-wda-launch");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop(String udid) {
        // Intencionalmente NO detiene WDA — pertenece al ciclo de vida de la
        // ejecución de test (WdaManager.cleanup), no al del mirror.
    }

    @Override
    public byte[] captureFrame(String udid) {
        try {
            String url = WdaManager.getWdaBaseUrl() + "/screenshot";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;

            JsonNode root   = MAPPER.readTree(resp.body());
            String   base64 = root.path("value").asText(null);
            if (base64 == null || base64.isBlank()) return null;

            byte[] frame = Base64.getDecoder().decode(base64);
            // Prueba real de que el Mirror funciona — un frame de verdad llegó.
            IOSMirrorStateTracker.markActive(udid);
            return frame;
        } catch (Exception e) {
            System.err.println("[IOSMirrorProvider] WDA screenshot error [" + udid + "]: " + e.getMessage());
            return null;
        }
    }
}
