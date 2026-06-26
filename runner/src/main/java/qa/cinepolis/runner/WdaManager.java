package qa.cinepolis.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WDA (WebDriverAgent) lifecycle manager for iOS testing.
 *
 * WDA is the iOS test automation bridge used by Appium's XCUITest driver.
 * In Xcode 16+/Xcode 26 with CoreDevice, WDA may bind to a device-specific IP
 * (e.g. http://192.168.1.13:8100) rather than localhost:8100. The URL is announced
 * in xcodebuild stdout as "ServerURLHere->URL<-ServerURLHere".
 *
 * This class detects that URL in real time, stores it, and probes it to confirm
 * WDA is truly ready before signalling the rest of the preflight chain.
 *
 * Responsibilities:
 *  1. Detect if WDA is already running   → isWdaRunning()
 *  2. Pre-start WDA before tests begin   → ensureWdaRunning()
 *  3. Stream xcodebuild output, detect URL pattern in real time
 *  4. Wait for WDA /status to respond    → waitForWdaReady()
 *  5. Expose detected URL to JobExecutor → getDetectedWdaUrl()
 *  6. Produce actionable failure reports → diagnoseWdaFailure()
 *
 * Android logic is NOT touched anywhere in this class.
 */
public final class WdaManager {

    static final String WDA_STATUS_URL = "http://localhost:8100/status";
    static final int    WDA_PORT       = 8100;

    // Pattern for the URL line that WDA/xcodebuild emits when the HTTP server starts.
    // Format: ServerURLHere->http://192.168.1.13:8100<-ServerURLHere
    private static final Pattern SERVER_URL_PAT = Pattern.compile(
            "ServerURLHere->(.+?)<-ServerURLHere");

    // The URL detected from xcodebuild stdout (may differ from localhost when using CoreDevice).
    // Volatile because it is written by the drain thread and read by the polling thread.
    private static volatile String detectedWdaUrl = null;

    // The xcodebuild controller process (Mac side). WDA itself runs on the device.
    private static volatile Process wdaProcess = null;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private WdaManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if WDA is responding on /status.
     *
     * Probes both localhost:8100 (legacy USB forwarding) and the URL detected
     * from xcodebuild stdout (CoreDevice IP in Xcode 16+/26).
     */
    public static boolean isWdaRunning() {
        if (probeStatus(WDA_STATUS_URL)) return true;
        String url = detectedWdaUrl;
        if (url != null && !url.isBlank()) {
            String statusUrl = url.endsWith("/") ? url + "status" : url + "/status";
            if (probeStatus(statusUrl)) return true;
        }
        return false;
    }

    /**
     * Returns the WDA URL detected from xcodebuild stdout during this session,
     * or null if no URL was detected yet.
     *
     * JobExecutor passes this as -DwebDriverAgentUrl so Appium connects to the
     * correct address instead of assuming localhost:8100.
     */
    public static String getDetectedWdaUrl() {
        return detectedWdaUrl;
    }

    /**
     * Main entry point: verifies WDA is running and starts it if needed.
     *
     * If wdaCached=true  → WDA was previously compiled and is installed on the device.
     *                       A fast start (~15-30 s) via test-without-building is attempted.
     *                       Falls back to full xcodebuild test if DerivedData is stale.
     *
     * If wdaCached=false → WDA needs full compilation. This class does not attempt it;
     *                       Appium XCUITest driver handles it when the session is created.
     *
     * @return true if WDA is confirmed ready, false if Appium must handle it
     */
    public static boolean ensureWdaRunning(BackendClient client, String executionId,
                                            String udid, String teamId,
                                            String wdaBundleId, boolean wdaCached) {

        // Reset any URL detected in a prior session so stale data isn't forwarded.
        detectedWdaUrl = null;

        client.sendLog(executionId, "INFO",
                "🔍 [WDA] Verificando WebDriverAgent en localhost:" + WDA_PORT + "...");

        // Fast path: WDA is already running (kept alive from a previous run)
        if (isWdaRunning()) {
            String active = detectedWdaUrl != null ? detectedWdaUrl : "http://localhost:" + WDA_PORT;
            client.sendLog(executionId, "INFO",
                    "✅ [WDA] WebDriverAgent ya está activo en " + active
                    + " — sesión Appium será instantánea.");
            return true;
        }

        if (!wdaCached) {
            client.sendLog(executionId, "INFO",
                    "ℹ️  [WDA] Primera ejecución en este dispositivo.\n"
                    + "   🔨 WDA será compilado e instalado automáticamente por Appium.\n"
                    + "   ⏱  Tiempo estimado: 5-10 minutos (solo la primera vez).\n"
                    + "   💡 Ejecuciones posteriores usarán el binario precompilado (~15-30 s).");
            return false;
        }

        // WDA was compiled before — attempt fast start
        client.sendLog(executionId, "INFO",
                "🚀 [WDA] WDA precompilado detectado. Iniciando en dispositivo " + udid + "...");

        // Attempt A: test-without-building (uses existing DerivedData, fastest)
        boolean launchAttempted = tryStartFromDerivedData(client, executionId, udid);

        // Attempt B: full xcodebuild test from WDA project (slower but more reliable)
        if (!launchAttempted) {
            String projectPath = findWdaProjectPath();
            if (projectPath != null) {
                launchAttempted = tryStartFromProject(
                        client, executionId, udid, teamId, wdaBundleId, projectPath);
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️  [WDA] Proyecto WebDriverAgent.xcodeproj no encontrado.\n"
                        + "   Appium intentará iniciar WDA durante la creación de sesión.");
            }
        }

        if (!launchAttempted) {
            client.sendLog(executionId, "WARN",
                    "⚠️  [WDA] No se pudo iniciar WDA directamente.\n"
                    + "   Appium lo iniciará durante la creación de sesión (puede tardar 1-2 min).");
            return false;
        }

        client.sendLog(executionId, "INFO",
                "✅ [WDA] Proceso WebDriverAgent iniciado."
                + "\n   Esperando que el servidor HTTP arranque en el dispositivo...");

        // Wait for WDA to respond on /status (probes both localhost and detected URL)
        boolean ready = waitForWdaReady(client, executionId, 180);

        if (!ready) {
            String detectedUrl = detectedWdaUrl;
            String cause = (detectedUrl == null || detectedUrl.isBlank())
                    ? "❌ [WDA] No apareció ServerURLHere en 180s — WDA nunca inició."
                    + "\n   Causa: error al compilar WDA, firma incorrecta, o dispositivo no responde."
                    : "❌ [WDA] WDA inició (URL: " + detectedUrl + ") pero /status no respondió en 180s."
                    + "\n   Causa: WDA compiló pero falló al arrancar el servidor HTTP en el dispositivo.";
            client.sendLog(executionId, "ERROR", cause
                    + "\n" + diagnoseWdaFailure(udid, teamId));
        }

        return ready;
    }

    /**
     * Polls WDA /status every 3 s until it responds or the timeout elapses.
     * Probes both localhost:8100 and the URL detected from xcodebuild stdout.
     *
     * @param timeoutSeconds maximum wait in seconds
     * @return true if WDA became ready within the timeout
     */
    public static boolean waitForWdaReady(BackendClient client, String executionId,
                                           int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1_000L);
        int  attempt  = 0;

        client.sendLog(executionId, "INFO",
                "   ⏳ [WDA] Validando endpoint /status (máx. " + timeoutSeconds + "s)...");

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            if (isWdaRunning()) {
                String activeUrl = (detectedWdaUrl != null && !detectedWdaUrl.isBlank())
                        ? detectedWdaUrl : "http://localhost:" + WDA_PORT;
                client.sendLog(executionId, "INFO",
                        "✅ [WDA] WebDriverAgent listo (~" + (attempt * 3) + "s)"
                        + "\n   URL detectada: " + activeUrl);
                return true;
            }

            // Progress update every ~15 s
            if (attempt % 5 == 0) {
                long remaining = (deadline - System.currentTimeMillis()) / 1_000;
                String status = (detectedWdaUrl != null && !detectedWdaUrl.isBlank())
                        ? "URL " + detectedWdaUrl + " detectada — esperando respuesta /status"
                        : "Esperando ServerURLHere en stdout de xcodebuild";
                client.sendLog(executionId, "INFO",
                        "   ⏳ [WDA] " + status + " (" + remaining + "s restantes)");
            }

            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Kills the xcodebuild controller process managed by this class (if any).
     * WDA itself keeps running on the device until the next Appium session stops it.
     */
    public static void stop() {
        Process p = wdaProcess;
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            wdaProcess = null;
            System.out.println("[WdaManager] xcodebuild controller detenido.");
        }
    }

    /**
     * Full iOS resource cleanup after a test execution.
     *
     * 1. Kills the Mac-side xcodebuild / WDA controller (stop()).
     * 2. Terminates WebDriverAgentRunner / XCTRunner on the physical device via
     *    `xcrun devicectl device process terminate` so the device stops showing
     *    "Automation Running" immediately after the suite ends.
     * 3. Resets detectedWdaUrl for the next run.
     *
     * Safe to call multiple times (idempotent). Never throws.
     */
    public static void cleanup(BackendClient client, String executionId, String physicalUdid) {
        stop(); // kill Mac-side xcodebuild

        if (physicalUdid != null && !physicalUdid.isBlank()) {
            if (client != null && executionId != null)
                client.sendLog(executionId, "INFO", "Finalizando WebDriverAgent...");
            terminateWdaOnDevice(physicalUdid);
            if (client != null && executionId != null)
                client.sendLog(executionId, "INFO", "✓ WebDriverAgent detenido");
        }

        detectedWdaUrl = null;
    }

    /**
     * Finds WebDriverAgentRunner / XCTRunner processes on the physical device via
     * `xcrun devicectl device process list` (Xcode 16+/26) and terminates each one.
     * Falls back to bundle-ID termination when PID extraction yields no results.
     * This immediately stops the "Automation Running" overlay on the device.
     */
    private static void terminateWdaOnDevice(String physicalUdid) {
        boolean killedByPid = false;

        // --- Step 1: terminate by PID (most reliable when device is responsive) ---
        try {
            Process list = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "process", "list",
                    "--device", physicalUdid, "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(list.getInputStream().readAllBytes());
            boolean done = list.waitFor(12, TimeUnit.SECONDS);
            if (!done) { list.destroyForcibly(); }
            else {
                Set<String> pids = extractWdaPids(json);
                if (pids.isEmpty()) {
                    System.out.println("[WdaManager] Sin procesos WDA en lista — intentando por bundle-id.");
                }
                for (String pid : pids) {
                    try {
                        Process kill = new ProcessBuilder(
                                "xcrun", "devicectl", "device", "process", "terminate",
                                "--device", physicalUdid, "--pid", pid)
                                .redirectErrorStream(true).start();
                        kill.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                        kill.waitFor(8, TimeUnit.SECONDS);
                        System.out.println("[WdaManager] ✅ WDA PID=" + pid + " terminado.");
                        killedByPid = true;
                    } catch (Exception ex) {
                        System.err.println("[WdaManager] No se pudo terminar PID=" + pid + ": " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WdaManager] process list error: " + e.getMessage());
        }

        // --- Step 2: fallback — terminate by bundle-id (covers Xcode 26 JSON format changes) ---
        if (!killedByPid) {
            for (String bundleId : new String[]{
                    "com.facebook.WebDriverAgentRunner.xctrunner",
                    "com.facebook.WebDriverAgentRunner"}) {
                try {
                    Process kill = new ProcessBuilder(
                            "xcrun", "devicectl", "device", "process", "terminate",
                            "--device", physicalUdid, "--bundle-id", bundleId)
                            .redirectErrorStream(true).start();
                    kill.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                    boolean fin = kill.waitFor(8, TimeUnit.SECONDS);
                    if (fin && kill.exitValue() == 0) {
                        System.out.println("[WdaManager] ✅ WDA terminado por bundle-id: " + bundleId);
                        break;
                    }
                } catch (Exception ex) {
                    // bundle-id not found or devicectl doesn't support --bundle-id — acceptable
                }
            }
        }
    }

    /**
     * Extracts process identifiers for WebDriverAgentRunner / XCTRunner entries from
     * a `xcrun devicectl device process list --json-output -` response.
     * Searches ±1000 chars around each WDA marker for processIdentifier or pid fields.
     */
    private static Set<String> extractWdaPids(String json) {
        Set<String> pids = new LinkedHashSet<>();
        String[] markers = {"WebDriverAgentRunner", "xctrunner", "webdriveragent"};
        String jsonLower = json.toLowerCase();
        // Accept both "processIdentifier" (Xcode 16) and "pid" (possible Xcode 26 alias)
        Pattern pidPat = Pattern.compile("\"(?:processIdentifier|pid)\"\\s*:\\s*(\\d+)");

        for (String marker : markers) {
            int pos = 0;
            while ((pos = jsonLower.indexOf(marker.toLowerCase(), pos)) >= 0) {
                // Scan ±1000 chars around this match for a processIdentifier/pid field
                int lo = Math.max(0, pos - 1000);
                int hi = Math.min(json.length(), pos + 1000);
                Matcher m = pidPat.matcher(json.substring(lo, hi));
                if (m.find()) pids.add(m.group(1));
                pos++;
            }
        }
        return pids;
    }

    // ── Start attempt A: test-without-building ────────────────────────────────

    /**
     * Looks for a pre-built WebDriverAgentRunner .xctestrun in Xcode DerivedData and
     * launches xcodebuild test-without-building. Returns true if the process started.
     */
    static boolean tryStartFromDerivedData(BackendClient client, String executionId, String udid) {
        File derivedDataRoot = new File(
                System.getProperty("user.home") + "/Library/Developer/Xcode/DerivedData");
        if (!derivedDataRoot.isDirectory()) return false;

        File[] wdaDirs = derivedDataRoot.listFiles(
                f -> f.isDirectory() && f.getName().startsWith("WebDriverAgent-"));
        if (wdaDirs == null || wdaDirs.length == 0) return false;

        for (File wdaDir : wdaDirs) {
            File productsDir = new File(wdaDir, "Build/Products");
            if (!productsDir.isDirectory()) continue;

            File[] suiteDirs = productsDir.listFiles(
                    f -> f.isDirectory() && f.getName().startsWith("Debug-iphoneos"));
            if (suiteDirs == null) continue;

            for (File suiteDir : suiteDirs) {
                File[] xctestrunFiles = suiteDir.listFiles(
                        (d, n) -> n.startsWith("WebDriverAgentRunner") && n.endsWith(".xctestrun"));
                if (xctestrunFiles == null || xctestrunFiles.length == 0) continue;

                File xctestrun = xctestrunFiles[0];
                client.sendLog(executionId, "INFO",
                        "   [WDA] Usando xctestrun: " + xctestrun.getName());
                try {
                    Process p = new ProcessBuilder(
                            "xcodebuild",
                            "-xctestrun", xctestrun.getAbsolutePath(),
                            "-destination", "id=" + udid,
                            "test-without-building")
                            .redirectErrorStream(true)
                            .start();
                    wdaProcess = p;
                    drainProcessOutput(p, "[WDA-fast]", client, executionId);
                    return true;
                } catch (Exception e) {
                    client.sendLog(executionId, "WARN",
                            "   [WDA] test-without-building no pudo iniciarse: " + e.getMessage());
                }
            }
        }
        return false;
    }

    // ── Start attempt B: full xcodebuild test ─────────────────────────────────

    /**
     * Starts WDA via full xcodebuild test from the WebDriverAgent.xcodeproj.
     * Slower but works even when DerivedData is stale or missing.
     */
    static boolean tryStartFromProject(BackendClient client, String executionId,
                                        String udid, String teamId,
                                        String wdaBundleId, String projectPath) {
        client.sendLog(executionId, "INFO",
                "   [WDA] Iniciando desde proyecto: " + projectPath);
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("xcodebuild");
            cmd.add("test");
            cmd.add("-project");     cmd.add(projectPath);
            cmd.add("-scheme");      cmd.add("WebDriverAgentRunner");
            cmd.add("-destination"); cmd.add("id=" + udid);

            if (!teamId.isBlank()) {
                cmd.add("DEVELOPMENT_TEAM=" + teamId);
                cmd.add("CODE_SIGN_IDENTITY=Apple Development");
            }
            if (!wdaBundleId.isBlank()) {
                cmd.add("UPDATEDWDABUNDLEID=" + wdaBundleId);
            }

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            wdaProcess = p;
            drainProcessOutput(p, "[WDA-build]", client, executionId);
            return true;
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "   [WDA] xcodebuild test no pudo iniciarse: " + e.getMessage());
            return false;
        }
    }

    // ── Project path discovery ────────────────────────────────────────────────

    /**
     * Searches known Appium installation locations for WebDriverAgent.xcodeproj.
     * Returns the first path found, or null if none exist.
     */
    public static String findWdaProjectPath() {
        String home = System.getProperty("user.home");
        String[] candidates = {
            // appium driver install xcuitest  (Appium 2.x driver store)
            home + "/.appium/node_modules/appium-xcuitest-driver"
                 + "/node_modules/appium-webdriveragent/WebDriverAgent.xcodeproj",
            // Embedded Runner appium (enterprise / self-hosted)
            home + "/.automationqa/runtime/appium/node_modules/appium-xcuitest-driver"
                 + "/node_modules/appium-webdriveragent/WebDriverAgent.xcodeproj",
            // Homebrew Apple Silicon
            "/opt/homebrew/lib/node_modules/appium-xcuitest-driver"
                 + "/node_modules/appium-webdriveragent/WebDriverAgent.xcodeproj",
            // Homebrew Intel / npm global
            "/usr/local/lib/node_modules/appium-xcuitest-driver"
                 + "/node_modules/appium-webdriveragent/WebDriverAgent.xcodeproj",
        };
        for (String path : candidates) {
            if (new File(path).exists()) return path;
        }
        return null;
    }

    // ── Failure diagnosis ─────────────────────────────────────────────────────

    /**
     * Returns a multi-line actionable diagnostic when WDA fails to start.
     */
    public static String diagnoseWdaFailure(String udid, String teamId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n   ──────── Diagnóstico y solución ────────\n");

        if (teamId == null || teamId.isBlank()) {
            sb.append("   ⚠️  Apple Developer Team ID no detectado.\n");
            sb.append("       → Abre Xcode → Settings → Accounts → agrega tu Apple ID.\n");
            sb.append("       → Acepta el certificado de desarrollo que Xcode descargue.\n");
        } else {
            sb.append("   ✅ Team ID: ").append(teamId).append("\n");
        }

        String projectPath = findWdaProjectPath();
        if (projectPath == null) {
            sb.append("   ⚠️  WebDriverAgent.xcodeproj no encontrado.\n");
            sb.append("       → Reinstala el driver: appium driver install xcuitest\n");
        }

        sb.append("   📋 Pasos adicionales:\n");
        sb.append("   1. Verifica que el iPhone esté desbloqueado y confíe en este Mac\n");
        sb.append("      (Ajustes → General → VPN y gestión de dispositivos → Confiar).\n");
        sb.append("   2. Activa Developer Mode en el iPhone:\n");
        sb.append("      Ajustes → Privacidad y seguridad → Modo desarrollador → Activar.\n");
        sb.append("   3. Ejecuta al menos una vez: xcodebuild -runFirstLaunch\n");
        sb.append("   4. Si el error persiste, borra el caché WDA y vuelve a Ejecutar:\n");
        sb.append("      rm ~/.qautomation/wda/");
        sb.append(udid != null ? udid.replaceAll("[^a-zA-Z0-9_-]", "_") : "UDID");
        sb.append(".properties\n");
        sb.append("   ──────────────────────────────────────────");
        return sb.toString();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Starts a daemon thread that streams xcodebuild stdout line by line.
     *
     * Detects key events in real time:
     *  - "ServerURLHere->URL<-ServerURLHere" → stores URL in detectedWdaUrl, logs it
     *  - "BUILD SUCCEEDED" → logs "WebDriverAgent compilado"
     *  - "BUILD FAILED"    → logs the failure signal
     *
     * This prevents the process from blocking when its output buffer fills.
     */
    private static void drainProcessOutput(Process p, String prefix,
                                            BackendClient client, String executionId) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(prefix + " " + line);

                    // Detect WDA server URL announcement
                    Matcher urlMatcher = SERVER_URL_PAT.matcher(line);
                    if (urlMatcher.find()) {
                        String url = urlMatcher.group(1).trim();
                        detectedWdaUrl = url;
                        System.out.println("[WDA] ✅ URL detectada: " + url);
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "INFO",
                                    "✅ [WDA] URL detectada: " + url
                                    + "\n   Validando endpoint /status...");
                        }
                    }

                    // Detect build completion
                    String upper = line.toUpperCase();
                    if (upper.contains("BUILD SUCCEEDED")) {
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "INFO",
                                    "✅ [WDA] WebDriverAgent compilado correctamente.");
                        }
                    } else if (upper.contains("BUILD FAILED")) {
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "ERROR",
                                    "❌ [WDA] Error compilando WDA — BUILD FAILED."
                                    + "\n   Revisa el log de xcodebuild para el error específico.");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }, "wda-drain");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Probes a single /status URL and returns true when it responds 2xx.
     */
    private static boolean probeStatus(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
