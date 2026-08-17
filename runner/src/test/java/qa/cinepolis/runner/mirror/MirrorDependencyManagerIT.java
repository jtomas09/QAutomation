package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de INTEGRACIÓN REAL — descarga ffmpeg y scrcpy de verdad (requiere
 * red) hacia un directorio temporal, y confirma que quedan ejecutables sin
 * ninguna acción manual (sin brew, sin abrir nada). Esta es la validación de
 * fondo del requisito "cero configuración manual" — no una simulación.
 *
 * Se activa solo con -Dmirror.it=true (evita depender de red en CI/entornos
 * restringidos por defecto); se corrió manualmente contra la red real antes
 * de integrar esta funcionalidad.
 */
@EnabledIfSystemProperty(named = "mirror.it", matches = "true")
@DisplayName("MirrorDependencyManager — descarga real (integración)")
class MirrorDependencyManagerIT {

    private static Path tempRunnerDir;

    @BeforeAll
    static void setup() throws Exception {
        tempRunnerDir = Files.createTempDirectory("mirror-it-runner-");
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (tempRunnerDir == null) return;
        try (var stream = Files.walk(tempRunnerDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
            });
        }
    }

    @Test
    @Timeout(120)
    @DisplayName("ensureFfmpeg() descarga un binario real, ejecutable, sin acción del usuario")
    void ensureFfmpegDownloadsRealExecutable() {
        MirrorDependencyManager deps = new MirrorDependencyManager(tempRunnerDir);
        boolean ok = deps.ensureFfmpeg();

        assertTrue(ok, "la descarga automática de ffmpeg debería funcionar con red real");
        Path bin = Path.of(deps.embeddedFfmpegPath());
        assertTrue(Files.exists(bin), "el binario debe existir tras la descarga");
        assertTrue(Files.isExecutable(bin), "el binario debe quedar marcado ejecutable automáticamente");

        // Segunda llamada: ya está presente, no debe volver a descargar (idempotente).
        assertTrue(deps.ensureFfmpeg());
    }

    @Test
    @Timeout(120)
    @DisplayName("ensureScrcpy() descarga scrcpy + su scrcpy-server, sin acción del usuario")
    void ensureScrcpyDownloadsRealExecutable() {
        MirrorDependencyManager deps = new MirrorDependencyManager(tempRunnerDir);
        boolean ok = deps.ensureScrcpy();

        assertTrue(ok, "la descarga automática de scrcpy debería funcionar con red real");
        Path bin = Path.of(deps.embeddedScrcpyPath());
        assertTrue(Files.exists(bin));
        assertTrue(Files.isExecutable(bin));

        Path server = deps.scrcpyDir().resolve("scrcpy-server");
        assertTrue(Files.exists(server), "scrcpy-server debe quedar disponible junto al binario cliente");
    }
}
