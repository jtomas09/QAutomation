package qa.cinepolis.backend.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.TestCaseResult;
import qa.cinepolis.backend.model.VideoQueryResult;
import qa.cinepolis.backend.model.VideoRecord;
import qa.cinepolis.backend.model.VideoSuiteSummary;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Almacena videos organizados por Suite: cada suite tiene su propia carpeta
 * (videos/{suiteSlug}/{id}.mp4), nunca se mezclan archivos de distintas suites.
 * El índice de metadatos se persiste en videos/index.json para sobrevivir un
 * restart del backend (antes era puramente en memoria y se perdía todo al reiniciar).
 */
@Component
public class VideoStore {

    private static final Logger log = LoggerFactory.getLogger(VideoStore.class);

    private static final Path ROOT       = Paths.get("videos");
    private static final Path INDEX_PATH = ROOT.resolve("index.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionStore executionStore;
    private final ConcurrentHashMap<String, VideoRecord> map = new ConcurrentHashMap<>();

    public VideoStore(ExecutionStore executionStore) throws IOException {
        this.executionStore = executionStore;
        Files.createDirectories(ROOT);
    }

    @PostConstruct
    void loadFromDisk() {
        if (!Files.exists(INDEX_PATH)) {
            log.info("[VideoStore] {} no existe todavía — se creará en el primer video subido.",
                    INDEX_PATH.toAbsolutePath());
            return;
        }
        try {
            Persisted[] records = mapper.readValue(INDEX_PATH.toFile(), Persisted[].class);
            for (Persisted p : records) {
                map.put(p.id, p.toRecord());
            }
            log.info("[VideoStore] Índice cargado desde {} — {} video(s).",
                    INDEX_PATH.toAbsolutePath(), map.size());
        } catch (Exception e) {
            log.error("[VideoStore] No se pudo leer {} ({}: {}) — arrancando con índice vacío en memoria "
                    + "sin sobrescribir el archivo existente.",
                    INDEX_PATH.toAbsolutePath(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    public VideoRecord save(String executionId, String originalName,
                            String suiteName, String testName, byte[] data) throws IOException {
        String resolvedSuite = (suiteName != null && !suiteName.isBlank()) ? suiteName : "Sin Suite";
        Path   suiteDir      = ROOT.resolve(sanitizeFolderName(resolvedSuite));
        Files.createDirectories(suiteDir);

        String id   = UUID.randomUUID().toString();
        Path   file = suiteDir.resolve(id + ".mp4");
        Files.write(file, data);

        VideoRecord rec = new VideoRecord();
        rec.setId(id);
        rec.setExecutionId(executionId);
        rec.setOriginalName(originalName != null ? originalName : "video.mp4");
        rec.setSuiteName(resolvedSuite);
        rec.setTestName(testName != null ? testName : "");
        rec.setSizeBytes(data.length);
        rec.setCreatedAt(Instant.now());
        enrichFromExecution(rec, executionId, testName);

        map.put(id, rec);
        persist();
        return rec;
    }

    /** Cruza con la Execution para heredar status del caso, dispositivo y ambiente. */
    private void enrichFromExecution(VideoRecord rec, String executionId, String testName) {
        Execution exec = executionStore.findById(executionId).orElse(null);
        if (exec == null) {
            rec.setStatus("UNKNOWN");
            return;
        }
        rec.setDevice(exec.getDevice());
        rec.setEnv(exec.getEnv());
        rec.setStatus(resolveTestStatus(exec.getTestCases(), testName));
    }

    private String resolveTestStatus(List<TestCaseResult> testCases, String testName) {
        if (testCases == null || testCases.isEmpty() || testName == null || testName.isBlank()) {
            return "UNKNOWN";
        }
        String target = normalizeForMatch(testName);
        for (TestCaseResult tc : testCases) {
            String candidate = normalizeForMatch(tc.name());
            if (candidate.equals(target) || candidate.contains(target) || target.contains(candidate)) {
                return tc.status();
            }
        }
        return "UNKNOWN";
    }

    private String normalizeForMatch(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public Path getFilePath(VideoRecord rec) {
        String resolvedSuite = (rec.getSuiteName() != null && !rec.getSuiteName().isBlank())
                ? rec.getSuiteName() : "Sin Suite";
        return ROOT.resolve(sanitizeFolderName(resolvedSuite)).resolve(rec.getId() + ".mp4");
    }

    public Optional<VideoRecord> findById(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public List<VideoRecord> findAll() {
        return map.values().stream()
                .sorted(Comparator.comparing(VideoRecord::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<VideoSuiteSummary> getSuiteSummaries() {
        Map<String, List<VideoRecord>> bySuite = map.values().stream()
                .collect(Collectors.groupingBy(v ->
                        (v.getSuiteName() != null && !v.getSuiteName().isBlank()) ? v.getSuiteName() : "Sin Suite"));

        List<VideoSuiteSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<VideoRecord>> e : bySuite.entrySet()) {
            List<VideoRecord> videos = e.getValue();
            Instant last = videos.stream().map(VideoRecord::getCreatedAt)
                    .max(Comparator.naturalOrder()).orElse(null);
            long totalSize = videos.stream().mapToLong(VideoRecord::getSizeBytes).sum();
            summaries.add(new VideoSuiteSummary(e.getKey(), videos.size(), last, totalSize,
                    overallStatusOf(videos)));
        }
        summaries.sort(Comparator.comparing(VideoSuiteSummary::getLastExecutionAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return summaries;
    }

    private String overallStatusOf(List<VideoRecord> videos) {
        boolean anyFail    = videos.stream().anyMatch(v -> "FAIL".equalsIgnoreCase(v.getStatus()));
        boolean anyPass    = videos.stream().anyMatch(v -> "PASS".equalsIgnoreCase(v.getStatus()));
        boolean anyUnknown = videos.stream().anyMatch(v ->
                v.getStatus() == null || "UNKNOWN".equalsIgnoreCase(v.getStatus()));
        if (anyFail && anyPass)              return "MIXED";
        if (anyFail)                          return "FAILED";
        if (anyPass && !anyUnknown)           return "PASSED";
        if (anyPass)                          return "MIXED";
        return "UNKNOWN";
    }

    /** Lista filtrada y paginada de videos de UNA suite (pantalla "Nivel 2"). */
    public VideoQueryResult query(String suite, String q, String status, String device, String env,
                                  int page, int pageSize) {
        List<VideoRecord> filtered = map.values().stream()
                .filter(v -> suite == null || suite.isBlank()
                        || suite.equalsIgnoreCase(v.getSuiteName() != null ? v.getSuiteName() : "Sin Suite"))
                .filter(v -> q == null || q.isBlank()
                        || containsIgnoreCase(v.getTestName(), q) || containsIgnoreCase(v.getOriginalName(), q))
                .filter(v -> status == null || status.isBlank() || status.equalsIgnoreCase(v.getStatus()))
                .filter(v -> device == null || device.isBlank() || containsIgnoreCase(v.getDevice(), device))
                .filter(v -> env == null || env.isBlank() || containsIgnoreCase(v.getEnv(), env))
                .sorted(Comparator.comparing(VideoRecord::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int total    = filtered.size();
        int safePage = Math.max(page, 0);
        int size     = pageSize > 0 ? pageSize : 24;
        int from     = Math.min(safePage * size, total);
        int to       = Math.min(from + size, total);
        return new VideoQueryResult(filtered.subList(from, to), total, safePage, size);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    public void delete(String id) throws IOException {
        VideoRecord rec = map.remove(id);
        if (rec != null) {
            Files.deleteIfExists(getFilePath(rec));
            persist();
        }
    }

    /** Elimina TODOS los videos de una suite (archivo, carpeta e índice). */
    public void deleteSuite(String suiteName) throws IOException {
        String resolved = (suiteName != null && !suiteName.isBlank()) ? suiteName : "Sin Suite";
        List<String> ids = map.values().stream()
                .filter(v -> resolved.equalsIgnoreCase(v.getSuiteName() != null ? v.getSuiteName() : "Sin Suite"))
                .map(VideoRecord::getId)
                .collect(Collectors.toList());
        for (String id : ids) {
            map.remove(id);
        }
        Path suiteDir = ROOT.resolve(sanitizeFolderName(resolved));
        if (Files.exists(suiteDir)) {
            try (var stream = Files.list(suiteDir)) {
                for (Path f : (Iterable<Path>) stream::iterator) {
                    Files.deleteIfExists(f);
                }
            }
            Files.deleteIfExists(suiteDir);
        }
        persist();
    }

    private String sanitizeFolderName(String name) {
        String cleaned = name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "Sin Suite" : cleaned;
    }

    private synchronized void persist() {
        try {
            Persisted[] records = map.values().stream()
                    .map(Persisted::fromRecord)
                    .toArray(Persisted[]::new);
            mapper.writerWithDefaultPrettyPrinter().writeValue(INDEX_PATH.toFile(), records);
        } catch (Exception e) {
            log.error("[VideoStore] No se pudo persistir el índice en {}: {}. "
                    + "Los videos quedan disponibles solo en memoria para esta sesión del backend.",
                    INDEX_PATH.toAbsolutePath(), e.getMessage(), e);
        }
    }

    /** Forma serializada en disco — createdAt como String ISO-8601 para no depender de módulos Jackson extra. */
    private static class Persisted {
        public String id;
        public String executionId;
        public String suiteName;
        public String testName;
        public String originalName;
        public long   sizeBytes;
        public String createdAt;
        public String status;
        public String device;
        public String env;

        public Persisted() {}

        static Persisted fromRecord(VideoRecord r) {
            Persisted p = new Persisted();
            p.id           = r.getId();
            p.executionId  = r.getExecutionId();
            p.suiteName    = r.getSuiteName();
            p.testName     = r.getTestName();
            p.originalName = r.getOriginalName();
            p.sizeBytes    = r.getSizeBytes();
            p.createdAt    = r.getCreatedAt() != null ? r.getCreatedAt().toString() : Instant.now().toString();
            p.status       = r.getStatus();
            p.device       = r.getDevice();
            p.env          = r.getEnv();
            return p;
        }

        VideoRecord toRecord() {
            VideoRecord r = new VideoRecord();
            r.setId(id);
            r.setExecutionId(executionId);
            r.setSuiteName(suiteName);
            r.setTestName(testName);
            r.setOriginalName(originalName);
            r.setSizeBytes(sizeBytes);
            r.setCreatedAt(Instant.parse(createdAt));
            r.setStatus(status);
            r.setDevice(device);
            r.setEnv(env);
            return r;
        }
    }
}
