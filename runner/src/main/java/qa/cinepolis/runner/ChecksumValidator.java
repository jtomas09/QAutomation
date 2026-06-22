package qa.cinepolis.runner;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 integrity validation for all embedded components.
 *
 * Usage pattern:
 *   // After first install — store baseline:
 *   ChecksumValidator.writeBaseline(jarPath);
 *
 *   // On every health check — compare against baseline:
 *   if (!ChecksumValidator.matchesBaseline(jarPath)) { reinstall(); }
 *
 *   // After download — validate against backend-provided hash:
 *   if (!ChecksumValidator.validate(downloaded, expectedSha256)) { reject(); }
 */
public final class ChecksumValidator {

    private ChecksumValidator() {}

    /**
     * Computes the SHA-256 hex digest of a file.
     * Streams the file in 64 KB blocks — safe for large JARs and tarballs.
     */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) > 0) digest.update(buf, 0, len);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 no disponible", e);
        }
    }

    /**
     * Writes {file}.sha256 alongside the file — stores a baseline for future checks.
     * Call this after a successful install or download.
     */
    public static void writeBaseline(Path file) throws IOException {
        String hash = sha256(file);
        Path sidecar = sidecarPath(file);
        Files.writeString(sidecar, hash + "  " + file.getFileName() + System.lineSeparator());
        System.out.printf("[Checksum] Baseline guardado: %s = %s%n",
                file.getFileName(), hash.substring(0, 12) + "...");
    }

    /**
     * Returns true if the file's current SHA-256 matches its stored baseline.
     * Returns true (pass-through) when no .sha256 sidecar exists — baseline not yet written.
     */
    public static boolean matchesBaseline(Path file) throws IOException {
        Path sidecar = sidecarPath(file);
        if (!Files.exists(sidecar)) return true; // first run — no baseline yet
        String stored = Files.readString(sidecar).trim().split("\\s+")[0];
        String actual = sha256(file);
        boolean ok = actual.equalsIgnoreCase(stored);
        if (!ok) {
            System.err.printf("[Checksum] MISMATCH %s%n  esperado: %s%n  actual:   %s%n",
                    file.getFileName(), stored, actual);
        }
        return ok;
    }

    /**
     * Validates a file against an expected SHA-256 string (e.g. from a backend response).
     * Returns true if expectedSha256 is null/blank (server didn't provide one — skip check).
     */
    public static boolean validate(Path file, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) return true;
        try {
            String actual = sha256(file);
            boolean ok = actual.equalsIgnoreCase(expectedSha256.trim());
            if (!ok) {
                System.err.printf("[Checksum] SHA256 invalido para %s%n  esperado: %s%n  actual:   %s%n",
                        file.getFileName(), expectedSha256, actual);
            }
            return ok;
        } catch (IOException e) {
            System.err.println("[Checksum] No se pudo calcular SHA256: " + e.getMessage());
            return false;
        }
    }

    /** Returns true if the file exists and its size is at least minBytes. */
    public static boolean meetsMinSize(Path file, long minBytes) {
        try {
            return Files.exists(file) && Files.size(file) >= minBytes;
        } catch (IOException e) {
            return false;
        }
    }

    public static Path sidecarPath(Path file) {
        return file.resolveSibling(file.getFileName() + ".sha256");
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
