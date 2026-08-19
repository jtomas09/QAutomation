package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que el resolver cae a WDA hoy en TODOS los casos (CoreDeviceMirrorProvider
 * siempre reporta "no disponible" — ver su javadoc) sin necesitar un dispositivo
 * físico ni un túnel real. Usa el FakeProvider ya existente en
 * FallbackChainProviderTest como sustituto de WDA.
 */
@DisplayName("IOSMirrorProviderResolver")
class IOSMirrorProviderResolverTest {

    @Test
    @DisplayName("sin CoreDeviceMirrorProvider funcional, siempre cae a WDA")
    void alwaysFallsBackToWdaToday() {
        FallbackChainProviderTest.FakeProvider wda = new FallbackChainProviderTest.FakeProvider("WDA", true, true);
        IOSMirrorProviderResolver resolver = new IOSMirrorProviderResolver(new CoreDeviceMirrorProvider(), wda);

        assertTrue(resolver.start("udid-sin-version-conocida"));
        assertEquals("WDA", resolver.name());
        assertEquals(1, wda.startCalls.get());
    }

    @Test
    @DisplayName("isSupported() es true si WDA está soportado, aunque CoreDevice no lo esté en este SO")
    void isSupportedReflectsWda() {
        FallbackChainProviderTest.FakeProvider wda = new FallbackChainProviderTest.FakeProvider("WDA", true, true);
        IOSMirrorProviderResolver resolver = new IOSMirrorProviderResolver(new CoreDeviceMirrorProvider(), wda);

        assertTrue(resolver.isSupported());
    }

    @Test
    @DisplayName("captureFrame()/stop() delegan al ganador (WDA) tras start()")
    void captureAndStopDelegateToWda() {
        FallbackChainProviderTest.FakeProvider wda = new FallbackChainProviderTest.FakeProvider("WDA", true, true);
        IOSMirrorProviderResolver resolver = new IOSMirrorProviderResolver(new CoreDeviceMirrorProvider(), wda);

        resolver.start("udid-1");
        assertArrayEquals(wda.frame, resolver.captureFrame("udid-1"));

        resolver.stop("udid-1");
        assertEquals(1, wda.stopCalls.get());
    }

    @Test
    @DisplayName("si WDA tampoco arranca, start() devuelve false")
    void returnsFalseIfWdaAlsoFails() {
        FallbackChainProviderTest.FakeProvider wda = new FallbackChainProviderTest.FakeProvider("WDA", true, false);
        IOSMirrorProviderResolver resolver = new IOSMirrorProviderResolver(new CoreDeviceMirrorProvider(), wda);

        assertFalse(resolver.start("udid-1"));
    }
}
