package qa.cinepolis.backend.controller;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes runner binary version info for the auto-update mechanism.
 *
 * UpdateManager (runner) polls GET /api/runner/version to check if a newer
 * JAR is available. When a new version is found it downloads the JAR via
 * GET /api/runner/download/jar (RunnerDownloadController) and validates the
 * SHA256 returned here before rotating the active binary.
 *
 * SHA256 is computed once at startup from the classpath JAR.
 * If the JAR is absent (dev mode), sha256 is omitted — UpdateManager skips
 * the integrity check gracefully.
 */
@RestController
@RequestMapping("/api/runner")
public class VersionController {

    private static final Logger log = LoggerFactory.getLogger(VersionController.class);

    @Value("${runner.version:5.0.0}")
    private String runnerVersion;

    private String jarSha256; // computed once at startup

    @PostConstruct
    public void init() {
        ClassPathResource jar = new ClassPathResource("installers/cinepolis-runner.jar");
        if (!jar.exists()) {
            log.warn("[Version] cinepolis-runner.jar no encontrado — sha256 no disponible");
            return;
        }
        try (InputStream in = new BufferedInputStream(jar.getInputStream(), 1 << 16)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) > 0) digest.update(buf, 0, len);
            jarSha256 = toHex(digest.digest());
            log.info("[Version] SHA256 del Runner JAR: {}...", jarSha256.substring(0, 16));
        } catch (Exception e) {
            log.warn("[Version] No se pudo calcular SHA256 del JAR: {}", e.getMessage());
        }
    }

    /**
     * GET /api/runner/version
     *
     * Response:
     * {
     *   "version":     "5.0.0",
     *   "downloadUrl": "/api/runner/download/jar",
     *   "sha256":      "abc123..."   // omitted if JAR not present
     * }
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersion() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version",     runnerVersion);
        info.put("downloadUrl", "/api/runner/download/jar");
        if (jarSha256 != null) info.put("sha256", jarSha256);
        return ResponseEntity.ok(info);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
