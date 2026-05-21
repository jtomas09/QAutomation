package qa.cinepolis.backend.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.VideoRecord;
import qa.cinepolis.backend.store.VideoStore;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final VideoStore videoStore;

    public VideoController(VideoStore videoStore) {
        this.videoStore = videoStore;
    }

    /**
     * POST /api/executions/{execId}/videos
     * Runner uploads an MP4 as raw bytes (Content-Type: application/octet-stream).
     * Metadata arrives in custom headers: X-File-Name, X-Suite-Name, X-Test-Name.
     */
    @PostMapping(value = "/executions/{execId}/videos", consumes = "application/octet-stream")
    public ResponseEntity<VideoRecord> upload(
            @PathVariable String execId,
            @RequestHeader(value = "X-File-Name",  defaultValue = "video.mp4") String fileName,
            @RequestHeader(value = "X-Suite-Name", defaultValue = "")          String suiteName,
            @RequestHeader(value = "X-Test-Name",  defaultValue = "")          String testName,
            @RequestBody byte[] data) throws Exception {
        VideoRecord rec = videoStore.save(execId, fileName, suiteName, testName, data);
        return ResponseEntity.ok(rec);
    }

    /** GET /api/videos — list all videos, newest first */
    @GetMapping("/videos")
    public List<VideoRecord> listAll() {
        return videoStore.findAll();
    }

    /**
     * GET /api/videos/{id}/file — serve the MP4.
     * ?download=true  → Content-Disposition: attachment (saves to disk)
     * ?download=false → Content-Disposition: inline (browser player)
     */
    @GetMapping("/videos/{id}/file")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean download) {
        return videoStore.findById(id).map(rec -> {
            Path     path     = videoStore.getFilePath(rec);
            Resource resource = new FileSystemResource(path);
            String   cd       = (download ? "attachment" : "inline")
                                + "; filename=\"" + rec.getOriginalName() + "\"";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .<Resource>body(resource);
        }).orElseGet(() -> ResponseEntity.notFound().<Resource>build());
    }

    /** DELETE /api/videos/{id} — remove a video from disk and memory */
    @DeleteMapping("/videos/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable String id) throws Exception {
        videoStore.delete(id);
        return ResponseEntity.noContent().build();
    }
}
