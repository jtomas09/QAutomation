package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("7. Salida genérica de xcodebuild sin relación con signing -> UNKNOWN")
    void genericXcodebuildOutput_classifiesAsUnknown() {
        // Sintético: salida de xcodebuild real en forma (fases de build) pero sin
        // ninguna señal de cuenta/provisioning/confianza ni "BUILD SUCCEEDED".
        AppleSigningProbe.Result r = AppleSigningProbe.classify(
                "Prepare packages\nCreateBuildRequest\nComputeTargetDependencyGraph\n"
                + "note: Building targets in dependency order\n** BUILD FAILED **\n",
                65, 15000L);
        assertEquals(AppleSigningProbe.Status.UNKNOWN, r.status());
    }

    @Test
    @DisplayName("8. Salida vacía -> UNKNOWN")
    void emptyOutput_classifiesAsUnknown() {
        AppleSigningProbe.Result r = AppleSigningProbe.classify("", 1, 100L);
        assertEquals(AppleSigningProbe.Status.UNKNOWN, r.status());

        AppleSigningProbe.Result rNull = AppleSigningProbe.classify(null, 1, 100L);
        assertEquals(AppleSigningProbe.Status.UNKNOWN, rNull.status());
    }
}
