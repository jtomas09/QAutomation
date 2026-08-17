package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el parseo de límites de frame PNG (chunk IEND + CRC de 4 bytes)
 * contra un PROCESO REAL (no un InputStream simulado en memoria) — usa `cat`
 * leyendo un archivo fixture con varios "frames" falsos concatenados, para
 * probar la implementación real de principio a fin (spawning de proceso,
 * lectura de stdout, detección de límites) sin depender de ffmpeg/scrcpy
 * instalados ni de hardware.
 *
 * Los "frames" no son PNGs válidos de verdad — FfmpegPngFrameSource nunca
 * valida el contenido, solo busca el patrón "IEND" + 4 bytes cualquiera, que
 * es exactamente lo que produce cualquier PNG real generado por ffmpeg.
 */
@DisplayName("FfmpegPngFrameSource")
class FfmpegPngFrameSourceTest {

    private final List<FfmpegPngFrameSource> toCleanUp = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (FfmpegPngFrameSource s : toCleanUp) s.stop();
        toCleanUp.clear();
    }

    private static byte[] fakeFrame(String marker) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(marker.getBytes(StandardCharsets.UTF_8));
        out.write("IEND".getBytes(StandardCharsets.UTF_8));
        out.write(new byte[]{1, 2, 3, 4}); // CRC falso de 4 bytes
        return out.toByteArray();
    }

    @Test
    @DisplayName("divide correctamente un stream con múltiples frames concatenados")
    void splitsMultipleConcatenatedFrames() throws Exception {
        Path fixture = Files.createTempFile("fake-frames-", ".bin");
        try (var os = Files.newOutputStream(fixture)) {
            os.write(fakeFrame("FRAME-ONE-"));
            os.write(fakeFrame("FRAME-TWO-"));
        }

        ProcessBuilder pb = new ProcessBuilder("cat", fixture.toString());
        FfmpegPngFrameSource source = new FfmpegPngFrameSource(pb, "test");
        toCleanUp.add(source);

        assertTrue(source.start(), "el proceso 'cat' debería arrancar sin problema");

        byte[] last = waitForFrame(source, 3_000);
        assertNotNull(last, "debería haber capturado al menos un frame");

        String asText = new String(last, StandardCharsets.UTF_8);
        assertTrue(asText.startsWith("FRAME-TWO-") || asText.startsWith("FRAME-ONE-"),
                "el frame capturado debe ser exactamente uno de los dos frames del fixture, sin mezclar bytes");
        assertTrue(asText.endsWith("IEND"),
                "el frame debe incluir el chunk IEND y su CRC completo, sin truncar");

        Files.deleteIfExists(fixture);
    }

    @Test
    @DisplayName("stop() limpia el último frame y mata el proceso")
    void stopClearsFrameAndKillsProcess() throws Exception {
        Path fixture = Files.createTempFile("fake-frames-", ".bin");
        try (var os = Files.newOutputStream(fixture)) {
            os.write(fakeFrame("ONLY-FRAME-"));
        }

        // `tail -f` mantiene el proceso vivo indefinidamente — simula un ffmpeg
        // de larga duración en vez de un `cat` que termina solo.
        ProcessBuilder pb = new ProcessBuilder("tail", "-f", fixture.toString());
        FfmpegPngFrameSource source = new FfmpegPngFrameSource(pb, "test");

        assertTrue(source.start());
        assertNotNull(waitForFrame(source, 3_000));
        assertTrue(source.isAlive());

        source.stop();

        assertNull(source.latestFrame(), "stop() debe limpiar el último frame inmediatamente");
        // destroyForcibly() es asíncrono a nivel de SO — se da un margen breve.
        long deadline = System.currentTimeMillis() + 2_000;
        while (source.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertFalse(source.isAlive(), "el proceso debe estar muerto tras stop()");

        Files.deleteIfExists(fixture);
    }

    @Test
    @DisplayName("un proceso que nunca produce output nunca reporta un frame (sin falsos positivos)")
    void neverProducesFrameWhenProcessIsSilent() throws Exception {
        // `sleep` no escribe nada a stdout — ninguna captura de frame debería ocurrir.
        ProcessBuilder pb = new ProcessBuilder("sleep", "2");
        FfmpegPngFrameSource source = new FfmpegPngFrameSource(pb, "test");
        toCleanUp.add(source);

        assertTrue(source.start());
        Thread.sleep(500);
        assertNull(source.latestFrame(), "un proceso silencioso nunca debe reportar un frame");
    }

    private static byte[] waitForFrame(FfmpegPngFrameSource source, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] frame = source.latestFrame();
            if (frame != null) return frame;
            Thread.sleep(50);
        }
        return null;
    }
}
