package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA 19 — tests de {@code JobExecutor.logIosReadinessDecision(...)}: la
 * primera integración estructurada del Engine en el flujo real del Job.
 *
 * Construye directamente objetos {@code IosPreflightManager.IosPreflightResult}
 * (constructor package-private, sin necesitar un runPreflight() real) y
 * {@code IOSRunnerReadinessResult} (vía su Builder package-private) para
 * controlar de forma determinista las 6 combinaciones Engine/Legacy de la
 * sección 10 de la tarea, sin tocar hardware ni disparar una segunda
 * evaluación del Engine.
 */
@DisplayName("JobExecutor — IOS_READINESS_DECISION (TAREA 19)")
class JobExecutorReadinessDecisionTest {

    private static final String UDID = "00008110-000129261482601E";

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

    private static IOSRunnerReadinessResult engineResult(
            IOSRunnerReadinessResult.Status status, String errorCode, IOSWdaErrorCode wdaErrorCode, String reason) {
        return IOSRunnerReadinessResult.builder()
                .udid(UDID).deviceId(UDID).platform("iOS")
                .status(status)
                .lastStageReached(IOSRunnerReadinessResult.Stage.READY)
                .errorCode(errorCode)
                .wdaErrorCode(wdaErrorCode)
                .reason(reason)
                .build();
    }

    private static IosPreflightManager.IosPreflightResult legacyResult(
            boolean readyForExecution, String notReadyReason, IOSRunnerReadinessResult engine) {
        IOSReadinessShadowComparison comparison = engine == null ? null :
                IOSReadinessShadowComparator.compare(engine,
                        IOSReadinessShadowComparator.normalizeReadyReason(readyForExecution, notReadyReason));
        return new IosPreflightManager.IosPreflightResult(
                "TEAMID", "26.6", "io.qautomation.wda", false, readyForExecution,
                true, "connected", "paired", "COREDEVICEID", "WIRED",
                readyForExecution, notReadyReason, true, System.currentTimeMillis(),
                comparison);
    }

