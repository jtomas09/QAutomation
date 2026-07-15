package config;

import org.slf4j.Logger;

/**
 * Revalida el estado del dispositivo iOS INMEDIATAMENTE antes de crear IOSDriver.
 *
 * Problema que resuelve:
 *   IOSDeviceState.fromRunnerProps() es una foto tomada minutos/segundos antes,
 *   dentro de IosPreflightManager.runPreflight() (proceso del Runner). Entre ese
 *   instante y la creación real de IOSDriver (arranque de Gradle sin daemon, boot
 *   de JVM, JUnit discovery — ver DriverFactory) el transporte CoreDevice puede
 *   cambiar (WIRED → LOCAL_NETWORK) sin que ningún componente de este repo lo
 *   cause: CoreDeviceTunnelManager ya documenta que el Runner solo OBSERVA el
 *   tunnel, nunca lo posee ni lo destruye. Appium, sin embargo, puede fallar al
 *   construir su port forwarder RemoteXPC si el transporte cambió
 *   ("Cannot create port forwarder via RemoteXPC tunnel").
 *
 * Qué hace esta clase:
 *   1. Ejecuta UNA consulta ligera y fresca — reutiliza
 *      {@link IOSDeviceStateService#refresh}, la misma consulta xctrace+devicectl
 *      ya usada por IOSDeviceSynchronizationManager. NO repite el Pre-flight
 *      completo (no hay selección de Team, no hay validación de caché de WDA,
 *      no hay polling de recuperación, no hay killall).
 *   2. Compara la foto fresca contra el snapshot original del Runner.
 *   3. Registra explícitamente cualquier diferencia (transporte, tunnel, pairing,
 *      xctrace, CoreDevice) — NUNCA aborta por una diferencia. transportType
 *      sigue sin ser un gate bloqueante, exactamente como CoreDeviceTunnelManager
 *      ya establece para el lado del Runner.
 *   4. Devuelve un IOSDeviceState actualizado (mismo objeto salvo los campos de
 *      hardware/transporte) para que DriverFactory lo use de aquí en adelante,
 *      incluida la clasificación de fallos si Appium igual rechaza la sesión.
 *
 * Separación de responsabilidades:
 *   - IOSDeviceStateService: única fuente de subprocesos xctrace/devicectl + caché.
 *   - IOSDeviceState: objeto de valor inmutable (snapshot).
 *   - IOSPreSessionRevalidator (esta clase): política que combina ambos para un
 *     único propósito — revalidar justo antes de crear la sesión Appium.
 *   No duplica ninguna lógica de subprocess ni de parsing — todo delega en
 *   IOSDeviceStateService/DevicectlParser, ya existentes.
 */
public final class IOSPreSessionRevalidator {

    private IOSPreSessionRevalidator() {}

    /**
     * @param snapshot snapshot original del Runner (IOSDeviceState.fromRunnerProps())
     * @param udid     UDID físico del dispositivo
     * @param log      logger del llamador (DriverFactory)
     * @return snapshot actualizado con el estado de hardware/transporte más reciente;
     *         el snapshot original sin cambios si udid es nulo/vacío.
     */
    public static IOSDeviceState revalidate(IOSDeviceState snapshot, String udid, Logger log) {
        if (udid == null || udid.isBlank()) return snapshot;

        log.info("[PreSessionRevalidator] Revalidando estado del dispositivo justo antes de IOSDriver...");
        IOSDeviceStateService.DeviceState fresh = IOSDeviceStateService.refresh(udid, log);

        logDrift(snapshot, fresh, log);

        return snapshot.withFreshHardwareState(fresh);
    }

    /**
     * Registra, sin abortar, exactamente qué cambió entre el snapshot del Runner y el
     * estado recién consultado. Formato deliberadamente explícito (antes → después) para
     * que quede evidencia directa en los logs cuando Appium falle más adelante.
     */
    private static void logDrift(IOSDeviceState before, IOSDeviceStateService.DeviceState after, Logger log) {
        StringBuilder drift = new StringBuilder();

        String beforeTransport = before.transportType.isBlank() ? "UNKNOWN" : before.transportType;
        String afterTransport  = after.transportType.isBlank()  ? "UNKNOWN" : after.transportType;
        if (!beforeTransport.equalsIgnoreCase(afterTransport)) {
            drift.append("\n   Transport  : ").append(beforeTransport).append(" → ").append(afterTransport);
        }

        boolean afterTunnelConnected = "connected".equalsIgnoreCase(after.tunnelState);
        if (before.tunnelConnected != afterTunnelConnected) {
            drift.append("\n   Tunnel     : ").append(before.tunnelConnected ? "connected" : "disconnected")
                 .append(" → ").append(afterTunnelConnected ? "connected" : "disconnected");
        }

        boolean afterPaired = !"unpaired".equalsIgnoreCase(after.pairingState);
        if (before.paired != afterPaired) {
            drift.append("\n   Pairing    : ").append(before.paired ? "paired" : "unpaired")
                 .append(" → ").append(after.pairingState);
        }

        if (before.xctraceVisible != after.xctraceVisible) {
            drift.append("\n   xctrace    : ").append(before.xctraceVisible ? "visible" : "no visible")
                 .append(" → ").append(after.xctraceVisible ? "visible" : "no visible");
        }

        if (before.coreDeviceVisible != after.coreDeviceVisible) {
            drift.append("\n   CoreDevice : ").append(before.coreDeviceVisible ? "visible" : "no visible")
                 .append(" → ").append(after.coreDeviceVisible ? "visible" : "no visible");
        }

        if (drift.length() == 0) {
            log.info("[PreSessionRevalidator] ✅ Sin cambios respecto al snapshot del Runner ({}s de antigüedad).",
                    before.ageSeconds());
        } else {
            log.warn("[PreSessionRevalidator] ⚠ Runner Snapshot difiere del estado actual "
                    + "(no bloqueante — transportType nunca es un gate):{}", drift);
        }
    }
}
