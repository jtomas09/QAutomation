package qa.cinepolis.backend.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Serves Automation QA Runner installer binaries.
 *
 * Files must be placed under:
 *   backend/src/main/resources/installers/
 *
 * Expected files:
 *   AutomationQA-Runner-Setup.exe   (Windows)
 *   AutomationQA-Runner.pkg         (macOS)
 *   AutomationQA-Runner-linux.tar.gz (Linux)
 *
 * GET /api/runner/download/{platform}
 *
 * Returns:
 *   200 + application/octet-stream  → file download
 *   404 + application/json          → not available yet
 *   400 + application/json          → unknown platform
 */
@RestController
@RequestMapping("/api/runner/download")
@CrossOrigin(origins = "*", exposedHeaders = { HttpHeaders.CONTENT_DISPOSITION })
public class RunnerDownloadController {

    private static final Map<String, String> PLATFORM_FILES = Map.of(
            "windows", "AutomationQA-Runner-Setup.exe",
            "macos",   "AutomationQA-Runner.pkg",
            "linux",   "AutomationQA-Runner-linux.tar.gz"
    );

    @GetMapping("/{platform}")
    public ResponseEntity<?> download(@PathVariable String platform) {
        String filename = PLATFORM_FILES.get(platform.toLowerCase());

        if (filename == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "success", false,
                            "message", "Platform not supported: " + platform
                    ));
        }

        ClassPathResource resource = new ClassPathResource("installers/" + filename);

        if (!resource.exists()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "success", false,
                            "message", "No hay una versión disponible del Runner para descargar.",
                            "platform", platform,
                            "filename", filename
                    ));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body((Resource) resource);
    }
}
