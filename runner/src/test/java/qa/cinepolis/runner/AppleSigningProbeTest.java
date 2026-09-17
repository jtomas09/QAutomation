package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 25 — tests de {@link AppleSigningProbe#classify(String, int, long)}, la única
 * parte de esta clase sin efectos secundarios (no ejecuta xcodebuild, no requiere Apple
 * ID, Xcode ni hardware). Todo el texto de entrada es real, capturado en el log del
 * Runner durante la investigación de TAREA 24 (evidencia citada en cada caso), salvo
 * donde se indica explícitamente que es sintético (casos 4, 7 y 8).
 */
@DisplayName("AppleSigningProbe.classify")
class AppleSigningProbeTest {

    @Test
    @DisplayName("1. 'No Accounts' -> ACCOUNT_SESSION_REQUIRED")
    void noAccounts_classifiesAsAccountSessionRequired() {
        // Evidencia real: automationqa-runner.log, RUN-1001 y múltiples intentos posteriores.
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "/path/WebDriverAgent.xcodeproj: error: No Accounts: Add a new account in Accounts "
                + "settings. (in target 'WebDriverAgentRunner' from project 'WebDriverAgent')",
                65, 5300L);
        assertEquals(AppleSigningProbe.Status.ACCOUNT_SESSION_REQUIRED, r.status());
        assertEquals(5300L, r.probeDurationMs());
    }

    @Test
    @DisplayName("2. 'No profiles for ...' (sin 'No Accounts') -> PROVISIONING_REQUIRED")
    void noProfilesAlone_classifiesAsProvisioningRequired() {
        // Evidencia real: mismo texto ya usado por IOSWdaErrorClassifierTest (caso 4).
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
                + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.",
                65, 8000L);
        assertEquals(AppleSigningProbe.Status.PROVISIONING_REQUIRED, r.status());
    }

    @Test
    @DisplayName("3. Certificado/confianza inválida -> SIGNING_ERROR")
    void invalidTrustSettings_classifiesAsSigningError() {
        // Evidencia real: automationqa-runner.log, bloque "Testing failed:" (RUN-1005/1006/1007).
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "Invalid trust settings. Restore system default trust settings for certificate "
                + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.",
                65, 4000L);
        assertEquals(AppleSigningProbe.Status.SIGNING_ERROR, r.status());
    }

    @Test
    @DisplayName("4. Error genérico no reconocido -> UNKNOWN")
    void genericUnrecognizedError_classifiesAsUnknown() {
        // Sintético a propósito: ningún patrón conocido de cuenta/provisioning/confianza
        // debe coincidir aquí — es exactamente el caso que el probe NO debe ocultar como READY.
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "xcodebuild: error: Could not resolve package dependency graph.", 65, 3000L);
        assertEquals(AppleSigningProbe.Status.UNKNOWN, r.status());
    }

    @Test
    @DisplayName("5. 'BUILD SUCCEEDED' + exitCode=0 -> READY")
    void buildSucceeded_classifiesAsReady() {
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "Build description signature: abc123\n** BUILD SUCCEEDED **\n", 0, 42000L);
        assertEquals(AppleSigningProbe.Status.READY, r.status());
    }

    @Test
    @DisplayName("14. exitCode=0 + BUILD SUCCEEDED + warning no-fatal mencionando provisioning -> READY, NUNCA PROVISIONING_REQUIRED")
    void buildSucceededWithBenignProvisioningWarning_stillClassifiesAsReady() {
        // TAREA — causa raíz real encontrada (evidencia RUN-1002): el orden anterior de
        // classify() comprobaba patrones de fallo (incluida la delegación a
        // IOSWdaErrorClassifier, que matchea "provisioning profile" con un simple
        // .find()) ANTES de comprobar éxito real, y sin exigir exitCode!=0. Un build
        // genuinamente exitoso (exitCode=0) que además contiene, en cualquier parte de
        // su log completo, un mensaje no-fatal que menciona "provisioning profile"
        // (nota/advertencia, no un error) terminaba mal clasificado como
        // PROVISIONING_REQUIRED, sin llegar nunca a comprobar "BUILD SUCCEEDED". Ahora
        // el éxito inequívoco se comprueba PRIMERO.
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "note: Using new build system\n"
                + "note: A provisioning profile was found but is set to expire soon — "
                + "consider renewing it before it expires.\n"
                + "Build description signature: abc123\n"
                + "** BUILD SUCCEEDED **\n",
                0, 38000L);
        assertEquals(AppleSigningProbe.Status.READY, r.status());
    }

    @Test
    @DisplayName("15. exitCode=65 + error real de provisioning (sin BUILD SUCCEEDED) -> PROVISIONING_REQUIRED")
    void realProvisioningErrorWithNonZeroExit_classifiesAsProvisioningRequired() {
        // Complementa el caso 2 dejando explícito el contraste directo con el caso 14:
        // el ÚNICO diferenciador real entre PROVISIONING_REQUIRED y READY es el exit
        // code + la presencia real de "BUILD SUCCEEDED", nunca solo el texto.
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "/path/WebDriverAgent.xcodeproj: error: No profiles for 'io.qautomation.wda' were "
                + "found: Xcode couldn't find any iOS App Development provisioning profiles "
                + "matching 'io.qautomation.wda'.\n** BUILD FAILED **\n",
                65, 6200L);
        assertEquals(AppleSigningProbe.Status.PROVISIONING_REQUIRED, r.status());
    }

    @Test
    @DisplayName("16. exitCode=0 + producto de WDA generado (READY) -> impliesReadyToAcquire=true, continúa a install/verification")
    void wdaProductBuiltSuccessfully_impliesProceedToAcquire() {
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "CompileSwift normal arm64 ...\n** BUILD SUCCEEDED **\n", 0, 45000L);
        assertEquals(AppleSigningProbe.Status.READY, r.status());
        assertTrue(IosPreflightManager.impliesReadyToAcquire(r.status()),
                "READY debe implicar continuar hacia WdaLifecycleOwner.acquire() para instalar/verificar, "
                + "nunca declarar ReadyForExecution=true solo porque xcodebuild terminó bien.");
    }

    @Test
    @DisplayName("6. 'No Accounts' + 'No profiles for' juntos (caso real) -> ACCOUNT_SESSION_REQUIRED, no PROVISIONING_REQUIRED")
    void noAccountsAndNoProfilesTogether_accountWins() {
        // Evidencia real EXACTA: automationqa-runner.log, ejecución real contra el
        // dispositivo físico (ver TAREA 24/25) — ambas líneas aparecen juntas en el mismo
        // fallo. La causa raíz es la sesión de cuenta; la ausencia de perfil es un
        // síntoma derivado de no poder crearlo. Este es el caso que exige comprobar
        // "No Accounts" ANTES de delegar en IOSWdaErrorClassifier (que por sí solo
        // prioriza PROVISIONING y devolvería el código equivocado para este probe).
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "/path/WebDriverAgent.xcodeproj: error: No Accounts: Add a new account in Accounts "
                + "settings. (in target 'WebDriverAgentRunner' from project 'WebDriverAgent')\n"
                + "/path/WebDriverAgent.xcodeproj: error: No profiles for 'io.qautomation.wda.xctrunner' "
                + "were found: Xcode couldn't find any iOS App Development provisioning profiles "
                + "matching 'io.qautomation.wda.xctrunner'. (in target 'WebDriverAgentRunner' from "
                + "project 'WebDriverAgent')",
                65, 5300L);
        assertEquals(AppleSigningProbe.Status.ACCOUNT_SESSION_REQUIRED, r.status());
    }

    @Test
    @DisplayName("7. '** BUILD FAILED **' + exitCode!=0, sin señal de cuenta/provisioning/confianza -> BUILD_FAILURE")
    void genericXcodebuildOutput_classifiesAsBuildFailure() {
        // Sintético: salida de xcodebuild real en forma (fases de build) pero sin
        // ninguna señal de cuenta/provisioning/confianza. Antes se perdía dentro del
        // UNKNOWN genérico pese a haber evidencia inequívoca de fallo real de build
        // (TAREA — Apple-Signing-Probe: "no ocultar la causa real detrás de UNKNOWN
        // cuando exista evidencia suficiente para clasificar").
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "Prepare packages\nCreateBuildRequest\nComputeTargetDependencyGraph\n"
                + "note: Building targets in dependency order\n** BUILD FAILED **\n",
                65, 15000L);
        assertEquals(AppleSigningProbe.Status.BUILD_FAILURE, r.status());
    }

    @Test
    @DisplayName("9. Keychain requiere interacción del usuario -> SIGNING_ERROR (KEYCHAIN_ACCESS_FAILURE)")
    void keychainInteractionNotAllowed_classifiesAsSigningError() {
        // Evidencia real y documentada: errSecInteractionNotAllowed = -25308, texto
        // exacto que devuelve SecCopyErrorMessageString para ese OSStatus — ocurre
        // cuando codesign/security necesita el diálogo "Siempre permitir" del Keychain
        // y no hay sesión de UI (típico de un proceso lanzado por LaunchAgent).
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "security: SecKeychainItemCopyContent: User interaction is not allowed.",
                65, 2000L);
        assertEquals(AppleSigningProbe.Status.SIGNING_ERROR, r.status());
    }

    @Test
    @DisplayName("10. Timeout con salida parcial clasificable no debe perderse como UNKNOWN ciego")
    void partialOutputBeforeTimeout_isStillClassifiable() {
        // Simula lo que antes se descartaba en el branch de timeout: si la salida ya
        // capturada (aunque el proceso siga vivo/se mate después) contiene evidencia
        // real, classify() debe reconocerla igual que en el camino normal — con
        // exitCode=-1 (sentinel usado en el timeout real) para que READY sea
        // estructuralmente imposible.
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "/path/WebDriverAgent.xcodeproj: error: No Accounts: Add a new account in Accounts "
                + "settings. (in target 'WebDriverAgentRunner' from project 'WebDriverAgent')",
                -1, 90000L);
        assertEquals(AppleSigningProbe.Status.ACCOUNT_SESSION_REQUIRED, r.status());
    }

    @Test
    @DisplayName("8. Salida vacía -> UNKNOWN")
    void emptyOutput_classifiesAsUnknown() {
        AppleSigningProbe.Result r = AppleSigningProbe.classify("", 1, 100L);
        assertEquals(AppleSigningProbe.Status.UNKNOWN, r.status());

        AppleSigningProbe.Result rNull = AppleSigningProbe.classify(null, 1, 100L);
        assertEquals(AppleSigningProbe.Status.UNKNOWN, rNull.status());
    }

    // ── AppleSigningProbe.classifyMissingTeam(DiscoveryResult, long) ──────────────
    // TAREA — Apple Signing Probe robusto: distinguir "Xcode no tiene ninguna cuenta
    // configurada" (requiere intervención humana) de "sí hay cuenta(s), pero ninguna
    // resolvió a un Team utilizable" (p. ej. certificado huérfano de un Team anterior —
    // NO requiere volver a iniciar sesión). Mismo patrón que
    // AppleDeveloperTeamManager.discoverCandidates(..., DiscoveryResult): la fuente
    // (Xcode.plist real) se resuelve en el llamador — estos tests pasan un
    // DiscoveryResult sintético, sin tocar el archivo real ni requerir Xcode.

    @Test
    @DisplayName("11. Xcode.plist leído correctamente pero sin ningún Team -> APPLE_ACCOUNT_NOT_AUTHENTICATED")
    void noAuthenticatedTeamsAtAll_classifiesAsAccountNotAuthenticated() {
        AppleDeveloperAccountProvider.DiscoveryResult accounts =
                AppleDeveloperAccountProvider.DiscoveryResult.available(Map.of());
        AppleSigningProbe.Result r = AppleSigningProbe.classifyMissingTeam(accounts, 50L);
        assertEquals(AppleSigningProbe.Status.APPLE_ACCOUNT_NOT_AUTHENTICATED, r.status());
        assertEquals(50L, r.probeDurationMs());
    }

    @Test
    @DisplayName("12. Cuenta(s) autenticada(s) pero teamId no resuelto -> APPLE_TEAM_NOT_AVAILABLE (no pide login)")
    void authenticatedTeamsButNoneUsable_classifiesAsTeamNotAvailable() {
        // Evidencia real citada en la tarea: Team C32VD96Q84 con estado ORPHAN_CERTIFICATE.
        AppleDeveloperAccountProvider.DiscoveryResult accounts =
                AppleDeveloperAccountProvider.DiscoveryResult.available(Map.of("C32VD96Q84", "Jairo Tomás Baza"));
        AppleSigningProbe.Result r = AppleSigningProbe.classifyMissingTeam(accounts, 50L);
        assertEquals(AppleSigningProbe.Status.APPLE_TEAM_NOT_AVAILABLE, r.status());
        // Evidencia del requisito explícito de la tarea: NO pedir reautenticación cuando
        // la cuenta sigue autenticada y el problema real es otro (Team/certificado).
        assertTrue(r.reason().contains("no requiere volver a iniciar sesión"));
    }

    @Test
    @DisplayName("13. Registro de cuentas de Xcode ilegible -> APPLE_TEAM_NOT_AVAILABLE (sin asumir desautenticación)")
    void xcodeAccountRegistryUnreadable_classifiesAsTeamNotAvailableWithoutAssuming() {
        AppleDeveloperAccountProvider.DiscoveryResult accounts =
                AppleDeveloperAccountProvider.DiscoveryResult.unavailable("com.apple.dt.Xcode.plist no existe");
        AppleSigningProbe.Result r = AppleSigningProbe.classifyMissingTeam(accounts, 50L);
        assertEquals(AppleSigningProbe.Status.APPLE_TEAM_NOT_AVAILABLE, r.status());
        assertTrue(r.reason().contains("no existe"));
    }
}
