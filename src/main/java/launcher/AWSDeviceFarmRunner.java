package launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

public class AWSDeviceFarmRunner {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // ─── SigV4 ────────────────────────────────────────────────────

    private static String sha256Hex(String data) throws Exception {
        return toHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes("UTF-8")));
    }

    private static byte[] hmacBytes(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes("UTF-8"));
    }

    private static String hmacHex(byte[] key, String data) throws Exception {
        return toHex(hmacBytes(key, data));
    }

    private static byte[] getSigningKey(String secretKey, String date, String region, String service) throws Exception {
        byte[] kDate    = hmacBytes(("AWS4" + secretKey).getBytes("UTF-8"), date);
        byte[] kRegion  = hmacBytes(kDate, region);
        byte[] kService = hmacBytes(kRegion, service);
        return hmacBytes(kService, "aws4_request");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── Device Farm API ──────────────────────────────────────────

    static JSONObject callApi(String target, JSONObject body,
                               String accessKeyId, String secretKey,
                               String region) throws Exception {
        SimpleDateFormat dateFmt     = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat dateTimeFmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateTimeFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date now      = new Date();
        String date   = dateFmt.format(now);
        String dateTime = dateTimeFmt.format(now);

        String host    = "devicefarm." + region + ".amazonaws.com";
        String bodyStr = body.toString();

        String canonicalHeaders =
                "content-type:application/x-amz-json-1.1\n" +
                "host:" + host + "\n" +
                "x-amz-date:" + dateTime + "\n" +
                "x-amz-target:" + target + "\n";
        String signedHeaders = "content-type;host;x-amz-date;x-amz-target";

        String canonicalRequest = "POST\n/\n\n" +
                canonicalHeaders + "\n" + signedHeaders + "\n" + sha256Hex(bodyStr);

        String scope        = date + "/" + region + "/devicefarm/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + dateTime + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        String authHeader = "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope +
                            ", SignedHeaders=" + signedHeaders +
                            ", Signature=" + hmacHex(getSigningKey(secretKey, date, region, "devicefarm"), stringToSign);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + "/"))
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                .header("Content-Type", "application/x-amz-json-1.1")
                .header("X-Amz-Target", target)
                .header("X-Amz-Date", dateTime)
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300)
            throw new IOException("DeviceFarm API " + resp.statusCode() + ": " + resp.body());
        return new JSONObject(resp.body());
    }

    // ─── Operations ───────────────────────────────────────────────

    /** Returns list of [arn, name] pairs. */
    public static List<String[]> listDevicePools(String projectArn,
                                                  String accessKeyId, String secretKey,
                                                  String region) throws Exception {
        JSONObject resp = callApi("DeviceFarm_20150623.ListDevicePools",
                new JSONObject().put("arn", projectArn), accessKeyId, secretKey, region);
        List<String[]> result = new ArrayList<>();
        JSONArray pools = resp.optJSONArray("devicePools");
        if (pools != null) {
            for (int i = 0; i < pools.length(); i++) {
                JSONObject p = pools.getJSONObject(i);
                result.add(new String[]{p.optString("arn"), p.optString("name")});
            }
        }
        return result;
    }

    /**
     * Creates an upload slot. type: ANDROID_APP | APPIUM_JAVA_JUNIT_TEST_PACKAGE
     * Returns [arn, presignedUrl].
     */
    public static String[] createUpload(String projectArn, String name, String type,
                                         String accessKeyId, String secretKey,
                                         String region) throws Exception {
        JSONObject resp = callApi("DeviceFarm_20150623.CreateUpload", new JSONObject()
                .put("projectArn", projectArn)
                .put("name", name)
                .put("type", type),
                accessKeyId, secretKey, region);
        JSONObject upload = resp.getJSONObject("upload");
        return new String[]{upload.getString("arn"), upload.getString("url")};
    }

    public static String getUploadStatus(String uploadArn,
                                          String accessKeyId, String secretKey,
                                          String region) throws Exception {
        JSONObject resp = callApi("DeviceFarm_20150623.GetUpload",
                new JSONObject().put("arn", uploadArn), accessKeyId, secretKey, region);
        return resp.getJSONObject("upload").optString("status");
    }

    /** Creates a test run and returns the run ARN. */
    public static String createRun(String projectArn, String devicePoolArn,
                                    String appArn, String testPackageArn,
                                    String runName,
                                    String accessKeyId, String secretKey,
                                    String region) throws Exception {
        JSONObject body = new JSONObject()
                .put("projectArn", projectArn)
                .put("devicePoolArn", devicePoolArn)
                .put("name", runName)
                .put("test", new JSONObject()
                        .put("type", "APPIUM_JAVA_JUNIT")
                        .put("testPackageArn", testPackageArn));
        if (appArn != null && !appArn.isBlank()) body.put("appArn", appArn);
        JSONObject resp = callApi("DeviceFarm_20150623.CreateRun", body, accessKeyId, secretKey, region);
        return resp.getJSONObject("run").getString("arn");
    }

    /** Returns [status, result, passed, failed, skipped]. */
    public static String[] getRun(String runArn,
                                   String accessKeyId, String secretKey,
                                   String region) throws Exception {
        JSONObject resp = callApi("DeviceFarm_20150623.GetRun",
                new JSONObject().put("arn", runArn), accessKeyId, secretKey, region);
        JSONObject run  = resp.getJSONObject("run");
        JSONObject cnts = run.optJSONObject("counters");
        return new String[]{
            run.optString("status"),
            run.optString("result"),
            cnts != null ? String.valueOf(cnts.optInt("passed"))  : "0",
            cnts != null ? String.valueOf(cnts.optInt("failed"))  : "0",
            cnts != null ? String.valueOf(cnts.optInt("skipped")) : "0"
        };
    }

    // ─── S3 presigned upload ──────────────────────────────────────

    public static void uploadToPresignedUrl(String url, Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Content-Type", "application/octet-stream")
                .timeout(Duration.ofMinutes(10))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2)
            throw new IOException("S3 upload failed " + resp.statusCode());
    }

    // ─── Package test JAR into Device Farm format ─────────────────

    /** Wraps jarPath in a ZIP as zip-with-dependencies.jar (Device Farm convention). */
    public static Path packageTestJar(Path jarPath) throws Exception {
        Path zipPath = jarPath.getParent().resolve("device-farm-tests.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry = new ZipEntry("zip-with-dependencies.jar");
            zos.putNextEntry(entry);
            Files.copy(jarPath, zos);
            zos.closeEntry();
        }
        return zipPath;
    }

    // ─── Poll helpers ─────────────────────────────────────────────

    public static void waitForUpload(String uploadArn,
                                      String accessKeyId, String secretKey, String region,
                                      Runnable onTick) throws Exception {
        long deadline = System.currentTimeMillis() + 10 * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            String status = getUploadStatus(uploadArn, accessKeyId, secretKey, region);
            if ("SUCCEEDED".equals(status)) return;
            if (status.contains("FAILED"))
                throw new IOException("Upload failed: " + status);
            if (onTick != null) onTick.run();
            Thread.sleep(5_000);
        }
        throw new IOException("Upload timed out after 10 minutes");
    }

    /** Polls until COMPLETED/ERRORED/STOPPED. Returns [status, result, passed, failed, skipped]. */
    public static String[] waitForRun(String runArn,
                                       String accessKeyId, String secretKey, String region,
                                       Consumer<String[]> onTick) throws Exception {
        long deadline = System.currentTimeMillis() + 90 * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            String[] info = getRun(runArn, accessKeyId, secretKey, region);
            if (onTick != null) onTick.accept(info);
            String status = info[0];
            if ("COMPLETED".equals(status) || "ERRORED".equals(status) || "STOPPED".equals(status))
                return info;
            Thread.sleep(30_000);
        }
        throw new IOException("Run timed out after 90 minutes");
    }
}
