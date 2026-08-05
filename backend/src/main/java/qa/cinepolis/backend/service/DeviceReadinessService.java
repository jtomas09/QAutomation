package qa.cinepolis.backend.service;

import org.springframework.stereotype.Service;
import qa.cinepolis.backend.model.Device;
import qa.cinepolis.backend.store.DeviceStore;

/**
 * Único punto de verdad para "¿este dispositivo está listo para ejecutar ahora
 * mismo?" — usado tanto por RunController (antes de encolar una Execution) como
 * por JobController (antes de despachar un Job al Runner). READY == que exista
 * un Device en el inventario cuyo status sea AVAILABLE (mismo criterio de
 * matching de 3 niveles que ya usaba claimDevice(), ver
 * DeviceStore.findAvailableCandidate()) — no se introduce un nuevo valor de
 * enum DeviceStatus para minimizar el radio de cambio: BUSY/OFFLINE/
 * MAINTENANCE/DISCOVERED ya significan "no ejecutable" tal como están.
 */
@Service
public class DeviceReadinessService {

    private final DeviceStore deviceStore;

    public DeviceReadinessService(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    /** true solo si existe un dispositivo AVAILABLE que matchee udidOrName. */
    public boolean isReady(String udidOrName) {
        return deviceStore.findAvailableCandidate(udidOrName).isPresent();
    }

    /**
     * Motivo legible para el usuario cuando isReady() es false. Se basa en el
     * match EXACTO de UDID (findByUdid) para dar un mensaje específico; si el
     * UDID ni siquiera está registrado, se devuelve un mensaje genérico.
     */
    public String notReadyReason(String udidOrName) {
        return deviceStore.findByUdid(udidOrName)
                .map(Device::getStatus)
                .map(status -> switch (status) {
                    case OFFLINE     -> "Dispositivo desconectado.";
                    case BUSY        -> "Dispositivo ocupado en otra ejecución.";
                    case MAINTENANCE -> "Dispositivo en mantenimiento.";
                    case DISCOVERED  -> "Dispositivo detectado pero aún no confirmado (sin túnel activo).";
                    case AVAILABLE   -> null; // no debería llamarse en este caso
                })
                .orElse("Dispositivo no encontrado en el inventario.");
    }
}
