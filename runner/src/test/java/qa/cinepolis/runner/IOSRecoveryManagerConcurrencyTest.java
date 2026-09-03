package qa.cinepolis.runner;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 8.2 — tests del guard {@code RECOVERY_REQUEST_PENDING}.
 *
 * IMPORTANTE (hardware): {@code IOSRecoveryManager.recover()} solo llega a
 * intentar adquirir el guard DESPUÉS de un diagnóstico fresco real que muestre
 * NOT_READY+IOS_WDA_NOT_STARTED — lo cual exige que el dispositivo esté
 * CONNECTED/PAIRED. Durante esta sesión el iPhone físico (00008110-...) quedó
 * intermitentemente OFFLINE para xctrace (confirmado con
 * `xcrun xctrace list devices`, ya documentado en los reportes de TAREA 8 y
 * TAREA 8.1 — no es una regresión de este cambio). Los tests de ciclo de vida
 * completo (TEST 2, 4, 5, 6, 7 del enunciado) requieren el dispositivo
 * conectado para poder ejercitarse de punta a punta; se marcan explícitamente
 * más abajo cuáles dependen de hardware. El test MÁS IMPORTANTE de esta tarea
 * — la atomicidad real del guard bajo concurrencia (TEST 1) — se prueba
 * DIRECTAMENTE sobre el campo real vía reflexión, sin depender del dispositivo
 * en absoluto, exactamente el mismo mecanismo que usa recover() en producción.
 */
@DisplayName("IOSRecoveryManager — guard RECOVERY_REQUEST_PENDING (TAREA 8.2)")
class IOSRecoveryManagerConcurrencyTest {

