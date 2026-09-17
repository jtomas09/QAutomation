package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — Apple Signing Probe robusto (cierre): tests puros de
 * {@link IosPreflightManager#decidePreflightPath(boolean, boolean)} y
 * {@link IosPreflightManager#impliesReadyToAcquire(AppleSigningProbe.Status)} — la
 * regla real que ahora usa {@code runPreflight()} para decidir si omite el Apple
 * Signing Probe cuando WDA ya responde en vivo (WDA_ALREADY_AVAILABLE), sin importar
 * el estado del caché en disco.
 *
 * No requieren iPhone físico, Xcode ni xcodebuild — ambos métodos son funciones
 * puras (sin I/O), extraídas de {@code runPreflight()} exactamente para poder
 * testear esta decisión sin hardware, mismo patrón ya usado en
 * {@code AppleSigningProbe.classify()}/{@code classifyMissingTeam()}.
 */
@DisplayName("IosPreflightManager — fast path WDA_ALREADY_AVAILABLE")
class IosPreflightManagerFastPathTest {

    @Test
    @DisplayName("1. WDA vivo + caché inválido -> SKIP_PROBE_WDA_ALREADY_RUNNING (NO ejecutar el probe)")
    void wdaRunningWithInvalidCache_skipsProbe() {
        IosPreflightManager.PreflightPath path = IosPreflightManager.decidePreflightPath(true, false);
        assertEquals(IosPreflightManager.PreflightPath.SKIP_PROBE_WDA_ALREADY_RUNNING, path);
    }

    @Test
    @DisplayName("2. WDA vivo + caché válido -> SKIP_PROBE_WDA_ALREADY_RUNNING también (el caché nunca le gana al estado vivo)")
    void wdaRunningWithValidCache_stillSkipsProbe() {
        // Evidencia del requisito explícito de la tarea: "El caché de signing NO debe
        // tener prioridad sobre el estado vivo de WDA" — con WDA vivo, el resultado es
        // el mismo sin importar wdaCached.
        IosPreflightManager.PreflightPath path = IosPreflightManager.decidePreflightPath(true, true);
        assertEquals(IosPreflightManager.PreflightPath.SKIP_PROBE_WDA_ALREADY_RUNNING, path);
    }

    @Test
    @DisplayName("3. WDA no vivo + caché inválido -> RUN_SIGNING_PROBE (sí se permite el probe)")
    void wdaNotRunningWithInvalidCache_runsProbe() {
        IosPreflightManager.PreflightPath path = IosPreflightManager.decidePreflightPath(false, false);
        assertEquals(IosPreflightManager.PreflightPath.RUN_SIGNING_PROBE, path);
    }

    @Test
    @DisplayName("4. WDA no vivo + caché válido -> USE_CACHED_WDA (camino rápido de caché, sin probe)")
    void wdaNotRunningWithValidCache_usesCachedPath() {
        IosPreflightManager.PreflightPath path = IosPreflightManager.decidePreflightPath(false, true);
        assertEquals(IosPreflightManager.PreflightPath.USE_CACHED_WDA, path);
    }

    @Test
    @DisplayName("5. WDA no vivo + cuenta inexistente -> APPLE_ACCOUNT_NOT_AUTHENTICATED (vía AppleSigningProbe.classifyMissingTeam)")
    void wdaNotRunningWithNoAccount_classifiesAsAccountNotAuthenticated() {
        // Cubre el caso C del criterio de éxito: el camino RUN_SIGNING_PROBE es el que
        // eventualmente invoca AppleSigningProbe.probe(), cuyo branch de teamId en
        // blanco ya está cubierto por AppleSigningProbeTest (casos 11-13) — se repite
        // aquí la aserción central para dejar explícito el escenario pedido.
        assertEquals(IosPreflightManager.PreflightPath.RUN_SIGNING_PROBE,
                IosPreflightManager.decidePreflightPath(false, false));
        AppleDeveloperAccountProvider.DiscoveryResult noAccounts =
                AppleDeveloperAccountProvider.DiscoveryResult.available(java.util.Map.of());
        assertEquals(AppleSigningProbe.Status.APPLE_ACCOUNT_NOT_AUTHENTICATED,
                AppleSigningProbe.classifyMissingTeam(noAccounts, 10L).status());
    }

    @Test
    @DisplayName("6. WDA no vivo + Team no disponible -> APPLE_TEAM_NOT_AVAILABLE (no pide login)")
    void wdaNotRunningWithNoUsableTeam_classifiesAsTeamNotAvailable() {
        // Caso D del criterio de éxito.
        assertEquals(IosPreflightManager.PreflightPath.RUN_SIGNING_PROBE,
                IosPreflightManager.decidePreflightPath(false, false));
        AppleDeveloperAccountProvider.DiscoveryResult orphanedTeam =
                AppleDeveloperAccountProvider.DiscoveryResult.available(
                        java.util.Map.of("C32VD96Q84", "Jairo Tomás Baza"));
        AppleSigningProbe.Result r = AppleSigningProbe.classifyMissingTeam(orphanedTeam, 10L);
        assertEquals(AppleSigningProbe.Status.APPLE_TEAM_NOT_AVAILABLE, r.status());
        assertFalse(r.reason().toLowerCase().contains("agrega tu"),
                "No debe pedir 'agrega tu cuenta' cuando la cuenta sigue autenticada.");
    }

    @Test
    @DisplayName("7. WDA no vivo + signing correcto (READY) -> impliesReadyToAcquire=true (continúa hacia acquire())")
    void wdaNotRunningWithReadyProbe_impliesAcquire() {
        assertTrue(IosPreflightManager.impliesReadyToAcquire(AppleSigningProbe.Status.READY));
    }

    @Test
    @DisplayName("8. Cualquier status distinto de READY -> impliesReadyToAcquire=false (NUNCA se llega a acquire() sin evidencia)")
    void nonReadyStatuses_neverImplyAcquire() {
        for (AppleSigningProbe.Status status : AppleSigningProbe.Status.values()) {
            if (status == AppleSigningProbe.Status.READY) continue;
            assertFalse(IosPreflightManager.impliesReadyToAcquire(status),
                    "Status " + status + " no debería implicar acquire()");
        }
    }
}
