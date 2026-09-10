package qa.cinepolis.runner;

import java.util.regex.Pattern;

/**
 * TAREA 3.2 — convierte el texto real de un fallo de xcodebuild/WDA (ya
 * preservado por {@code WdaManager.BuildOutcome.captureError}, ver TAREA 3.1)
 * en un {@link IOSWdaErrorCode}, sin descartar el texto original.
 *
 * Puramente determinista y sin efectos secundarios: NO ejecuta xcodebuild, NO
 * inicia/instala/repara WDA, NO crea cuentas ni perfiles, NO toca el
 * dispositivo. Solo clasifica una cadena de texto ya existente.
 *
 * ── Evidencia real usada para cada patrón (sin inventar ninguno) ───────────
 *
 *   IOS_ACCOUNT_SESSION_REQUIRED (TAREA 26A)
 *     Marcador exclusivo "[APPLE-SIGNING-PROBE] ACCOUNT_SESSION_REQUIRED", que
 *     {@link AppleSigningProbe} antepone a su propio mensaje cuando una operación
 *     REAL de xcodebuild (no una simple lectura de Keychain/plist) reprodujo "No
 *     Accounts" ANTES de intentar compilar WDA — evidencia real: TAREA 24/25,
 *     hardware físico, 00008110-000129261482601E. Comprobado con máxima prioridad
 *     porque es la señal más específica posible (el propio probe ya clasificó el
 *     evento; este método solo la transporta a través de
 *     {@code WdaLifecycleOwner.TERMINAL_ERRORS}, que solo almacena texto).
 *
 *   IOS_DEVELOPER_TRUST_REQUIRED
 *     "Invalid trust settings. Restore system default trust settings for
 *      certificate ..." / "The application could not be launched because the
 *      Developer App Certificate is not trusted."
 *     Fuente: logs reales RUN-1005/1006/1007 (bloque "Testing failed:" de
 *     WdaManager.streamBuildOutput()); mismo patrón ya usado por
 *     IOSReadinessShadowComparator.normalizeReadyReason (TAREA 3).
 *
 *   IOS_SIGNING_REQUIRED
 *     "No Accounts: Add a new account in Accounts settings."
 *     Fuente: logs reales RUN-1005/1006/1007; WdaManager.NO_ACCOUNTS_PAT ya
 *     reconoce esta cadena exacta (aunque para otro propósito: marcar
 *     BuildOutcome.noAccountsSigningIssue).
 *
 *   IOS_PROVISIONING_REQUIRED
 *     "No profiles for 'io.qautomation.wda.xctrunner' were found: ..."
 *     Fuente: logs reales RUN-1005/1006/1007; WdaManager.NO_ACCOUNTS_PAT
 *     también reconoce "no profiles for ". Adicionalmente "provisioning
 *     profile"/"requires a provisioning profile" — evidencia:
 *     WdaManager.IMMEDIATE_SIGNAL ya reconoce ambas frases como señal real de
 *     provisioning en la salida de xcodebuild.
 *
 *   IOS_WDA_STARTUP_FAILED
 *     "... Timed out while enabling automation mode." — build/instalación ya
 *     tuvieron éxito (WDA llegó a lanzarse), pero la sesión de automatización
 *     XCTest no llegó a confirmarse. Fuente: logs reales (líneas
 *     "WebDriverAgentRunner-Runner (PID) encountered an error (... Timed out
 *     while enabling automation mode.)"); IosPreflightManager.runPreflight()
 *     YA trata esta frase exacta como un caso distinto de un fallo de build
 *     (ver su variable local isAutomationTrustTimeout) — no es una distinción
 *     inventada aquí, ya existe en el código productivo.
 *
 *   IOS_WDA_BUILD_FAILED
 *     "Testing cancelled because the build failed." (el resumen genérico que
 *     TAREA 3.1 preserva solo cuando NO hay causa específica disponible) y
 *     variantes con "build failed" — evidencia: logs reales + comentario
 *     existente en WdaManager sobre "xcodebuild: error: Failed to build
 *     workspace ...".
 *
 *   UNKNOWN
 *     Cualquier texto que no coincida con lo anterior, null, o cadena vacía.
 *     Nunca se descarta el texto original — queda disponible aparte del code.
 */
public final class IOSWdaErrorClassifier {

    private IOSWdaErrorClassifier() {}

    // ── Patrones — cada uno con evidencia citada en el Javadoc de clase ────────

