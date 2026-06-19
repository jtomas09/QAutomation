package qa.cinepolis.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves Automation QA Runner installer files.
 *
 * File resolution order per platform (cascade):
 *   1. Proper installer  (installers/AutomationQA-Runner-Setup.exe / .pkg)
 *   2. Temp script       (installers/AutomationQA-Runner-Windows.bat / -macOS.sh)
 *   3. Not available     (404 JSON)
 *
 * Endpoints:
 *   GET /api/runner/download/availability  → JSON map of what is available per platform
 *   GET /api/runner/download/{platform}    → binary file or JSON error
 *
 * To deploy proper installers: place files in
 *   backend/src/main/resources/installers/
 */
@RestController
@RequestMapping("/api/runner/download")
@CrossOrigin(origins = "*", exposedHeaders = { HttpHeaders.CONTENT_DISPOSITION })
public class RunnerDownloadController {

    private static final Logger log = LoggerFactory.getLogger(RunnerDownloadController.class);

    // ── Cascade entries: [0] proper installer, [1] temp fallback ────────────

    private static final Map<String, String[]> PROPER = Map.of(
            "windows", new String[]{ "AutomationQA-Runner-Setup.exe" },
            "macos",   new String[]{ "AutomationQA-Runner.pkg", "AutomationQA-Runner.dmg" },
            "linux",   new String[]{ "AutomationQA-Runner-linux.tar.gz" }
    );

    private static final Map<String, String> TEMP = Map.of(
            "windows", "AutomationQA-Runner-Windows.bat",
            "macos",   "AutomationQA-Runner-macOS.sh",
            "linux",   "AutomationQA-Runner-macOS.sh"   // same sh, detects Linux internally
    );

    // Human-readable labels
    private static final Map<String, String> TEMP_LABELS = Map.of(
            "windows", "Automation QA Runner para Windows",
            "macos",   "Automation QA Runner para macOS",
            "linux",   "Automation QA Runner para Linux"
    );

    // ── Availability endpoint ────────────────────────────────────────────────

    /**
     * Returns what is available per platform.
     * Frontend calls this to decide which UI to show.
     *
     * Response format per platform:
     *   { available: boolean, type: "proper"|"temp"|"unavailable",
     *     filename: "...", label: "..." }
     */
    @GetMapping("/availability")
    public Map<String, Object> availability() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String platform : new String[]{ "windows", "macos", "linux" }) {
            result.put(platform, resolvePackageInfo(platform));
        }
        return result;
    }

    // ── Download endpoint ────────────────────────────────────────────────────

    @GetMapping("/{platform}")
    public ResponseEntity<?> download(@PathVariable String platform) {
        String p = platform.toLowerCase();

        if (!PROPER.containsKey(p) && !TEMP.containsKey(p)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("success", false, "message", "Plataforma no soportada: " + platform));
        }

        // ── Priority 1: proper installer ─────────────────────────────────────
        if (PROPER.containsKey(p)) {
            for (String fn : PROPER.get(p)) {
                ClassPathResource res = new ClassPathResource("installers/" + fn);
                if (res.exists()) {
                    return serveFile(res, fn);
                }
            }
        }

        // ── Priority 2: temp fallback script ─────────────────────────────────
        String tempFn = TEMP.get(p);
        if (tempFn != null) {
            ClassPathResource res = new ClassPathResource("installers/" + tempFn);
            if (res.exists()) {
                return serveFile(res, tempFn);
            }
        }

        // ── Priority 3: nothing available ────────────────────────────────────
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "success",  false,
                        "message",  "No hay una versión disponible del Runner para descargar.",
                        "platform", platform
                ));
    }

    // ── JAR download endpoint ────────────────────────────────────────────────

    /**
     * Serves the runner JAR from classpath for self-contained install scripts.
     * The JAR is compiled from runner/ and embedded at Docker build time as
     * backend/src/main/resources/installers/cinepolis-runner.jar.
     * Served to clients as automationqa-runner.jar.
     */
    @GetMapping("/jar")
    public ResponseEntity<?> downloadJar() {
        log.info("GET /api/runner/download/jar - solicitado");

        ClassPathResource res = new ClassPathResource("installers/cinepolis-runner.jar");

        if (!res.exists()) {
            log.warn("GET /api/runner/download/jar - HTTP 404: cinepolis-runner.jar no encontrado en classpath. " +
                     "Asegurate de que el Dockerfile compila el runner antes de empaquetar el backend.");
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("success", false, "message",
                            "El componente del Runner no está disponible en este momento."));
        }

        try {
            long size = res.contentLength();
            log.info("GET /api/runner/download/jar - HTTP 200 | filename: automationqa-runner.jar | " +
                     "Content-Type: application/octet-stream | Content-Length: {} bytes ({} KB)",
                     size, size / 1024);
            return serveFile(res, "automationqa-runner.jar", size);
        } catch (IOException e) {
            log.error("GET /api/runner/download/jar - error leyendo cinepolis-runner.jar: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("success", false, "message", "Error al leer el componente del Runner."));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> resolvePackageInfo(String platform) {
        // Check proper installer
        if (PROPER.containsKey(platform)) {
            for (String fn : PROPER.get(platform)) {
                ClassPathResource res = new ClassPathResource("installers/" + fn);
                if (res.exists()) {
                    return packageInfo(true, "proper", fn,
                            "Automation QA Runner para " + osLabel(platform));
                }
            }
        }

        // Check temp fallback
        String tempFn = TEMP.get(platform);
        if (tempFn != null) {
            ClassPathResource res = new ClassPathResource("installers/" + tempFn);
            if (res.exists()) {
                return packageInfo(true, "temp", tempFn,
                        TEMP_LABELS.getOrDefault(platform, "Script provisional"));
            }
        }

        return packageInfo(false, "unavailable", "", "No disponible");
    }

    private Map<String, Object> packageInfo(boolean available, String type, String filename, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available);
        m.put("type",      type);
        m.put("filename",  filename);
        m.put("label",     label);
        return m;
    }

    private ResponseEntity<Resource> serveFile(ClassPathResource resource, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private ResponseEntity<Resource> serveFile(ClassPathResource resource, String filename, long contentLength) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private String osLabel(String platform) {
        return switch (platform) {
            case "macos"  -> "macOS";
            case "linux"  -> "Linux";
            default       -> "Windows";
        };
    }
}