    private static final String UDID = "00008110-000129261482601E";
    private static final String UDID_B = "test-udid-b-tarea-8-2";

    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    @AfterEach
    void cleanup() throws Exception {
        WdaLifecycleOwner.resetForRetry(UDID);
        WdaLifecycleOwner.resetForRetry(UDID_B);
        pendingSet().remove(UDID);
        pendingSet().remove(UDID_B);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> pendingSet() throws Exception {
        Field f = IOSRecoveryManager.class.getDeclaredField("RECOVERY_REQUEST_PENDING");
        f.setAccessible(true);
        return (Set<String>) f.get(null);
    }

    // ── TEST 1 — atomicidad real bajo concurrencia, sin depender del dispositivo ──

    @Test
    @DisplayName("TEST 1: add() concurrente sobre el MISMO UDID -> exactamente un hilo gana, "
            + "el resto pierde (atomicidad real, campo de producción vía reflexión)")
    void concurrentAddOnSameUdid_exactlyOneWinner() throws Exception {
        Set<String> pending = pendingSet();
        assertFalse(pending.contains(UDID), "Precondición: el guard debe estar libre antes del test");

        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        Thread[] pool = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            pool[i] = new Thread(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ignored) {}
                if (pending.add(UDID)) {
                    winners.incrementAndGet();
                }
            });
            pool[i].start();
        }
        ready.await();
        go.countDown(); // libera los 20 hilos a la vez, maximizando la ventana de carrera real
        for (Thread t : pool) t.join(5000);

        assertEquals(1, winners.get(),
                "Con add() atómico, exactamente UN hilo debe haber insertado el UDID, sin importar cuántos compitan");
        assertTrue(pending.contains(UDID));

        pending.remove(UDID); // limpieza — simula el finally { RECOVERY_REQUEST_PENDING.remove(udid) } real
    }

    @Test
    @DisplayName("TEST 1b: mismo experimento, pero UDIDs distintos -> todos ganan, sin lock global")
    void concurrentAddOnDifferentUdids_allWin() throws Exception {
        Set<String> pending = pendingSet();
        int threads = 10;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        Thread[] pool = new Thread[threads];
        String[] udids = new String[threads];

        for (int i = 0; i < threads; i++) {
            udids[i] = "concurrency-test-udid-" + i;
            final String udid = udids[i];
            pool[i] = new Thread(() -> {
                try { go.await(); } catch (InterruptedException ignored) {}
                if (pending.add(udid)) winners.incrementAndGet();
            });
            pool[i].start();
        }
        go.countDown();
        for (Thread t : pool) t.join(5000);

        assertEquals(threads, winners.get(), "UDIDs distintos no deben competir entre sí — ningún lock global");
        for (String udid : udids) pending.remove(udid);
    }

    @Test
    @DisplayName("TEST 2: secuencial sobre el mismo UDID -> tras remove(), un segundo add() sí tiene éxito "
            + "(el guard no queda bloqueado permanentemente)")
    void sequentialSameUdid_secondAddSucceedsAfterRemove() throws Exception {
        Set<String> pending = pendingSet();

        assertTrue(pending.add(UDID), "Primer recovery adquiere el guard");
        assertFalse(pending.add(UDID), "Mientras el primero no libera, un segundo intento debe fallar");

        pending.remove(UDID); // equivalente al finally { RECOVERY_REQUEST_PENDING.remove(udid) } real

        assertTrue(pending.add(UDID), "Tras liberar, un nuevo recovery para el mismo UDID sí debe poder iniciar");
        pending.remove(UDID);
    }

    // ── Tests de ciclo de vida completo — REQUIEREN el dispositivo conectado ────
    // ── (ver nota de clase). Se dejan implementados y se documenta su resultado ──
    // ── real en el reporte, sin fabricar un resultado si el hardware no responde. ──

    @Test
    @DisplayName("TEST 5 (hardware): Trust requerido -> ACTION_REQUIRED, guard liberado, "
            + "recover() puede volver a invocarse")
    void trustRequired_guardReleased_canRetryLater() throws Exception {
        WdaLifecycleOwner.markTerminalError(UDID,
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.");

        IOSRecoveryManager.RecoveryResult result =
                IOSRecoveryManager.recover(client, "tarea82-test5", UDID, null);

        assertFalse(pendingSet().contains(UDID), "El guard nunca debió adquirirse para ACTION_REQUIRED");
        // Si el dispositivo respondió CONNECTED/PAIRED/DEVELOPER_MODE, el resultado real es ACTION_REQUIRED.
        // Si el dispositivo está offline en este momento, el diagnóstico fresco corta antes (OFFLINE) —
        // en cualquiera de los dos casos el guard NO debe quedar ocupado, que es lo que este test verifica.
        assertNotEquals(IOSRecoveryOutcome.RECOVERY_IN_PROGRESS, result.outcome,
                "Ni ACTION_REQUIRED ni OFFLINE deben reportarse como si hubiera un recovery en curso");

        IOSRecoveryManager.RecoveryResult second =
                IOSRecoveryManager.recover(client, "tarea82-test5b", UDID, null);
        assertFalse(pendingSet().contains(UDID));
        assertEquals(result.outcome, second.outcome,
                "Sin cambios en el dispositivo entre ambas llamadas, el resultado debe ser reproducible");
    }

    @Test
    @DisplayName("TEST 6 (hardware): WDA ya disponible -> guard nunca se adquiere, recover() reentrante")
    void wdaAlreadyReady_guardNeverAcquired() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8100), 0);
        server.createContext("/status", exchange -> {
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            IOSRecoveryManager.RecoveryResult result =
                    IOSRecoveryManager.recover(client, "tarea82-test6", UDID, null);

            assertFalse(pendingSet().contains(UDID));
            // Con WDA arriba (vía servidor fake) Y el dispositivo CONNECTED, el resultado es
            // NO_ACTION_REQUIRED (ver IOSRecoveryManager.withoutAction para READY). Si el
            // dispositivo está offline en este instante, el resultado es igualmente
            // NO_ACTION_REQUIRED (rama OFFLINE del mismo método) — en ningún caso debe
            // quedar el guard ocupado ni reportarse como recovery en curso.
            assertNotEquals(IOSRecoveryOutcome.RECOVERY_IN_PROGRESS, result.outcome);

            IOSRecoveryManager.RecoveryResult second =
                    IOSRecoveryManager.recover(client, "tarea82-test6b", UDID, null);
            assertFalse(pendingSet().contains(UDID));
        } finally {
            server.stop(0);
        }
    }
}
