package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 27 — tests puros (sin hardware) de {@link CoreDeviceTunnelManager#isConnectedForAppium}.
 *
 * Esta es la ÚNICA pieza nueva de lógica de esta tarea — extraída, sin cambiar ni una
 * condición, del if/else que ya vivía (y sigue viviendo, ahora delegando aquí) en
 * {@code IosPreflightManager.runPreflight()}. Cubre los escenarios 1-3 de la Fase 6
 * de forma determinista, construyendo {@link CoreDeviceTunnelManager.DeviceConnectionState}
 * directamente (constructor de paquete, sin necesidad de xcrun/devicectl/xctrace reales).
 *
 * Los escenarios 5-14 (semántica de status para cada IOSWdaErrorCode) NO cambiaron en
 * esta tarea — el único punto tocado en IOSRunnerReadinessEngine.evaluate() es el
 * cálculo de "connected", ANTES de cualquier lógica de terminalError/clasificación.
 * Ya están cubiertos por IOSRunnerReadinessEngineRecoveringSemanticsTest (TAREA 6/10)
 * e IOSAccountSessionRequiredTest (TAREA 26A) — ver el entregable de esta tarea para
 * la comparación exacta antes/después de esos tests contra hardware real.
 */
@DisplayName("CoreDeviceTunnelManager.isConnectedForAppium (TAREA 27)")
class CoreDeviceTunnelManagerConnectedForAppiumTest {

    private static CoreDeviceTunnelManager.DeviceConnectionState state(
            DevicectlParser.TransportType transport, String tunnelState, boolean xctraceVisible) {
        return new CoreDeviceTunnelManager.DeviceConnectionState(
                "COREDEVICE-UUID", "00008110-000129261482601E",
                tunnelState, "paired", xctraceVisible, transport);
    }

    @Test
    @DisplayName("1a. WIRED + xctraceVisible=true (caso normal) -> conectado")
    void wired_normalCase_isConnected() {
        assertTrue(CoreDeviceTunnelManager.isConnectedForAppium(
                state(DevicectlParser.TransportType.WIRED, "connected", true)));
    }

    @Test
    @DisplayName("1b/2. WIRED + tunnelState=disconnected + xctraceVisible=false (evidencia real "
            + "2026-09-10: devicectl 'available (paired)' mientras xctrace no listaba el dispositivo, "
            + "incluso tras recuperación activa completa) -> SIGUE conectado — ya no produce falso OFFLINE")
    void wired_xctraceStale_stillConnected() {
        assertTrue(CoreDeviceTunnelManager.isConnectedForAppium(
                state(DevicectlParser.TransportType.WIRED, "disconnected", false)),
                "USB (WIRED) nunca debe depender de xctraceVisible ni de tunnelState — mismo criterio "
                + "ya usado por IosPreflightManager.runPreflight() antes de esta tarea");
    }

    @Test
    @DisplayName("LOCAL_NETWORK + tunnel connected -> conectado")
    void localNetwork_tunnelConnected_isConnected() {
        assertTrue(CoreDeviceTunnelManager.isConnectedForAppium(
                state(DevicectlParser.TransportType.LOCAL_NETWORK, "connected", true)));
    }

    @Test
    @DisplayName("LOCAL_NETWORK + tunnel connected pero xctraceVisible=false -> SIGUE conectado "
            + "(WiFi tampoco depende de xctrace, solo de tunnelState — sin cambios respecto a antes)")
    void localNetwork_tunnelConnectedXctraceStale_stillConnected() {
        assertTrue(CoreDeviceTunnelManager.isConnectedForAppium(
                state(DevicectlParser.TransportType.LOCAL_NETWORK, "connected", false)));
    }

    @Test
    @DisplayName("3a. LOCAL_NETWORK + tunnel disconnected -> NO conectado (túnel genuinamente caído)")
    void localNetwork_tunnelDisconnected_notConnected() {
        assertFalse(CoreDeviceTunnelManager.isConnectedForAppium(
                state(DevicectlParser.TransportType.LOCAL_NETWORK, "disconnected", true)));
    }

    @Test
    @DisplayName("3b. UNKNOWN transport -> NO conectado (dispositivo realmente sin identificar)")
    void unknownTransport_notConnected() {
        assertFalse(CoreDeviceTunnelManager.isConnectedForAppium(
                state(DevicectlParser.TransportType.UNKNOWN, "unknown", true)));
    }

    @Test
    @DisplayName("3c. Estado nulo (devicectl sin datos en absoluto) -> NO conectado")
    void nullState_notConnected() {
        assertFalse(CoreDeviceTunnelManager.isConnectedForAppium(null));
    }
}
