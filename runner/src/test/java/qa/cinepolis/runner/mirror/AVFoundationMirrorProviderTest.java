package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica la correlación UDID → índice de captura AVFoundation — en
 * particular el escenario que más preocupa con múltiples dispositivos
 * simultáneos: NUNCA cruzar índices entre dos iPhones, incluso si sus
 * nombres son ambiguos o idénticos.
 */
@DisplayName("AVFoundationMirrorProvider.matchDeviceIndex")
class AVFoundationMirrorProviderTest {

    @Test
    @DisplayName("un único candidato con nombre único se resuelve correctamente")
    void singleUniqueNameResolves() {
        Map<Integer, String> devices = new LinkedHashMap<>();
        devices.put(0, "FaceTime HD Camera");
        devices.put(1, "Jairo's iPhone");

        Integer index = AVFoundationMirrorProvider.matchDeviceIndex("udid-1", "Jairo's iPhone", devices);
        assertEquals(1, index);
    }

    @Test
    @DisplayName("CRÍTICO — dos iPhones con el MISMO nombre de fábrica nunca se cruzan: ambos devuelven null")
    void twoIphonesWithSameNameNeverCrossWires() {
        Map<Integer, String> devices = new LinkedHashMap<>();
        devices.put(0, "FaceTime HD Camera");
        devices.put(1, "iPhone");
        devices.put(2, "iPhone");

        // Ambos UDIDs comparten el mismo nombre amigable "iPhone" (caso real:
        // dispositivos de prueba sin nombre personalizado) — ninguno debe
        // resolver a un índice arbitrario, porque eso mezclaría frames de un
        // dispositivo con el UDID de otro.
        assertNull(AVFoundationMirrorProvider.matchDeviceIndex("udid-A", "iPhone", devices));
        assertNull(AVFoundationMirrorProvider.matchDeviceIndex("udid-B", "iPhone", devices));
    }

    @Test
    @DisplayName("tres iPhones con nombres distintos se resuelven cada uno a su propio índice")
    void threeIphonesWithDistinctNamesEachResolveIndependently() {
        Map<Integer, String> devices = new LinkedHashMap<>();
        devices.put(0, "FaceTime HD Camera");
        devices.put(1, "Jairo's iPhone");
        devices.put(2, "QA Device 2");
        devices.put(3, "iPhone de Tester");

        assertEquals(1, AVFoundationMirrorProvider.matchDeviceIndex("udid-jairo", "Jairo's iPhone", devices));
        assertEquals(2, AVFoundationMirrorProvider.matchDeviceIndex("udid-qa2", "QA Device 2", devices));
        assertEquals(3, AVFoundationMirrorProvider.matchDeviceIndex("udid-tester", "iPhone de Tester", devices));
    }

    @Test
    @DisplayName("sin nombre amigable y con un único candidato no-built-in, se usa como último recurso")
    void noNameButSingleNonBuiltInCandidateIsUsedAsLastResort() {
        Map<Integer, String> devices = new LinkedHashMap<>();
        devices.put(0, "FaceTime HD Camera");
        devices.put(1, "Capture screen 0");
        devices.put(2, "Unknown Device");

        assertEquals(2, AVFoundationMirrorProvider.matchDeviceIndex("udid-1", null, devices));
    }

    @Test
    @DisplayName("sin nombre amigable y con VARIOS candidatos no-built-in, no se adivina (null)")
    void noNameAndMultipleCandidatesNeverGuesses() {
        Map<Integer, String> devices = new LinkedHashMap<>();
        devices.put(0, "FaceTime HD Camera");
        devices.put(1, "iPhone A");
        devices.put(2, "iPhone B");

        assertNull(AVFoundationMirrorProvider.matchDeviceIndex("udid-1", null, devices));
        assertNull(AVFoundationMirrorProvider.matchDeviceIndex("udid-1", "", devices));
    }

    @Test
    @DisplayName("lista de dispositivos vacía siempre devuelve null")
    void emptyDeviceListReturnsNull() {
        assertNull(AVFoundationMirrorProvider.matchDeviceIndex("udid-1", "Jairo's iPhone", Map.of()));
    }
}
