package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AllureSummaryReader {

    public static class Stats {
        public final int total;
        public final int passed;
        public final int failed;
        public final int broken;
        public final int skipped;
        public final int unknown;
        public final long durationMs;

        public Stats(int total, int passed, int failed,
                     int broken, int skipped, int unknown,
                     long durationMs) {
            this.total = total;
            this.passed = passed;
            this.failed = failed;
            this.broken = broken;
            this.skipped = skipped;
            this.unknown = unknown;
            this.durationMs = durationMs;
        }
    }

    /**
     * Lee summary.json desde un "reportRoot" (carpeta donde está index.html y widgets/).
     * Ejemplos de reportRoot:
     * - build/reports/allure-report/allureReport
     * - build/allure-report
     */
    public static Stats readFromReportRoot(Path reportRoot) {
        try {
            Path summary = reportRoot.resolve("widgets").resolve("summary.json");
            String json = Files.readString(summary);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode stat = root.get("statistic");
            JsonNode time = root.get("time");

            long durationMs = 0L;
            if (time != null && time.has("duration")) {
                durationMs = time.get("duration").asLong();
            }

            return new Stats(
                    stat.get("total").asInt(),
                    stat.get("passed").asInt(),
                    stat.get("failed").asInt(),
                    stat.get("broken").asInt(),
                    stat.get("skipped").asInt(),
                    stat.get("unknown").asInt(),
                    durationMs
            );
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo summary.json desde: " + reportRoot, e);
        }
    }

    /**
     * Busca summary.json en rutas típicas (por si cambiaste dónde se genera el reporte).
     * NO cambia tu lógica: solo ayuda a no apuntar al folder equivocado.
     */
    public static Stats readAuto() {
        Path[] candidates = new Path[]{
                Paths.get("build", "reports", "allure-report", "allureReport"),
                Paths.get("build", "allure-report")
        };

        for (Path root : candidates) {
            Path summary = root.resolve("widgets").resolve("summary.json");
            if (Files.exists(summary)) {
                return readFromReportRoot(root);
            }
        }

        throw new RuntimeException("No se encontró summary.json en rutas conocidas.");
    }
}
