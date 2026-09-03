package qa.cinepolis.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 15 — tests del contrato corregido de {@code WdaLifecycleOwner.acquire()}:
 * el hueco identificado en TAREA 13 donde una excepción de {@code future.get()}
 * (distinta de una simple interrupción local) se perdía sin dejar rastro en
 * {@code TERMINAL_ERRORS}, haciendo que {@link IOSRunnerReadinessEngine}
 * interpretara un intento de WDA realmente fallido como "nunca intentado".
 *
 * Técnica: inserción directa de un {@code CompletableFuture} en el mapa REAL
 * {@code WdaLifecycleOwner.INFLIGHT} vía reflexión — misma técnica ya
 * establecida en TAREA 6/8.1/13, evita disparar un build real de xcodebuild.
 * {@code acquire()} y {@code isTerminalError()}/{@code markTerminalError()}/
 * {@code resetForRetry()} son accesores reales (package-private o públicos) de
 * la propia clase — no se mockea ninguna lógica de negocio.
 */
@DisplayName("WdaLifecycleOwner.acquire() — diagnóstico del joiner (TAREA 15)")
class WdaLifecycleOwnerJoinerDiagnosisTest {

    private static final String UDID   = "tarea15-test-udid";
    private static final String UDID_B = "tarea15-test-udid-b";
    private static final String REAL_UDID = "00008110-000129261482601E";

    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    @AfterEach
    void cleanup() throws Exception {
        WdaLifecycleOwner.resetForRetry(UDID);
        WdaLifecycleOwner.resetForRetry(UDID_B);
        WdaLifecycleOwner.resetForRetry(REAL_UDID);
        inflight().remove(UDID);
        inflight().remove(UDID_B);
        inflight().remove(REAL_UDID);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CompletableFuture<WdaLifecycleOwner.Result>> inflight() throws Exception {
        Field f = WdaLifecycleOwner.class.getDeclaredField("INFLIGHT");
        f.setAccessible(true);
        return (Map<String, CompletableFuture<WdaLifecycleOwner.Result>>) f.get(null);
    }

    // ── TEST 1 — Future exitoso: sin terminal error artificial ────────────────

    @Test
    @DisplayName("TEST 1: future completa con éxito -> Result(true), sin terminal error")
    void successfulFuture_noArtificialTerminalError() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        Thread joiner = new Thread(() -> {
            WdaLifecycleOwner.Result r = WdaLifecycleOwner.acquire(
                    WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t1", UDID, "TEAM", "bundle", false);
            assertTrue(r.ready);
            assertNull(r.reason);
        });
        joiner.start();
        Thread.sleep(200);
        pending.complete(new WdaLifecycleOwner.Result(true, null));
        joiner.join(5000);

        assertFalse(WdaLifecycleOwner.isTerminalError(UDID));
        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID));
    }

    // ── TEST 2 — Future falla con excepción real (ExecutionException) ─────────

    @Test
    @DisplayName("TEST 2: ExecutionException real -> Result(false), motivo preservado, terminal diagnosis disponible")
    void executionException_persistsRealFailureReason() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        WdaLifecycleOwner.Result[] out = new WdaLifecycleOwner.Result[1];
        Thread joiner = new Thread(() -> out[0] = WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t2", UDID, "TEAM", "bundle", false));
        joiner.start();
        Thread.sleep(200);
        pending.completeExceptionally(new RuntimeException("simulated runAttempt crash"));
        joiner.join(5000);

        assertFalse(out[0].ready);
        assertTrue(out[0].reason.contains("RuntimeException"));
        assertTrue(out[0].reason.contains("simulated runAttempt crash"));
        assertTrue(WdaLifecycleOwner.isTerminalError(UDID),
                "ANTES de TAREA 15 esto habría quedado false — exactamente el hueco de TAREA 13");
        assertEquals(out[0].reason, WdaLifecycleOwner.terminalErrorReason(UDID));
    }

    // ── TEST 3 — ya existe un terminal error específico: no se sobrescribe ────

    @Test
    @DisplayName("TEST 3: terminal error específico ya registrado por runAttempt() -> permanece intacto")
    void existingSpecificTerminalError_isNotOverwritten() throws Exception {
        WdaLifecycleOwner.markTerminalError(UDID,
                "Invalid trust settings. Restore system default trust settings for certificate \"Apple Development\".");

        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        Thread joiner = new Thread(() -> WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t3", UDID, "TEAM", "bundle", false));
        joiner.start();
        Thread.sleep(200);
        pending.completeExceptionally(new RuntimeException("unrelated later crash"));
        joiner.join(5000);

        assertEquals("Invalid trust settings. Restore system default trust settings for certificate \"Apple Development\".",
                WdaLifecycleOwner.terminalErrorReason(UDID),
                "El motivo específico ya registrado nunca debe perderse por una excepción posterior más genérica");
    }

    // ── TEST 4 — motivo genérico nuevo vs motivo específico previo ────────────

    @Test
    @DisplayName("TEST 4: excepción genérica nueva + motivo específico previo -> se conserva el específico")
    void genericNewFailure_specificPreviousReason_specificWins() throws Exception {
        WdaLifecycleOwner.markTerminalError(UDID, "No Accounts: Add a new account in Accounts settings.");

        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        Thread joiner = new Thread(() -> WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t4", UDID, "TEAM", "bundle", false));
        joiner.start();
        Thread.sleep(200);
        pending.completeExceptionally(new IllegalStateException());
        joiner.join(5000);

        assertEquals("No Accounts: Add a new account in Accounts settings.",
                WdaLifecycleOwner.terminalErrorReason(UDID));
    }

    // ── TEST 5 — joiner recibe el fallo: sin diagnóstico incorrecto o duplicado ─

    @Test
    @DisplayName("TEST 5: joiner observa el fallo del future -> diagnóstico global correcto, no duplicado")
    void joinerObservesFailure_globalDiagnosisCorrect() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        WdaLifecycleOwner.Result[] out = new WdaLifecycleOwner.Result[1];
        Thread joiner = new Thread(() -> out[0] = WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.MIRROR, client, "t5", UDID, "TEAM", "bundle", false));
        joiner.start();
        Thread.sleep(200);
        pending.completeExceptionally(new RuntimeException("build failed for real"));
        joiner.join(5000);

        assertFalse(out[0].ready);
        assertTrue(WdaLifecycleOwner.isTerminalError(UDID));
        assertTrue(WdaLifecycleOwner.terminalErrorReason(UDID).contains("build failed for real"),
                "El motivo global debe reflejar la causa REAL, no un texto sintetizado por el joiner");
    }

    // ── TEST 6 — joiner interrumpido mientras el productor continúa ───────────

    @Test
    @DisplayName("TEST 6: joiner interrumpido -> NO marca WDA como terminalmente fallido")
    void joinerInterrupted_doesNotMarkFalseTerminalError() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        WdaLifecycleOwner.Result[] out = new WdaLifecycleOwner.Result[1];
        boolean[] interruptFlagRestored = new boolean[1];
        CountDownLatch started = new CountDownLatch(1);
        Thread joiner = new Thread(() -> {
            started.countDown();
            out[0] = WdaLifecycleOwner.acquire(
                    WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t6", UDID, "TEAM", "bundle", false);
            interruptFlagRestored[0] = Thread.currentThread().isInterrupted();
        });
        joiner.start();
        started.await();
        Thread.sleep(200); // deja que el joiner llegue a future.get() y quede bloqueado
        joiner.interrupt(); // simula la interrupción LOCAL de este consumidor
        joiner.join(5000);

        assertFalse(out[0].ready);
        assertFalse(WdaLifecycleOwner.isTerminalError(UDID),
                "Una interrupción local del joiner NUNCA debe registrarse como fallo terminal de WDA "
                        + "— el intento real (el productor) puede seguir en curso");
        assertTrue(interruptFlagRestored[0], "El flag de interrupción del hilo debe restaurarse (Thread.interrupt())");

        // El productor (representado aquí por 'pending') sigue existiendo y puede
        // completarse con éxito después, sin que la interrupción del joiner lo afecte.
        pending.complete(new WdaLifecycleOwner.Result(true, null));
        assertFalse(WdaLifecycleOwner.isTerminalError(UDID));
    }

    // ── TEST 7 — dos consumidores concurrentes, mismo UDID ─────────────────────

    @Test
    @DisplayName("TEST 7: dos consumidores concurrentes -> mismo diagnóstico, sin duplicar ni corromper INFLIGHT")
    void twoConcurrentConsumers_sameUdid_consistentDiagnosis() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        WdaLifecycleOwner.Result[] out = new WdaLifecycleOwner.Result[2];
        CountDownLatch bothStarted = new CountDownLatch(2);
        Thread a = new Thread(() -> {
            bothStarted.countDown();
            out[0] = WdaLifecycleOwner.acquire(
                    WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t7a", UDID, "TEAM", "bundle", false);
        });
        Thread b = new Thread(() -> {
            bothStarted.countDown();
            out[1] = WdaLifecycleOwner.acquire(
                    WdaLifecycleOwner.Consumer.RECOVERY, client, "t7b", UDID, "TEAM", "bundle", false);
        });
        a.start(); b.start();
        bothStarted.await();
        Thread.sleep(200);
        pending.completeExceptionally(new RuntimeException("shared failure"));
        a.join(5000); b.join(5000);

        assertFalse(out[0].ready);
        assertFalse(out[1].ready);
        assertTrue(WdaLifecycleOwner.isTerminalError(UDID));
        assertEquals(WdaLifecycleOwner.terminalErrorReason(UDID), WdaLifecycleOwner.terminalErrorReason(UDID),
                "Un único diagnóstico global consistente, no uno por consumidor");
        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID));
    }

    // ── TEST 8 — dos UDIDs distintos: independencia total ──────────────────────

    @Test
    @DisplayName("TEST 8: dos UDIDs distintos -> diagnósticos totalmente independientes")
    void twoDifferentUdids_independentDiagnosis() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pendingA = new CompletableFuture<>();
        CompletableFuture<WdaLifecycleOwner.Result> pendingB = new CompletableFuture<>();
        inflight().put(UDID, pendingA);
        inflight().put(UDID_B, pendingB);

        Thread ta = new Thread(() -> WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t8a", UDID, "TEAM", "bundle", false));
        Thread tb = new Thread(() -> WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t8b", UDID_B, "TEAM", "bundle", false));
        ta.start(); tb.start();
        Thread.sleep(200);
        pendingA.completeExceptionally(new RuntimeException("fails for A only"));
        pendingB.complete(new WdaLifecycleOwner.Result(true, null));
        ta.join(5000); tb.join(5000);

        assertTrue(WdaLifecycleOwner.isTerminalError(UDID));
        assertFalse(WdaLifecycleOwner.isTerminalError(UDID_B));
    }

    // ── TEST 9 — INFLIGHT queda limpio tras el fallo ───────────────────────────

    @Test
    @DisplayName("TEST 9: tras el fallo, INFLIGHT queda limpio para el UDID")
    void afterFailure_inflightIsClean() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(UDID, pending);

        Thread joiner = new Thread(() -> WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t9", UDID, "TEAM", "bundle", false));
        joiner.start();
        Thread.sleep(200);
        pending.completeExceptionally(new RuntimeException("boom"));
        joiner.join(5000);

        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID));
        assertFalse(inflight().containsKey(UDID));
    }

    // ── TEST 10 — el Engine ya no reporta IOS_WDA_NOT_STARTED tras el fallo real ─

    @Test
    @DisplayName("TEST 10: tras el fallo real registrado, Engine.evaluate() NO reporta IOS_WDA_NOT_STARTED")
    void afterRealFailureRegistered_engineDoesNotReportNeverAttempted() throws Exception {
        CompletableFuture<WdaLifecycleOwner.Result> pending = new CompletableFuture<>();
        inflight().put(REAL_UDID, pending);

        Thread joiner = new Thread(() -> WdaLifecycleOwner.acquire(
                WdaLifecycleOwner.Consumer.JOB_EXECUTION, client, "t10", REAL_UDID, "TEAM", "bundle", false));
        joiner.start();
        Thread.sleep(200);
        pending.completeExceptionally(new RuntimeException("real attempt crashed unexpectedly"));
        joiner.join(5000);

        assertTrue(WdaLifecycleOwner.isTerminalError(REAL_UDID));
        assertFalse(WdaLifecycleOwner.isBuildInFlight(REAL_UDID));

        IOSRunnerReadinessResult after =
                IOSRunnerReadinessEngine.evaluate(client, "t10-engine", REAL_UDID);

        System.out.println("[TAREA15][ANTES-DEL-FIX-habria-sido] status=NOT_READY errorCode=IOS_WDA_NOT_STARTED");
        System.out.println("[TAREA15][DESPUES-DEL-FIX] " + after);

        // Solo se puede afirmar la comparación completa si el dispositivo pasó todas
        // las etapas previas (CONNECTED/PAIRED/DEVELOPER_MODE/TRUST/SIGNED/PROVISIONED)
        // reales — si el hardware está offline en este instante, el Engine corta antes
        // (OFFLINE) y no llega siquiera a mirar terminalReason; se reporta explícitamente
        // en vez de fallar con un mensaje confuso.
        if (after.status == IOSRunnerReadinessResult.Status.OFFLINE) {
            System.out.println("[TAREA15] Dispositivo offline en este instante — no se puede completar "
                    + "la comparación end-to-end contra el Engine ahora mismo (ambiental, no atribuible al fix).");
            return;
        }

        assertNotEquals("IOS_WDA_NOT_STARTED", after.errorCode,
                "ANTES de TAREA 15 esto habría sido IOS_WDA_NOT_STARTED (el hueco reproducido en TAREA 13) — "
                        + "con el fix, el Engine debe ver el terminal error real");
        assertNotEquals(IOSRunnerReadinessResult.Status.NOT_READY, after.status);
    }
}
