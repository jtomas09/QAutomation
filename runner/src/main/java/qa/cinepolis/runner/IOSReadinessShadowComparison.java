package qa.cinepolis.runner;

/**
 * TAREA 3 — resultado de comparar, para un mismo UDID y un mismo instante, el
 * veredicto de {@link IOSRunnerReadinessEngine} contra el veredicto ya
 * calculado por el flujo real (IosPreflightManager/DeviceReadinessEvaluator,
 * según el punto de integración — ver {@link IOSReadinessShadowComparator}).
 *
 * Puramente informativo: nada en este tipo de dato ni en quien lo produce
 * puede alterar readyForExecution, iniciar/detener WDA, ni afectar la
 * ejecución real. Ver IOSReadinessShadowComparator para la política de
 * normalización.
 */
public final class IOSReadinessShadowComparison {

    public final String                     udid;
    public final IOSRunnerReadinessResult   engineResult;
    /** Veredicto del flujo actual, YA normalizado a una de las 7 categorías comunes. */
    public final String                     currentFlowStatus;
    public final boolean                    verdictMatches;
    /** null cuando verdictMatches=true. */
    public final String                     mismatchReason;
    public final long                       timestamp;

    IOSReadinessShadowComparison(String udid, IOSRunnerReadinessResult engineResult,
                                  String currentFlowStatus, boolean verdictMatches,
                                  String mismatchReason, long timestamp) {
        this.udid              = udid;
        this.engineResult      = engineResult;
        this.currentFlowStatus = currentFlowStatus;
        this.verdictMatches    = verdictMatches;
        this.mismatchReason    = mismatchReason;
        this.timestamp         = timestamp;
    }

    @Override
    public String toString() {
        return "IOSReadinessShadowComparison{"
                + "udid=" + udid
                + ", engineVerdict=" + (engineResult != null ? engineResult.status : "null")
                + ", currentFlowStatus=" + currentFlowStatus
                + ", verdictMatches=" + verdictMatches
                + (mismatchReason != null ? ", mismatchReason=" + mismatchReason : "")
                + '}';
    }
}
