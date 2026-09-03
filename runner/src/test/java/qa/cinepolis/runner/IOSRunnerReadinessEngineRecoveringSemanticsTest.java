package qa.cinepolis.runner;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 6 — tests de la semántica corregida de RECOVERING vs NOT_READY en
 * {@link IOSRunnerReadinessEngine}.
 *
 * LIMITACIÓN HONESTA: los 6 casos exigidos requieren que el dispositivo pase
 * primero CONNECTED/PAIRED/DEVELOPER_MODE (todos) y, salvo TEST 6 (Trust,
 * que corta antes), también TRUSTED/SIGNED/PROVISIONED — es decir, requieren
 * el iPhone físico real conectado (UDID 00008110-000129261482601E, el mismo
 * usado en toda esta sesión). No hay forma de simular esas etapas sin tocar
 * WdaLifecycleOwner/AppleDeveloperTeamManager/AppleSigningUtils, lo cual esta
 * tarea prohíbe explícitamente. Si el dispositivo no está conectado, estos
 * tests fallarán con un mensaje claro en vez de dar un falso positivo.
 *
 * TEST 2 (build realmente en curso) no dispara un xcodebuild real de varios
 * minutos — en su lugar inserta directamente una entrada en el mapa interno
 * REAL {@code WdaLifecycleOwner.INFLIGHT} vía reflexión, exactamente la misma
 * estructura que {@code acquire()} ya puebla en producción. Esto NO inventa
 * una condición nueva: ejercita el mecanismo real (`isBuildInFlight()` sigue
 * siendo `INFLIGHT.containsKey(udid)`, sin ningún cambio), solo evita esperar
 * un build real de varios minutos en cada corrida de tests.
 *
 * TEST 3 (WDA disponible) no requiere una compilación real de WDA — levanta un
 * servidor HTTP local mínimo en :8100 que responde 200 a /status, exactamente
 * lo único que {@code WdaManager.probeStatus()} verifica (código 2xx, sin
 * inspeccionar el cuerpo) — ver su código fuente, sin modificarlo.
 */
@DisplayName("IOSRunnerReadinessEngine — semántica RECOVERING vs NOT_READY (TAREA 6)")
class IOSRunnerReadinessEngineRecoveringSemanticsTest {

    private static final String UDID = "00008110-000129261482601E";

    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    @AfterEach
    void cleanup() {
        WdaLifecycleOwner.resetForRetry(UDID);
    }

    // ── TEST 1 — WDA no iniciado, sin operación activa ────────────────────────

