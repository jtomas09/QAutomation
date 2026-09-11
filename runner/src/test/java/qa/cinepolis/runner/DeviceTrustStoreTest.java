package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DeviceTrustStore")
class DeviceTrustStoreTest {

    @Test
    @DisplayName("Dispositivo nuevo nunca está TRUSTED")
    void newDeviceIsNotTrusted(@TempDir Path tmp) {
        DeviceTrustStore store = new DeviceTrustStore(tmp);
        assertFalse(store.isTrusted("UDID-1", "AA:BB:CC"));
    }

    @Test
    @DisplayName("markTrusted + isTrusted con el mismo fingerprint -> true")
    void markTrustedThenIsTrusted(@TempDir Path tmp) {
        DeviceTrustStore store = new DeviceTrustStore(tmp);
        store.markTrusted("UDID-1", "AA:BB:CC");
        assertTrue(store.isTrusted("UDID-1", "AA:BB:CC"));
    }

    @Test
    @DisplayName("El estado persiste entre instancias (mismo agentDataDir)")
    void persistsAcrossInstances(@TempDir Path tmp) {
        new DeviceTrustStore(tmp).markTrusted("UDID-1", "AA:BB:CC");

        DeviceTrustStore reloaded = new DeviceTrustStore(tmp);
        assertTrue(reloaded.isTrusted("UDID-1", "AA:BB:CC"));
    }

    @Test
    @DisplayName("Si el fingerprint de CA cambia, el dispositivo deja de estar TRUSTED")
    void caRotationInvalidatesTrust(@TempDir Path tmp) {
        DeviceTrustStore store = new DeviceTrustStore(tmp);
        store.markTrusted("UDID-1", "AA:BB:CC");

        assertFalse(store.isTrusted("UDID-1", "DD:EE:FF"),
                "Un fingerprint de CA distinto (CA regenerada/rotada) nunca debe considerarse confiado");
    }

    @Test
    @DisplayName("Confirmar contra la CA nueva vuelve a marcar TRUSTED")
    void reconfirmingAfterRotationWorks(@TempDir Path tmp) {
        DeviceTrustStore store = new DeviceTrustStore(tmp);
        store.markTrusted("UDID-1", "AA:BB:CC");
        assertFalse(store.isTrusted("UDID-1", "DD:EE:FF"));

        store.markTrusted("UDID-1", "DD:EE:FF");
        assertTrue(store.isTrusted("UDID-1", "DD:EE:FF"));
    }

    @Test
    @DisplayName("Dos dispositivos distintos mantienen estados independientes")
    void multipleDevicesAreIndependent(@TempDir Path tmp) {
        DeviceTrustStore store = new DeviceTrustStore(tmp);
        store.markTrusted("UDID-1", "AA:BB:CC");
        store.markNotConfigured("UDID-2");

        assertTrue(store.isTrusted("UDID-1", "AA:BB:CC"));
        assertFalse(store.isTrusted("UDID-2", "AA:BB:CC"));
    }

    @Test
    @DisplayName("UDID null/blank nunca está trusted y no lanza")
    void nullOrBlankUdidIsSafe(@TempDir Path tmp) {
        DeviceTrustStore store = new DeviceTrustStore(tmp);
        assertFalse(store.isTrusted(null, "AA:BB:CC"));
        assertFalse(store.isTrusted("", "AA:BB:CC"));
        store.markTrusted(null, "AA:BB:CC"); // no debe lanzar
        store.markTrusted("", "AA:BB:CC");   // no debe lanzar
    }
}
