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
 * TAREA 8 — tests del vertical slice de {@link IOSRecoveryManager}.
 *
 * TEST 1 y TEST 7 del enunciado (recovery realmente ejecutándose de punta a
 * punta hasta terminar en READY) requieren que {@code IosPreflightManager.runPreflight()}
 * complete una operación REAL contra el iPhone físico — no se dejan como test
 * committed permanente (correrían un xcodebuild real en cada ejecución de la
 * suite); se validaron en vivo una vez, ver el reporte de la tarea. Aquí se
 * cubren con la tabla de mapeo pura (mapAfterRecovery) más los casos que sí son
 * seguros y rápidos de reproducir contra el dispositivo real sin disparar un
 * build real: TEST 2-6 usan la misma técnica ya establecida en TAREA 6
 * (servidor HTTP fake en :8100, inyección directa del mapa INFLIGHT vía
 * reflexión, y markTerminalError) para llegar a un diagnóstico FRESCO distinto
 * de NOT_READY+IOS_WDA_NOT_STARTED — con lo cual {@code recover()} nunca llega
 * a invocar runPreflight, y puede verificarse de forma rápida y determinista.
 *
 * TEST 10 (excepción durante la recuperación): {@code IosPreflightManager.runPreflight()}
 * está escrito defensivamente (captura casi todo internamente) — no existe hoy
 * una forma confiable de forzar una excepción real sin modificar código de
 * producción, lo cual esta tarea prohíbe. Se documenta esta limitación
 * explícitamente (ver el reporte) en vez de fabricar un mock o inventar un
 * comportamiento — el manejo de excepción se verificó por inspección directa
 * del código (bloque try/catch/finally de {@code recover()}, que preserva la
 * excepción en {@code failureCause} sin ocultarla).
 */
@DisplayName("IOSRecoveryManager (TAREA 8)")
class IOSRecoveryManagerTest {

    private static final String UDID = "00008110-000129261482601E";

    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    @AfterEach
    void cleanup() {
        WdaLifecycleOwner.resetForRetry(UDID);
    }

    // ── TEST 2 — fresh diagnosis ya es READY -> sin acción ────────────────────

