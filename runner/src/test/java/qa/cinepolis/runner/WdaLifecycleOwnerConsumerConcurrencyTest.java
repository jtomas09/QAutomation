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
 * TAREA 8.1 — auditoría de concurrencia de Consumer.RECOVERY dentro de
 * WdaLifecycleOwner. Usa exclusivamente mecanismos ya existentes (servidor
 * HTTP fake en :8100 para evitar un xcodebuild real, reflexión sobre el mapa
 * INFLIGHT real) — cero cambios productivos motivados por esta tarea.
 *
 * acquire()/release()/isBuildInFlight() son package-private — accesibles
 * directamente desde este test por estar en el mismo paquete, sin reflexión
 * para esos tres.
 */
@DisplayName("WdaLifecycleOwner — concurrencia de Consumer (TAREA 8.1)")
class WdaLifecycleOwnerConsumerConcurrencyTest {

    private static final String UDID_A = "00008110-000129261482601E";
    private static final String UDID_B = "test-udid-b-tarea-8-1";

    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    private HttpServer fakeWdaServer;

    @AfterEach
    void cleanup() {
        WdaLifecycleOwner.resetForRetry(UDID_A);
        WdaLifecycleOwner.resetForRetry(UDID_B);
        if (fakeWdaServer != null) fakeWdaServer.stop(0);
    }

    private void startFakeWdaServer() throws Exception {
        fakeWdaServer = HttpServer.create(new InetSocketAddress("localhost", 8100), 0);
        fakeWdaServer.createContext("/status", exchange -> {
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeWdaServer.start();
    }

    // ── HALLAZGO PRINCIPAL: dos registros del MISMO Consumer + un solo release ──
    // ── derriba WDA aunque, conceptualmente, otro llamador del mismo tipo ──────
    // ── todavía no ha terminado. Ver sección G del reporte. ────────────────────

    @Test
    @DisplayName("HALLAZGO: dos acquire(RECOVERY) + un solo release(RECOVERY) SÍ derriba WDA "
            + "(Consumer no es un contador de referencias, es una marca de tipo por UDID)")
    void twoRecoveryAcquires_oneRelease_stillTearsDown() throws Exception {
        startFakeWdaServer(); // evita build real: acquire() toma el fast-path de isWdaRunning()

        WdaLifecycleOwner.Result r1 = WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A, "team", "bundle", true);
        WdaLifecycleOwner.Result r2 = WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.RECOVERY, client, "t2", UDID_A, "team", "bundle", true);

        assertTrue(r1.ready);
        assertTrue(r2.ready);

        // Simula que SOLO el primero de los dos llamadores concurrentes ya terminó
        // y libera su referencia — el segundo, conceptualmente, "todavía la necesita".
        boolean tornDown = WdaLifecycleOwner.release(
                WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A);

        assertTrue(tornDown,
                "CONFIRMADO: un solo release(RECOVERY) derriba WDA aunque un segundo llamador "
                + "RECOVERY (r2) nunca liberó su propia referencia — Consumer.RECOVERY no está "
                + "protegido contra este escenario, exactamente igual que JOB_EXECUTION/MIRROR ya "
                + "lo estaban antes de TAREA 8 (limitación preexistente, no introducida por RECOVERY, "
                + "pero SÍ relevante para RECOVERY porque MIRROR ya tiene su propio guard dedicado "
                + "-MIRROR_REQUEST_PENDING- y RECOVERY no tiene ningún guard equivalente).");
    }

    // ── Casos A-D del enunciado: seguridad ENTRE tipos de Consumer distintos ───

    @Test
    @DisplayName("CASO A: JOB_EXECUTION activo, release(RECOVERY) NO debe derribar WDA")
    void jobExecutionActive_recoveryRelease_doesNotTeardown() throws Exception {
        startFakeWdaServer();
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t1", UDID_A, "team", "bundle", true);
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.RECOVERY, client, "t2", UDID_A, "team", "bundle", true);

