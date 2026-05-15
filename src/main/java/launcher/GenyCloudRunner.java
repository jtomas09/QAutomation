package launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GenyCloudRunner {

    static final String API_BASE = "https://api.geny.io/cloud";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // ─── Auth helpers ─────────────────────────────────────────────

    /**
     * Login with email + password.
     * Returns a JWT token valid for 48 h.
     */
    public static String login(String email, String password) throws Exception {
        JSONObject body = new JSONObject().put("email", email).put("password", password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/v1/users/login"))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("Genymotion login failed " + resp.statusCode() + ": " + resp.body());
        JSONObject json = new JSONObject(resp.body());
        // Field name varies across API versions
        String token = json.optString("jwtToken", json.optString("token", ""));
        if (token.isBlank())
            throw new IOException("Genymotion login: token not found in response: " + resp.body());
        return token;
    }

    /** Adds the correct auth header depending on whether the caller provides an API token or JWT. */
    private static HttpRequest.Builder authRequest(String path, String apiToken, String jwt) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .timeout(Duration.ofSeconds(20));
        if (!apiToken.isBlank()) b.header("x-api-token", apiToken);
        else                     b.header("Authorization", "Bearer " + jwt);
        return b;
    }

    /**
     * Returns (token, isApiToken).
     * If apiToken is provided, uses it directly (no login).
     * Otherwise logs in with email+password and returns the JWT.
     */
    private static String resolveToken(String email, String password, String apiToken) throws Exception {
        return apiToken.isBlank() ? login(email, password) : apiToken;
    }

    // ─── Recipes ──────────────────────────────────────────────────

    /**
     * Lists available device recipes.
     * Returns list of [uuid, name, androidVersion].
     */
    public static List<String[]> listRecipes(String email, String password, String apiToken) throws Exception {
        String tok    = resolveToken(email, password, apiToken);
        boolean isApi = !apiToken.isBlank();

        HttpRequest req = authRequest("/v3/recipes/", isApi ? tok : "", isApi ? "" : tok)
                .GET()
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("List recipes failed " + resp.statusCode() + ": " + resp.body());

        List<String[]> result = new ArrayList<>();
        String respBody = resp.body().trim();
        JSONArray arr;
        if (respBody.startsWith("[")) {
            arr = new JSONArray(respBody);
        } else {
            JSONObject obj = new JSONObject(respBody);
            arr = obj.optJSONArray("results");
            if (arr == null) arr = obj.optJSONArray("recipes");
            if (arr == null) arr = obj.optJSONArray("objects");
        }
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r    = arr.getJSONObject(i);
                String uuid     = r.optString("uuid", "");
                String name     = r.optString("name", r.optString("display_name", ""));
                String android  = r.optString("androidVersion",
                                  r.optString("android_version",
                                  extractAndroidVersion(r)));
                if (!uuid.isBlank()) result.add(new String[]{uuid, name, android});
            }
        }
        return result;
    }

    private static String extractAndroidVersion(JSONObject recipe) {
        JSONObject hw = recipe.optJSONObject("hardware_profile");
        if (hw != null) {
            String v = hw.optString("androidVersion", hw.optString("android_version", ""));
            if (!v.isBlank()) return v;
        }
        JSONObject os = recipe.optJSONObject("os_image_details");
        if (os != null) return os.optString("androidVersion", os.optString("android_version", ""));
        return "";
    }

    // ─── Instances ────────────────────────────────────────────────

    /**
     * Starts a disposable instance from a recipe.
     * Returns [instanceUuid, adbUrl] — adbUrl may be empty until state = ONLINE.
     */
    public static String[] startInstance(String recipeUuid, String email, String password, String apiToken) throws Exception {
        String tok    = resolveToken(email, password, apiToken);
        boolean isApi = !apiToken.isBlank();

        JSONObject body = new JSONObject()
                .put("instance_name", "cinepolis-qa-" + System.currentTimeMillis())
                .put("rename_on_conflict", true);

        HttpRequest req = authRequest("/v1/recipes/" + recipeUuid + "/start-disposable",
                isApi ? tok : "", isApi ? "" : tok)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2)
            throw new IOException("Start instance failed " + resp.statusCode() + ": " + resp.body());

        JSONObject json = new JSONObject(resp.body());
        String uuid   = json.optString("uuid", "");
        String adbUrl = json.optString("adb_url", "");
        return new String[]{uuid, adbUrl};
    }

    /**
     * Gets the current state and adb_url of an instance.
     * Returns [state, adbUrl].
     */
    public static String[] getInstance(String instanceUuid, String email, String password, String apiToken) throws Exception {
        String tok    = resolveToken(email, password, apiToken);
        boolean isApi = !apiToken.isBlank();

        HttpRequest req = authRequest("/v1/instances/" + instanceUuid,
                isApi ? tok : "", isApi ? "" : tok)
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("Get instance failed " + resp.statusCode() + ": " + resp.body());
        JSONObject json = new JSONObject(resp.body());
        return new String[]{json.optString("state", ""), json.optString("adb_url", "")};
    }

    /**
     * Polls until the instance state is ONLINE (or timeout after 5 min).
     * Returns the adb_url when ready.
     */
    public static String waitForInstance(String instanceUuid,
                                          String email, String password, String apiToken,
                                          Runnable onTick) throws Exception {
        long deadline = System.currentTimeMillis() + 5 * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            String[] info = getInstance(instanceUuid, email, password, apiToken);
            String state  = info[0];
            String adbUrl = info[1];
            if ("ONLINE".equalsIgnoreCase(state)) {
                if (!adbUrl.isBlank()) return adbUrl;
            }
            if ("ERROR".equalsIgnoreCase(state) || "STOPPING".equalsIgnoreCase(state))
                throw new IOException("Instance reached unexpected state: " + state);
            if (onTick != null) onTick.run();
            Thread.sleep(8_000);
        }
        throw new IOException("Genymotion instance timed out after 5 minutes");
    }

    /** Stops a disposable instance. */
    public static void stopInstance(String instanceUuid,
                                     String email, String password, String apiToken) throws Exception {
        String tok    = resolveToken(email, password, apiToken);
        boolean isApi = !apiToken.isBlank();

        HttpRequest req = authRequest("/v1/instances/" + instanceUuid + "/stop-disposable",
                isApi ? tok : "", isApi ? "" : tok)
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Content-Type", "application/json")
                .build();
        HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
