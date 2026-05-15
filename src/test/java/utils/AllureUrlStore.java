package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AllureUrlStore {

    private static final Logger log = LoggerFactory.getLogger(AllureUrlStore.class);

    private static final Path OUT = Paths.get("build", "allure-report-url.txt");

    /**
     * Persists the Netlify-published Allure report URL for use by the email sender.
     */
    public static void saveUrl(String url) {
        try {
            Files.createDirectories(OUT.getParent());

            if (url == null) url = "";
            url = url.trim();

            Files.writeString(OUT, url);
            log.info("[AllureUrlStore] URL saved: {} -> {}", OUT.toAbsolutePath(), url);
        } catch (Exception e) {
            log.error("[AllureUrlStore] Failed to save URL: {}", e.getMessage(), e);
        }
    }

    /**
     * Reads the previously saved Netlify report URL. Returns an empty string if not found.
     */
    public static String readUrl() {
        try {
            if (!Files.exists(OUT)) {
                log.debug("[AllureUrlStore] URL file not found: {}", OUT.toAbsolutePath());
                return "";
            }

            String url = Files.readString(OUT).trim();
            if (url.isEmpty()) return "";

            // Ensure the URL has a scheme so email clients render it as a link.
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            log.info("[AllureUrlStore] URL read: {}", url);
            return url;

        } catch (Exception e) {
            log.error("[AllureUrlStore] Failed to read URL: {}", e.getMessage());
            return "";
        }
    }
}