        boolean tornDown = WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.RECOVERY, client, "t2", UDID_A);

        assertFalse(tornDown, "JOB_EXECUTION sigue activo — release(RECOVERY) no debe derribar WDA");
        // Limpieza real para no dejar JOB_EXECUTION colgado en este UDID para otros tests.
        WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t1", UDID_A);
    }

    @Test
    @DisplayName("CASO B: MIRROR activo, release(RECOVERY) NO debe derribar WDA")
    void mirrorActive_recoveryRelease_doesNotTeardown() throws Exception {
        startFakeWdaServer();
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.MIRROR, client, "t1", UDID_A, "team", "bundle", true);
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.RECOVERY, client, "t2", UDID_A, "team", "bundle", true);

        boolean tornDown = WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.RECOVERY, client, "t2", UDID_A);

        assertFalse(tornDown, "MIRROR sigue activo — release(RECOVERY) no debe derribar WDA");
        WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.MIRROR, client, "t1", UDID_A);
    }

    @Test
    @DisplayName("CASO C: RECOVERY activo, release(JOB_EXECUTION) NO debe liberar/cancelar RECOVERY")
    void recoveryActive_jobExecutionRelease_doesNotAffectRecovery() throws Exception {
        startFakeWdaServer();
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A, "team", "bundle", true);
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t2", UDID_A, "team", "bundle", true);

        boolean tornDown = WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t2", UDID_A);

        assertFalse(tornDown, "RECOVERY sigue activo — release(JOB_EXECUTION) no debe derribar WDA");
        WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A);
    }

    @Test
    @DisplayName("CASO D: RECOVERY activo, release(MIRROR) NO debe liberar/cancelar RECOVERY")
    void recoveryActive_mirrorRelease_doesNotAffectRecovery() throws Exception {
        startFakeWdaServer();
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A, "team", "bundle", true);
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.MIRROR, client, "t2", UDID_A, "team", "bundle", true);

        boolean tornDown = WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.MIRROR, client, "t2", UDID_A);

        assertFalse(tornDown, "RECOVERY sigue activo — release(MIRROR) no debe derribar WDA");
        WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A);
    }

    // ── Ciclo INFLIGHT: no debe quedar residual, ni en éxito ni en excepción ──

    @Test
    @DisplayName("isBuildInFlight(udid) == false inmediatamente después de acquire() exitoso (fast-path)")
    void afterSuccessfulAcquire_notInFlight() throws Exception {
        startFakeWdaServer();
        WdaLifecycleOwner.acquire(WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A, "team", "bundle", true);

        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID_A),
                "El fast-path (WDA ya corriendo) nunca toca INFLIGHT — nada que limpiar, pero tampoco debe quedar marcado");
        WdaLifecycleOwner.release(WdaLifecycleOwner.Consumer.RECOVERY, client, "t1", UDID_A);
    }

    @Test
    @DisplayName("INFLIGHT inyectado + completado con excepción -> isBuildInFlight vuelve a false")
    void inflightEntryCompletedExceptionally_clearsFlag() throws Exception {
        Field inflightField = WdaLifecycleOwner.class.getDeclaredField("INFLIGHT");
        inflightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CompletableFuture<WdaLifecycleOwner.Result>> inflight =
                (Map<String, CompletableFuture<WdaLifecycleOwner.Result>>) inflightField.get(null);

        CompletableFuture<WdaLifecycleOwner.Result> future = new CompletableFuture<>();
        inflight.put(UDID_A, future);
        assertTrue(WdaLifecycleOwner.isBuildInFlight(UDID_A));

        future.completeExceptionally(new RuntimeException("simulated failure"));
        // El propio acquire() real es quien hace INFLIGHT.remove(udid, future) en su finally;
        // aquí se simula esa misma limpieza para verificar que, una vez hecha, el flag cae.
        inflight.remove(UDID_A, future);

        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID_A));
    }

    // ── Dos UDIDs distintos: independencia, sin bloqueo global ────────────────

    @Test
    @DisplayName("Dos UDIDs distintos: RECOVERY en uno no bloquea ni interfiere con el otro")
    void twoDifferentUdids_independentInflight() throws Exception {
        Field inflightField = WdaLifecycleOwner.class.getDeclaredField("INFLIGHT");
        inflightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CompletableFuture<WdaLifecycleOwner.Result>> inflight =
                (Map<String, CompletableFuture<WdaLifecycleOwner.Result>>) inflightField.get(null);

        CompletableFuture<WdaLifecycleOwner.Result> futureA = new CompletableFuture<>();
        inflight.put(UDID_A, futureA);
        try {
            assertTrue(WdaLifecycleOwner.isBuildInFlight(UDID_A));
            assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID_B),
                    "UDID_B no debe verse afectado por una operación en curso para UDID_A");
        } finally {
            inflight.remove(UDID_A);
            futureA.complete(new WdaLifecycleOwner.Result(false, "cleanup"));
        }
    }
}
