package qa.cinepolis.runner;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full Appium lifecycle manager + AppiumValidator.
 *
 * Resolution strategy:
 *   Node:   AGENT_DATA_DIR/runtime/node/bin/node  →  NODE_BIN prop  →  system node
 *   Appium: AGENT_DATA_DIR/runtime/appium/node_modules/appium/index.js  →  APPIUM_BIN prop
 *
 * All Appium commands run as:  node  appium/index.js  [args...]
 * Never uses PATH, which appium, npm global, or .bin/appium shell wrappers.
 */
public class AppiumManager {

    private static final String APPIUM_URL                  = "http://127.0.0.1:4723/status";
    private static final int    PORT                        = 4723;
    private static final int    STARTUP_WAIT                = 30; // seconds
    private static final int    RESTART_DELAY               = 5;  // seconds
    private static final int    DRIVER_INSTALL_TIMEOUT_MIN  = 5;  // minutes

    private final String os;
    private volatile Process       appiumProcess = null;
    private volatile boolean       managedByUs   = false;
    private final    AtomicBoolean stopping      = new AtomicBoolean(false);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AppiumManager(String os) { this.os = os; }

    // ── Public API ────────────────────────────────────────────────────────

    public synchronized void ensureRunning() throws IOException, InterruptedException {
        if (isAlive()) {
            System.out.println("[AppiumValidator] Ya en ejecucion en el puerto " + PORT);
            return;
        }

        System.out.println("[AppiumValidator] Localizando Appium...");
        String entryPoint = findAppiumEntryPoint();

        if (entryPoint == null) {
            System.out.println("[AppiumValidator] No encontrado. Instalando via npm...");
            installAppium();
            entryPoint = findAppiumEntryPoint();
        }

        if (entryPoint == null) {
            throw new IOException("Appium no pudo ser instalado ni localizado.");
        }

        startAppium(entryPoint);
    }

