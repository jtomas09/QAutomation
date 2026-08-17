package qa.cinepolis.runner.mirror;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica la lógica de fallback por prioridad SIN subprocesos reales
 * (ffmpeg/scrcpy/ADB) — usa providers falsos controlables para probar el
 * comportamiento determinista del encadenamiento en sí.
 */
@DisplayName("FallbackChainProvider")
class FallbackChainProviderTest {

    /** Provider falso completamente controlable desde el test. */
    static final class FakeProvider implements DeviceMirrorProvider {
        final String  fakeName;
        final boolean supported;
        final boolean startsSuccessfully;
        final AtomicInteger startCalls = new AtomicInteger();
        final AtomicInteger stopCalls  = new AtomicInteger();
        byte[] frame = "fake-frame".getBytes();

        FakeProvider(String name, boolean supported, boolean startsSuccessfully) {
            this.fakeName = name;
            this.supported = supported;
            this.startsSuccessfully = startsSuccessfully;
        }

        @Override public String name() { return fakeName; }
        @Override public boolean isSupported() { return supported; }
        @Override public boolean isDeviceConnected(String udid) { return true; }
        @Override public boolean start(String udid) { startCalls.incrementAndGet(); return startsSuccessfully; }
        @Override public void stop(String udid) { stopCalls.incrementAndGet(); }
        @Override public byte[] captureFrame(String udid) { return frame; }
    }

    @Test
    @DisplayName("el primer candidato soportado y que arranca exitosamente gana la sesión")
    void firstSupportedAndStartableWins() {
        FakeProvider primary   = new FakeProvider("PRIMARY", true, true);
        FakeProvider secondary = new FakeProvider("SECONDARY", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(primary, secondary));

        assertTrue(chain.start("udid-1"));
        assertEquals("PRIMARY", chain.name());
        assertEquals(1, primary.startCalls.get());
        assertEquals(0, secondary.startCalls.get(), "el segundo candidato nunca debería intentarse si el primero ganó");
    }

    @Test
    @DisplayName("si el primer candidato falla al arrancar, cae al segundo")
    void fallsBackToSecondWhenFirstFailsToStart() {
        FakeProvider primary   = new FakeProvider("PRIMARY", true, false); // soportado pero start() falla
        FakeProvider secondary = new FakeProvider("SECONDARY", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(primary, secondary));

        assertTrue(chain.start("udid-1"));
        assertEquals("SECONDARY", chain.name());
        assertEquals(1, primary.startCalls.get());
        assertEquals(1, secondary.startCalls.get());
    }

    @Test
    @DisplayName("un candidato no soportado (isSupported=false) nunca se intenta arrancar")
    void unsupportedCandidateIsSkippedEntirely() {
        FakeProvider unsupported = new FakeProvider("UNSUPPORTED", false, true);
        FakeProvider fallback    = new FakeProvider("FALLBACK", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(unsupported, fallback));

        assertTrue(chain.start("udid-1"));
        assertEquals("FALLBACK", chain.name());
        assertEquals(0, unsupported.startCalls.get(), "isSupported()=false nunca debe generar una llamada a start()");
    }

    @Test
    @DisplayName("si ningún candidato arranca, start() devuelve false")
    void allCandidatesFailingReturnsFalse() {
        FakeProvider a = new FakeProvider("A", true, false);
        FakeProvider b = new FakeProvider("B", true, false);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(a, b));

        assertFalse(chain.start("udid-1"));
        assertEquals(1, a.startCalls.get());
        assertEquals(1, b.startCalls.get());
    }

    @Test
    @DisplayName("una vez ganada la sesión, start() no vuelve a intentar arrancar (idempotente)")
    void startIsIdempotentOnceWinnerEstablished() {
        FakeProvider primary = new FakeProvider("PRIMARY", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(primary));

        assertTrue(chain.start("udid-1"));
        assertTrue(chain.start("udid-1")); // segunda llamada, mismo udid
        assertEquals(1, primary.startCalls.get(), "start() no debe reinvocarse si ya hay un ganador activo para este UDID");
    }

    @Test
    @DisplayName("captureFrame()/stop() delegan exclusivamente al candidato ganador")
    void captureAndStopDelegateToWinner() {
        FakeProvider primary   = new FakeProvider("PRIMARY", true, false);
        FakeProvider secondary = new FakeProvider("SECONDARY", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(primary, secondary));

        chain.start("udid-1");
        assertArrayEquals(secondary.frame, chain.captureFrame("udid-1"));

        chain.stop("udid-1");
        assertEquals(1, secondary.stopCalls.get());
        assertEquals(0, primary.stopCalls.get(), "stop() nunca debe llamarse en un candidato que no ganó");
    }

    @Test
    @DisplayName("dos UDIDs distintos pueden tener ganadores distintos de forma independiente")
    void differentUdidsCanHaveDifferentWinners() {
        FakeProvider primary   = new FakeProvider("PRIMARY", true, false);
        FakeProvider secondary = new FakeProvider("SECONDARY", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(primary, secondary));

        assertTrue(chain.start("udid-A"));
        assertEquals(1, primary.startCalls.get());
        assertEquals(1, secondary.startCalls.get());

        // Mismo resultado para un segundo UDID — cada uno se resuelve independientemente.
        assertTrue(chain.start("udid-B"));
        assertEquals(2, primary.startCalls.get());
        assertEquals(2, secondary.startCalls.get());
    }

    @Test
    @DisplayName("isSupported() es true si CUALQUIER candidato está soportado")
    void isSupportedIsOrAcrossCandidates() {
        FakeProvider unsupported = new FakeProvider("A", false, true);
        FakeProvider supported   = new FakeProvider("B", true, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(unsupported, supported));

        assertTrue(chain.isSupported());
    }

    @Test
    @DisplayName("isSupported() es false si NINGÚN candidato está soportado")
    void isSupportedFalseWhenNoCandidateSupported() {
        FakeProvider a = new FakeProvider("A", false, true);
        FakeProvider b = new FakeProvider("B", false, true);
        FallbackChainProvider chain = new FallbackChainProvider("test", List.of(a, b));

        assertFalse(chain.isSupported());
    }
}
