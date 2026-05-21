package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.VideoRecord;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class VideoStore {

    private final Path storageDir;
    private final ConcurrentHashMap<String, VideoRecord> map = new ConcurrentHashMap<>();

    public VideoStore() throws IOException {
        this.storageDir = Paths.get("videos");
        Files.createDirectories(storageDir);
    }

    public VideoRecord save(String executionId, String originalName,
                            String suiteName, String testName, byte[] data) throws IOException {
        String id   = UUID.randomUUID().toString();
        Path   file = storageDir.resolve(id + ".mp4");
        Files.write(file, data);

        VideoRecord rec = new VideoRecord();
        rec.setId(id);
        rec.setExecutionId(executionId);
        rec.setOriginalName(originalName != null ? originalName : "video.mp4");
        rec.setSuiteName(suiteName != null ? suiteName : "");
        rec.setTestName(testName   != null ? testName  : "");
        rec.setSizeBytes(data.length);
        rec.setCreatedAt(Instant.now());

        map.put(id, rec);
        return rec;
    }

    public List<VideoRecord> findAll() {
        return map.values().stream()
                .sorted(Comparator.comparing(VideoRecord::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<VideoRecord> findById(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public Path getFilePath(VideoRecord rec) {
        return storageDir.resolve(rec.getId() + ".mp4");
    }

    public void delete(String id) throws IOException {
        VideoRecord rec = map.remove(id);
        if (rec != null) {
            Files.deleteIfExists(getFilePath(rec));
        }
    }
}