    @Test
    @DisplayName("TEST 1: WDA no iniciado + sin operación activa -> NOT_READY, no RECOVERING")
    void wdaNotStartedWithNoActiveOperation_isNotReady() {
        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID),
                "Precondición: no debe haber ningún build real en curso para este UDID ahora mismo");
        assertFalse(WdaManager.isWdaRunning(),
                "Precondición: WDA no debe estar respondiendo ahora mismo para que este test sea válido");

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea6-test1", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.NOT_READY, result.status,
                "Antes de TAREA 6 esto habría sido RECOVERING sin ninguna operación real en curso");
        assertNotEquals(IOSRunnerReadinessResult.Status.RECOVERING, result.status);
        assertEquals("IOS_WDA_NOT_STARTED", result.errorCode);
        assertFalse(result.readyForExecution);
    }

    // ── TEST 2 — build realmente en curso (mapa INFLIGHT real, sin xcodebuild real) ──

    @Test
    @DisplayName("TEST 2: operación real en curso (INFLIGHT poblado) -> RECOVERING + IOS_WDA_BUILDING")
    void realBuildInFlight_isRecovering() throws Exception {
        Field inflightField = WdaLifecycleOwner.class.getDeclaredField("INFLIGHT");
        inflightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CompletableFuture<WdaLifecycleOwner.Result>> inflight =
                (Map<String, CompletableFuture<WdaLifecycleOwner.Result>>) inflightField.get(null);

        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight.put(UDID, pending);
        try {
            assertTrue(WdaLifecycleOwner.isBuildInFlight(UDID),
                    "La inserción directa en el mapa real debe reflejarse en isBuildInFlight()");

            IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea6-test2", UDID);

            assertEquals(IOSRunnerReadinessResult.Status.RECOVERING, result.status);
            assertEquals("IOS_WDA_BUILDING", result.errorCode);
            assertFalse(result.readyForExecution);
        } finally {
            inflight.remove(UDID);
            pending.complete(new WdaLifecycleOwner.Result(false, "test cleanup"));
        }
    }

    // ── TEST 3 — WDA disponible (servidor HTTP local fingiendo /status) ───────

    @Test
    @DisplayName("TEST 3: WDA disponible -> READY, wdaErrorCode=NONE, readyForExecution=true")
    void wdaAvailable_isReady() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8100), 0);
        server.createContext("/status", exchange -> {
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertTrue(WdaManager.isWdaRunning(), "El servidor fake debe hacer que isWdaRunning() sea true");

            IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea6-test3", UDID);

            assertEquals(IOSRunnerReadinessResult.Status.READY, result.status);
            assertEquals(IOSWdaErrorCode.NONE, result.wdaErrorCode);
            assertTrue(result.readyForExecution);
        } finally {
            server.stop(0);
        }
    }

    // ── TEST 4 — build terminado con error genérico ───────────────────────────

    @Test
    @DisplayName("TEST 4: build terminado con error genérico -> ERROR + IOS_WDA_BUILD_FAILED")
    void buildFailedGeneric_isError() {
        WdaLifecycleOwner.markTerminalError(UDID, "Testing cancelled because the build failed.");

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea6-test4", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.ERROR, result.status);
        assertEquals(IOSWdaErrorCode.IOS_WDA_BUILD_FAILED, result.wdaErrorCode);
        assertFalse(result.readyForExecution);
        assertNotEquals(IOSRunnerReadinessResult.Status.RECOVERING, result.status,
                "Un build ya terminado con error nunca debe reportarse como una recuperación en curso");
    }

    // ── TEST 5 — startup terminado con timeout de "Enable UI Automation" ──────

    @Test
    @DisplayName("TEST 5 (TAREA 10): startup con 'Enable UI Automation' -> ACTION_REQUIRED + "
            + "IOS_WDA_STARTUP_FAILED, no ERROR")
    void startupFailed_isActionRequired() {
        String terminalReason =
            "WebDriverAgentRunner-Runner (2490) encountered an error (The test runner failed to "
            + "initialize for UI testing. (Underlying Error: Timed out while enabling automation mode.))";
        WdaLifecycleOwner.markTerminalError(UDID, terminalReason);

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea6-test5", UDID);

        // TAREA 10: este caso exige acción física del usuario (confirmar el prompt
        // nativo en el iPhone) — ya NO se reporta como ERROR genérico reintentable.
        assertEquals(IOSRunnerReadinessResult.Status.ACTION_REQUIRED, result.status);
        assertEquals(IOSWdaErrorCode.IOS_WDA_STARTUP_FAILED, result.wdaErrorCode);
        assertEquals("IOS_WDA_BUILD_FAILED", result.errorCode, "errorCode String no cambia (TAREA 10)");
        assertEquals(terminalReason, result.reason, "reason conserva el texto original intacto");
        assertFalse(result.readyForExecution);
        assertNotEquals(IOSRunnerReadinessResult.Status.RECOVERING, result.status);
        assertNotEquals(IOSRunnerReadinessResult.Status.ERROR, result.status);
    }

    // ── TEST 5b (TAREA 10) — Signing: no debe verse afectado por el cambio ────

    @Test
    @DisplayName("TEST 5b (TAREA 10, no regresión): Signing (IOS_SIGNING_REQUIRED) -> sigue en ERROR")
    void signingRequired_remainsError() {
        WdaLifecycleOwner.markTerminalError(UDID, "No Accounts: Add a new account in Accounts settings.");

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea10-test5b", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.ERROR, result.status);
        assertEquals(IOSWdaErrorCode.IOS_SIGNING_REQUIRED, result.wdaErrorCode);
    }

    // ── TEST 5c (TAREA 10) — Provisioning: no debe verse afectado por el cambio ─

    @Test
    @DisplayName("TEST 5c (TAREA 10, no regresión): Provisioning (IOS_PROVISIONING_REQUIRED) -> sigue en ERROR")
    void provisioningRequired_remainsError() {
        WdaLifecycleOwner.markTerminalError(UDID,
            "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
            + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.");

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea10-test5c", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.ERROR, result.status);
        assertEquals(IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED, result.wdaErrorCode);
    }

    // ── TEST 5d (TAREA 10) — UNKNOWN: no debe verse afectado por el cambio ────

    @Test
    @DisplayName("TEST 5d (TAREA 10, no regresión): texto no clasificable (UNKNOWN) -> sigue en ERROR")
    void unknownError_remainsError() {
        // Texto real capturado en TAREA 8.3 — diagnóstico operativo genérico de
        // devicectl, sin ninguna causa clasificable conocida.
        WdaLifecycleOwner.markTerminalError(UDID,
            "ERROR: The operation couldn't be completed. (CoreDeviceCLISupport.DiagnoseError error 0.)");

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea10-test5d", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.ERROR, result.status);
        assertEquals(IOSWdaErrorCode.UNKNOWN, result.wdaErrorCode);
    }

    // ── TEST 6 — Trust requerido: confirma que ACTION_REQUIRED no cambió ──────

    @Test
    @DisplayName("TEST 6: Trust requerido -> ACTION_REQUIRED + IOS_DEVELOPER_TRUST_REQUIRED (sin cambios)")
    void trustRequired_remainsActionRequired() {
        WdaLifecycleOwner.markTerminalError(UDID,
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.");

        IOSRunnerReadinessResult result = IOSRunnerReadinessEngine.evaluate(client, "tarea6-test6", UDID);

        assertEquals(IOSRunnerReadinessResult.Status.ACTION_REQUIRED, result.status);
        assertEquals(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED, result.wdaErrorCode);
        assertFalse(result.readyForExecution);
    }
}
