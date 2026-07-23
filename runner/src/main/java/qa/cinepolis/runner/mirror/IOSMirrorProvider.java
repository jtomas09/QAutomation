package qa.cinepolis.runner.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.IOSDeviceRegistry;
import qa.cinepolis.runner.RunnerAgent;
import qa.cinepolis.runner.WdaLifecycleOwner;
import qa.cinepolis.runner.WdaManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Captura de pantalla para dispositivos iOS físicos, reutilizando el
 * WebDriverAgent (WDA) que WdaLifecycleOwner ya administra como puente de
 * automatización de Appium.
 *
 * Formato de salida: PNG (igual que AndroidMirrorProvider), vía el endpoint
 * WDA GET /screenshot, que WDA expone sin necesitar una sesión Appium activa.
 *
 * ── El Mirror NUNCA construye WDA por su cuenta ──────────────────────────
 * Este provider jamás invoca xcodebuild, Preflight ni ninguna lógica de
 * construcción directamente — eso sigue 100% centralizado en
 * {@link WdaLifecycleOwner} (única autoridad del ciclo de vida). Lo que SÍ
 * hace es solicitar FORMALMENTE una instancia activa, vía
 * {@link WdaLifecycleOwner#requestForMirror}: si WDA ya existe, lo reutiliza;
 * si no existe, WdaLifecycleOwner la construye por el mismo camino que usa
 * una ejecución de test real (IosPreflightManager → acquire()), con la misma
 * garantía de "una sola compilación a la vez" (Future compartido por UDID)
 * que ya tenía antes de este cambio — el Mirror simplemente se une a ella
 * como un consumidor más, nunca como una segunda autoridad.
 *
 * stop() libera la referencia del Mirror (WdaLifecycleOwner.release()) — NO
 * destruye WDA directamente; si una ejecución real todavía lo necesita, la
 * instancia se mantiene viva. Solo cuando ningún consumidor queda activo
 * WdaLifecycleOwner ejecuta el teardown real.
 */
public final class IOSMirrorProvider implements DeviceMirrorProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

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
        // Presencia física real (misma fuente que readyForExecution) — NUNCA
        // WdaManager.isWdaRunning(). Conectado y "WDA arriba" son conceptos
        // distintos: un fallo de WDA no debe reportarse como dispositivo
        // desconectado (ver IOSMirrorStateTracker).
        return IOSDeviceRegistry.isPresent(udid);
    }

    @Override
    public boolean start(String udid) {
        // Registra al Mirror como consumidor activo y, solo si hace falta, solicita
        // formalmente al único propietario del ciclo de vida que consiga WDA — esta
        // llamada nunca compila, instala ni lanza xcodebuild aquí mismo (ver
        // WdaLifecycleOwner.requestForMirror(), que dispara el mismo camino que
        // JobExecutor en un hilo de fondo, sin bloquear esta llamada).
        WdaLifecycleOwner.requestForMirror(RunnerAgent.getClient(), udid);

        if (WdaManager.isWdaRunning()) {
            // Ya arriba (por esta solicitud o por una ejecución real) —
            // captureFrame() promoverá a MIRROR_ACTIVE en el primer frame real.
            return true;
        }

        if (WdaLifecycleOwner.isTerminalError(udid)) {
            // El último intento (de cualquier consumidor) falló de forma terminal —
            // el Mirror solo refleja ese estado, nunca reintenta por su cuenta. Solo
            // WdaLifecycleOwner.resetForRetry(), disparado por una acción explícita
            // del usuario (botón "Reintentar"), reabre este camino.
            return false;
        }

        // requestForMirror() ya disparó (o se unió a) la construcción en segundo
        // plano — el stream se abre igual; los frames empezarán a llegar cuando
        // WDA esté listo (ver DeviceStreamServer, que tolera la espera mientras
        // WdaLifecycleOwner.isBuildInFlight(udid) sea true).
        // TEMP LOG (auditoría Mirror/WDA — remover tras validar Problema 2)
        System.out.println("[IOSMirrorProvider][TEMP] Mirror waiting — udid=" + udid);
        return true;
    }

    @Override
    public void stop(String udid) {
        // Libera la referencia del Mirror. Si ninguna ejecución real sigue usando
        // esta misma instancia, WdaLifecycleOwner la destruye aquí; si sigue en
        // uso, la mantiene viva. El Mirror nunca decide esto por su cuenta — ver
        // WdaLifecycleOwner.release().
        WdaLifecycleOwner.release(
                WdaLifecycleOwner.Consumer.MIRROR, RunnerAgent.getClient(), "mirror-" + udid, udid);
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
            // Agnóstico al origen: no importa si WDA lo levantó el propio Mirror
            // o una ejecución real vía Appium — este probe HTTP directo es el mismo.
            WdaEventBus.publish(udid, WdaEventBus.WdaEvent.ACTIVE);
            return frame;
        } catch (Exception e) {
            System.err.println("[IOSMirrorProvider] WDA screenshot error [" + udid + "]: " + e.getMessage());
            return null;
        }
    }
}
