package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 28 — tests puros (sin hardware) de
 * {@link CoreDeviceTunnelManager.DeviceConnectionState#isReadyForAppium()} tras dejar
 * de depender de {@code xctraceVisible} como gate y delegar en
 * {@link CoreDeviceTunnelManager#isConnectedForAppium} (mismo criterio ya validado en
 * TAREA 27 para {@code IOSRunnerReadinessEngine}).
 *
 * Complementa a {@code CoreDeviceTunnelManagerConnectedForAppiumTest} (TAREA 27, que
 * prueba {@code isConnectedForAppium()} en aislamiento) agregando el chequeo de
 * {@code pairingState}, que {@code isConnectedForAppium()} deliberadamente NO evalúa
 * (queda a cargo de cada llamador, sin cambios en esta tarea).
 */
@DisplayName("DeviceConnectionState.isReadyForAppium (TAREA 28)")
class DeviceConnectionStateReadyForAppiumTest {

    private static CoreDeviceTunnelManager.DeviceConnectionState state(
            DevicectlParser.TransportType transport, String tunnelState,
            String pairingState, boolean xctraceVisible) {
        return new CoreDeviceTunnelManager.DeviceConnectionState(
                "COREDEVICE-UUID", "00008110-000129261482601E",
                tunnelState, pairingState, xctraceVisible, transport);
    }

    @Test
    @DisplayName("1. WIRED + paired + devicectl usable (xctrace también visible, caso normal) -> ready")
    void wired_normalCase_isReady() {
        assertTrue(state(DevicectlParser.TransportType.WIRED, "connected", "paired", true)
                .isReadyForAppium());
    }

    @Test
    @DisplayName("2. WIRED + paired + xctrace stale/ausente (evidencia real 2026-09-10: devicectl "
            + "'available (paired)' continuo, xctrace ausente incluso tras ~67s de recovery activo "
            + "completo) -> SIGUE ready, sin depender de xctrace — ya no produce falso NOT_READY")
    void wired_xctraceStale_stillReady() {
        assertTrue(state(DevicectlParser.TransportType.WIRED, "disconnected", "paired", false)
                .isReadyForAppium(),
                "USB (WIRED) + paired nunca debe depender de xctraceVisible ni de tunnelState");
    }

    @Test
    @DisplayName("3. Dispositivo realmente sin transporte identificado (UNKNOWN) -> NOT ready — "
            + "la recuperación de ensureTunnelConnected() sigue siendo posible/necesaria")
    void unknownTransport_notReady() {
        assertFalse(state(DevicectlParser.TransportType.UNKNOWN, "unknown", "paired", true)
                .isReadyForAppium(),
                "UNKNOWN transport nunca debe considerarse ready, ni siquiera con xctraceVisible=true "
                + "(esto además corrige una inconsistencia previa: antes UNKNOWN+xctraceVisible=true SÍ "
                + "se consideraba ready aquí, aunque IosPreflightManager.runPreflight() ya lo trataba "
                + "como no-listo por separado)");
    }

    @Test
    @DisplayName("4. LOCAL_NETWORK conectado + paired -> ready")
    void localNetwork_connected_isReady() {
        assertTrue(state(DevicectlParser.TransportType.LOCAL_NETWORK, "connected", "paired", true)
                .isReadyForAppium());
    }

    @Test
    @DisplayName("5. LOCAL_NETWORK desconectado -> NOT ready — recuperación (devicectl connection "
            + "connect) sigue disparándose correctamente")
    void localNetwork_disconnected_notReady() {
        assertFalse(state(DevicectlParser.TransportType.LOCAL_NETWORK, "disconnected", "paired", true)
                .isReadyForAppium());
    }

    @Test
    @DisplayName("6. UNKNOWN no se falsifica como CONNECTED aunque xctrace lo vea")
    void unknown_neverFalsifiedAsConnectedEvenWithXctrace() {
        assertFalse(state(DevicectlParser.TransportType.UNKNOWN, "connected", "paired", true)
                .isReadyForAppium());
    }

    @Test
    @DisplayName("Pairing no confiado (unpaired) sigue bloqueando incluso con transporte/túnel OK — "
            + "condición adicional preservada sin cambios (Opción B/A híbrida: se conserva pairingState)")
    void unpaired_stillNotReady_evenWithGoodTransport() {
        assertFalse(state(DevicectlParser.TransportType.WIRED, "connected", "unpaired", true)
                .isReadyForAppium());
        assertFalse(state(DevicectlParser.TransportType.LOCAL_NETWORK, "connected", "unpaired", true)
                .isReadyForAppium());
    }
}
