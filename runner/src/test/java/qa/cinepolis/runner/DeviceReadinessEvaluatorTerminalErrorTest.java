package qa.cinepolis.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 26C — tests de la corrección de divergencia entre Pipeline A
 * ({@link DeviceReadinessEvaluator}, alimenta el Dashboard "Dispositivos Conectados")
 * y Pipeline B ({@link IOSRunnerReadinessEngine}, decide si una ejecución real
 * procede). TAREA 26B demostró que Pipeline A nunca consultaba
 * {@link WdaLifecycleOwner} — este archivo prueba la consulta de solo lectura
 * agregada en {@code DeviceReadinessEvaluator.terminalErrorOverride()}.
 *
 * Usa {@code evaluateXctrace(udid)} (no requiere un {@code DevicectlParser.DeviceInfo}
 * real) — ejercita exactamente la misma lógica de "última puerta" que
 * {@code evaluate(info, udid)}, ya que ambas delegan en el mismo helper privado.
 * No requiere hardware conectado: cuando el dispositivo no aparece, Appium/Xcode
 * system props no bloquean, así que la única condición que decide el resultado en
 * estos tests es el propio {@code WdaLifecycleOwner.TERMINAL_ERRORS} — el chequeo
 * bajo prueba.
 */
@DisplayName("DeviceReadinessEvaluator — consulta de terminalError (TAREA 26C)")
class DeviceReadinessEvaluatorTerminalErrorTest {

    private static final String UDID = "TEST-UDID-26C-0001";

    @AfterEach
    void cleanup() {
        WdaLifecycleOwner.resetForRetry(UDID);
    }

    @Test
    @DisplayName("1/2/3/4. ACCOUNT_SESSION_REQUIRED no se pierde: produce readyForExecution=false "
            + "con el mensaje humano sugerido (no 'expiró')")
    void accountSessionRequired_isReflectedAsNotReady() {
        String probeReason =
                "[APPLE-SIGNING-PROBE] ACCOUNT_SESSION_REQUIRED — No Accounts: la sesión de Apple ID "
                + "utilizada por Xcode/xcodebuild no está disponible para provisioning automático. "
                + "Requiere reautenticación en Xcode.";
        WdaLifecycleOwner.markTerminalError(UDID, probeReason);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertNotNull(r.notReadyReason);
        assertTrue(r.notReadyReason.contains("Acción requerida"),
                "Debe usar el mensaje humano sugerido por la tarea, no el texto técnico crudo");
        assertFalse(r.notReadyReason.toLowerCase().contains("expir"),
                "TAREA 24 no demostró el mecanismo de expiración — nunca debe afirmarse 'expiró'");
    }

    @Test
    @DisplayName("5. IOS_SIGNING_REQUIRED (texto RAW) no se convierte en el mensaje de ACCOUNT_SESSION_REQUIRED")
    void signingRequiredRawText_keepsOriginalReason() {
        String raw = "No Accounts: Add a new account in Accounts settings.";
        WdaLifecycleOwner.markTerminalError(UDID, raw);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertEquals(raw, r.notReadyReason,
                "Sin el marcador exclusivo del probe, IOSWdaErrorClassifier clasifica esto como "
                + "IOS_SIGNING_REQUIRED (no ACCOUNT_SESSION_REQUIRED) — el texto original se conserva");
        assertEquals(IOSWdaErrorCode.IOS_SIGNING_REQUIRED, IOSWdaErrorClassifier.classify(raw));
    }

    @Test
    @DisplayName("6. IOS_PROVISIONING_REQUIRED no se convierte en ACCOUNT_SESSION_REQUIRED")
    void provisioningRequired_keepsOriginalReason() {
        String raw = "No profiles for 'io.qautomation.wda.xctrunner' were found: Xcode couldn't find any "
                + "iOS App Development provisioning profiles matching 'io.qautomation.wda.xctrunner'.";
        WdaLifecycleOwner.markTerminalError(UDID, raw);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertEquals(raw, r.notReadyReason);
        assertEquals(IOSWdaErrorCode.IOS_PROVISIONING_REQUIRED, IOSWdaErrorClassifier.classify(raw));
    }

    @Test
    @DisplayName("7. IOS_DEVELOPER_TRUST_REQUIRED continúa reflejándose (texto original, sin reformatear)")
    void trustRequired_keepsOriginalReason() {
        String raw = "Invalid trust settings. Restore system default trust settings for certificate "
                + "\"Apple Development: jtomasb@ia.com.mx (CC333X3A2Q)\" in order to sign code with it.";
        WdaLifecycleOwner.markTerminalError(UDID, raw);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertEquals(raw, r.notReadyReason);
    }

    @Test
    @DisplayName("8. IOS_WDA_STARTUP_FAILED continúa reflejándose")
    void startupFailed_keepsOriginalReason() {
        String raw = "WebDriverAgentRunner-Runner (2490) encountered an error (The test runner failed to "
                + "initialize for UI testing. (Underlying Error: Timed out while enabling automation mode.))";
        WdaLifecycleOwner.markTerminalError(UDID, raw);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertEquals(raw, r.notReadyReason);
    }

