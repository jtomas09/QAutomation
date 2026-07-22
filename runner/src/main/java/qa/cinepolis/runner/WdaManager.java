package qa.cinepolis.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WDA (WebDriverAgent) process MECHANISM for iOS testing — no launch/orchestration
 * decisions live here (ver {@link WdaLifecycleOwner}, la ÚNICA autoridad que decide
 * CUÁNDO construir/arrancar/verificar/detener WDA). Esta clase solo sabe CÓMO
 * hacerlo, y sus métodos de construcción/detención son package-private a propósito:
 * SOLO {@link WdaLifecycleOwner} debe invocarlos. Ningún otro componente de este
 * paquete debe llamar a tryStartFrom..., stop() o cleanup() directamente.
 *
 * WDA es el puente de automatización que usa el driver XCUITest de Appium. En Xcode
 * 16+/26 con CoreDevice, WDA puede enlazar a una IP específica del dispositivo (p.ej.
 * http://192.168.1.13:8100) en vez de localhost:8100. La URL se anuncia en el stdout
 * de xcodebuild como "ServerURLHere->URL<-ServerURLHere".
 *
 * Responsabilidades (mecanismo puro, sin decidir "cuándo"):
 *  1. Detectar si WDA ya está corriendo        → isWdaRunning()
 *  2. Invocar xcodebuild (dos estrategias)      → tryStartFromDerivedData()/tryStartFromProject()
 *  3. Reenviar TODA la salida relevante de xcodebuild al Dashboard, sin perder
 *     información (codesign, provisioning, CompileSwift, Ld, PhaseScript, error:,
 *     warning:, no solo BUILD SUCCEEDED/FAILED) → streamBuildOutput()
 *  4. Esperar a que /status responda            → waitForWdaReady()
 *  5. Exponer la URL detectada                  → getDetectedWdaUrl()
 *  6. Producir diagnósticos accionables          → diagnoseWdaFailure()
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
     * Returns the best-known WDA base URL, never null.
     * Uses the URL detected from xcodebuild output when available (CoreDevice /
     * Xcode 16+ may bind to a device IP rather than localhost); falls back to
     * "http://localhost:8100" for classic USB-forwarding setups.
     *
     * Used by IOSAccessibilityInspector so it does not need to know about
     * WdaManager internals.
     */
    public static String getWdaBaseUrl() {
        String url = detectedWdaUrl;
        if (url != null && !url.isBlank()) {
            // Strip trailing slash if present
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        return "http://localhost:" + WDA_PORT;
    }

    /** Limpia la URL detectada de una sesión previa — llamar al inicio de cada nuevo intento. */
    static void resetDetectedUrl() {
        detectedWdaUrl = null;
    }

    // La propiedad "¿quién tiene derecho a lanzar/gestionar WDA ahora mismo?"
    // (ejecución real vs. Mirror on-demand) vive en WdaLaunchCoordinator; CUÁNDO
    // construir/arrancar/verificar vive en WdaLifecycleOwner. Esta clase (WdaManager)
    // solo ofrece el mecanismo — ver su javadoc de clase para el porqué de la separación.

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

        client.sendTechLog(executionId,
                "[WDA] Validando endpoint /status (máx. " + timeoutSeconds + "s)...");

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

            // El proceso xcodebuild ya terminó (con cualquier desenlace: BUILD
            // FAILED, ** TEST FAILED **, Testing failed, o cualquier formato
            // futuro que Apple use) — seguir esperando hasta agotar
            // timeoutSeconds sería una espera inútil. Demostrado con ejecución
            // real: sin esta verificación, un fallo confirmado a los pocos
            // segundos se reportaba recién a los 600s. El ciclo de vida real
            // del proceso del SO es la señal más robusta de "terminó" — cubre
            // cualquier desenlace, no depende de parsear un string concreto.
            Process activeProcess = wdaProcess;
            if (activeProcess != null && !activeProcess.isAlive()) {
                client.sendTechLog(executionId,
                        "[WDA] El proceso xcodebuild ya finalizó — se detiene la espera de /status.");
                return false;
            }

            // Progress update every ~15 s
            if (attempt % 5 == 0) {
                long remaining = (deadline - System.currentTimeMillis()) / 1_000;
                String status = (detectedWdaUrl != null && !detectedWdaUrl.isBlank())
                        ? "URL " + detectedWdaUrl + " detectada — esperando respuesta /status"
                        : "Esperando ServerURLHere en stdout de xcodebuild";
                client.sendTechLog(executionId,
                        "[WDA] " + status + " (" + remaining + "s restantes)");
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
     *
     * Mata también los procesos DESCENDIENTES de xcodebuild (xcbuild/clang/
     * swift-frontend), no solo el PID directo: destroyForcibly() en Unix solo
     * envía la señal al proceso indicado, nunca a su árbol. Sin esto, un hijo de
     * xcodebuild puede sobrevivir a este destroy() y seguir compilando/hospedando
     * el forwarder de red de WDA de forma huérfana — mismo patrón ya usado para
     * el proceso Gradle en JobExecutor.forceKillProcessTree().
     */
    static void stop() {
        Process p = wdaProcess;
        if (p != null && p.isAlive()) {
            try {
                p.toHandle().descendants().forEach(h -> {
                    try { h.destroyForcibly(); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
            p.destroyForcibly();
            wdaProcess = null;
            System.out.println("[WdaManager] xcodebuild controller detenido (árbol de procesos incluido).");
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
    static void cleanup(BackendClient client, String executionId, String physicalUdid) {
        stop(); // kill Mac-side xcodebuild

        if (physicalUdid != null && !physicalUdid.isBlank()) {
            if (client != null && executionId != null)
                client.sendLog(executionId, "INFO", "Finalizando WebDriverAgent...");

            terminateWdaOnDevice(physicalUdid); // SIGTERM

            // Verify termination — immediate check first, then wait if process needs time
            boolean stopped = !isWdaRunning();
            if (!stopped) {
                try { Thread.sleep(2_500); } catch (InterruptedException ignored) {}
                stopped = !isWdaRunning();
            }
            if (!stopped) {
                // SIGTERM was not enough — escalate to SIGKILL
                if (client != null && executionId != null)
                    client.sendTechLog(executionId, "[WDA] SIGTERM sin efecto — enviando SIGKILL...");
                terminateWdaOnDevice(physicalUdid, 9);
                try { Thread.sleep(1_500); } catch (InterruptedException ignored) {}
                stopped = !isWdaRunning();
            }

            // Verificación determinística real: isWdaRunning() solo prueba /status por
            // HTTP (puede fallar por motivos ajenos al proceso, p.ej. red). La prueba
            // definitiva de "ya no queda ningún proceso WDA en el dispositivo" es
            // volver a consultar devicectl directamente — la misma fuente que
            // terminateWdaOnDevice() usó para encontrar los PIDs a matar.
            Set<String> survivors = queryWdaPidsOnDevice(physicalUdid);
            if (!survivors.isEmpty()) {
                stopped = false;
                if (client != null && executionId != null) {
                    client.sendTechLog(executionId,
                            "[WDA] devicectl reporta procesos WDA aún vivos tras SIGKILL: " + survivors);
                }
                // Último intento: SIGKILL directo a los PIDs que devicectl confirma vivos.
                terminateWdaOnDevice(physicalUdid, 9);
                try { Thread.sleep(1_500); } catch (InterruptedException ignored) {}
                survivors = queryWdaPidsOnDevice(physicalUdid);
                stopped = survivors.isEmpty();
            }

            // Log honest result — ONLY "✓ WebDriverAgent detenido" when truly verified
            if (client != null && executionId != null) {
                if (stopped)
                    client.sendLog(executionId, "INFO", "✓ WebDriverAgent detenido");
                else
                    client.sendLog(executionId, "WARN",
                            "⚠ WebDriverAgent aún activo en el dispositivo (devicectl confirma PIDs: "
                            + survivors + ")");
            }
        }

        detectedWdaUrl = null;
    }

    /**
     * Finds WebDriverAgentRunner processes on the physical device via
     * `xcrun devicectl device info processes` (Xcode 26 / devicectl 518+) and sends
     * SIGTERM to each one. This immediately clears the "Automation Running" banner.
     *
     * Xcode 26 API changes vs. Xcode 16:
     *   - List:      device info processes  (was: device process list)
     *   - JSON out:  must be a file path    (was: supported "-" for stdout)
     *   - Kill:      process signal --signal 15  (was: process terminate)
     */
    private static void terminateWdaOnDevice(String physicalUdid) {
        terminateWdaOnDevice(physicalUdid, 15);
    }

    /**
     * Desinstala una app del dispositivo por su bundle identifier — usado
     * exclusivamente para resolver MismatchedApplicationIdentifierEntitlement
     * (ver {@link #tryStartFromProject}): un WDA anterior, firmado con un Team
     * distinto al actual, bloquea la reinstalación con el mismo bundle ID.
     *
     * Equivalente exacto al comportamiento real de Appium ante este mismo error
     * (appium-webdriveragent, real-device-management.ts::installToRealDevice):
     * solo se invoca cuando iOS mismo ya confirmó el conflicto reportando ese
     * error — nunca de forma preventiva/especulativa — así que nunca toca una
     * app ajena ni un WDA correctamente instalado (eso jamás produce ese error).
     *
     * No es una copia del código de Appium: invoca la MISMA herramienta oficial
     * del sistema operativo (xcrun devicectl) que Appium termina invocando para
     * dispositivos reales — se reutiliza el comportamiento (detectar → eliminar
     * el conflictivo → reintentar una vez), no su implementación en TypeScript.
     *
     * @return true si el comando de desinstalación terminó con éxito.
     */
    static boolean uninstallApp(String physicalUdid, String bundleId) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "uninstall", "app",
                    "--device", physicalUdid, bundleId)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                System.err.println("[WdaManager] Timeout desinstalando " + bundleId);
                return false;
            }
            boolean ok = p.exitValue() == 0;
            System.out.println("[WdaManager] " + (ok ? "✅" : "⚠️") + " Desinstalación de " + bundleId
                    + " (exit=" + p.exitValue() + "): " + out.trim());
            return ok;
        } catch (Exception e) {
            System.err.println("[WdaManager] uninstallApp error: " + e.getMessage());
            return false;
        }
    }

    private static void terminateWdaOnDevice(String physicalUdid, int signal) {
        Set<String> pids = queryWdaPidsOnDevice(physicalUdid);
        if (pids.isEmpty()) {
            System.out.println("[WdaManager] Sin procesos WDA activos en dispositivo.");
            return;
        }

        // Xcode 26: send SIGTERM (signal 15) — replaces "process terminate"
        for (String pid : pids) {
            try {
                Process kill = new ProcessBuilder(
                        "xcrun", "devicectl", "device", "process", "signal",
                        "--device", physicalUdid, "--pid", pid, "--signal", String.valueOf(signal))
                        .redirectErrorStream(true).start();
                kill.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                kill.waitFor(8, TimeUnit.SECONDS);
                System.out.println("[WdaManager] ✅ WDA PID=" + pid + " señal=" + signal + " enviada.");
            } catch (Exception ex) {
                System.err.println("[WdaManager] No se pudo terminar PID=" + pid + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Consulta directa a devicectl — devuelve los PIDs de procesos WDA vivos AHORA
     * MISMO en el dispositivo, sin depender de ninguna referencia Java a un
     * proceso. Es la fuente de verdad usada tanto para decidir a quién matar
     * (terminateWdaOnDevice) como para verificar de forma determinística que ya
     * no queda nada vivo (cleanup) — nunca confía en el resultado de la señal por
     * sí solo.
     */
    static Set<String> queryWdaPidsOnDevice(String physicalUdid) {
        File tmpJson = null;
        try {
            tmpJson = File.createTempFile("wda_procs_", ".json");

            // Xcode 26: list running processes → JSON file
            Process list = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "info", "processes",
                    "--device", physicalUdid,
                    "--json-output", tmpJson.getAbsolutePath())
                    .redirectErrorStream(true).start();
            // Drain human-readable stdout (not the JSON file)
            list.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            boolean done = list.waitFor(15, TimeUnit.SECONDS);
            if (!done) {
                list.destroyForcibly();
                System.err.println("[WdaManager] Timeout listando procesos del dispositivo.");
                return java.util.Collections.emptySet();
            }

            String json;
            try (FileInputStream fis = new FileInputStream(tmpJson)) {
                json = new String(fis.readAllBytes());
            }
            return extractWdaPids(json);
        } catch (Exception e) {
            System.err.println("[WdaManager] queryWdaPidsOnDevice error: " + e.getMessage());
            return java.util.Collections.emptySet();
        } finally {
            if (tmpJson != null) tmpJson.delete();
        }
    }

    /**
     * Extracts process identifiers for WebDriverAgentRunner entries from
     * a `xcrun devicectl device info processes --json-output` response.
     *
     * Xcode 26 JSON format per process:
     *   {"executable": "file:///path/to/WebDriverAgentRunner-Runner", "processIdentifier": N}
     *
     * Searches ±500 chars around each WDA marker in the executable URL.
     */
    private static Set<String> extractWdaPids(String json) {
        Set<String> pids = new LinkedHashSet<>();
        String[] markers = {"webdriveragentrunner", "webdriveragent", "xctrunner"};
        String jsonLower = json.toLowerCase();
        Pattern pidPat = Pattern.compile("\"processIdentifier\"\\s*:\\s*(\\d+)");

        for (String marker : markers) {
            int pos = 0;
            while ((pos = jsonLower.indexOf(marker, pos)) >= 0) {
                // Scan ±500 chars — processIdentifier is always adjacent in the same JSON object
                int lo = Math.max(0, pos - 500);
                int hi = Math.min(json.length(), pos + 500);
                Matcher m = pidPat.matcher(json.substring(lo, hi));
                if (m.find()) pids.add(m.group(1));
                pos++;
            }
        }
        return pids;
    }

    // ── Resultado de un intento de build ──────────────────────────────────────

    /**
     * Resultado de UN intento de arrancar WDA — nunca se comparte entre intentos.
     * {@code capturedError} lo actualiza en tiempo real {@link #streamBuildOutput}
     * con la línea "error:" más específica vista en ESTE proceso — WdaLifecycleOwner
     * la lee después de que {@link #waitForWdaReady} agota su plazo, garantizando
     * que el motivo mostrado en el Mirror pertenece siempre a este mismo intento,
     * nunca a appium.log ni a ningún otro proceso.
     */
    static final class BuildOutcome {
        final boolean started;
        final AtomicReference<String> capturedError = new AtomicReference<>();
        /**
         * True cuando xcodebuild reportó "MismatchedApplicationIdentifierEntitlement" —
         * ya hay un WDA instalado en el dispositivo firmado con un Team distinto al
         * actual (confirmado con evidencia real: iOS rechaza la instalación porque el
         * application-identifier embebido no coincide). Detección dedicada, separada de
         * capturedError, porque esta cadena NUNCA contiene el patrón "error:" — no es
         * detectable por ese mecanismo genérico.
         */
        final java.util.concurrent.atomic.AtomicBoolean mismatchedIdentifier =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        /**
         * True cuando xcodebuild emitió un final de tipo "test" (Testing failed:
         * / ** TEST FAILED **) en vez de un final de tipo "build" (BUILD FAILED).
         * xcodebuild test NUNCA imprime BUILD FAILED si el paso de compilación
         * en sí tuvo éxito y solo falló la instalación/lanzamiento del test —
         * confirmado con ejecución real (ver evidencia en findWdaProjectPath()
         * y streamBuildOutput()). Sin este flag, ese desenlace no se distinguía
         * de un build exitoso hasta agotar todo el timeout de waitForWdaReady.
         */
        final java.util.concurrent.atomic.AtomicBoolean testFailed =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        /**
         * True cuando xcodebuild reportó que el dispositivo está bloqueado/requiere
         * passcode ("Unlock ... to Continue", dominio com.apple.dt.deviceprep) —
         * confirmado con ejecución real: sin esta detección, xcodebuild espera su
         * propio timeout interno de "Run Destination Preflight" (hasta ~6 minutos)
         * antes de rendirse. Al detectar esta línea, streamBuildOutput() mata el
         * proceso INMEDIATAMENTE en vez de esperar ese timeout.
         */
        final java.util.concurrent.atomic.AtomicBoolean deviceLocked =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        private BuildOutcome(boolean started) { this.started = started; }

        static BuildOutcome started()    { return new BuildOutcome(true); }
        static BuildOutcome notStarted() { return new BuildOutcome(false); }

        String  capturedError()       { return capturedError.get(); }
        boolean mismatchedIdentifier() { return mismatchedIdentifier.get(); }
        boolean testFailed()           { return testFailed.get(); }
        boolean deviceLocked()         { return deviceLocked.get(); }
    }

    // ── Start attempt A: test-without-building ────────────────────────────────

    /**
     * Looks for a pre-built WebDriverAgentRunner .xctestrun in Xcode DerivedData and
     * launches xcodebuild test-without-building.
     */
    static BuildOutcome tryStartFromDerivedData(BackendClient client, String executionId, String udid) {
        File derivedDataRoot = new File(
                System.getProperty("user.home") + "/Library/Developer/Xcode/DerivedData");
        if (!derivedDataRoot.isDirectory()) return BuildOutcome.notStarted();

        File[] wdaDirs = derivedDataRoot.listFiles(
                f -> f.isDirectory() && f.getName().startsWith("WebDriverAgent-"));
        if (wdaDirs == null || wdaDirs.length == 0) return BuildOutcome.notStarted();

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
                    BuildOutcome outcome = BuildOutcome.started();
                    streamBuildOutput(p, "[WDA-fast]", client, executionId, outcome);
                    return outcome;
                } catch (Exception e) {
                    client.sendLog(executionId, "WARN",
                            "   [WDA] test-without-building no pudo iniciarse: " + e.getMessage());
                }
            }
        }
        return BuildOutcome.notStarted();
    }

    // ── Start attempt B: full xcodebuild test ─────────────────────────────────

    /**
     * Starts WDA via full xcodebuild test from the WebDriverAgent.xcodeproj.
     * Compila desde cero — funciona incluso sin DerivedData previo (primera vez
     * en un dispositivo/Mac limpios). Más lento, pero es el único camino que NO
     * depende de que algo haya compilado WDA antes.
     */
    static BuildOutcome tryStartFromProject(BackendClient client, String executionId,
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
                // Sin estos dos flags, xcodebuild no autogenera un provisioning profile
                // para un bundle ID nuevo (uno distinto por UDID, ver generateWdaBundleId())
                // y falla con "Automatic signing is disabled and unable to generate a
                // profile" / "No profiles for <bundleId>.xctrunner". Este es el ÚNICO
                // compilador real de WDA desde 0bdd825 (Appium ya nunca construye WDA por
                // su cuenta) — antes este mismo problema lo resolvía la capability
                // allowProvisioningDeviceRegistration de Appium (DriverFactory), que quedó
                // huérfana al retirar esa ruta. Se agrega aquí, en el único compilador que
                // sobrevivió, con la misma condición (solo cuando hay teamId real).
                cmd.add("-allowProvisioningUpdates");
                cmd.add("-allowProvisioningDeviceRegistration");
            }
            if (!wdaBundleId.isBlank()) {
                cmd.add("UPDATEDWDABUNDLEID=" + wdaBundleId);
            }

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            wdaProcess = p;
            BuildOutcome outcome = BuildOutcome.started();
            streamBuildOutput(p, "[WDA-build]", client, executionId, outcome);
            return outcome;
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "   [WDA] xcodebuild test no pudo iniciarse: " + e.getMessage());
            return BuildOutcome.notStarted();
        }
    }

    // ── Project path discovery ────────────────────────────────────────────────

    /**
     * Locates WebDriverAgent.xcodeproj using the SAME single source of truth
     * AppiumManager uses for where Appium's drivers are installed: the
     * {@code APPIUM_HOME} JVM property (set by the Runner launcher and forwarded
     * verbatim to the Appium server's own environment — see
     * {@code AppiumManager.java}'s "APPIUM_HOME — Appium's own node_modules location").
     *
     * Causa raíz corregida aquí (demostrada con ejecución real contra un dispositivo
     * físico): esta lista antes contenía 4 rutas hardcodeadas que nunca coincidían
     * con la ubicación real de instalación de este Runner
     * (AGENT_DATA_DIR/runtime/appium-home/...), así que el Mirror fallaba en
     * milisegundos con "No se encontró WebDriverAgent.xcodeproj" sin invocar
     * xcodebuild jamás. Ahora hay una única fuente de verdad: si APPIUM_HOME no
     * está definido (p. ej. un entorno de desarrollo sin el launcher del Runner),
     * se usa el mismo default documentado de Appium (~/.appium) — no una ruta
     * inventada específica de este proyecto.
     *
     * @return absolute path to WebDriverAgent.xcodeproj, or null if not found there.
     */
    public static String findWdaProjectPath() {
        String appiumHome = System.getProperty("APPIUM_HOME");
        if (appiumHome == null || appiumHome.isBlank()) {
            // Appium's own documented default when APPIUM_HOME is unset.
            appiumHome = System.getProperty("user.home") + "/.appium";
        }
        File candidate = new File(appiumHome,
                "node_modules/appium-xcuitest-driver/node_modules/appium-webdriveragent/WebDriverAgent.xcodeproj");
        return candidate.isDirectory() ? candidate.getAbsolutePath() : null;
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

    // Líneas que SIEMPRE deben llegar completas al Dashboard — categorías pedidas
    // explícitamente: codesign, provisioning, compilación, link, phase scripts,
    // errores y warnings. No es un filtro de qué se reenvía (se reenvía TODO,
    // ver streamBuildOutput) — solo decide qué línea puntual dispara una alerta
    // INFO/ERROR inmediata en vez de esperar al siguiente flush por lotes.
    private static final Pattern IMMEDIATE_SIGNAL = Pattern.compile(
            "(?i)error:|codesign|provisioning profile|requires a provisioning profile|"
            + "developer mode|coredevice|miinstallererrordomain|ixuserpresentableerrordomain|"
            + "mismatchedapplicationidentifierentitlement|testing failed|test failed|failed to install");

    // Dispositivo bloqueado/passcode requerido — confirmado con ejecución real contra
    // dispositivo físico: xcodebuild imprime "com.apple.dt.deviceprep" con el mensaje
    // "Unlock <device> to Continue" / "the device is locked" cuando la pantalla está
    // bloqueada. Detección específica de ese dominio de error — no el genérico "error:"
    // (evita falsos positivos con cualquier otra línea que mencione "locked").
    private static final Pattern DEVICE_LOCKED_PAT = Pattern.compile(
            "(?i)com\\.apple\\.dt\\.deviceprep|unlock .* to continue|the device is locked|"
            + "may need to be unlocked|timed out waiting for all destinations");

    private static final long BUILD_LOG_FLUSH_MS    = 1_000L;
    private static final int  BUILD_LOG_FLUSH_CHARS = 6_000;

    /**
     * Hilo daemon que reenvía TODA la salida de xcodebuild al Dashboard — no
     * únicamente BUILD SUCCEEDED/FAILED. Se agrupa en lotes (cada ~1s o ~6000
     * caracteres) para no convertir un build de cientos de líneas en cientos de
     * peticiones HTTP individuales, pero ninguna línea se descarta.
     *
     * Además:
     *  - Detecta "ServerURLHere->URL<-ServerURLHere" → guarda la URL, log INFO inmediato.
     *  - Detecta BUILD SUCCEEDED/FAILED → log INFO/ERROR inmediato (además del lote).
     *  - Captura en {@code capturedError} la línea "error:" más específica vista —
     *    WdaLifecycleOwner la usa como motivo real si el intento termina fallando,
     *    en vez de un mensaje genérico o de contenido de otro proceso.
     */
    private static void streamBuildOutput(Process p, String prefix, BackendClient client,
                                           String executionId, BuildOutcome outcome) {
        AtomicReference<String> capturedError = outcome.capturedError;
        Thread t = new Thread(() -> {
            StringBuilder batch = new StringBuilder();
            long lastFlush = System.currentTimeMillis();
            // True entre la línea "Testing failed:" y la línea en blanco /
            // "** TEST FAILED **" que la cierra — ver captura dedicada abajo.
            boolean insideTestFailureBlock = false;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(prefix + " " + line);
                    batch.append(line).append('\n');

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

                    // xcodebuild tiene DOS familias de desenlace final, no una:
                    //  - "build":  BUILD SUCCEEDED / BUILD FAILED (falla la compilación).
                    //  - "test":   ** TEST SUCCEEDED ** / ** TEST FAILED ** + el bloque
                    //              "Testing failed:" (falla instalar/lanzar el test aunque
                    //              la compilación haya tenido éxito).
                    // xcodebuild test (el que usa WDA) NUNCA imprime BUILD FAILED cuando
                    // el fallo ocurre en instalar/lanzar, no en compilar — confirmado con
                    // ejecución real: sin esta rama, ese desenlace pasaba desapercibido
                    // hasta agotar el timeout completo de waitForWdaReady (hasta 600s).
                    String upper = line.toUpperCase();
                    if (upper.contains("BUILD SUCCEEDED")) {
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "INFO",
                                    "✅ [WDA] WebDriverAgent compilado correctamente.");
                        }
                    } else if (upper.contains("BUILD FAILED")) {
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "ERROR",
                                    "❌ [WDA] Error compilando WDA — BUILD FAILED.");
                        }
                    } else if (upper.contains("** TEST SUCCEEDED **")) {
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "INFO",
                                    "✅ [WDA] xcodebuild test finalizó: TEST SUCCEEDED.");
                        }
                    } else if (upper.contains("** TEST FAILED **")) {
                        outcome.testFailed.set(true);
                        insideTestFailureBlock = false;
                        if (client != null && executionId != null) {
                            client.sendLog(executionId, "ERROR",
                                    "❌ [WDA] xcodebuild test finalizó: TEST FAILED.");
                        }
                    }

                    // Bloque "Testing failed:" — precede a "** TEST FAILED **" y trae la
                    // razón real, legible, del propio xcodebuild (p.ej. "Failed to install
                    // the app on the device. (Underlying Error: ... rejecting upgrade.)").
                    // Se captura de forma AUTORITATIVA (set, no compareAndSet): sustituye
                    // cualquier línea "error:" genérica ya capturada, porque esta cadena
                    // real de iOS demostró NO contener "error:" y por eso el detector
                    // genérico podía quedarse con una línea de diagnóstico irrelevante
                    // vista antes (falso positivo confirmado con ejecución real).
                    if (line.trim().equalsIgnoreCase("Testing failed:")) {
                        insideTestFailureBlock = true;
                        outcome.testFailed.set(true);
                    } else if (insideTestFailureBlock) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("**")) {
                            insideTestFailureBlock = false;
                        } else {
                            capturedError.set(trimmed);
                        }
                    }

                    // Captura la PRIMERA línea "error:" de ESTE intento — la causa raíz
                    // real suele aparecer antes que los resúmenes genéricos que xcodebuild
                    // imprime al final (p.ej. "xcodebuild: error: Failed to build workspace
                    // ..."). Nunca se sobreescribe con contenido de otro proceso (appium.log).
                    // El bloque "Testing failed:" de arriba puede sobreescribir esto después
                    // — deliberado, ver comentario de esa rama.
                    if (line.toLowerCase().contains("error:")) {
                        capturedError.compareAndSet(null, line.trim());
                    }

                    // Detección dedicada (no vía capturedError — esta línea real de iOS no
                    // contiene "error:", confirmado con ejecución real): un WDA anterior
                    // sigue instalado en el dispositivo, firmado con un Team distinto al
                    // actual. WdaLifecycleOwner decide si reintenta tras desinstalarlo.
                    if (line.contains("MismatchedApplicationIdentifierEntitlement")) {
                        outcome.mismatchedIdentifier.set(true);
                    }

                    // Dispositivo bloqueado/passcode requerido — confirmado con ejecución
                    // real: xcodebuild imprime esta línea (dominio com.apple.dt.deviceprep,
                    // "Unlock <device> to Continue") y luego espera SU PROPIO timeout interno
                    // de "Run Destination Preflight" — hasta ~6 minutos observados — antes de
                    // rendirse por su cuenta. En vez de esperar eso, se mata el proceso (y su
                    // árbol) AQUÍ MISMO, en cuanto aparece la línea, sin esperar nada más.
                    if (DEVICE_LOCKED_PAT.matcher(line).find()) {
                        outcome.deviceLocked.set(true);
                        client.sendLog(executionId, "ERROR",
                                "🔒 [WDA] Dispositivo bloqueado — cancelando el arranque de WDA "
                                + "inmediatamente (no se espera el timeout interno de xcodebuild). "
                                + "Desbloquea el iPhone y reintenta.");
                        try {
                            p.toHandle().descendants().forEach(h -> {
                                try { h.destroyForcibly(); } catch (Exception ignored2) {}
                            });
                        } catch (Exception ignored2) {}
                        p.destroyForcibly();
                        break;
                    }

                    long now = System.currentTimeMillis();
                    boolean dueByTime  = now - lastFlush >= BUILD_LOG_FLUSH_MS;
                    boolean dueBySize  = batch.length() >= BUILD_LOG_FLUSH_CHARS;
                    boolean immediate  = IMMEDIATE_SIGNAL.matcher(line).find();
                    if (batch.length() > 0 && (dueByTime || dueBySize || immediate)) {
                        flushBatch(client, executionId, prefix, batch);
                        lastFlush = now;
                    }
                }
                if (batch.length() > 0) flushBatch(client, executionId, prefix, batch);
            } catch (Exception ignored) {}
        }, "wda-drain");
        t.setDaemon(true);
        t.start();
    }

    private static void flushBatch(BackendClient client, String executionId, String prefix, StringBuilder batch) {
        if (client != null && executionId != null) {
            client.sendTechLog(executionId, prefix + "\n" + batch);
        }
        batch.setLength(0);
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
