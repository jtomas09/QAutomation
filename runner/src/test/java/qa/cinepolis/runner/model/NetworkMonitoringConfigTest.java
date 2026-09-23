package qa.cinepolis.runner.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — hacer opcional la captura de tráfico vía Dashboard.
 *
 * Pruebas puras (sin red, sin proceso mitmproxy) de withEnabled() — el mecanismo
 * que permite que JobExecutor use el valor FRESCO del Dashboard (obtenido en cada
 * Job vía GET /api/runner/config) sin mutar el objeto NetworkMonitoringConfig
 * compartido, que vive congelado durante toda la vida del proceso RunnerAgent.
 */
@DisplayName("NetworkMonitoringConfig.withEnabled()")
class NetworkMonitoringConfigTest {

    private static NetworkMonitoringConfig unaConfigCompleta() {
        NetworkMonitoringConfig c = new NetworkMonitoringConfig();
        c.enabled = true;
        c.captureRequestBody = true;
        c.captureResponseBody = false;
        c.maxResponseBodySize = 12345L;
        c.attachToAllure = true;
        c.saveAllTraffic = false;
        c.saveErrors = true;
        c.redactSensitiveData = true;
        return c;
    }

    @Test
    @DisplayName("1. withEnabled(false) cambia SOLO enabled, preserva el resto de los campos")
    void withEnabledFalse_changesOnlyEnabled() {
        NetworkMonitoringConfig original = unaConfigCompleta();
        NetworkMonitoringConfig copia = original.withEnabled(false);

        assertFalse(copia.enabled);
        assertEquals(original.captureRequestBody, copia.captureRequestBody);
        assertEquals(original.captureResponseBody, copia.captureResponseBody);
        assertEquals(original.maxResponseBodySize, copia.maxResponseBodySize);
        assertEquals(original.attachToAllure, copia.attachToAllure);
        assertEquals(original.saveAllTraffic, copia.saveAllTraffic);
        assertEquals(original.saveErrors, copia.saveErrors);
        assertEquals(original.redactSensitiveData, copia.redactSensitiveData);
    }

    @Test
    @DisplayName("2. withEnabled(true) cambia SOLO enabled, preserva el resto de los campos")
    void withEnabledTrue_changesOnlyEnabled() {
        NetworkMonitoringConfig original = unaConfigCompleta();
        original.enabled = false;
        NetworkMonitoringConfig copia = original.withEnabled(true);

        assertTrue(copia.enabled);
        assertEquals(original.captureRequestBody, copia.captureRequestBody);
        assertEquals(original.maxResponseBodySize, copia.maxResponseBodySize);
    }

    @Test
    @DisplayName("3. withEnabled() nunca muta el objeto original, y un cambio posterior al "
            + "original no afecta una copia ya tomada (snapshot por ejecución)")
    void withEnabled_neverMutatesOriginal() {
        NetworkMonitoringConfig original = unaConfigCompleta();
        original.enabled = false;

        NetworkMonitoringConfig copia = original.withEnabled(true);
        assertTrue(copia.enabled);
        assertFalse(original.enabled, "pedir una copia con otro valor no debe alterar el original");

        // Simula el Dashboard cambiando el toggle DESPUÉS de que una ejecución ya
        // tomó su snapshot (config.networkMonitoring.withEnabled(...) en
        // JobExecutor) — esa ejecución en curso no debe verse afectada.
        original.enabled = true;
        assertTrue(copia.enabled, "la copia ya tomada no debe verse afectada por cambios posteriores al original");
    }
}