    @Test
    @DisplayName("9. IOS_WDA_BUILD_FAILED (genérico) continúa reflejándose")
    void genericBuildFailed_keepsOriginalReason() {
        String raw = "Testing cancelled because the build failed.";
        WdaLifecycleOwner.markTerminalError(UDID, raw);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertEquals(raw, r.notReadyReason);
    }

    @Test
    @DisplayName("10. UNKNOWN continúa reflejándose (nunca se oculta como READY)")
    void unknownError_keepsOriginalReason() {
        String raw = "ERROR: The operation couldn't be completed. (CoreDeviceCLISupport.DiagnoseError error 0.)";
        WdaLifecycleOwner.markTerminalError(UDID, raw);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(r.readyForExecution);
        assertEquals(raw, r.notReadyReason);
        assertEquals(IOSWdaErrorCode.UNKNOWN, IOSWdaErrorClassifier.classify(raw));
    }

    @Test
    @DisplayName("11/12. Un terminalError NO bloquea permanentemente: tras resetForRetry() "
            + "(mismo efecto que acquire()/requestForMirror() al detectar WDA sano) vuelve a ready=true")
    void terminalErrorClears_deviceBecomesReadyAgain() {
        WdaLifecycleOwner.markTerminalError(UDID, "No Accounts: Add a new account in Accounts settings.");
        assertFalse(DeviceReadinessEvaluator.evaluateXctrace(UDID).readyForExecution,
                "Precondición: con terminalError activo, no debe reportarse listo");

        // Mismo mecanismo YA EXISTENTE que acquire()/requestForMirror() disparan
        // automáticamente al encontrar WDA vivo de nuevo (WdaLifecycleOwner.java,
        // comentario "evidencia real Fase 16") — esta clase nunca implementa su
        // propia limpieza, solo lee el estado que esos otros ya mantienen correcto.
        WdaLifecycleOwner.resetForRetry(UDID);

        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);
        assertTrue(r.readyForExecution, "Tras limpiar el error terminal, debe volver a reportarse listo");
        assertNull(r.notReadyReason);
    }

    @Test
    @DisplayName("13. 'Runner restart' no dejar stale state: TERMINAL_ERRORS es un mapa en memoria del "
            + "proceso — una JVM/proceso sin ningún markTerminalError previo para este UDID nunca lo reporta "
            + "(evidencia estructural: no hay persistencia en disco de este mapa, confirmado por inspección "
            + "de WdaLifecycleOwner.java — ver TERMINAL_ERRORS = new ConcurrentHashMap<>(), sin carga desde archivo)")
    void freshState_hasNoTerminalError() {
        // Un UDID nunca antes marcado en ESTA JVM (equivalente a un Runner recién
        // reiniciado, ver Javadoc del test) — no puede haber stale state porque
        // nunca hubo escritura previa en este proceso.
        String neverMarkedUdid = "TEST-UDID-26C-NEVER-MARKED";
        assertFalse(WdaLifecycleOwner.isTerminalError(neverMarkedUdid));
        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(neverMarkedUdid);
        assertTrue(r.readyForExecution);
    }

    @Test
    @DisplayName("14. Disconnect/reconnect no deja stale state INCORRECTO: el error terminal persiste "
            + "correctamente por UDID (no por sesión de conexión) porque un problema de cuenta Apple no se "
            + "relaciona con el estado de conexión física — mismo UDID, misma respuesta, sin importar cuántas "
            + "veces se re-evalúe")
    void reconnect_sameUdidStillReflectsRealTerminalError() {
        WdaLifecycleOwner.markTerminalError(UDID, "No Accounts: Add a new account in Accounts settings.");

        // Simula múltiples evaluaciones (como las que dispara un ciclo de "desconectar/reconectar" en
        // el escaneo periódico de IOSDeviceScanner) — el resultado debe ser consistente, nunca
        // "curarse" por sí solo salvo que WdaLifecycleOwner lo libere explícitamente.
        assertFalse(DeviceReadinessEvaluator.evaluateXctrace(UDID).readyForExecution);
        assertFalse(DeviceReadinessEvaluator.evaluateXctrace(UDID).readyForExecution);
        assertFalse(DeviceReadinessEvaluator.evaluateXctrace(UDID).readyForExecution);
    }

    @Test
    @DisplayName("15. La consulta es de solo lectura: no dispara ningún build, no cambia INFLIGHT/BUILD_EXECUTOR")
    void readOnlyCheck_neverTriggersBuild() {
        WdaLifecycleOwner.markTerminalError(UDID, "No Accounts: Add a new account in Accounts settings.");

        DeviceReadinessEvaluator.evaluateXctrace(UDID);
        DeviceReadinessEvaluator.evaluateXctrace(UDID);
        DeviceReadinessEvaluator.evaluateXctrace(UDID);

        assertFalse(WdaLifecycleOwner.isBuildInFlight(UDID),
                "evaluateXctrace() nunca debe disparar WdaLifecycleOwner.acquire() ni ningún build");
    }

    @Test
    @DisplayName("Sin terminalError -> comportamiento idéntico al anterior (no regresión)")
    void noTerminalError_behavesAsBefore() {
        assertFalse(WdaLifecycleOwner.isTerminalError(UDID), "Precondición");
        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace(UDID);
        assertTrue(r.readyForExecution);
        assertNull(r.notReadyReason);
    }
}
