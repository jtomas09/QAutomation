package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 3.2 — tests del clasificador, todos con texto REAL (RUN-1005/1006/1007,
 * comentarios ya existentes en WdaManager/IosPreflightManager) — ninguno
 * inventado. No requiere iPhone, Xcode, xcodebuild, Appium ni WDA real.
 */
@DisplayName("IOSWdaErrorClassifier")
class IOSWdaErrorClassifierTest {

    @Test
    @DisplayName("1. 'Invalid trust settings...' -> IOS_DEVELOPER_TRUST_REQUIRED")
    void invalidTrustSettings_classifiesAsTrustRequired() {
        assertEquals(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED, IOSWdaErrorClassifier.classify(
            "Invalid trust settings. Restore system default trust settings for certificate "
            + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it."));
    }

    @Test
    @DisplayName("2. 'Developer App Certificate is not trusted' -> IOS_DEVELOPER_TRUST_REQUIRED")
    void developerAppCertificateNotTrusted_classifiesAsTrustRequired() {
        // Evidencia real: automationqa-runner.log, bloque "Testing failed:".
        assertEquals(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED, IOSWdaErrorClassifier.classify(
            "The application could not be launched because the Developer App Certificate is not trusted."));
    }

    @Test
    @DisplayName("3. 'No Accounts: ...' -> IOS_SIGNING_REQUIRED")
    void noAccounts_classifiesAsSigningRequired() {
        assertEquals(IOSWdaErrorCode.IOS_SIGNING_REQUIRED,
                IOSWdaErrorClassifier.classify("No Accounts: Add a new account in Accounts settings."));
    }

    @Test
    @DisplayName("4. \"No profiles for 'io.qautomation.wda.xctrunner' were found\" -> IOS_PROVISIONING_REQUIRED")
    void noProfilesFor_classifiesAsProvisioningRequired() {
        assertEquals(IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED, IOSWdaErrorClassifier.classify(
            "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
            + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'."));
    }

    @Test
    @DisplayName("5. Otro mensaje real de provisioning ('...pass -allowProvisioningUpdates...') "
            + "-> IOS_PROVISIONING_REQUIRED")
    void alternateProvisioningMessage_classifiesAsProvisioningRequired() {
        // Evidencia real: variante encontrada en el log histórico del Runner.
        assertEquals(IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED, IOSWdaErrorClassifier.classify(
            "No profiles for 'io.qautomation.wda.0000811000.xctrunner' were found: Xcode couldn't find "
            + "any iOS App Development provisioning profiles matching "
            + "'io.qautomation.wda.0000811000.xctrunner'. Automatic signing is disabled and unable to "
            + "generate a profile. To enable automatic signing, pass -allowProvisioningUpdates to xcodebuild."));
    }

    @Test
    @DisplayName("6. Fallo real de build sin trust/signing/provisioning -> IOS_WDA_BUILD_FAILED")
    void genericBuildFailure_classifiesAsWdaBuildFailed() {
        // Texto real preservado por TAREA 3.1 cuando no hay causa específica disponible.
        assertEquals(IOSWdaErrorCode.IOS_WDA_BUILD_FAILED,
                IOSWdaErrorClassifier.classify("Testing cancelled because the build failed."));
    }

    @Test
    @DisplayName("6b. 'Timed out while enabling automation mode' (build OK, WDA no arrancó) "
            + "-> IOS_WDA_STARTUP_FAILED")
    void automationModeTimeout_classifiesAsWdaStartupFailed() {
        // Evidencia real de logs + IosPreflightManager.runPreflight() ya distingue este
        // caso exacto de un fallo de build genérico (variable isAutomationTrustTimeout).
        assertEquals(IOSWdaErrorCode.IOS_WDA_STARTUP_FAILED, IOSWdaErrorClassifier.classify(
            "WebDriverAgentRunner-Runner (2490) encountered an error (The test runner failed to "
            + "initialize for UI testing. (Underlying Error: Timed out while enabling automation mode.))"));
    }

    @Test
    @DisplayName("7. Mensaje desconocido -> UNKNOWN")
    void unrecognizedMessage_classifiesAsUnknown() {
        assertEquals(IOSWdaErrorCode.UNKNOWN,
                IOSWdaErrorClassifier.classify("Some new Apple error message that has never been seen before."));
    }

    @Test
    @DisplayName("8. null -> UNKNOWN")
    void nullText_classifiesAsUnknown() {
        assertEquals(IOSWdaErrorCode.UNKNOWN, IOSWdaErrorClassifier.classify(null));
    }

    @Test
    @DisplayName("9. cadena vacía -> UNKNOWN")
    void emptyText_classifiesAsUnknown() {
        assertEquals(IOSWdaErrorCode.UNKNOWN, IOSWdaErrorClassifier.classify(""));
        assertEquals(IOSWdaErrorCode.UNKNOWN, IOSWdaErrorClassifier.classify("   "));
    }

    @Test
    @DisplayName("10. Case-insensitive: 'INVALID TRUST SETTINGS' -> IOS_DEVELOPER_TRUST_REQUIRED")
    void caseInsensitive_stillClassifiesAsTrustRequired() {
        assertEquals(IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED,
                IOSWdaErrorClassifier.classify("INVALID TRUST SETTINGS for certificate XYZ."));
    }

    // ── Preservación del texto original — el clasificador nunca lo reemplaza ──

    @Test
    @DisplayName("El texto original nunca se modifica ni se pierde — solo se agrega un code")
    void originalTextIsNeverAltered() {
        String original = "No Accounts: Add a new account in Accounts settings.";
        IOSWdaErrorCode code = IOSWdaErrorClassifier.classify(original);

        assertEquals(IOSWdaErrorCode.IOS_SIGNING_REQUIRED, code);
        assertEquals("No Accounts: Add a new account in Accounts settings.", original,
                "classify() no debe mutar ni reemplazar el String de entrada");
    }

    @Test
    @DisplayName("explain() referencia el errorCode sin descartar el texto original")
    void explain_referencesCodeWithoutDiscardingOriginalText() {
        String explanation = IOSWdaErrorClassifier.explain("Invalid trust settings for certificate X.");
        assertTrue(explanation.contains("IOS_DEVELOPER_TRUST_REQUIRED"));
    }

    // ── No falsos positivos por la sola palabra "account" ──────────────────────

    @Test
    @DisplayName("Un mensaje que menciona 'account' sin el patrón exacto 'No Accounts:' no es SIGNING")
    void genericAccountMention_isNotFalsePositiveForSigning() {
        // Contiene "account" pero no la frase exacta "No Accounts:" ni ningún otro
        // patrón conocido — debe caer a UNKNOWN, nunca a SIGNING por coincidencia parcial.
        assertEquals(IOSWdaErrorCode.UNKNOWN,
                IOSWdaErrorClassifier.classify("Checking developer account status..."));
    }
}