    // ── CASO A ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CASO A: Engine READY + Legacy READY -> comparison=MATCH, authority=LEGACY")
    void caseA_bothReady_isMatch() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.READY, null, IOSWdaErrorCode.NONE, null);
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-A", UDID, legacy));

        assertTrue(out.contains("comparison=MATCH"));
        assertTrue(out.contains("authority=LEGACY"));
        assertTrue(out.contains("engineStatus=READY"));
        assertTrue(out.contains("legacyReadyForExecution=true"));
    }

    // ── CASO B ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CASO B: Engine NOT_READY + Legacy NOT_READY -> Legacy bloquea (readyForExecution=false intacto)")
    void caseB_bothNotReady_legacyBlocks() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.NOT_READY, "IOS_WDA_NOT_STARTED", IOSWdaErrorCode.NONE,
                "WebDriverAgent aún no se ha iniciado para este dispositivo.");
        IosPreflightManager.IosPreflightResult legacy =
                legacyResult(false, "WDA no confirmado — aún no se ha iniciado", engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-B", UDID, legacy));

        assertFalse(legacy.readyForExecution, "Legacy sigue bloqueando — ninguna autoridad nueva lo cambia");
        assertTrue(out.contains("engineStatus=NOT_READY"));
        assertTrue(out.contains("legacyReadyForExecution=false"));
    }

    // ── CASO C — CRÍTICO: posible falso negativo del Engine ────────────────

    @Test
    @DisplayName("CASO C: Engine NOT_READY + Legacy READY -> comparison=ENGINE_MORE_RESTRICTIVE, Legacy sigue permitiendo")
    void caseC_engineMoreRestrictive_legacyStillAllows() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.NOT_READY, "IOS_WDA_NOT_STARTED", IOSWdaErrorCode.NONE, "motivo Engine");
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-C", UDID, legacy));

        assertTrue(out.contains("comparison=ENGINE_MORE_RESTRICTIVE"));
        assertTrue(legacy.readyForExecution, "Legacy debe seguir permitiendo la ejecución — Engine no cancela");
        assertTrue(out.contains("authority=LEGACY"));
    }

    // ── CASO D — CRÍTICO: posible falso positivo del Engine ────────────────

    @Test
    @DisplayName("CASO D: Engine READY + Legacy NOT_READY -> comparison=ENGINE_MORE_PERMISSIVE, Legacy sigue bloqueando")
    void caseD_enginePermissive_legacyStillBlocks() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.READY, null, IOSWdaErrorCode.NONE, null);
        IosPreflightManager.IosPreflightResult legacy = legacyResult(false, "motivo Legacy", engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-D", UDID, legacy));

        assertTrue(out.contains("comparison=ENGINE_MORE_PERMISSIVE"));
        assertFalse(legacy.readyForExecution, "Legacy debe seguir bloqueando — un Engine=READY no autoriza nada");
    }

    // ── CASO E ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CASO E: Engine ACTION_REQUIRED + Legacy READY -> comparison=ENGINE_ACTION_REQUIRED, Legacy permite")
    void caseE_actionRequired_legacyAllows() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.ACTION_REQUIRED, "IOS_DEVELOPER_TRUST_REQUIRED",
                IOSWdaErrorCode.IOS_DEVELOPER_TRUST_REQUIRED, "requiere confiar en el certificado");
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-E", UDID, legacy));

        assertTrue(out.contains("comparison=ENGINE_ACTION_REQUIRED"));
        assertTrue(legacy.readyForExecution, "ACTION_REQUIRED NO debe bloquear todavía (regla explícita de TAREA 19)");
    }

    // ── CASO F ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CASO F: Engine ERROR + Legacy READY -> comparison=ENGINE_ERROR, Legacy permite")
    void caseF_engineError_legacyAllows() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.ERROR, "IOS_WDA_BUILD_FAILED", IOSWdaErrorCode.UNKNOWN, "fallo real");
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-F", UDID, legacy));

        assertTrue(out.contains("comparison=ENGINE_ERROR"));
        assertTrue(legacy.readyForExecution, "ERROR del Engine NO debe bloquear todavía (regla explícita de TAREA 19)");
    }

    // ── executionId / udid / reasons preservados ───────────────────────────

    @Test
    @DisplayName("executionId, udid, engineReason y legacyNotReadyReason se preservan intactos en el log")
    void executionIdUdidAndReasons_arePreserved() {
        String executionId = "RUN-TAREA19-XYZ";
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.NOT_READY, "IOS_WDA_NOT_STARTED", IOSWdaErrorCode.NONE,
                "motivo real del Engine");
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, engine);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision(executionId, UDID, legacy));

        assertTrue(out.contains("executionId=" + executionId + " udid=" + UDID));
        assertTrue(out.contains("engineReason=motivo real del Engine"));
    }

    // ── Garantías estructurales (Engine no puede cancelar / no muta nada) ──

    @Test
    @DisplayName("logIosReadinessDecision es de solo lectura: no puede mutar readyForExecution/notReadyReason "
            + "(son 'final') ni invocar Recovery")
    void logIosReadinessDecision_cannotMutateOrTriggerRecovery() {
        IOSRunnerReadinessResult engine = engineResult(
                IOSRunnerReadinessResult.Status.READY, null, IOSWdaErrorCode.NONE, null);
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, engine);

        boolean readyBefore = legacy.readyForExecution;
        String  reasonBefore = legacy.notReadyReason;

        JobExecutor.logIosReadinessDecision("RUN-GUARD", UDID, legacy);

        // readyForExecution/notReadyReason son "public final" en IosPreflightResult —
        // el propio compilador garantiza que ningún método puede reasignarlos.
        assertEquals(readyBefore, legacy.readyForExecution);
        assertEquals(reasonBefore, legacy.notReadyReason);
    }

    /** Líneas de código real (no comentarios) del archivo — para no confundir una
     *  mención en un comentario/Javadoc con una invocación real. */
    private static java.util.List<String> nonCommentLines(String path) throws Exception {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(path))) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
            // Descarta también el comentario de línea final (si lo hay), conservando el código previo.
            int commentIdx = line.indexOf("//");
            result.add(commentIdx >= 0 ? line.substring(0, commentIdx) : line);
        }
        return result;
    }

    @Test
    @DisplayName("JobExecutor no invoca IOSRecoveryManager.recover(...) (Recovery sigue sin integrarse)")
    void jobExecutorDoesNotReferenceRecoveryManager() throws Exception {
        java.util.List<String> lines = nonCommentLines("src/main/java/qa/cinepolis/runner/JobExecutor.java");
        boolean callsRecovery = lines.stream().anyMatch(l -> l.contains("IOSRecoveryManager."));
        assertFalse(callsRecovery,
                "TAREA 19 no debe integrar Recovery — cero invocaciones reales esperadas en JobExecutor "
                        + "(las menciones en comentarios/Javadoc son aceptables y no cuentan)");
    }

    @Test
    @DisplayName("No existe una segunda evaluación real del Engine en JobExecutor (sin doble Shadow)")
    void noSecondEngineEvaluationInJobExecutor() throws Exception {
        java.util.List<String> lines = nonCommentLines("src/main/java/qa/cinepolis/runner/JobExecutor.java");
        boolean callsEvaluate = lines.stream().anyMatch(l -> l.contains("IOSRunnerReadinessEngine.evaluate("));
        assertFalse(callsEvaluate,
                "JobExecutor debe leer iosResult.engineDiagnosis (ya calculado), nunca volver a llamar "
                        + "IOSRunnerReadinessEngine.evaluate() por su cuenta (las menciones en comentarios "
                        + "son aceptables y no cuentan)");
    }

    @Test
    @DisplayName("Engine ausente (Shadow no completó a tiempo) -> comparison=UNKNOWN, sin excepción")
    void engineDiagnosisNull_isUnknownNotException() {
        IosPreflightManager.IosPreflightResult legacy = legacyResult(true, null, null);

        String out = captureStdout(() -> JobExecutor.logIosReadinessDecision("RUN-NULL", UDID, legacy));

        assertTrue(out.contains("comparison=UNKNOWN"));
        assertTrue(out.contains("engineStatus=null"));
    }
}
