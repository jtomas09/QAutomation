package utils;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;

public class NetlifyApi {

    private static final Logger log = LoggerFactory.getLogger(NetlifyApi.class);

    private static final int MAX_ATTEMPTS = 3;

    /**
     * Deploys a ZIP file to a Netlify site and returns the published URL.
     * Retries up to {@value MAX_ATTEMPTS} times with exponential back-off on failure.
     *
     * @param siteId  Netlify site ID
     * @param token   Netlify personal access token
     * @param zipPath path to the ZIP archive to deploy
     * @return public URL of the deployed site
     * @throws Exception if all attempts fail
     */
    public static String deployZip(String siteId, String token, Path zipPath) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMinutes(15))
                .build();

        Exception last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("[NetlifyApi] Deploy attempt {}/{} zip={}", attempt, MAX_ATTEMPTS, zipPath.toAbsolutePath());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.netlify.com/api/v1/sites/" + siteId + "/deploys"))
                        .timeout(Duration.ofMinutes(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/zip")
                        .POST(HttpRequest.BodyPublishers.ofFile(zipPath))
                        .build();

                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() >= 400 && resp.statusCode() < 500) {
                    // Client error (wrong token/siteId, etc.) — retrying won't help
                    throw new IllegalStateException(
                            "Netlify error " + resp.statusCode() + " (no reintentable): " + resp.body());
                }
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    throw new RuntimeException("Netlify deploy failed: " + resp.statusCode() + " - " + resp.body());
                }

                JSONObject json = new JSONObject(resp.body());
                if (json.has("deploy_ssl_url")) return json.getString("deploy_ssl_url");
                if (json.has("deploy_url"))     return json.getString("deploy_url");
                if (json.has("url"))            return json.getString("url");

                throw new RuntimeException("Netlify response did not contain a URL: " + resp.body());

            } catch (IllegalStateException e) {
                // 4xx client error — no point retrying
                throw e;
            } catch (Exception e) {
                last = e;
                log.warn("[NetlifyApi] Attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < MAX_ATTEMPTS) {
                    long backoffMs = 10_000L * attempt;
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                }
            }
        }

        throw last;
    }
}
