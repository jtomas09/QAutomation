package qa.cinepolis.runner;

import java.util.ArrayList;
import java.util.List;

/**
 * Checklist de pre-flight de Network Monitoring — construido de forma incremental
 * por {@link NetworkMonitoringManager#start} a medida que cada paso real ocurre
 * (nunca re-implementa las comprobaciones por separado, para no arriesgar que
 * diverjan de lo que realmente pasó).
 *
 * Resultado: READY solo si TODOS los checks agregados pasaron; NOT_READY en
 * cuanto uno falla, con la razón exacta. Nunca lanza, nunca bloquea el Job —
 * quien lo consulta decide degradar a "sin Network Monitoring" en NOT_READY.
 */
public final class NetworkRuntimeReadiness {

    public record Check(String name, boolean passed, String detail) {}

    private final List<Check> checks = new ArrayList<>();
    private boolean stopped = false;

    public void add(String name, boolean passed, String detail) {
        if (stopped) return; // una vez NOT_READY, no tiene sentido seguir evaluando pasos posteriores
        checks.add(new Check(name, passed, detail));
        if (!passed) stopped = true;
    }

    public boolean isReady() {
        return !checks.isEmpty() && checks.stream().allMatch(Check::passed);
    }

    public String failureReason() {
        return checks.stream().filter(c -> !c.passed()).findFirst()
                .map(c -> c.name() + ": " + c.detail())
                .orElse(null);
    }

    /** Bloque de log legible — mismo espíritu que el resumen de estado que ya usan otros managers del Runner. */
    public String renderSummary() {
        StringBuilder sb = new StringBuilder();
        for (Check c : checks) {
            sb.append("[NETWORK] ").append(c.passed() ? "✓" : "✗").append(' ').append(c.name());
            if (c.detail() != null && !c.detail().isBlank()) sb.append(" (").append(c.detail()).append(')');
            sb.append('\n');
        }
        if (isReady()) {
            sb.append("[NETWORK] Network Runtime Readiness: READY");
        } else {
            sb.append("[NETWORK] Network Monitoring unavailable\n");
            sb.append("[NETWORK] Reason: ").append(failureReason());
        }
        return sb.toString();
    }
}
