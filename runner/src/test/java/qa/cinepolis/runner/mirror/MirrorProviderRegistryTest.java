package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica la clasificación iOS-vs-Android por forma de UDID — la única
 * señal que usa MirrorProviderRegistry para decidir qué cadena de providers
 * (iOS o Android) atiende un dispositivo. Casos concretos reales para
 * descartar falsos positivos con seriales de emuladores/dispositivos Android.
 */
@DisplayName("MirrorProviderRegistry.isIosUdid")
class MirrorProviderRegistryTest {

    @Test
    @DisplayName("UDID iOS moderno (Xcode 15+): 8 hex - 16 hex")
    void modernIosUdidIsRecognized() {
        assertTrue(MirrorProviderRegistry.isIosUdid("00008030-001A2D9E0EA1802E"));
        assertTrue(MirrorProviderRegistry.isIosUdid("00008101-000C550A3C29001E"));
    }

    @Test
    @DisplayName("LIMITACIÓN CONOCIDA: formato UUID clásico (8-4-4-4-12) NO coincide con ningún patrón")
    void classicUuidFormatDoesNotMatchEitherPattern() {
        // Ver IosVersionGuardTest — el ejemplo real de ahí usa este formato con guiones
        // (8-4-4-4-12). Ni MODERN_IOS_UDID (un solo guion, 8+16 hex) ni LEGACY_IOS_UDID
        // (40 hex sin guiones) lo reconocen. Si algún dispositivo real reporta su udid
        // en ESTE formato específico como identificador de mirror, isIosUdid() lo
        // clasificaría (incorrectamente) como Android. No confirmado que esto ocurra
        // en producción — el síntoma "Verificando WDA" ya observado con un dispositivo
        // real indica que, al menos en ese caso, el udid SÍ llegó en formato moderno.
        String classicUuid = "554E89EA-E69D-54EE-9877-B26F70061A0A";
        assertFalse(MirrorProviderRegistry.isIosUdid(classicUuid),
                "documenta el comportamiento actual — no es una aserción de corrección");
    }

    @Test
    @DisplayName("UDID iOS legado: 40 caracteres hex sin separador")
    void legacyIosUdidIsRecognized() {
        assertTrue(MirrorProviderRegistry.isIosUdid("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"));
    }

    @Test
    @DisplayName("seriales típicos de emuladores Android NO se clasifican como iOS")
    void androidEmulatorSerialsAreNotMisclassified() {
        assertFalse(MirrorProviderRegistry.isIosUdid("emulator-5554"));
        assertFalse(MirrorProviderRegistry.isIosUdid("127.0.0.1:5555"));
        assertFalse(MirrorProviderRegistry.isIosUdid("192.168.56.101:5555"));
    }

    @Test
    @DisplayName("seriales típicos de dispositivos Android físicos NO se clasifican como iOS")
    void physicalAndroidSerialsAreNotMisclassified() {
        // Seriales reales de fabricantes: alfanuméricos con letras fuera de a-f, o longitud distinta.
        assertFalse(MirrorProviderRegistry.isIosUdid("R58M123ABCD"));       // Samsung, típico
        assertFalse(MirrorProviderRegistry.isIosUdid("HT79K1A00123"));      // HTC/otros
        assertFalse(MirrorProviderRegistry.isIosUdid("ZY223CFKQR"));        // corto, no-hex
    }

    @Test
    @DisplayName("null y vacío nunca se clasifican como iOS")
    void nullAndEmptyAreNeverIos() {
        assertFalse(MirrorProviderRegistry.isIosUdid(null));
        assertFalse(MirrorProviderRegistry.isIosUdid(""));
    }

    @Test
    @DisplayName("un serial Android puramente hexadecimal de 40 caracteres SÍ se clasificaría como iOS (limitación conocida)")
    void fortyCharHexAndroidSerialWouldBeMisclassified() {
        // Documenta una limitación real y ya conocida de este heurístico (ver
        // investigación previa): es geométricamente improbable pero no imposible
        // que un serial Android sea puro hex de exactamente 40 u 8-16 caracteres.
        String pureHex40 = "0123456789abcdef0123456789abcdef01234567";
        assertTrue(MirrorProviderRegistry.isIosUdid(pureHex40),
                "limitación documentada: un serial Android puramente hex de 40 chars se confundiría con iOS legado");
    }
}
