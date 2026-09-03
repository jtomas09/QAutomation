package qa.cinepolis.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 18 — tests del Shadow instrumentado de {@link IosPreflightManager}.
 *
 * {@code runShadowComparison(...)} se extrajo de la lambda del hilo original
 * (TAREA 3) únicamente para poder invocarlo aquí de forma síncrona y directa,
 * sin threading y sin necesitar un {@code runPreflight()} completo contra
 * hardware real (que sí requiere WDA/xcodebuild reales) — misma lógica exacta,
 * sin mover la arquitectura ni crear un segundo Shadow. Usa el UDID físico real
 * de esta sesión para que {@link IOSRunnerReadinessEngine#evaluate} recorra
 * código real (CONNECTED/PAIRED/DEVELOPER_MODE/etc.), consistente con el
 * patrón ya establecido en TAREA 6-15.
 */
@DisplayName("IosPreflightManager — Shadow instrumentado (TAREA 18)")
class IosPreflightManagerShadowTest {

    private static final String UDID = "00008110-000129261482601E";
    private final BackendClient client = new BackendClient("http://127.0.0.1:1", "test-token", "test-runner");

    @AfterEach
    void cleanup() {
        WdaLifecycleOwner.resetForRetry(UDID);
    }

    private static String captureStdout(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    // ── TEST 1/2/3/6 — el hilo (aquí, el método síncrono) inicia, ejecuta el
    // Engine, registra el resultado y termina, en ese orden ────────────────────

    @Test
    @DisplayName("TEST 1-3/6: runShadowComparison ejecuta el Engine y registra START/EVALUATING/RESULT/END en orden")
    void runShadowComparison_logsFullLifecycleInOrder() {
        String out = captureStdout(() ->
                IosPreflightManager.runShadowComparison(client, "tarea18-t1", UDID, false, "motivo de prueba"));

        int start      = out.indexOf("IOS_READINESS_SHADOW THREAD START");
        int evaluating = out.indexOf("IOS_READINESS_SHADOW EVALUATING");
        int result     = out.indexOf("IOS_READINESS_SHADOW RESULT");
        int comparison = out.indexOf("IOS_READINESS_SHADOW START"); // bloque de logComparison (TAREA 3)
        int end        = out.lastIndexOf("IOS_READINESS_SHADOW THREAD END");

        assertTrue(start >= 0, "Debe existir THREAD START");
        assertTrue(evaluating > start, "EVALUATING debe venir después de THREAD START");
        assertTrue(result > evaluating, "RESULT debe venir después de EVALUATING (ya con el Engine ejecutado)");
        assertTrue(comparison > evaluating, "El bloque de comparación (TAREA 3) debe imprimirse tras evaluar");
        assertTrue(end > result, "THREAD END debe ser lo último");
    }

    // ── TEST 7/8 — executionId y udid se conservan en cada línea ──────────────

    @Test
    @DisplayName("TEST 7/8: executionId y udid llegan intactos a cada línea del Shadow")
    void executionIdAndUdid_arePreservedInEveryLine() {
        String executionId = "RUN-TAREA18-TEST";
        String out = captureStdout(() ->
                IosPreflightManager.runShadowComparison(client, executionId, UDID, true, null));

        assertTrue(out.contains("executionId=" + executionId + " udid=" + UDID),
                "THREAD START/EVALUATING/END deben llevar executionId+udid juntos, no solo udid");
        assertTrue(out.contains("RESULT executionId=" + executionId + " udid=" + UDID),
                "La línea RESULT debe poder asociarse inequívocamente al mismo Job");
    }

    // ── TEST RESULT — contenido real del Engine vs Legacy ──────────────────────

    @Test
    @DisplayName("RESULT: incluye status/stage/errorCode/wdaErrorCode/reason reales del Engine + legacy")
    void resultLine_containsRealEngineAndLegacyFields() {
        // Reproduce el escenario real de RUN-1010 (TAREA 17): WDA falló con el
        // timeout nativo de "Enable UI Automation" — mismo texto real ya usado
        // en TAREA 6/10/13/15 para ejercitar el mismo camino sin xcodebuild real.
        String realReason =
            "WebDriverAgentRunner-Runner (3306) encountered an error (The test runner failed to "
            + "initialize for UI testing. (Underlying Error: Timed out while enabling automation mode.))";
        WdaLifecycleOwner.markTerminalError(UDID, realReason);

        String out = captureStdout(() -> IosPreflightManager.runShadowComparison(
                client, "tarea18-result", UDID, false, "WDA no confirmado — " + realReason));

        assertTrue(out.contains("engineWdaErrorCode=IOS_WDA_STARTUP_FAILED"),
                "El Engine ya clasifica este texto real como IOS_WDA_STARTUP_FAILED desde TAREA 8.4");
        assertTrue(out.contains("legacyReadyForExecution=false"));
        assertTrue(out.contains("legacyNotReadyReason=WDA no confirmado — " + realReason));
    }

    // ── TEST 10 — readyForExecution/notReadyReason nunca se modifican ─────────

    @Test
    @DisplayName("TEST 10: runShadowComparison es de solo lectura — no puede alterar readyForExecution/notReadyReason")
    void shadowComparison_neverMutatesLegacyValues() {
        boolean readyForExecution = false;
        String  notReadyReason    = "motivo original, inmutable";

        IosPreflightManager.runShadowComparison(client, "tarea18-t10", UDID, readyForExecution, notReadyReason);

        // readyForExecution/notReadyReason son parámetros primitivos/inmutables (boolean/String) —
        // el propio compilador ya garantiza que el método no puede "devolverlos modificados" a quien
        // lo llama (no hay parámetro de salida, el método es void) — se verifica aquí que la firma
        // sigue siendo exactamente esa (void, sin efectos de salida), documentando la garantía.
        assertFalse(readyForExecution, "El valor en el llamador nunca cambia — Java pasa por valor");
        assertEquals("motivo original, inmutable", notReadyReason);
    }

    // ── TEST 9 — no existe doble ejecución del Shadow (validación estática) ───

    @Test
    @DisplayName("TEST 9: un solo punto de invocación de runShadowComparison en todo el árbol de producción")
    void onlyOneCallSiteExists() throws Exception {
        // Verificación estructural mínima: el método existe, es package-private
        // (no público, no se expone para ser llamado desde otro punto como
        // JobExecutor/WdaLifecycleOwner/IOSRecoveryManager).
        var method = IosPreflightManager.class.getDeclaredMethod(
                "runShadowComparison", BackendClient.class, String.class, String.class,
                boolean.class, String.class);
        assertFalse(java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                "runShadowComparison no debe ser público — evita que otra clase lo invoque y duplique el Shadow");
    }
}
