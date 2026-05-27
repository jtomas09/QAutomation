package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AllureAutoPublisher {

    private static final Logger log = LoggerFactory.getLogger(AllureAutoPublisher.class);

    /** Set -DdeployToNetlify=true to enable publishing the Allure report to Netlify after each run. */
    private static final boolean IS_DEPLOY_ENABLED =
            Boolean.parseBoolean(System.getProperty("deployToNetlify", "false"));

    private static final Path ALLURE_HTML_DIR =
            Paths.get("build", "reports", "allure-report", "allureReport");

    private static final Path DEPLOY_LOCK =
            Paths.get("build", "netlify-deploy.lock");

    public static String generateAndPublish() throws Exception {
        log.info("[AllureAutoPublisher] generateAndPublish() invoked.");

        if (!IS_DEPLOY_ENABLED) {
            log.info("[AllureAutoPublisher] Netlify deploy disabled (-DdeployToNetlify=false). Skipping.");
            return "";
        }

        if (Files.exists(DEPLOY_LOCK)) {
            log.info("[AllureAutoPublisher] Deploy lock exists — ya se publicó en esta ejecución. Leyendo URL guardada.");
            return AllureUrlStore.readUrl();
        }

        Path index = ALLURE_HTML_DIR.resolve("index.html");

        if (!Files.exists(index)) {
            log.info("[AllureAutoPublisher] index.html not found. Attempting to generate via Gradle (best-effort)...");
            tryRunGradleAllureReport();
        }

        if (!Files.exists(index)) {
            throw new IllegalStateException("index.html not found at: " + index.toAbsolutePath());
        }

        patchAllureTitle(ALLURE_HTML_DIR, "Reporte de Cinépolis");

        log.info("[AllureAutoPublisher] Publishing to Netlify...");
        String baseUrl = publishToNetlify(ALLURE_HTML_DIR);
        String finalUrl = normalizeAllureUrl(baseUrl);

        log.info("[AllureAutoPublisher] Published. URL: {}", finalUrl);
        AllureUrlStore.saveUrl(finalUrl);
        writeDeployLock();
        return finalUrl;
    }

    private static void tryRunGradleAllureReport() {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb = new ProcessBuilder(isWindows ? "gradlew.bat" : "./gradlew", "allureReport", "--clean");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            log.info("[AllureAutoPublisher] gradlew allureReport exit={}", p.exitValue());
        } catch (Exception e) {
            log.warn("[AllureAutoPublisher] Could not run gradlew allureReport: {}", e.getMessage());
        }
    }

    private static String publishToNetlify(Path htmlDir) throws Exception {
        String token = System.getenv("NETLIFY_AUTH_TOKEN");
        String siteId = System.getenv("NETLIFY_SITE_ID");

        if (token == null || token.isBlank() || siteId == null || siteId.isBlank()) {
            throw new IllegalStateException("Missing environment variables: NETLIFY_AUTH_TOKEN and/or NETLIFY_SITE_ID");
        }

        Path zip = zipFolder(htmlDir);
        return NetlifyApi.deployZip(siteId, token, zip);
    }

    private static Path zipFolder(Path folder) throws Exception {
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        Path zipPath = Paths.get("build", "allure-report-" + ts + ".zip");
        Files.createDirectories(zipPath.getParent());

        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {

            Files.walk(folder).forEach(path -> {
                try {
                    if (Files.isDirectory(path)) return;

                    String entryName = folder.relativize(path)
                            .toString()
                            .replace("\\", "/");

                    if (entryName.isBlank()) return;

                    zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        log.info("[AllureAutoPublisher] Zip created: {}", zipPath.toAbsolutePath());
        return zipPath;
    }

    /**
     * Validates that the deployed URL correctly serves Allure's summary.json.
     * Netlify sometimes places content under a subdirectory; this method normalizes the URL.
     */
    private static String normalizeAllureUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String b = baseUrl.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);

        try {
            if (is200(b + "/widgets/summary.json")) return b;
            if (is200(b + "/allureReport/widgets/summary.json")) return b + "/allureReport";
        } catch (Exception ignored) {}

        return b;
    }

    private static boolean is200(String url) throws Exception {
        Objects.requireNonNull(url);
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(6000);
        con.setReadTimeout(6000);
        con.setRequestMethod("GET");
        con.setInstanceFollowRedirects(true);
        int code = con.getResponseCode();
        log.debug("[AllureAutoPublisher] Probe {} -> {}", url, code);
        return code >= 200 && code < 300;
    }

    private static void patchAllureTitle(Path allureReportDir, String newTitle) {
        try {
            Path index = allureReportDir.resolve("index.html");
            if (Files.exists(index)) {
                String html = Files.readString(index, StandardCharsets.UTF_8);
                html = html.replaceAll("(?is)<title>.*?</title>",
                        "<title>" + escapeForHtml(newTitle) + "</title>");
                Files.writeString(index, html, StandardCharsets.UTF_8);
                log.debug("[AllureAutoPublisher] Patched <title> in index.html.");
            }

            Path p1 = allureReportDir.resolve(Paths.get("widgets", "summary.json"));
            Path p2 = allureReportDir.resolve(Paths.get("data", "widgets", "summary.json"));

            boolean patched = patchReportNameInSummaryJson(p1, newTitle)
                    | patchReportNameInSummaryJson(p2, newTitle);

            if (!patched) {
                log.warn("[AllureAutoPublisher] summary.json not found; reportName not patched.");
            }

        } catch (Exception e) {
            log.warn("[AllureAutoPublisher] Failed to patch Allure title: {}", e.getMessage());
        }
    }

    private static boolean patchReportNameInSummaryJson(Path summaryJson, String newTitle) {
        try {
            if (!Files.exists(summaryJson)) return false;

            String json = Files.readString(summaryJson, StandardCharsets.UTF_8);

            Pattern p = Pattern.compile("(\"reportName\"\\s*:\\s*\")([^\"]*)(\")");
            Matcher m = p.matcher(json);

            if (m.find()) {
                String patched = m.replaceFirst("$1" + escapeForJson(newTitle) + "$3");
                Files.writeString(summaryJson, patched, StandardCharsets.UTF_8);
                log.debug("[AllureAutoPublisher] reportName updated in: {}", summaryJson.toAbsolutePath());
                return true;
            }

            String insert = "{\n  \"reportName\": \"" + escapeForJson(newTitle) + "\",";
            String patched2 = json.replaceFirst("\\{", insert);
            Files.writeString(summaryJson, patched2, StandardCharsets.UTF_8);
            log.debug("[AllureAutoPublisher] reportName inserted in: {}", summaryJson.toAbsolutePath());
            return true;

        } catch (Exception e) {
            log.warn("[AllureAutoPublisher] Could not patch {}: {}", summaryJson, e.getMessage());
            return false;
        }
    }

    private static String escapeForJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeForHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Called at test plan start to allow a fresh deploy in the new run. */
    public static void resetDeployLock() {
        try {
            if (Files.deleteIfExists(DEPLOY_LOCK)) {
                log.debug("[AllureAutoPublisher] Deploy lock cleared.");
            }
        } catch (Exception e) {
            log.warn("[AllureAutoPublisher] Could not clear deploy lock: {}", e.getMessage());
        }
    }

    private static void writeDeployLock() {
        try {
            Files.createDirectories(DEPLOY_LOCK.getParent());
            Files.writeString(DEPLOY_LOCK, "deployed",
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("[AllureAutoPublisher] Could not write deploy lock: {}", e.getMessage());
        }
    }
}