    /**
     * TAREA 26A — marcador estable y exclusivo de {@link AppleSigningProbe}, nunca
     * presente en la salida RAW de xcodebuild (que dice "No Accounts:" sin este
     * prefijo). Máxima prioridad: es la señal MÁS específica posible — el propio
     * probe ya determinó de antemano, con una operación real, que se trata
     * exactamente de esto, no una inferencia posterior sobre texto ambiguo. Agregar
     * este patrón es puramente aditivo: ningún texto existente (raw de xcodebuild,
     * ya cubierto por SIGNING_PAT más abajo) puede coincidir con él, así que no
     * cambia la clasificación de ningún llamador existente.
     */
    private static final Pattern ACCOUNT_SESSION_PROBE_PAT = Pattern.compile(
            java.util.regex.Pattern.quote("[APPLE-SIGNING-PROBE] ACCOUNT_SESSION_REQUIRED"));

    private static final Pattern INVALID_TRUST_SETTINGS_PAT = Pattern.compile(
            "(?i)invalid trust settings");

    private static final Pattern SIGNING_PAT = Pattern.compile(
            "(?i)no accounts:");

    private static final Pattern PROVISIONING_PAT = Pattern.compile(
            "(?i)no profiles for |provisioning profile|requires a provisioning profile");

    private static final Pattern STARTUP_PAT = Pattern.compile(
            "(?i)enabling automation mode");

    private static final Pattern BUILD_FAILED_PAT = Pattern.compile(
            "(?i)build failed");

    /**
     * Clasifica {@code errorText} en una de las categorías de {@link IOSWdaErrorCode}.
     *
     * Prioridad determinista (ver Javadoc de clase para el razonamiento):
     * TRUST → PROVISIONING → SIGNING → WDA_STARTUP → WDA_BUILD_FAILED → UNKNOWN.
     * Con la evidencia real disponible hoy ningún mensaje coincide con más de
     * un patrón a la vez, pero el orden queda fijado explícitamente para que
     * un futuro mensaje ambiguo tenga un resultado predecible, no accidental.
     *
     * @return nunca {@code null}; {@link IOSWdaErrorCode#UNKNOWN} para texto
     *         nulo, vacío, o que no coincide con ningún patrón conocido.
     */
    public static IOSWdaErrorCode classify(String errorText) {
        if (errorText == null || errorText.isBlank()) return IOSWdaErrorCode.UNKNOWN;

        // TAREA 26A — comprobado ANTES que cualquier otro patrón (ver Javadoc de
        // ACCOUNT_SESSION_PROBE_PAT): es un marcador exclusivo del probe, nunca
        // ambiguo con el resto de los patrones de abajo.
        if (ACCOUNT_SESSION_PROBE_PAT.matcher(errorText).find())
            return IOSWdaErrorCode.IOS_ACCOUNT_SESSION_REQUIRED;

        if (isDeveloperTrustError(errorText))            return IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED;
        if (PROVISIONING_PAT.matcher(errorText).find())  return IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED;
        if (SIGNING_PAT.matcher(errorText).find())       return IOSWdaErrorCode.IOS_SIGNING_REQUIRED;
        if (STARTUP_PAT.matcher(errorText).find())       return IOSWdaErrorCode.IOS_WDA_STARTUP_FAILED;
        if (BUILD_FAILED_PAT.matcher(errorText).find())  return IOSWdaErrorCode.IOS_WDA_BUILD_FAILED;

        return IOSWdaErrorCode.UNKNOWN;
    }

    /**
     * "Invalid trust settings..." (RUN-1005/1006/1007) o "...Developer App
     * Certificate is not trusted..." (evidencia real, mismo patrón ya usado
     * por IOSReadinessShadowComparator.normalizeReadyReason en TAREA 3) — se
     * comprueban ambas frases por separado en vez de una sola regex compuesta,
     * a propósito, para que la condición sea legible y fácil de auditar.
     */
    private static boolean isDeveloperTrustError(String errorText) {
        if (INVALID_TRUST_SETTINGS_PAT.matcher(errorText).find()) return true;
        String lower = errorText.toLowerCase();
        return lower.contains("developer app certificate") && lower.contains("not trusted");
    }

    /**
     * Explicación legible de qué patrón (si alguno) determinó la clasificación
     * — nunca modifica ni reemplaza {@code errorText}, solo lo referencia.
     */
    public static String explain(String errorText) {
        IOSWdaErrorCode code = classify(errorText);
        String base = "errorCode=" + code;
        if (code == IOSWdaErrorCode.UNKNOWN) {
            return base + " — ningún patrón conocido coincide con el texto original.";
        }
        return base + " — coincide con un patrón real conocido de " + code + ".";
    }
}