    @Test
    @DisplayName("TEST 2: diagnóstico fresco ya es READY -> NO_ACTION_REQUIRED, sin segunda operación")
    void freshDiagnosisAlreadyReady_noAction() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8100), 0);
        server.createContext("/status", exchange -> {
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            IOSRunnerReadinessResult stale = IOSRunnerReadinessResult.builder()
                    .udid(UDID).status(IOSRunnerReadinessResult.Status.NOT_READY)
                    .errorCode("IOS_WDA_NOT_STARTED").build();

            IOSRecoveryManager.RecoveryResult result =
                    IOSRecoveryManager.recover(client, "tarea8-test2", UDID, stale);

            assertEquals(IOSRecoveryOutcome.NO_ACTION_REQUIRED, result.outcome);
            assertEquals(IOSRunnerReadinessResult.Status.READY, result.diagnosisBeforeRecovery.status);
            assertNull(result.diagnosisAfterRecovery, "No debe haberse intentado ninguna operación real");
        } finally {
            server.stop(0);
        }
    }

    // ── TEST 3 — ya existe una operación real en curso -> RECOVERY_IN_PROGRESS ─

    @Test
    @DisplayName("TEST 3: build ya en curso -> RECOVERY_IN_PROGRESS, sin segundo build")
    void existingOperationInFlight_noSecondRecovery() throws Exception {
        Field inflightField = WdaLifecycleOwner.class.getDeclaredField("INFLIGHT");
        inflightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CompletableFuture<WdaLifecycleOwner.Result>> inflight =
                (Map<String, CompletableFuture<WdaLifecycleOwner.Result>>) inflightField.get(null);

        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight.put(UDID, pending);
        try {
            IOSRecoveryManager.RecoveryResult result =
                    IOSRecoveryManager.recover(client, "tarea8-test3", UDID, null);

            assertEquals(IOSRecoveryOutcome.RECOVERY_IN_PROGRESS, result.outcome);
            assertEquals(IOSRunnerReadinessResult.Status.RECOVERING, result.diagnosisBeforeRecovery.status);
            assertNull(result.diagnosisAfterRecovery, "No debe haberse iniciado un segundo build");
        } finally {
            inflight.remove(UDID);
            pending.complete(new WdaLifecycleOwner.Result(false, "test cleanup"));
        }
    }

    // ── TEST 4 — Trust requerido -> ACTION_REQUIRED, nunca recovery ───────────

    @Test
    @DisplayName("TEST 4: Trust requerido -> ACTION_REQUIRED, sin recovery, nunca bypass")
    void trustRequired_neverRecovers() {
        WdaLifecycleOwner.markTerminalError(UDID,
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.");

        IOSRecoveryManager.RecoveryResult result =
                IOSRecoveryManager.recover(client, "tarea8-test4", UDID, null);

        assertEquals(IOSRecoveryOutcome.ACTION_REQUIRED, result.outcome);
        assertEquals(IOSRunnerReadinessResult.Status.ACTION_REQUIRED, result.diagnosisBeforeRecovery.status);
        assertNull(result.diagnosisAfterRecovery, "Trust nunca debe disparar una operación de recovery");
    }

    // ── TEST 5 — build fallido -> sin recovery automático ─────────────────────

    @Test
    @DisplayName("TEST 5: build fallido (IOS_WDA_BUILD_FAILED) -> sin recovery automático")
    void buildFailed_noAutomaticRecovery() {
        WdaLifecycleOwner.markTerminalError(UDID, "Testing cancelled because the build failed.");

        IOSRecoveryManager.RecoveryResult result =
                IOSRecoveryManager.recover(client, "tarea8-test5", UDID, null);

        assertEquals(IOSRecoveryOutcome.NO_ACTION_REQUIRED, result.outcome);
        assertEquals(IOSRunnerReadinessResult.Status.ERROR, result.diagnosisBeforeRecovery.status);
        assertNull(result.diagnosisAfterRecovery);
    }

    // ── TEST 6 — startup fallido ("Enable UI Automation") -> sin recovery automático ──

    @Test
    @DisplayName("TEST 6 (TAREA 10): startup fallido (IOS_WDA_STARTUP_FAILED) -> ACTION_REQUIRED, "
            + "nunca runPreflight/xcodebuild/acquire/release")
    void startupFailed_noAutomaticRecovery() {
        WdaLifecycleOwner.markTerminalError(UDID,
            "WebDriverAgentRunner-Runner (2490) encountered an error (The test runner failed to "
            + "initialize for UI testing. (Underlying Error: Timed out while enabling automation mode.))");

        IOSRecoveryManager.RecoveryResult result =
                IOSRecoveryManager.recover(client, "tarea8-test6", UDID, null);

        // TAREA 10: antes de esta tarea, este caso llegaba a diagnosisBeforeRecovery.status
        // == ERROR y outcome == NO_ACTION_REQUIRED. Ahora el Engine lo clasifica
        // ACTION_REQUIRED (requiere confirmar el prompt físico en el iPhone), y
        // IOSRecoveryManager.withoutAction() ya traduce ACTION_REQUIRED -> ACTION_REQUIRED
        // sin necesitar ningún cambio en IOSRecoveryManager mismo.
        assertEquals(IOSRecoveryOutcome.ACTION_REQUIRED, result.outcome);
        assertEquals(IOSRunnerReadinessResult.Status.ACTION_REQUIRED, result.diagnosisBeforeRecovery.status);
        assertEquals(IOSWdaErrorCode.IOS_WDA_STARTUP_FAILED, result.diagnosisBeforeRecovery.wdaErrorCode);
        assertNull(result.diagnosisAfterRecovery,
                "No debe haberse intentado ninguna operación real (runPreflight/xcodebuild/acquire/release)");
    }

    // ── TEST 8/9 y cobertura completa — tabla de mapeo pura, sin dispositivo ──

    @Test
    @DisplayName("TEST 8: mapAfterRecovery(ERROR) -> RECOVERY_FAILED")
    void mapAfterRecovery_error_isRecoveryFailed() {
        assertEquals(IOSRecoveryOutcome.RECOVERY_FAILED,
                IOSRecoveryManager.mapAfterRecovery(IOSRunnerReadinessResult.Status.ERROR));
    }

    @Test
    @DisplayName("TEST 9: mapAfterRecovery(ACTION_REQUIRED) -> ACTION_REQUIRED")
    void mapAfterRecovery_actionRequired_isActionRequired() {
        assertEquals(IOSRecoveryOutcome.ACTION_REQUIRED,
                IOSRecoveryManager.mapAfterRecovery(IOSRunnerReadinessResult.Status.ACTION_REQUIRED));
    }

    @Test
    @DisplayName("mapAfterRecovery(READY) -> RECOVERY_SUCCEEDED (usado en TEST 7, validado en vivo)")
    void mapAfterRecovery_ready_isRecoverySucceeded() {
        assertEquals(IOSRecoveryOutcome.RECOVERY_SUCCEEDED,
                IOSRecoveryManager.mapAfterRecovery(IOSRunnerReadinessResult.Status.READY));
    }

    @Test
    @DisplayName("mapAfterRecovery(RECOVERING/NOT_READY/OFFLINE) -> cobertura completa de la tabla")
    void mapAfterRecovery_remainingStatuses() {
        assertEquals(IOSRecoveryOutcome.RECOVERY_IN_PROGRESS,
                IOSRecoveryManager.mapAfterRecovery(IOSRunnerReadinessResult.Status.RECOVERING));
        assertEquals(IOSRecoveryOutcome.RECOVERY_FAILED,
                IOSRecoveryManager.mapAfterRecovery(IOSRunnerReadinessResult.Status.NOT_READY));
        assertEquals(IOSRecoveryOutcome.RECOVERY_FAILED,
                IOSRecoveryManager.mapAfterRecovery(IOSRunnerReadinessResult.Status.OFFLINE));
    }

    // ── Precondición ────────────────────────────────────────────────────────

    @Test
    @DisplayName("UDID nulo/vacío -> IllegalArgumentException, nunca se intenta nada")
    void blankUdid_throwsImmediately() {
        assertThrows(IllegalArgumentException.class,
                () -> IOSRecoveryManager.recover(client, "tarea8-precondicion", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> IOSRecoveryManager.recover(client, "tarea8-precondicion", null, null));
    }
}
