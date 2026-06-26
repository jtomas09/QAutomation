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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WDA (WebDriverAgent) lifecycle manager for iOS testing.
 *
 * WDA is the iOS test automation bridge used by Appium's XCUITest driver.
 * It runs as a native XCTest process ON the iPhone, exposed via USB tunnel on
 * localhost:8100. Without WDA the Appium session cannot be created.
 *
 * Responsibilities:
 *  1. Detect if WDA is already running   → isWdaRunning()
 *  2. Pre-start WDA before tests begin   → ensureWdaRunning()
 *  3. Wait for WDA to become ready       → waitForWdaReady()
 *  4. Produce actionable failure reports → diagnoseWdaFailure()
 *
 * Start strategy (in order of speed):
 *  a) test-without-building from DerivedData xctestrun  (~15-30s, cached build)
 *  b) xcodebuild test from project                      (~5-10 min, full build)
 *
 * Android logic is NOT touched anywhere in this class.
 */
public final class WdaManager {

    static final String WDA_STATUS_URL = "http://localhost:8100/status";
    static final int    WDA_PORT       = 8100;

    // The xcodebuild controller process (Mac side). WDA itself runs on the device.
    private static volatile Process wdaProcess = null;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private WdaManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if WDA is responding on localhost:8100/status.
     */
    public static boolean isWdaRunning() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(WDA_STATUS_URL))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Main entry point: verifies WDA is running and starts it if needed.
     *
     * If wdaCached=true  → WDA was previously compiled and is installed on the device.
     *                       A fast start (~15-30 s) via test-without-building is attempted.
     *                       Falls back to full xcodebuild test if DerivedData is stale.
     *
     * If wdaCached=false → WDA needs full compilation. This class does not attempt compilation;
     *                       Appium XCUITest driver handles it when the session is created.
     *                       An informational log is emitted about expected wait time.
     *
     * @return true if WDA is confirmed ready on localhost:8100, false if Appium must handle it
     */
    public static boolean ensureWdaRunning(BackendClient client, String executionId,
                                            String udid, String teamId,
                                            String wdaBundleId, boolean wdaCached) {

        client.sendLog(executionId, "INFO",
                "🔍 [WDA] Verificando WebDriverAgent en localhost:" + WDA_PORT + "...");

        // Fast path: WDA is already running (e.g. kept alive from previous test run)
        if (isWdaRunning()) {
            client.sendLog(executionId, "INFO",
                    "✅ [WDA] WebDriverAgent ya está activo — sesión Appium será instantánea.");
            return true;
        }

        if (!wdaCached) {
            // First execution: WDA needs compilation by Appium/xcodebuild
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

        // Wait for WDA to respond on localhost:8100
        boolean ready = waitForWdaReady(client, executionId, 180);

        if (!ready) {
            String diagnosis = diagnoseWdaFailure(udid, teamId);
            client.sendLog(executionId, "ERROR",
                    "❌ [WDA] Tiempo de espera agotado (180s).\n"
                    + "   WebDriverAgent no respondió en localhost:" + WDA_PORT + ".\n"
                    + diagnosis);
        }

        return ready;
    }

    /**
     * Polls localhost:8100/status every 3 seconds until WDA responds or timeout elapses.
     *
     * @param timeoutSeconds maximum wait in seconds
     * @return true if WDA became ready within the timeout
     */
    public static boolean waitForWdaReady(BackendClient client, String executionId,
                                           int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1_000L);
        int  attempt  = 0;

        client.sendLog(executionId, "INFO",
                "   ⏳ [WDA] Esperando respuesta en localhost:" + WDA_PORT
                + " (máx. " + timeoutSeconds + "s)...");

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            if (isWdaRunning()) {
                client.sendLog(executionId, "INFO",
                        "✅ [WDA] Listo en localhost:" + WDA_PORT
                        + " después de ~" + (attempt * 3) + "s");
                return true;
            }

            // Progress ping every 15 seconds (every 5 attempts × 3s)
            if (attempt % 5 == 0) {
                long remaining = (deadline - System.currentTimeMillis()) / 1_000;
                client.sendLog(executionId, "INFO",
                        "   ⏳ [WDA] Aún iniciando... (" + remaining + "s restantes)");
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
                    drainProcessOutput(p, "[WDA-fast]");
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
            drainProcessOutput(p, "[WDA-build]");
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
     * Checks the most common failure causes and provides specific fix steps.
     */
    public static String diagnoseWdaFailure(String udid, String teamId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n   ──────── Diagnóstico y solución ────────\n");

        // Team ID
        if (teamId == null || teamId.isBlank()) {
            sb.append("   ⚠️  Apple Developer Team ID no detectado.\n");
            sb.append("       → Abre Xcode → Settings → Accounts → agrega tu Apple ID.\n");
            sb.append("       → Acepta el certificado de desarrollo que Xcode descargue.\n");
        } else {
            sb.append("   ✅ Team ID: ").append(teamId).append("\n");
        }

        // WDA project existence
        String projectPath = findWdaProjectPath();
        if (projectPath == null) {
            sb.append("   ⚠️  WebDriverAgent.xcodeproj no encontrado.\n");
            sb.append("       → Reinstala el driver: appium driver install xcuitest\n");
        }

        // Common steps
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
     * Starts a daemon thread that drains and prints the process stdout/stderr.
     * Prevents the process from blocking when its output buffer fills.
     */
    private static void drainProcessOutput(Process p, String prefix) {
        Thread drainThread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(prefix + " " + line);
                }
            } catch (Exception ignored) {}
        }, "wda-drain");
        drainThread.setDaemon(true);
        drainThread.start();
    }
}
