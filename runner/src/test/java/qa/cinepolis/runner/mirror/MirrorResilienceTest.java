package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de resistencia — simula conectar/desconectar/cambiar de
 * dispositivo repetidamente y verifica que no queden procesos, hilos ni
 * pipes huérfanos. Usa `tail -f` como sustituto de un ffmpeg/scrcpy de larga
 * duración (mismo patrón de proceso-continuo-que-hay-que-matar), evitando
 * depender de esos binarios reales para esta verificación específica.
 */
@DisplayName("Mirror — resistencia y limpieza de recursos")
class MirrorResilienceTest {

    private static final int CYCLES = 25;

    @Test
    @Timeout(30)
    @DisplayName("25 ciclos de start/stop no dejan procesos vivos ni hilos acumulados")
    void repeatedStartStopLeavesNoOrphans() throws Exception {
        Path fixture = Files.createTempFile("resilience-", ".bin");
        Files.writeString(fixture, "x".repeat(64));

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int baselineThreads = threads.getThreadCount();

        for (int i = 0; i < CYCLES; i++) {
            ProcessBuilder pb = new ProcessBuilder("tail", "-f", fixture.toString());
            FfmpegPngFrameSource source = new FfmpegPngFrameSource(pb, "cycle-" + i);

            assertTrue(source.start(), "ciclo " + i + ": el proceso debería arrancar");
            assertTrue(source.isAlive(), "ciclo " + i + ": el proceso debería estar vivo tras start()");

            source.stop();

            // destroyForcibly() es asíncrono a nivel de SO — se da un margen breve
            // por ciclo, igual que tendría que hacerlo cualquier llamador real.
            long deadline = System.currentTimeMillis() + 2_000;
            while (source.isAlive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertFalse(source.isAlive(), "ciclo " + i + ": el proceso debe estar muerto tras stop()");
        }

        // El hilo lector de cada ciclo es daemon y debe terminar solo al cerrarse
        // su InputStream (proceso muerto) — el conteo de hilos JVM no debe crecer
        // de forma acumulativa ciclo tras ciclo (permite algo de holgura por hilos
        // daemon todavía terminando de salir en el momento exacto de la medición).
        Thread.sleep(300); // margen para que los últimos hilos lectores terminen
        int finalThreads = threads.getThreadCount();
        assertTrue(finalThreads <= baselineThreads + 5,
                "conteo de hilos creció de " + baselineThreads + " a " + finalThreads
                        + " tras " + CYCLES + " ciclos — posible fuga de hilos lectores");

        Files.deleteIfExists(fixture);
    }

    @Test
    @Timeout(20)
    @DisplayName("cambiar de dispositivo (UDIDs distintos) libera el proceso del anterior")
    void switchingDeviceStopsThePreviousProcess() throws Exception {
        Path fixtureA = Files.createTempFile("device-a-", ".bin");
        Path fixtureB = Files.createTempFile("device-b-", ".bin");
        Files.writeString(fixtureA, "a".repeat(64));
        Files.writeString(fixtureB, "b".repeat(64));

        FfmpegPngFrameSource sourceA = new FfmpegPngFrameSource(
                new ProcessBuilder("tail", "-f", fixtureA.toString()), "device-A");
        assertTrue(sourceA.start());
        assertTrue(sourceA.isAlive());

        // Simula "el usuario seleccionó otro dispositivo": se detiene la sesión
        // anterior ANTES de abrir la nueva — mismo patrón que PhoneFrame usa al
        // cambiar selectedDevice (stop() del provider saliente).
        sourceA.stop();

        FfmpegPngFrameSource sourceB = new FfmpegPngFrameSource(
                new ProcessBuilder("tail", "-f", fixtureB.toString()), "device-B");
        assertTrue(sourceB.start());
        assertTrue(sourceB.isAlive());

        long deadline = System.currentTimeMillis() + 2_000;
        while (sourceA.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(sourceA.isAlive(), "el proceso del dispositivo anterior no debe seguir vivo");
        assertTrue(sourceB.isAlive(), "el proceso del nuevo dispositivo debe seguir activo, sin verse afectado");

        sourceB.stop();
        Files.deleteIfExists(fixtureA);
        Files.deleteIfExists(fixtureB);
    }

    @Test
    @DisplayName("stop() es idempotente — llamarlo varias veces no lanza ni deja el objeto en estado inconsistente")
    void stopIsIdempotent() throws Exception {
        Path fixture = Files.createTempFile("idempotent-", ".bin");
        Files.writeString(fixture, "z".repeat(64));

        FfmpegPngFrameSource source = new FfmpegPngFrameSource(
                new ProcessBuilder("tail", "-f", fixture.toString()), "idempotent");
        assertTrue(source.start());

        assertDoesNotThrow(() -> {
            source.stop();
            source.stop();
            source.stop();
        });
        assertNull(source.latestFrame());

        // destroyForcibly() es asíncrono a nivel de SO (confirmado por esta misma
        // prueba en su primera versión: isAlive() seguía true justo después de
        // stop()) — se sondea con margen, igual que el resto de las pruebas.
        long deadline = System.currentTimeMillis() + 2_000;
        while (source.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(source.isAlive());

        Files.deleteIfExists(fixture);
    }
}
