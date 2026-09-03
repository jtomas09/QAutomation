package qa.cinepolis.runner;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * TAREA 3 — clase pura de comparación. NUNCA ejecuta xcodebuild, NUNCA inicia
 * ni detiene WDA, NUNCA instala nada, NUNCA hace recovery, NUNCA toca signing
 * ni provisioning ni el dispositivo. Solo compara dos veredictos ya calculados
 * por otras clases.
 *
 * Categorías comunes de normalización (ambos lados se expresan en una de estas
 * 7, nunca se comparan strings arbitrarios directamente):
 *
 *   READY, NOT_READY, ACTION_REQUIRED, ERROR, OFFLINE, RECOVERING, UNKNOWN
 *
 * El "flujo actual" en este repo NO tiene hoy un tipo de estado — solo un par
 * (readyForExecution: boolean, notReadyReason: String) tanto en
 * IosPreflightManager.IosPreflightResult como en DeviceReadinessEvaluator.Readiness.
 * {@link #normalizeReadyReason} traduce ese par a una de las 7 categorías
 * mediante coincidencia de texto contra las cadenas REALES que esas clases
 * generan (leídas directamente de su código fuente, no inventadas) — esto es,
 * en sí mismo, una limitación arquitectónica del flujo actual que esta tarea
 * documenta, no corrige.
 */
public final class IOSReadinessShadowComparator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private IOSReadinessShadowComparator() {}

    /**
     * Logging estructurado de una comparación ya realizada — nunca calcula
     * nada, solo imprime. {@code currentFlowSource} identifica de dónde salió
     * el veredicto "current" (p.ej. "DeviceReadinessEvaluator" o
     * "IosPreflightManager") para poder distinguir los dos puntos de
     * integración en los logs.
     */
    static void logComparison(IOSReadinessShadowComparison shadow, String currentFlowSource) {
        logLine("IOS_READINESS_SHADOW START");
        logLine("UDID=" + shadow.udid);
        logLine("CURRENT_FLOW_SOURCE=" + currentFlowSource);
        logLine("ENGINE_VERDICT=" + (shadow.engineResult != null ? shadow.engineResult.status.name() : "UNKNOWN"));
        logLine("CURRENT_VERDICT=" + shadow.currentFlowStatus);
        logLine("SHADOW_MATCH=" + shadow.verdictMatches);
        if (!shadow.verdictMatches) {
            logLine("MISMATCH_REASON=" + shadow.mismatchReason);
        }
        logLine("IOS_READINESS_SHADOW END");
    }

    private static void logLine(String line) {
        System.out.printf("[%s] %s%n", LocalTime.now().format(TS), line);
    }

    /**
     * Compara el veredicto del Engine contra un veredicto de flujo actual YA
     * normalizado (ver {@link #normalizeReadyReason}). No normaliza el lado
     * "currentFlowStatus" — eso es responsabilidad de quien integra esta clase
     * en cada punto real, precisamente para que el Comparator en sí permanezca
     * puro y testeable con strings sintéticos.
     */
    public static IOSReadinessShadowComparison compare(
            IOSRunnerReadinessResult engineResult, String currentFlowStatus) {

        String engineNorm  = engineResult != null && engineResult.status != null
                ? engineResult.status.name() : "UNKNOWN";
        String currentNorm = (currentFlowStatus == null || currentFlowStatus.isBlank())
                ? "UNKNOWN" : currentFlowStatus;

        boolean matches = engineNorm.equals(currentNorm);
        String  reason  = matches ? null : buildMismatchReason(engineResult, engineNorm, currentNorm);

        return new IOSReadinessShadowComparison(
                engineResult != null ? engineResult.udid : null,
                engineResult, currentNorm, matches, reason, System.currentTimeMillis());
    }

    /**
     * Traduce el par (readyForExecution, notReadyReason) que YA producen tanto
     * IosPreflightManager como DeviceReadinessEvaluator a una de las 7
     * categorías comunes. Basado únicamente en las cadenas reales que esas dos
     * clases generan hoy (ver Javadoc de clase) — si el texto no coincide con
     * ninguna conocida, el resultado es UNKNOWN, nunca una suposición.
     */
    public static String normalizeReadyReason(boolean readyForExecution, String notReadyReason) {
        if (readyForExecution || notReadyReason == null) return "READY";
        String r = notReadyReason.toLowerCase();

        if (r.contains("pantalla bloqueada"))                         return "ACTION_REQUIRED";
        if (r.contains("no emparejado"))                               return "ACTION_REQUIRED";
        if (r.contains("developer app certificate") && r.contains("not trusted")) return "ACTION_REQUIRED";
        if (r.contains("no accounts") || r.contains("no profiles for")
                || r.contains("invalid trust settings"))               return "ACTION_REQUIRED";
        if (r.contains("appium no disponible") || r.contains("xcode no disponible")) return "ERROR";
        if (r.contains("tipo de transporte no identificado"))          return "OFFLINE";
        if (r.contains("túnel coredevice") || r.contains("tunel coredevice")
                || r.contains("wi-fi") || r.contains("wifi"))          return "OFFLINE";
        if (r.startsWith("wda no confirmado"))                         return "ERROR";

        return "UNKNOWN";
    }

    private static String buildMismatchReason(
            IOSRunnerReadinessResult engineResult, String engineNorm, String currentNorm) {

        StringBuilder sb = new StringBuilder();
        sb.append("Engine=").append(engineNorm);
        if (engineResult != null && engineResult.errorCode != null) {
            sb.append(" (").append(engineResult.errorCode).append(")");
        }
        sb.append(" vs CurrentFlow=").append(currentNorm).append(".");

        String errorCode = engineResult != null ? engineResult.errorCode : null;

        if ("IOS_DEVELOPER_TRUST_REQUIRED".equals(errorCode) && "ERROR".equals(currentNorm)) {
            sb.append(" ARCHITECTURAL_OBSERVATION: el flujo actual no distingue Developer Trust "
                    + "de un fallo genérico de build — WdaLifecycleOwner captura la ÚLTIMA línea del "
                    + "bloque \"Testing failed:\" de xcodebuild, que en la práctica suele ser el "
                    + "resumen genérico (\"Testing cancelled because the build failed.\"), no la línea "
                    + "específica (\"Invalid trust settings\"/\"No Accounts\") — evidencia real de esta "
                    + "sesión: RUN-1005/1006/1007 mostraron notReadyReason=\"WDA no confirmado — "
                    + "Testing cancelled because the build failed.\" para este exacto escenario.");
        } else if (("IOS_WDA_NOT_STARTED".equals(errorCode) || "IOS_WDA_BUILDING".equals(errorCode))
                && "READY".equals(currentNorm)) {
            sb.append(" ARCHITECTURAL_OBSERVATION: este currentFlowStatus probablemente proviene de "
                    + "DeviceReadinessEvaluator (conectividad/Appium/Xcode), que NUNCA considera el "
                    + "estado real de WDA — puede reportar \"listo\" mientras WDA todavía no arrancó.");
        }

        if (engineResult != null && engineResult.status == IOSRunnerReadinessResult.Status.RECOVERING) {
            sb.append(" RECOVERING_SEMANTICS_REVIEW_REQUIRED: IOSRunnerReadinessEngine es de solo "
                    + "lectura — RECOVERING aquí significa \"WDA aún no se ha iniciado o se está "
                    + "compilando en otro hilo\", nunca que este Engine esté ejecutando una "
                    + "recuperación. El nombre del estado puede sugerir lo contrario.");
        }

        return sb.toString();
    }
}
