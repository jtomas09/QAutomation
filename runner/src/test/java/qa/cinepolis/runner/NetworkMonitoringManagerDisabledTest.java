package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qa.cinepolis.runner.model.NetworkMonitoringConfig;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — hacer opcional la captura de tráfico vía Dashboard.
 *
 * Verifica, llamando al método REAL (sin mocks — este proyecto no usa Mockito),
 * que con la bandera desactivada NetworkMonitoringManager.start() nunca llega a
 * tocar NetworkRuntimeProvisioner/mitmproxy/adb — el propio código ya tiene ese
 * early-return (cfg == null || !cfg.enabled) ANTES de cualquier resolución de
 * runtime, descarga, o configuración de proxy. Se pasa client=null a propósito:
 * el único uso de `client` dentro de esa rama (client.sendLog(...)) ya está
 * envuelto en try/catch dentro del propio método, así que un NPE ahí se atrapa en
 * silencio y el método igual retorna Session.INACTIVE — si esto no fuera cierto,
 * este test lanzaría NullPointerException en vez de pasar.
 */
@DisplayName("NetworkMonitoringManager.start() con la bandera desactivada")
class NetworkMonitoringManagerDisabledTest {

    @Test
    @DisplayName("1. enabled=false -> Session.INACTIVE, sin tocar proxy/runtime/CA")
    void disabled_returnsInactiveImmediately(@TempDir Path tmp) {
        NetworkMonitoringConfig cfg = new NetworkMonitoringConfig();
        cfg.enabled = false;

        NetworkMonitoringManager.Session session = NetworkMonitoringManager.start(
                null, "TEST-EXEC", cfg, tmp, "UDID-TEST", false, tmp.resolve("evidence"));

        assertSame(NetworkMonitoringManager.Session.INACTIVE, session);
        assertFalse(session.active());
    }

    @Test
    @DisplayName("2. cfg=null (equivalente a deshabilitado) -> Session.INACTIVE, mismo camino")
    void nullConfig_returnsInactiveImmediately(@TempDir Path tmp) {
        NetworkMonitoringManager.Session session = NetworkMonitoringManager.start(
                null, "TEST-EXEC", null, tmp, "UDID-TEST", false, tmp.resolve("evidence"));

        assertSame(NetworkMonitoringManager.Session.INACTIVE, session);
        assertFalse(session.active());
    }

    @Test
    @DisplayName("3. Con la bandera desactivada, ningún error de proxy puede bloquear la "
            + "ejecución — el método retorna de inmediato sin lanzar excepción, incluso con "
            + "un agentDataDir/evidenceRootDir inexistentes")
    void disabled_neverThrowsRegardlessOfFilesystemState() {
        NetworkMonitoringConfig cfg = new NetworkMonitoringConfig();
        cfg.enabled = false;
        Path rutaInexistente = Path.of("/ruta/que/no/existe/en/este/disco");

        assertDoesNotThrow(() -> {
            NetworkMonitoringManager.Session session = NetworkMonitoringManager.start(
                    null, "TEST-EXEC", cfg, rutaInexistente, "UDID-TEST", true,
                    rutaInexistente.resolve("evidence"));
            assertFalse(session.active());
        });
    }
}
