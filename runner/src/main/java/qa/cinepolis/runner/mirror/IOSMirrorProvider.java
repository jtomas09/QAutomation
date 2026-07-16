package qa.cinepolis.runner.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.runner.IOSDeviceRegistry;
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
 * ── El Mirror NUNCA inicia WDA ───────────────────────────────────────────
 * Este provider ÚNICAMENTE consume un WDA que ya exista (lanzado por una
 * ejecución de test real, vía IosPreflightManager → WdaLifecycleOwner). Si
 * WDA no está corriendo, start() no hace nada más que reflejar el estado
 * neutral — nunca compila, nunca instala, nunca lanza xcodebuild. Esto
 * elimina por completo la condición de carrera Mirror-vs-ejecución real (antes
 * ambos podían terminar compilando WDA para el mismo dispositivo) y garantiza
 * que WdaLifecycleOwner sea la única autoridad que decide cuándo se construye.
 *
 * stop() tampoco detiene WDA nunca — pertenece al ciclo de vida de la
 * ejecución de test (WdaLifecycleOwner.teardown()), no al del Mirror.
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
        if (WdaManager.isWdaRunning()) {
            // Ya arriba — captureFrame() promoverá a MIRROR_ACTIVE en el primer frame real.
            return true;
        }

        if (WdaLifecycleOwner.isTerminalError(udid)) {
            // El último intento de una EJECUCIÓN REAL falló de forma terminal (ver
            // WdaLifecycleOwner) — el Mirror solo refleja ese estado, nunca reintenta
            // por su cuenta. Solo WdaLifecycleOwner.resetForRetry(), disparado por una
            // acción explícita del usuario (botón "Reintentar"), reabre este camino.
            return false;
        }

        // WDA no existe todavía y no hay un error terminal que mostrar: el Mirror
        // publica el estado neutral (nada corriendo) y espera — nunca construye ni
        // instala nada por su cuenta. Solo una ejecución real (JobExecutor →
        // IosPreflightManager → WdaLifecycleOwner) puede levantar WDA.
        WdaEventBus.publish(udid, WdaEventBus.WdaEvent.STOPPED);
        return false;
    }

    @Override
    public void stop(String udid) {
        // Intencionalmente NO detiene WDA — pertenece al ciclo de vida de la
        // ejecución de test (WdaLifecycleOwner.teardown()), no al del mirror.
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