    /**
     * True if Appium responds on /status with HTTP 200 AND body contains "ready":true.
     */
    public boolean isAlive() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(APPIUM_URL))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean http200 = res.statusCode() == 200;
            boolean ready   = res.body().contains("\"ready\":true")
                           || res.body().contains("\"ready\": true");
            return http200 && ready;
        } catch (Exception e) { return false; }
    }

    /**
     * Repairs Appium by stopping it, reinstalling drivers at server-compatible versions,
     * and restarting. Called by DependencySelfHealingManager when Appium is FAIL.
     */
    public void healDrivers() throws IOException, InterruptedException {
        System.out.println("[AppiumValidator] Iniciando reparacion de drivers...");
        stop();
        stopping.set(false);
        Thread.sleep(2000);

        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) {
            System.err.println("[AppiumValidator] Entry point no encontrado — no se puede reparar.");
            return;
        }

        String appiumVersion = getAppiumVersion();
        System.out.printf("[AppiumValidator] Server=%s — reinstalando drivers compatibles...%n", appiumVersion);

        uninstallDriver(entryPoint, "uiautomator2");
        if ("MACOS".equals(os)) uninstallDriver(entryPoint, "xcuitest");

        installDriver("uiautomator2", appiumVersion);
        if ("MACOS".equals(os)) installDriver("xcuitest", appiumVersion);

        System.out.println("[AppiumValidator] Reiniciando Appium post-reparacion...");
        ensureRunning();
    }

    /**
     * Returns true if the xcuitest driver is present in {@code driverListOutput}.
     *
     * Appium 2.x sometimes emits ALL available drivers (installed + not-installed) even
     * with --installed, so a plain .contains("xcuitest") produces false positives.
     * This method requires xcuitest to appear with a version number (xcuitest@X) — the
     * only form Appium uses for a genuinely installed driver — and rejects explicit
     * "not installed" markers.
     */
    static boolean xcuitestIsInstalled(String driverListOutput) {
        if (driverListOutput == null) return false;
        String lower = driverListOutput.toLowerCase();
        if (!lower.contains("xcuitest")) return false;
        // Reject explicit "not installed" entries
        if (java.util.regex.Pattern.compile("xcuitest[^\\n]*not installed")
                .matcher(lower).find()) return false;
        // Accept only when a version number follows (xcuitest@7.19.6 or xcuitest 7.19.6)
        return java.util.regex.Pattern.compile("xcuitest[@\\s]\\d")
                .matcher(lower).find();
    }

    /**
     * Ensures the xcuitest Appium driver is installed.
     * Checks 'appium driver list --installed'; installs if missing.
     *
     * @return true if xcuitest is installed (pre-existing or just installed), false on failure
     */
    public boolean ensureXcuitestInstalled() {
        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) return false;

        if (xcuitestIsInstalled(getInstalledDriverList())) return true;

        String appiumVersion = getAppiumVersion();
        String spec          = buildDriverSpec("xcuitest", appiumVersion);
        try {
            Process p = withAppiumHome(
                    new ProcessBuilder(nodeCmd(entryPoint, "driver", "install", spec))
                            .redirectErrorStream(true))
                    .start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            boolean done = p.waitFor(DRIVER_INSTALL_TIMEOUT_MIN, TimeUnit.MINUTES);
            if (!done) { p.destroyForcibly(); return false; }
            if (p.exitValue() != 0) return false;
        } catch (Exception e) { return false; }

        return xcuitestIsInstalled(getInstalledDriverList());
    }

    /**
     * Returns the raw output of 'appium driver list --installed' for diagnostics.
     */
    public String getInstalledDriverList() {
        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) return "appium-entry-point-not-found";
        try {
            ProcessBuilder pb = withAppiumHome(
                    new ProcessBuilder(nodeCmd(entryPoint, "driver", "list", "--installed"))
                            .redirectErrorStream(true));
            Process p = pb.start();
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return "timeout"; }
            return new String(p.getInputStream().readAllBytes()).trim();
        } catch (Exception e) { return "error: " + e.getMessage(); }
    }

    public void startWatchdog(ScheduledExecutorService scheduler) {
        startWatchdog(scheduler, null);
    }

    public void startWatchdog(ScheduledExecutorService scheduler, Runnable onRestart) {
        scheduler.scheduleAtFixedRate(() -> {
            if (stopping.get() || !managedByUs) return;
            if (!isAlive()) {
                System.out.println("[AppiumValidator] Proceso caido — reiniciando...");
                try {
                    Thread.sleep(RESTART_DELAY * 1000L);
                    ensureRunning();
                    if (isAlive() && onRestart != null) onRestart.run();
                } catch (Exception e) {
                    System.err.println("[AppiumValidator] Error al reiniciar: " + e.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /** True if an Appium entry point can be located (server need not be running). */
    public boolean canStart() { return findAppiumEntryPoint() != null; }

    /** Returns the Appium version string (e.g. "3.5.2") or "unavailable". */
    public String getAppiumVersion() {
        String entryPoint = findAppiumEntryPoint();
        if (entryPoint == null) return "unavailable";
        try {
            Process p = new ProcessBuilder(nodeCmd(entryPoint, "--version"))
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return "unavailable"; }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return (p.exitValue() == 0 && !out.isBlank()) ? out.split("\n")[0].trim() : "unavailable";
        } catch (Exception e) { return "unavailable"; }
    }

    public void stop() {
        stopping.set(true);
        Process p = appiumProcess;
        if (p != null && p.isAlive()) {
            p.destroy();
            try { p.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            if (p.isAlive()) p.destroyForcibly();
            System.out.println("[AppiumValidator] Proceso detenido.");
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────

    /**
     * Logs a full diagnostic snapshot. Does NOT assume Appium is running.
     * Logged format:
     *   [AppiumValidator]
     *   [AppiumValidator] NodeBin=...  Exists=true
     *   [AppiumValidator] AppiumEntryPoint=...
     *   [AppiumValidator] Exists=true  Readable=true
     *   [AppiumValidator] Version=3.5.2
     *   [AppiumValidator] StatusEndpoint=OK
     */
    public void logDiagnostic() {
        String  nodeBin    = resolveNodeBin();
        boolean nodeExists = Files.isExecutable(Path.of(nodeBin));
        String  entryPoint = resolveEmbeddedEntryPoint();
        boolean epExists   = entryPoint != null && Files.exists(Path.of(entryPoint));
        boolean epReadable = epExists && Files.isReadable(Path.of(entryPoint));
        String  version    = "unavailable";
        if (epExists && epReadable && nodeExists) {
            try {
                Process p = new ProcessBuilder(nodeBin, entryPoint, "--version")
                        .redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    String out = new String(p.getInputStream().readAllBytes()).trim();
                    if (!out.isBlank()) version = out.split("\n")[0].trim();
                } else {
                    p.getInputStream().transferTo(OutputStream.nullOutputStream());
                }
            } catch (Exception ignored) {}
        }
        boolean alive = isAlive();

        System.out.println("[AppiumValidator]");
        System.out.printf("[AppiumValidator] NodeBin=%s Exists=%s%n", nodeBin, nodeExists);
        System.out.printf("[AppiumValidator] AppiumEntryPoint=%s%n",
                entryPoint != null ? entryPoint : "NOT_FOUND");
        System.out.printf("[AppiumValidator] Exists=%s Readable=%s%n", epExists, epReadable);
        System.out.printf("[AppiumValidator] Version=%s%n", version);
        System.out.printf("[AppiumValidator] StatusEndpoint=%s%n", alive ? "OK" : "FAIL");
    }

    // ── Resolution ────────────────────────────────────────────────────────

    /**
     * Resolves the Node.js binary:
     *   1. AGENT_DATA_DIR/runtime/node/bin/node  (embedded enterprise — primary)
     *   2. NODE_BIN system property
     *   3. System "node" (dev/fallback — not used in enterprise runtime)
     */
    private String resolveNodeBin() {
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path embedded = Path.of(agentDataDir, "runtime", "node", "bin",
                                    isWindows() ? "node.exe" : "node");
            if (Files.isExecutable(embedded)) return embedded.toString();
        }
        String prop = System.getProperty("NODE_BIN", "");
        if (!prop.isBlank() && Files.isExecutable(Path.of(prop))) return prop;
        return isWindows() ? "node.exe" : "node";
    }

    /**
     * Resolves the Appium entry point without running any process.
     * Used by logDiagnostic().
     *   1. AGENT_DATA_DIR/runtime/appium/node_modules/appium/index.js
     *   2. APPIUM_BIN system property
     */
    private String resolveEmbeddedEntryPoint() {
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path p = Path.of(agentDataDir, "runtime", "appium",
                             "node_modules", "appium", "index.js");
            if (Files.exists(p)) return p.toString();
        }
        String prop = System.getProperty("APPIUM_BIN", "");
        if (!prop.isBlank() && Files.exists(Path.of(prop))) return prop;
        return null;
    }

    /**
     * Finds a working Appium entry point by probing candidates with --version.
     * Package-visible so DependencySelfHealingManager can call canStart() without duplication.
     */
    String findAppiumEntryPoint() {
        List<String> candidates = buildCandidates();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (probeEntryPoint(candidate)) {
                System.out.println("[AppiumValidator] AppiumEntryPoint=" + candidate);
                return candidate;
            }
        }
        System.err.println("[AppiumValidator] AppiumEntryPoint=NOT_FOUND — intentados: " + candidates.size());
        return null;
    }

    /** Backward-compat alias used internally and by DependencySelfHealingManager. */
    String findAppiumBin() { return findAppiumEntryPoint(); }

    private List<String> buildCandidates() {
        List<String> list = new ArrayList<>();

        // Priority 1: canonical embedded enterprise path — index.js, not shell wrapper
        String agentDataDir = System.getProperty("AGENT_DATA_DIR", "");
        if (!agentDataDir.isBlank()) {
            Path indexJs = Path.of(agentDataDir, "runtime", "appium",
                                   "node_modules", "appium", "index.js");
            if (Files.exists(indexJs)) list.add(indexJs.toString());
        }

        // Priority 2: APPIUM_BIN property (may point to index.js or a wrapper)
        String prop = System.getProperty("APPIUM_BIN", "");
        if (!prop.isBlank() && Files.exists(Path.of(prop)) && !list.contains(prop))
            list.add(prop);

        // Priority 3+: dev/non-enterprise only
        if (!isWindows()) {
            list.add("/opt/homebrew/bin/appium");
            list.add("/usr/local/bin/appium");
        }
        list.add(isWindows() ? "npx.cmd appium" : "npx appium");

        return list;
    }

    /**
     * Probes an entry point candidate by running --version via Node.
     * File paths → ProcessBuilder array (no shell, handles spaces correctly).
     * Shell tokens (npx) → sh -c.
     */
    private boolean probeEntryPoint(String candidate) {
        try {
            boolean isFile = Files.exists(Path.of(candidate));
            String[] cmd;
            if (isFile) {
                // index.js or any real file: run via Node — array form handles spaces
                cmd = new String[]{resolveNodeBin(), candidate, "--version"};
            } else {
                // Shell token: "npx appium"
                cmd = isWindows() ? new String[]{"cmd", "/c", candidate, "--version"}
                                  : new String[]{"sh", "-c", candidate + " --version"};
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes());
            if (!done) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0 && out.matches("(?s).*\\d+\\.\\d+.*");
        } catch (Exception e) { return false; }
    }

    // ── Commands ──────────────────────────────────────────────────────────

    /**
     * Builds a [nodeBin, entryPoint, args...] command array.
     * ProcessBuilder handles spaces in all path components correctly.
     */
    private String[] nodeCmd(String entryPoint, String... args) {
        String[] cmd = new String[2 + args.length];
        cmd[0] = resolveNodeBin();
        cmd[1] = entryPoint;
        System.arraycopy(args, 0, cmd, 2, args.length);
        return cmd;
    }

    private ProcessBuilder withAppiumHome(ProcessBuilder pb) {
        // APPIUM_HOME — Appium's own node_modules location
        String appiumHome = System.getProperty("APPIUM_HOME");
        if (appiumHome != null && !appiumHome.isBlank())
            pb.environment().put("APPIUM_HOME", appiumHome);

        // Android SDK — UiAutomator2 driver runs adb internally and needs ANDROID_HOME.
        // The process inherits the Runner JVM env, which may lack these when running as a
        // LaunchAgent / Windows Service (shell rc files are NOT sourced for services).
        AndroidEnvironmentBootstrap androidEnv = AndroidEnvironmentBootstrap.get();
        if (androidEnv.isValid()) {
            pb.environment().putAll(androidEnv.buildEnv());
        }

        return pb;
    }

    // ── Start ─────────────────────────────────────────────────────────────

    /**
     * Ruta del log del SERVIDOR Appium (proceso Node.js de larga duración,
     * separado del proceso Gradle/JUnit que el Runner reenvía al backend).
     * Con showXcodeLog=true, Appium escribe aquí la salida CRUDA de xcodebuild
     * — ver AppiumXcodebuildLogForwarder, que lee este mismo archivo para
     * reenviar esa salida al backend sin tocar el flujo de JobExecutor.
     */
    static Path resolveLogFile() {
        String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                System.getProperty("user.home") + "/.automationqa");
        return Path.of(agentDataDir, "logs", "appium.log");
    }

    // ── Reglas de entorno específicas por plataforma ─────────────────────────
    //
    // Punto único de decisión: cada variable de entorno que solo debe aplicarse
    // bajo ciertas condiciones de plataforma se modela como un PlatformEnvRule.
    // Agregar una nueva variable futura = agregar una nueva implementación aquí
    // y sumarla a PLATFORM_ENV_RULES — startAppium() no necesita condicionales
    // adicionales dispersos.
    //
    // Limitación arquitectónica honesta: Appium corre como UN proceso Node.js
    // persistente, arrancado una sola vez (ensureRunning() se llama una única
    // vez al iniciar el Runner — ver RunnerAgent.java). Las variables de entorno
    // de un proceso solo pueden fijarse al arrancarlo, así que estas reglas se
    // evalúan una vez por arranque de Appium (no por cada ejecución individual).
    // El contexto ("¿este Runner tiene capacidad iOS?") es lo más cercano a
    // "plataforma objetivo" que se puede determinar sin reiniciar Appium por
    // job — que requeriría que JobExecutor llamara a AppiumManager antes de
    // cada ejecución, fuera del alcance de este cambio.

    /** Contexto de plataforma usado para evaluar las reglas de entorno de Appium. */
    private record AppiumEnvironmentContext(String runnerOs, boolean iosCapable) {
        String detectedPlatformLabel() {
            return switch (runnerOs) {
                case "MACOS"   -> "🍎 macOS";
                case "WINDOWS" -> "🖥 Windows";
                case "LINUX"   -> "🐧 Linux";
                default        -> "❓ " + runnerOs;
            };
        }
        String targetPlatformLabel() {
            return iosCapable ? "📱 iOS" : "🤖 Android";
        }
    }

    /** Contrato de una regla de entorno específica por plataforma para el proceso Appium. */
    private interface PlatformEnvRule {
        String variableName();
        String valueWhenApplies();
        boolean appliesTo(AppiumEnvironmentContext ctx);
        String rationale();
    }

    /**
     * APPIUM_XCUITEST_PREFER_DEVICECTL=1 — solo cuando el Runner es macOS Y tiene
     * capacidad iOS (driver xcuitest instalado). Nunca se activa en Windows/Linux
     * ni en un Runner que nunca aprovisionó iOS.
     *
     * Qué cambia exactamente cuando aplica: dentro de appium-xcuitest-driver,
     * ConnectedDevicesClient.listLegacyUdids() (connected-devices-client.ts) usa
     * esta variable para decidir cómo listar UDIDs de dispositivos reales cuando
     * el registro de túneles RemoteXPC no está disponible ("Tunnel registry port
     * not found"): en vez de usbmuxd (appium-ios-device), usa
     * `xcrun devicectl list devices --json-output` (node-devicectl) — el mismo
     * comando que ya usa el Runner (IOSDeviceScanner/CoreDeviceTunnelManager)
     * para detectar el dispositivo.
     */
    private static final class PreferDevicectlRule implements PlatformEnvRule {
        static final String VAR = "APPIUM_XCUITEST_PREFER_DEVICECTL";
        @Override public String variableName() { return VAR; }
        @Override public String valueWhenApplies() { return "1"; }
        @Override public boolean appliesTo(AppiumEnvironmentContext ctx) {
            return "MACOS".equals(ctx.runnerOs()) && ctx.iosCapable();
        }
        @Override public String rationale() {
            return "listado de dispositivos usará xcrun devicectl en vez de usbmuxd";
        }
    }

    /** Reglas activas — agregar futuras variables de entorno específicas por plataforma aquí. */
    private static final List<PlatformEnvRule> PLATFORM_ENV_RULES = List.of(new PreferDevicectlRule());

    /** Alias retrocompatible usado por el resto de la clase (diagnóstico, validación de herencia). */
    private static final String PREFER_DEVICECTL_VAR = PreferDevicectlRule.VAR;

    /**
     * Determina si este Runner tiene capacidad iOS: macOS Y el driver xcuitest
     * instalado. En Windows/Linux se evita por completo la llamada a
     * getInstalledDriverList() (cortocircuito de &&) — no hace falta consultar
     * nada para saber que no aplica.
     */
    private boolean detectIosCapability() {
        return "MACOS".equals(os) && xcuitestIsInstalled(getInstalledDriverList());
    }

    private void startAppium(String entryPoint) throws IOException, InterruptedException {
        System.out.println("[AppiumValidator] 🚀 Iniciando Appium...");
        System.out.println("[AppiumValidator] Comando: node " + entryPoint + " --port " + PORT);

        Path logFile = resolveLogFile();
        Files.createDirectories(logFile.getParent());

        ProcessBuilder pb = withAppiumHome(
                new ProcessBuilder(nodeCmd(entryPoint, "--port", String.valueOf(PORT),
                        "--log", logFile.toString(), "--log-timestamp"))
                        .redirectErrorStream(true)
                        .redirectOutput(logFile.toFile()));

        // ── Decisión centralizada de entorno por plataforma ──────────────────
        AppiumEnvironmentContext ctx = new AppiumEnvironmentContext(os, detectIosCapability());
        System.out.println("[AppiumValidator] " + ctx.detectedPlatformLabel() + " Plataforma detectada: " + os);
        System.out.println("[AppiumValidator] " + ctx.targetPlatformLabel() + " Plataforma objetivo: "
                + (ctx.iosCapable() ? "iOS" : "Android"));

        for (PlatformEnvRule rule : PLATFORM_ENV_RULES) {
            if (rule.appliesTo(ctx)) {
                pb.environment().put(rule.variableName(), rule.valueWhenApplies());
                System.out.println("[AppiumValidator] ✅ " + rule.variableName() + "=" + rule.valueWhenApplies()
                        + " habilitado — " + rule.rationale());
            } else {
                System.out.println("[AppiumValidator] ℹ " + rule.variableName() + " no aplica para esta ejecución.");
            }
        }

        appiumProcess = pb.start();
        managedByUs   = true;
        long pid = appiumProcess.pid();

        // Bloque de diagnóstico completo — se escribe tanto al log técnico (System.out,
        // reenviado al backend) como directamente a appium.log. Se genera DESPUÉS de
        // pb.start(): ProcessBuilder.redirectOutput(file) trunca el archivo al abrirlo,
        // así que escribir antes se perdería.
        String diagnosticBlock = buildAppiumEnvironmentDiagnostic(pb, pid, entryPoint);
        System.out.println(diagnosticBlock);
        appendToAppiumLog(logFile, diagnosticBlock);

        System.out.println("[AppiumValidator] Esperando /status en puerto " + PORT + "...");
        System.out.println("[AppiumValidator] Log: " + logFile);

        boolean started = false;
        for (int i = 0; i < STARTUP_WAIT; i++) {
            Thread.sleep(1000);
            if (isAlive()) {
                started = true;
                break;
            }
            if (!appiumProcess.isAlive()) {
                throw new IOException("Appium salio prematuramente. Ver log: " + logFile);
            }
        }
        if (!started) {
            throw new IOException("Appium no respondio en " + STARTUP_WAIT + "s. Ver log: " + logFile);
        }

        System.out.println("[AppiumValidator] ✓ Appium iniciado correctamente (StatusEndpoint OK, ready:true)");
        appendToAppiumLog(logFile, "[AppiumValidator] ✓ Appium iniciado correctamente (PID " + pid + ")");

        // Validación inmediata — confirma que el proceso REALMENTE heredó la variable
        // cuando la regla decidió aplicarla, en vez de asumirlo solo por haberla puesto
        // en pb.environment(). Cualquier fallo en la propia verificación se reporta como
        // ERROR explícito, nunca como éxito. Cuando la regla NO aplica (Android/Windows/
        // Linux/sin capacidad iOS), no hay nada que verificar — se informa y se sale.
        verifyPreferDevicectlInheritance(pid, logFile, new PreferDevicectlRule().appliesTo(ctx));
    }

    /** Construye el bloque de diagnóstico de entorno solicitado — no asume nada, lee valores reales. */
    private String buildAppiumEnvironmentDiagnostic(ProcessBuilder pb, long pid, String entryPoint) {
        Map<String, String> env = pb.environment();
        String appiumVersion   = getAppiumVersion();
        String xcuitestVersion = "MACOS".equals(os)
                ? extractXcuitestVersion(getInstalledDriverList())
                : "N/A (plataforma no-macOS)";
        String workDir = pb.directory() != null
                ? pb.directory().getAbsolutePath()
                : System.getProperty("user.dir");

        StringBuilder sb = new StringBuilder();
        sb.append("[AppiumValidator] 🔧 Appium Environment\n");
        sb.append("[AppiumValidator]   ").append(PREFER_DEVICECTL_VAR).append(" = ")
                .append(nvl(env.get(PREFER_DEVICECTL_VAR))).append('\n');
        sb.append("[AppiumValidator]   JAVA_HOME    = ").append(nvl(System.getenv("JAVA_HOME"))).append('\n');
        sb.append("[AppiumValidator]   NODE_HOME    = ").append(nvl(resolveNodeBin())).append('\n');
        sb.append("[AppiumValidator]   ANDROID_HOME = ")
                .append(nvl(env.getOrDefault("ANDROID_HOME", System.getenv("ANDROID_HOME")))).append('\n');
        sb.append("[AppiumValidator]   APPIUM_HOME  = ").append(nvl(env.get("APPIUM_HOME"))).append('\n');
        sb.append("[AppiumValidator]   PATH         = ").append(nvl(env.get("PATH"))).append('\n');
        sb.append("[AppiumValidator] Appium version        : ").append(appiumVersion).append('\n');
        sb.append("[AppiumValidator] XCUITest driver       : ").append(xcuitestVersion).append('\n');
        sb.append("[AppiumValidator] Puerto                : ").append(PORT).append('\n');
        sb.append("[AppiumValidator] PID                   : ").append(pid).append('\n');
        sb.append("[AppiumValidator] Directorio de trabajo : ").append(workDir).append('\n');
        sb.append("[AppiumValidator] Entry point           : ").append(entryPoint);
        return sb.toString();
    }

    /** Extrae la versión instalada del driver xcuitest de la salida de 'driver list --installed'. */
    private String extractXcuitestVersion(String driverListOutput) {
        if (driverListOutput == null) return "no detectado";
        Matcher m = Pattern.compile("xcuitest[@\\s]([\\d][\\w.\\-]*)", Pattern.CASE_INSENSITIVE)
                .matcher(driverListOutput);
        return m.find() ? m.group(1) : "no detectado";
    }

    private static String nvl(String v) {
        return (v == null || v.isBlank()) ? "(no establecida)" : v;
    }

    /** Agrega una línea/bloque con timestamp directamente a appium.log — no interfiere con lo que Appium mismo escribe. */
    private void appendToAppiumLog(Path logFile, String block) {
        try {
            String stamped = "[" + Instant.now() + "] " + block.replace("\n", "\n[" + Instant.now() + "] ") + "\n";
            Files.writeString(logFile, stamped, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[AppiumValidator] No se pudo escribir el diagnostico en appium.log: " + e.getMessage());
        }
    }

    /**
     * Verifica que el proceso Appium REALMENTE heredó APPIUM_XCUITEST_PREFER_DEVICECTL=1,
     * leyendo el entorno real del proceso vía 'ps eww {pid}' (macOS) — nunca asume éxito
     * solo por haberla puesto en ProcessBuilder.environment(). Cualquier fallo de la
     * propia verificación se reporta como ERROR explícito, para no producir falsos
     * positivos.
     *
     * @param expectedApplied si la regla decidió que esta variable debía activarse para
     *                        esta plataforma — si es false (Android/Windows/Linux/sin
     *                        capacidad iOS), no hay nada que verificar.
     */
    private void verifyPreferDevicectlInheritance(long pid, Path logFile, boolean expectedApplied) {
        if (!expectedApplied) {
            System.out.println("[AppiumValidator] Verificación de herencia omitida — "
                    + PREFER_DEVICECTL_VAR + " no aplica para esta plataforma/ejecución.");
            return;
        }
        if (!"MACOS".equals(os)) {
            System.out.println("[AppiumValidator] Verificación de herencia de entorno omitida (solo aplica en macOS).");
            return;
        }
        try {
            Process p = new ProcessBuilder("ps", "eww", String.valueOf(pid))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                String msg = "[AppiumValidator] ERROR: no se pudo verificar el entorno real del proceso Appium "
                        + "('ps eww " + pid + "' no respondió a tiempo) — NO se puede confirmar si "
                        + PREFER_DEVICECTL_VAR + " fue heredada. No se asume éxito.";
                System.err.println(msg);
                appendToAppiumLog(logFile, msg);
                return;
            }

            boolean inherited = out.contains(PREFER_DEVICECTL_VAR + "=1");
            if (inherited) {
                System.out.println("[AppiumValidator] ✓ Variable heredada por el proceso "
                        + "(confirmado leyendo el entorno real vía 'ps eww " + pid + "')");
                System.out.println("[AppiumValidator] ✓ Entorno cargado correctamente");
                appendToAppiumLog(logFile, "[AppiumValidator] ✓ " + PREFER_DEVICECTL_VAR
                        + " confirmada en el entorno real del proceso (PID " + pid + ")");
            } else {
                String msg = "[AppiumValidator] ERROR: " + PREFER_DEVICECTL_VAR + " NO aparece en el entorno "
                        + "real del proceso Appium (PID " + pid + "). Se estableció en "
                        + "ProcessBuilder.environment() pero 'ps eww' no la muestra heredada — el listado "
                        + "de dispositivos usará usbmuxd (comportamiento anterior), no xcrun devicectl. "
                        + "Revisa si algo está reemplazando el entorno del proceso.";
                System.err.println(msg);
                appendToAppiumLog(logFile, msg);
            }
        } catch (Exception e) {
            String msg = "[AppiumValidator] ERROR: fallo al verificar herencia de entorno: " + e.getMessage()
                    + " — no se puede confirmar " + PREFER_DEVICECTL_VAR + ". No se asume éxito.";
            System.err.println(msg);
            appendToAppiumLog(logFile, msg);
        }
    }

    // ── Install ───────────────────────────────────────────────────────────

    private void installAppium() throws IOException, InterruptedException {
        String nodeBin = resolveNodeBin();
        String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                System.getProperty("user.home") + "/.automationqa");
        String prefixDir = Path.of(agentDataDir, "runtime", "appium").toString();
        Files.createDirectories(Path.of(prefixDir));

        Path nodeDir = Path.of(nodeBin).getParent();
        String npmBin = nodeDir.resolve(isWindows() ? "npm.cmd" : "npm").toString();
        System.out.println("[AppiumValidator] Instalando Appium en: " + prefixDir);
        Process p = new ProcessBuilder(npmBin, "install", "--prefix", prefixDir, "appium",
                "--no-audit", "--no-fund")
                .inheritIO().start();
        boolean done = p.waitFor(10, TimeUnit.MINUTES);
        if (!done || p.exitValue() != 0)
            throw new IOException("npm install appium fallo con codigo " + (done ? p.exitValue() : "timeout"));

        System.out.println("[AppiumValidator] Appium instalado.");
        String version = getAppiumVersion();
        System.out.printf("[AppiumValidator] Server=%s — instalando drivers compatibles...%n", version);
        installDriver("uiautomator2", version);
        if ("MACOS".equals(os)) installDriver("xcuitest", version);
    }

    private void installDriver(String driver, String appiumVersion) {
        String spec = buildDriverSpec(driver, appiumVersion);
        try {
            System.out.println("[AppiumValidator] Instalando driver: " + spec);
            String entryPoint = findAppiumEntryPoint();
            if (entryPoint == null) return;
            withAppiumHome(new ProcessBuilder(nodeCmd(entryPoint, "driver", "install", spec))
                    .inheritIO())
                    .start().waitFor(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[AppiumValidator] Warning: no se pudo instalar driver " + spec + ": " + e.getMessage());
        }
    }

    private void uninstallDriver(String entryPoint, String driver) {
        try {
            System.out.println("[AppiumValidator] Desinstalando driver: " + driver);
            withAppiumHome(new ProcessBuilder(nodeCmd(entryPoint, "driver", "uninstall", driver))
                    .redirectErrorStream(true))
                    .start().waitFor(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("[AppiumValidator] Warning: uninstall " + driver + ": " + e.getMessage());
        }
    }

    /**
     * Maps driver + Appium server version to a version-pinned npm spec.
     *   Appium 2.x → uiautomator2@2 / xcuitest@7
     *   Appium 3.x → latest (compatible by design)
     */
    private String buildDriverSpec(String driver, String appiumVersion) {
        if (appiumVersion == null || appiumVersion.equals("unavailable")) return driver;
        if (appiumVersion.startsWith("2.")) {
            return switch (driver) {
                case "uiautomator2" -> "uiautomator2@2";
                case "xcuitest"     -> "xcuitest@7";
                default             -> driver;
            };
        }
        return driver;
    }

    // ── Utils ─────────────────────────────────────────────────────────────

    private boolean isWindows() { return "WINDOWS".equals(os); }
}
