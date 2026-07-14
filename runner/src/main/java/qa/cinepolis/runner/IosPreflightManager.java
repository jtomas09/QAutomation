package qa.cinepolis.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * iOS-specific pre-flight checks and WDA (WebDriverAgent) cache management.
 *
 * Responsibilities:
 *  1. Validate Xcode, Apple Developer Team, iOS device, Developer Mode
 *  2. Detect real iOS version via xcrun/devicectl
 *  3. Detect Apple Team ID via security keychain
 *  4. Manage per-device WDA cache so recompilation is skipped on subsequent runs
 *
 * The cache file lives at ~/.qautomation/wda/{udid}.properties.
 * DriverFactory (test process) writes to this file after a successful WDA build.
 * This class (Runner process) reads it before launching Gradle to pass -DwdaPrebuilt=true.
 *
 * Does NOT touch Android logic.
 */
public class IosPreflightManager {

    static final String CACHE_DIR =
            System.getProperty("user.home") + "/.qautomation/wda";

    // ── Result ────────────────────────────────────────────────────────────────

    public static class IosPreflightResult {
        public final String  teamId;
        public final String  iosVersion;
        public final String  wdaBundleId;
        public final boolean wdaCached;
        /** True if WDA was confirmed running on localhost:8100 during preflight. */
        public final boolean wdaReady;

        // ── Confirmed device sync state (passed to DriverFactory via -DiosState.*) ──
        /** True when xctrace confirmed the device visible at end of preflight. */
        public final boolean xctraceConfirmed;
        /** tunnelState observed at end of preflight (informational). */
        public final String  tunnelState;
        /** pairingState observed at end of preflight. */
        public final String  pairingState;
        /** CoreDevice identifier UUID, or empty string if not detected. */
        public final String  coreDeviceId;
        /** System.currentTimeMillis() at the moment this result was created. */
        public final long    confirmedAtMs;
        /** transportType as reported by devicectl — WIRED, LOCAL_NETWORK, or UNKNOWN. */
        public final String  transportType;
        /** Runner's definitive readiness decision — false when transport/tunnel/pairing block execution. */
        public final boolean readyForExecution;
        /** Human-readable reason when readyForExecution=false; null when ready. */
        public final String  notReadyReason;
        /** True when the device screen was confirmed unlocked at the final stability check. */
        public final boolean deviceUnlocked;
        /**
         * System.currentTimeMillis() when the final unlock check passed.
         * The Framework uses this to compute elapsed time before creating IOSDriver and
         * warn when the gap exceeds 5 seconds (device may have auto-locked).
         */
        public final long    confirmedUnlockedAtMs;

        IosPreflightResult(String teamId, String iosVersion,
                           String wdaBundleId, boolean wdaCached, boolean wdaReady,
                           boolean xctraceConfirmed, String tunnelState,
                           String pairingState, String coreDeviceId,
                           String transportType, boolean readyForExecution, String notReadyReason,
                           boolean deviceUnlocked, long confirmedUnlockedAtMs) {
            this.teamId                = teamId;
            this.iosVersion            = iosVersion;
            this.wdaBundleId           = wdaBundleId;
            this.wdaCached             = wdaCached;
            this.wdaReady              = wdaReady;
            this.xctraceConfirmed      = xctraceConfirmed;
            this.tunnelState           = tunnelState   != null ? tunnelState   : "unknown";
            this.pairingState          = pairingState  != null ? pairingState  : "unknown";
            this.coreDeviceId          = coreDeviceId  != null ? coreDeviceId  : "";
            this.confirmedAtMs         = System.currentTimeMillis();
            this.transportType         = transportType != null ? transportType : "UNKNOWN";
            this.readyForExecution     = readyForExecution;
            this.notReadyReason        = notReadyReason;
            this.deviceUnlocked        = deviceUnlocked;
            this.confirmedUnlockedAtMs = confirmedUnlockedAtMs;
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static IosPreflightResult runPreflight(
            BackendClient client, String executionId, String udid) {

        // Señala a IOSMirrorProvider que una ejecución real es dueña del ciclo de vida
        // de WDA a partir de aquí — ver WdaManager.markTestExecutionStart().
        WdaManager.markTestExecutionStart();

        // Único punto de publicación de "WDA inicializando" para AMBOS flujos —
        // este método lo invoca tanto una ejecución real (JobExecutor) como el
        // lanzamiento on-demand del Mirror (IOSMirrorProvider.triggerOnDemandLaunch).
        // Ver WdaEventBus — no duplicar esta llamada en ninguno de los dos flujos.
        qa.cinepolis.runner.mirror.WdaEventBus.publish(
                udid, qa.cinepolis.runner.mirror.WdaEventBus.WdaEvent.INITIALIZING);

        client.sendLog(executionId, "INFO",
                "🍎 ══════════════ iOS Pre-flight ══════════════");

        // 0. Screen lock — initial check before any long operation.
        //    A locked device at the start means the user hasn't prepared for the run.
        DeviceScreenLockChecker.LockState initialLock = DeviceScreenLockChecker.check(udid);
        client.sendLog(executionId, initialLock.unlocked ? "INFO" : "WARN",
                initialLock.unlocked
                        ? "🔓 Pantalla desbloqueada al inicio del Pre-flight. ✅"
                        : "⚠️  Pantalla bloqueada al inicio del Pre-flight — desbloquea el iPhone para continuar.");

        // 1. Xcode
        checkXcode(client, executionId);

        // 2. Apple Developer Team
        String teamId = detectAppleTeamId(client, executionId);

        // 3. CoreDevice tunnel — must be connected before Appium session creation.
        //    tunnelState=disconnected causes Appium XCUITest to reject the UDID with
        //    "Unknown device or simulator UDID" even when the device is physically connected.
        CoreDeviceTunnelManager.DeviceConnectionState tunnel =
                CoreDeviceTunnelManager.ensureTunnelConnected(client, executionId, udid);

        // 4. iOS version (from device, not from Xcode)
        String iosVersion = detectIosVersion(client, executionId, udid);

        // 5. Developer Mode (warn only, non-blocking)
        checkDeveloperMode(client, executionId, udid);

        // 6. WDA cache — invalidada si cambió la versión de iOS, o si la validación
        //    completa (perfil/certificado/equipo/bundle/dispositivo) falla.
        Properties cache  = loadWdaCache(udid, iosVersion, client, executionId);
        String  wdaBundleId;
        boolean wdaCached;
        if (cache != null) {
            wdaBundleId = cache.getProperty("bundleId", generateWdaBundleId(udid));
            WdaCacheValidation validation = validateCachedWda(udid, teamId, wdaBundleId, client, executionId);

            if (validation.valid) {
                wdaCached = true;
                client.sendLog(executionId, "INFO",
                        "✅ WebDriverAgent precompilado y validado — saltando recompilación.");
                client.sendLog(executionId, "INFO",
                        "🔎 [WDA validación] Team: " + validation.profileTeamId
                        + " | Certificado: " + shortSha(validation.certSha1)
                        + " | Expira: " + validation.expirationDate);
                client.sendTechLog(executionId,
                        "[WDA caché] bundle: " + wdaBundleId
                        + " | iOS: " + cache.getProperty("iosVersion", "?")
                        + " | built: " + cache.getProperty("builtAt", "?"));
            } else {
                // ── Autocorrección: cache inválido → limpiar todo y forzar recompilación ──
                // Nunca se pide intervención manual: se borra el cache, el DerivedData de
                // WebDriverAgent, y se fuerza wdaCached=false para que Appium reconstruya,
                // refirme e reinstale WDA por su cuenta en la sesión que sigue.
                client.sendLog(executionId, "WARN",
                        "♻️  [WDA] Caché inválido — motivo: " + validation.reason);
                invalidateWdaCache(udid);
                cleanupStaleDerivedData(client, executionId);
                wdaCached = false;
                client.sendLog(executionId, "INFO",
                        "🔨 [WDA] Recompilación automática — motivo: " + validation.reason + "\n"
                        + "   DerivedData de WebDriverAgent eliminado. Appium recompilará, "
                        + "refirmará e reinstalará WDA en la próxima sesión sin intervención manual.");
            }
        } else {
            wdaBundleId = generateWdaBundleId(udid);
            wdaCached   = false;
            client.sendLog(executionId, "INFO",
                    "🔨 WebDriverAgent se compilará e instalará automáticamente.");
            client.sendTechLog(executionId,
                    "[WDA build] bundle: " + wdaBundleId
                    + " | teamId: " + (teamId.isBlank() ? "no detectado" : teamId));
        }

        // 7. WDA verification and pre-start
        // If WDA is cached (previously installed on device), attempt a fast warm start so
        // that Appium's session creation is instantaneous (no build wait during tests).
        // If WDA is not cached, Appium handles the full compilation during first session.
        boolean wdaReady = WdaManager.ensureWdaRunning(
                client, executionId, udid, teamId, wdaBundleId, wdaCached);

        // Invalidate cache only when a real WDA launch failure occurred:
        //   wdaCached      = true  → cache said WDA was installed
        //   !wdaReady      = true  → WDA didn't respond
        //   wasAttempted   = true  → xcodebuild was actually started (not just "no xcodeproj")
        //   !isWdaRunning  = true  → not already alive on the port
        //
        // Do NOT invalidate when launchAttempted=false ("no xcodeproj" path): Appium will
        // handle WDA startup using its own xcodeproj, and the device binary stays valid.
        boolean wasAttempted = WdaManager.wasLastLaunchAttempted();
        if (wdaCached && !wdaReady && wasAttempted && !WdaManager.isWdaRunning()) {
            client.sendLog(executionId, "WARN",
                    "♻️  [WDA] El caché existe pero WDA no respondió tras intentar iniciarlo.\n"
                    + "   Invalidando caché — la próxima ejecución recompilará WDA.");
            invalidateWdaCache(udid);
            wdaCached = false;
        }

        // ── Stability check — device may have auto-locked during the preflight steps ──
        // Wait 1.5 s to let any transient CoreDevice state settle, then re-query lock.
        // This is the authoritative unlock timestamp passed to the Framework JVM so it
        // can compute elapsed time before creating IOSDriver.
        try { Thread.sleep(1_500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        DeviceScreenLockChecker.LockState stabilityLock = DeviceScreenLockChecker.check(udid);
        boolean deviceUnlocked = stabilityLock.unlocked;
        client.sendLog(executionId, deviceUnlocked ? "INFO" : "ERROR",
                deviceUnlocked
                        ? "🔓 Device unlocked: YES ✅ — pantalla desbloqueada confirmada al final del Pre-flight."
                        : "🔒 Device unlocked: NO ❌ — pantalla bloqueada durante el Pre-flight."
                        + "\n   Desbloquea el iPhone antes de iniciar la ejecución.");

        // ── Compute Runner readiness decision — mirrors DeviceReadinessEvaluator logic ──
        // This determination is final: the Framework must not attempt recovery when
        // readyForExecution=false. The Runner is the single authority.
        boolean readyForExecution;
        String  notReadyReason;
        if (!deviceUnlocked) {
            readyForExecution = false;
            notReadyReason    = "Pantalla bloqueada al final del Pre-flight — desbloquea el iPhone y reintenta";
        } else if (tunnel.transportType == DevicectlParser.TransportType.UNKNOWN) {
            readyForExecution = false;
            notReadyReason    = "Tipo de transporte no identificado (transportType=UNKNOWN)";
        } else if (tunnel.transportType == DevicectlParser.TransportType.LOCAL_NETWORK
                && !"connected".equalsIgnoreCase(tunnel.tunnelState)) {
            readyForExecution = false;
            notReadyReason    = "Wi-Fi / Bonjour — túnel CoreDevice " + tunnel.tunnelState
                              + " (conecta USB o ejecuta: xcrun devicectl device connection connect)";
        } else if ("unpaired".equalsIgnoreCase(tunnel.pairingState)) {
            readyForExecution = false;
            notReadyReason    = "Dispositivo no emparejado — desbloquea el iPhone y acepta «Confiar en este Mac»";
        } else {
            readyForExecution = true;   // system health confirmed by DependencySelfHealingManager
            notReadyReason    = null;
        }

        String tunnelSummary = "connected".equalsIgnoreCase(tunnel.tunnelState)
                ? tunnel.tunnelState + " ✅"
                : tunnel.tunnelState + " ⚠️";
        client.sendLog(executionId, "INFO",
                "🍎 ════════════ iOS Pre-flight completo ════════════\n"
                + "   Team ID          : " + (teamId.isBlank()    ? "no detectado ⚠️" : teamId + " ✅") + "\n"
                + "   iOS              : " + (iosVersion.isBlank() ? "desconocida"     : iosVersion) + "\n"
                + "   Transport        : " + tunnel.transportType + "\n"
                + "   Tunnel           : " + tunnelSummary
                + (tunnel.coreDeviceId.isBlank() ? "" : "  (" + tunnel.coreDeviceId + ")") + "\n"
                + "   Device unlocked  : " + (deviceUnlocked ? "✅ YES" : "❌ NO — pantalla bloqueada") + "\n"
                + "   ReadyForExecution: " + (readyForExecution ? "✅ true"
                        : "❌ false — " + notReadyReason) + "\n"
                + "   UDID             : " + udid + "  ← appium:udid\n"
                + "   WDA bundle       : " + wdaBundleId + "\n"
                + "   WDA caché        : " + (wdaCached ? "precompilado ✅" : "compilará en primera sesión") + "\n"
                + "   WDA activo       : " + (wdaReady  ? "sí ✅" : "iniciará con Appium"));

        return new IosPreflightResult(
            teamId, iosVersion, wdaBundleId, wdaCached, wdaReady,
            tunnel.xctraceVisible,
            tunnel.tunnelState,
            tunnel.pairingState,
            tunnel.coreDeviceId,
            tunnel.transportType.name(),
            readyForExecution,
            notReadyReason,
            deviceUnlocked,
            stabilityLock.checkedAtMs
        );
    }

    // ── 1. Xcode ──────────────────────────────────────────────────────────────

    private static void checkXcode(BackendClient client, String executionId) {
        try {
            Process p = new ProcessBuilder("xcodebuild", "-version")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(15, TimeUnit.SECONDS);
            if (out.startsWith("Xcode")) {
                client.sendLog(executionId, "INFO",
                        "✅ " + out.replace("\n", " | "));
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️  Xcode no encontrado. Instala desde App Store y ejecuta:\n"
                        + "   sudo xcode-select --switch /Applications/Xcode.app\n"
                        + "   xcodebuild -runFirstLaunch");
            }
        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "⚠️  No se pudo verificar Xcode: " + e.getMessage());
        }
    }

    // ── 2. Apple Developer Team — detección multi-estrategia ─────────────────
    //
    // Apple Team IDs son siempre 10 caracteres alfanuméricos en mayúsculas
    // (p.ej. C32VD96Q84). IMPORTANTE: el Team ID NUNCA se lee del Common Name
    // (CN) de un certificado — el CN de "Apple Development: email (XXXXXXXXXX)"
    // contiene un identificador de cuenta/persona, NO el Team ID. El campo
    // autoritativo es:
    //   - TeamIdentifier   dentro de un provisioning profile real (fuente más confiable), o
    //   - OU (Organizational Unit) del subject X.509 del certificado.
    // Se prueban en orden de confiabilidad y se registra cada intento para que
    // los fallos sean diagnosticables.

    public static String detectAppleTeamId(BackendClient client, String executionId) {
        client.sendTechLog(executionId,
                "[TeamID] Buscando Apple Developer Team ID (OU del certificado / TeamIdentifier del perfil — nunca el CN)...");

        String id;

        // ── Estrategia 1: TeamIdentifier de un provisioning profile real en disco ──
        // Es la fuente más autoritativa: es literalmente el campo que Apple firmó.
        client.sendTechLog(executionId, "[TeamID] [1/4] TeamIdentifier de provisioning profiles en disco");
        id = strategyProvisioningProfiles(client, executionId);
        if (!id.isBlank()) return reportFound(client, executionId, id, "TeamIdentifier de provisioning profile");

        // ── Estrategia 2: OU del certificado "Apple Development" ─────────────
        client.sendTechLog(executionId, "[TeamID] [2/4] OU del certificado \"Apple Development\"");
        id = strategyCertificateOU(client, executionId, "Apple Development");
        if (!id.isBlank()) return reportFound(client, executionId, id, "OU del certificado Apple Development");

        // ── Estrategia 3: OU del certificado "iPhone Developer" (legado) ─────
        client.sendTechLog(executionId, "[TeamID] [3/4] OU del certificado \"iPhone Developer\" (legado)");
        id = strategyCertificateOU(client, executionId, "iPhone Developer");
        if (!id.isBlank()) return reportFound(client, executionId, id, "OU del certificado iPhone Developer (legado)");

        // ── Estrategia 4: escanear TODOS los certificados del keychain por su OU ──
        client.sendTechLog(executionId, "[TeamID] [4/4] OU de cualquier certificado del keychain");
        id = strategyAnyCertificateOU(client, executionId);
        if (!id.isBlank()) return reportFound(client, executionId, id, "OU de certificado en keychain (escaneo completo)");

        // All strategies exhausted
        client.sendLog(executionId, "WARN",
                "⚠️  Team ID no encontrado por ninguna estrategia.\n"
                + "   WDA no podrá firmarse sin Team ID en dispositivos físicos.\n"
                + "   Solución: Xcode → Settings → Accounts → agrega tu Apple ID\n"
                + "   y descarga tus certificados de desarrollo.");
        return "";
    }

    private static String reportFound(BackendClient client, String executionId,
                                       String id, String via) {
        client.sendLog(executionId, "INFO",
                "✅ Apple Developer Team ID: " + id + " (vía " + via + ")");
        return id;
    }

    // ── Estrategia 1: TeamIdentifier de provisioning profiles ────────────────

    /**
     * Fuente más específica y confiable: el provisioning profile embebido en el
     * ÚLTIMO build de WebDriverAgent (no un perfil cualquiera del sistema — un Mac
     * puede tener perfiles de otros proyectos/equipos personales que NO aplican aquí).
     *
     * Si WDA nunca se compiló en este dispositivo (primera vez), cae a buscar entre
     * los perfiles instalados SOLO los que correspondan a un bundle de este proyecto
     * (io.qautomation.wda.*) — nunca acepta un perfil de otro proyecto sin relación.
     */
    private static String strategyProvisioningProfiles(BackendClient client, String executionId) {
        File wdaProfile = findLatestWdaEmbeddedProfile();
        if (wdaProfile != null) {
            String plist = decodeProfilePlist(wdaProfile);
            String teamId = plist != null ? extractPlistArrayFirstString(plist, "TeamIdentifier") : null;
            if (teamId != null) {
                client.sendTechLog(executionId,
                        "[TeamID] TeamIdentifier leído del perfil embebido de WebDriverAgent ("
                        + wdaProfile.getParentFile().getName() + "): " + teamId);
                return teamId;
            }
        }

        File[] dirs = {
            new File(System.getProperty("user.home") + "/Library/MobileDevice/Provisioning Profiles"),
            new File(System.getProperty("user.home") + "/Library/Developer/Xcode/UserData/Provisioning Profiles"),
        };
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            File[] profiles = dir.listFiles((d, n) -> n.endsWith(".mobileprovision"));
            if (profiles == null || profiles.length == 0) continue;

            // Perfil más reciente primero — más probable que refleje la config actual.
            Arrays.sort(profiles, Comparator.comparingLong(File::lastModified).reversed());
            client.sendTechLog(executionId,
                    "[TeamID] Sin build previo de WDA — analizando " + profiles.length
                    + " perfil(es) en " + dir.getAbsolutePath() + " (solo io.qautomation.wda.*)...");

            for (File f : profiles) {
                try {
                    String plist = decodeProfilePlist(f);
                    if (plist == null) continue;
                    // Nunca aceptar un perfil de un proyecto sin relación (p.ej. otra app
                    // de prueba personal) solo porque exista en el sistema.
                    String appId = extractPlistArrayFirstString(plist, "application-identifier");
                    if (appId == null || !appId.contains("io.qautomation.wda")) continue;
                    String teamId = extractPlistArrayFirstString(plist, "TeamIdentifier");
                    if (teamId != null) {
                        client.sendTechLog(executionId, "[TeamID] TeamIdentifier leído de " + f.getName() + ": " + teamId);
                        return teamId;
                    }
                } catch (Exception ignored) {}
            }
        }
        client.sendTechLog(executionId,
                "[TeamID] Sin perfil de WebDriverAgent existente ni perfil io.qautomation.wda.* instalado.");
        return "";
    }

    // ── Estrategia 2/3: OU del certificado (nunca el CN) ──────────────────────

    private static String strategyCertificateOU(BackendClient client, String executionId, String certName) {
        try {
            String cmd = "security find-certificate -c '" + certName + "' -p 2>/dev/null"
                       + " | openssl x509 -noout -subject 2>/dev/null";
            Process p = new ProcessBuilder("/bin/sh", "-c", cmd).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);

            if (out.isBlank()) {
                client.sendTechLog(executionId, "[TeamID] Certificado \"" + certName + "\" no encontrado.");
                return "";
            }
            String ou = extractOuFromSubject(out);
            if (ou == null) {
                client.sendTechLog(executionId,
                        "[TeamID] Certificado \"" + certName + "\" encontrado pero sin OU extraíble: "
                        + out.substring(0, Math.min(120, out.length())));
                return "";
            }
            return ou;
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] Error leyendo OU de \"" + certName + "\": " + e.getMessage());
            return "";
        }
    }

    // ── Estrategia 4: OU de cualquier certificado del keychain ───────────────

    private static String strategyAnyCertificateOU(BackendClient client, String executionId) {
        try {
            String cmd = "security find-certificate -a -p 2>/dev/null | openssl x509 -noout -subject 2>/dev/null";
            Process p = new ProcessBuilder("/bin/sh", "-c", cmd).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(15, TimeUnit.SECONDS);
            if (out.isBlank()) return "";
            String ou = extractOuFromSubject(out);
            if (ou != null) return ou;
            client.sendTechLog(executionId, "[TeamID] Sin OU extraíble en ningún certificado del keychain.");
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] Error escaneando certificados del keychain: " + e.getMessage());
        }
        return "";
    }

    /**
     * Extrae ÚNICAMENTE el campo OU (Organizational Unit) de un subject X.509 —
     * jamás el CN, jamás el UID. Ambos formatos de openssl son soportados:
     *   legado (LibreSSL/macOS):  /UID=.../CN=.../OU=TEAMID/O=.../C=US
     *   RFC2253 (orden inverso):  CN=...,OU=TEAMID,O=...,C=US,UID=...
     * El ancla explícita a "OU=" (nunca "UID=") es lo que evita el bug original,
     * donde una alternancia (?:OU|UID) emparejaba el UID por aparecer primero
     * en el subject legado.
     */
    private static String extractOuFromSubject(String subject) {
        Pattern slashOu = Pattern.compile("/OU=([A-Z0-9]{10})(?:/|$)");
        Matcher m = slashOu.matcher(subject);
        if (m.find()) return m.group(1);

        Pattern commaOu = Pattern.compile("(?:^|,\\s*)OU=([A-Z0-9]{10})(?:,|$)");
        Matcher mc = commaOu.matcher(subject);
        if (mc.find()) return mc.group(1);

        return null;
    }

    // ── 3. iOS Version ────────────────────────────────────────────────────────

    public static String detectIosVersion(
            BackendClient client, String executionId, String udid) {
        if (udid == null || udid.isBlank()) return "";

        // Strategy 1: xcrun devicectl --json-output (Xcode 14+, CoreDevice-aware)
        // Uses DevicectlParser to locate deviceProperties.osVersionNumber via structural
        // JSON traversal — never uses indexOf or substring on the JSON string.
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            DevicectlParser.DeviceInfo info = DevicectlParser.findByUdid(json, udid);
            if (info != null && !info.osVersion.isBlank()) {
                client.sendLog(executionId, "INFO", "📱 iOS " + info.osVersion);
                client.sendTechLog(executionId, "[iOS version] fuente: devicectl JSON (DevicectlParser)");
                return info.osVersion;
            }
        } catch (Exception ignored) {}

        // Strategy 2: xcrun xctrace list devices
        // Format: "iPhone name (version) (UDID)"
        // Works for traditional 8-16 UDIDs. For CoreDevice UUIDs (Xcode 26), we fall back
        // to the first physical device in the == Devices == section (usually only one phone).
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);

            // Exact match — works when udid is in traditional 8-16 UDID format
            Pattern exact = Pattern.compile(
                    "\\(([\\d]+\\.[\\d.]+)\\)\\s*\\(\\s*" + Pattern.quote(udid) + "\\s*\\)");
            Matcher em = exact.matcher(out);
            if (em.find()) {
                String v = em.group(1);
                client.sendLog(executionId, "INFO", "📱 iOS " + v + " (vía xctrace)");
                return v;
            }

            // CoreDevice UUID fallback: scan == Devices == section for first physical device
            // xctrace always shows legacy UDIDs — direct match on CoreDevice UUID is impossible,
            // but if only one iPhone is connected its version is still the right one.
            boolean inDevices = false;
            Pattern physLine = Pattern.compile(
                    "^.+\\s+\\(([\\d]+\\.[\\d.]+)\\)\\s+\\([0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}\\)\\s*$");
            for (String line : out.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("== Devices ==")) { inDevices = true; continue; }
                if (trimmed.startsWith("=="))            { inDevices = false; continue; }
                if (!inDevices) continue;
                Matcher dm = physLine.matcher(trimmed);
                if (dm.matches()) {
                    String v = dm.group(1);
                    client.sendLog(executionId, "INFO",
                            "📱 iOS " + v + " (vía xctrace — primer dispositivo físico conectado)");
                    return v;
                }
            }

            client.sendLog(executionId, "WARN",
                    "⚠️  Versión iOS no detectada para " + udid
                    + " — Appium la detectará automáticamente del dispositivo.");
        } catch (Exception e) {
            client.sendLog(executionId, "WARN", "⚠️  xcrun no respondió: " + e.getMessage());
        }
        return "";
    }

    // ── 4. Developer Mode ─────────────────────────────────────────────────────

    private static void checkDeveloperMode(
            BackendClient client, String executionId, String udid) {
        if (udid == null || udid.isBlank()) return;
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            DevicectlParser.DeviceInfo info = DevicectlParser.findByUdid(json, udid);
            if (info == null) return;
            if (info.developerModeEnabled) {
                client.sendLog(executionId, "INFO",
                        "✅ Developer Mode activo en el dispositivo");
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️  Developer Mode INACTIVO.\n"
                        + "   Actívalo: Ajustes → Privacidad y seguridad → Modo desarrollador\n"
                        + "   Sin él, xcodebuild no puede instalar WebDriverAgent.");
            }
        } catch (Exception ignored) {
            // devicectl not available (Xcode < 14) — skip silently
        }
    }

    // ── WDA bundle ID ─────────────────────────────────────────────────────────

    public static String generateWdaBundleId(String udid) {
        String suffix = udid.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (suffix.length() > 10) suffix = suffix.substring(0, 10);
        return "io.qautomation.wda." + suffix;
    }

    // ── WDA cache ─────────────────────────────────────────────────────────────

    /**
     * Loads the WDA cache for a device.
     * Returns null if:
     *  - Cache file does not exist (WDA never built)
     *  - iOS version changed since last build (WDA must be recompiled)
     */
    static Properties loadWdaCache(String udid, String currentIosVersion,
                                    BackendClient client, String executionId) {
        if (udid == null || udid.isBlank()) return null;
        File f = cacheFile(udid);
        if (!f.exists()) return null;
        try {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            }
            String cachedVersion = p.getProperty("iosVersion", "");
            if (!currentIosVersion.isBlank() && !cachedVersion.isBlank()
                    && !currentIosVersion.equals(cachedVersion)) {
                f.delete();
                client.sendLog(executionId, "INFO",
                        "♻️  iOS actualizado (" + cachedVersion + " → " + currentIosVersion
                        + ") — WDA se recompilará para esta versión.");
                return null;
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Saves WDA build record. Called by DriverFactory (Gradle process) after a
     * successful IOSDriver session creation to indicate WDA is installed on device.
     */
    public static void saveWdaCache(
            String udid, String bundleId, String teamId, String iosVersion) {
        if (udid == null || udid.isBlank()) return;
        try {
            new File(CACHE_DIR).mkdirs();
            Properties p = new Properties();
            p.setProperty("udid",       udid);
            p.setProperty("bundleId",   bundleId);
            p.setProperty("teamId",     teamId);
            p.setProperty("iosVersion", iosVersion);
            p.setProperty("builtAt",    String.valueOf(System.currentTimeMillis()));
            try (FileOutputStream out = new FileOutputStream(cacheFile(udid))) {
                p.store(out, "QAutomation WDA cache — do not edit manually");
            }
        } catch (Exception e) {
            System.err.println("[WdaCache] Save failed for " + udid + ": " + e.getMessage());
        }
    }

    /**
     * Deletes the WDA cache for a device, forcing recompilation on the next run.
     * Called when WDA was cached but failed to start (corrupted state).
     */
    public static void invalidateWdaCache(String udid) {
        if (udid == null || udid.isBlank()) return;
        File f = cacheFile(udid);
        if (f.exists()) {
            f.delete();
            System.out.println("[WdaCache] Cache invalidated for " + udid);
        }
    }

    private static File cacheFile(String udid) {
        String safe = udid.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(CACHE_DIR, safe + ".properties");
    }

    // ── Validación de WDA cacheado ────────────────────────────────────────────
    //
    // Antes de decirle a Appium "reutiliza el WDA ya instalado" (wdaCached=true),
    // se valida el provisioning profile REAL embebido en el .app compilado —
    // nunca se confía ciegamente en el archivo de caché (~/.qautomation/wda/*.properties),
    // que solo registra intención, no el estado real de firma en disco.

    /** Resultado de validar el WDA cacheado contra el provisioning profile real en disco. */
    static final class WdaCacheValidation {
        final boolean valid;
        final String  reason;         // motivo de invalidación (o "OK" si valid=true)
        final String  certSha1;       // SHA1 del certificado embebido, o null
        final String  profileTeamId;  // TeamIdentifier del perfil, o null
        final String  expirationDate; // ExpirationDate del perfil, texto legible

        private WdaCacheValidation(boolean valid, String reason, String certSha1,
                                    String profileTeamId, String expirationDate) {
            this.valid          = valid;
            this.reason         = reason;
            this.certSha1       = certSha1;
            this.profileTeamId  = profileTeamId;
            this.expirationDate = expirationDate;
        }

        static WdaCacheValidation invalid(String reason) {
            return new WdaCacheValidation(false, reason, null, null, "desconocida");
        }
    }

    /**
     * Valida el WDA ya compilado/instalado contra su provisioning profile REAL
     * (el embedded.mobileprovision dentro de WebDriverAgentRunner-Runner.app en
     * DerivedData) antes de permitir que se reutilice. Verifica, en orden:
     *   1. Que exista el .app compilado con su perfil embebido.
     *   2. Que el perfil no haya expirado (ExpirationDate vs ahora).
     *   3. Que el TeamIdentifier del perfil coincida con el Team ID detectado.
     *   4. Que el Bundle Identifier coincida con el esperado.
     *   5. Que el UDID esté en ProvisionedDevices.
     *   6. Que el certificado embebido siga siendo una identidad válida del keychain.
     */
    static WdaCacheValidation validateCachedWda(String udid, String expectedTeamId, String expectedBundleId,
                                                 BackendClient client, String executionId) {
        File profileFile = findLatestWdaEmbeddedProfile();
        if (profileFile == null) {
            return WdaCacheValidation.invalid(
                    "No se encontró WebDriverAgentRunner-Runner.app compilado en DerivedData");
        }

        String plist = decodeProfilePlist(profileFile);
        if (plist == null) {
            return WdaCacheValidation.invalid(
                    "No se pudo decodificar el provisioning profile embebido (" + profileFile.getName() + ")");
        }

        // 1. Bundle ID (se valida antes que nada — un profile de OTRO proyecto no cuenta)
        String appId = extractPlistArrayFirstString(plist, "application-identifier");
        if (appId == null || (expectedBundleId != null && !appId.contains(expectedBundleId))) {
            return new WdaCacheValidation(false,
                    "Bundle ID del perfil (" + appId + ") no coincide con el esperado (" + expectedBundleId + ")",
                    null, null, "desconocida");
        }

        // 2. Expiración
        Instant expiration = extractExpirationDate(plist);
        String  expirationStr = expiration != null ? expiration.toString() : "desconocida";
        if (expiration == null) {
            return WdaCacheValidation.invalid("El perfil no tiene ExpirationDate legible");
        }
        if (Instant.now().isAfter(expiration)) {
            return new WdaCacheValidation(false,
                    "Provisioning profile EXPIRADO el " + expirationStr + " (hoy: " + Instant.now() + ")",
                    null, null, expirationStr);
        }

        // 3. Team ID — el mismo Team ID robusto que detectAppleTeamId() calcula ahora
        String profileTeamId = extractPlistArrayFirstString(plist, "TeamIdentifier");
        if (profileTeamId == null) {
            return new WdaCacheValidation(false, "El perfil no tiene TeamIdentifier legible",
                    null, null, expirationStr);
        }
        if (expectedTeamId != null && !expectedTeamId.isBlank() && !expectedTeamId.equals(profileTeamId)) {
            return new WdaCacheValidation(false,
                    "Team ID del perfil (" + profileTeamId + ") no coincide con el detectado (" + expectedTeamId + ")",
                    null, profileTeamId, expirationStr);
        }

        // 4. Dispositivo incluido
        if (!containsProvisionedDevice(plist, udid)) {
            return new WdaCacheValidation(false,
                    "El dispositivo " + udid + " no está en ProvisionedDevices del perfil",
                    null, profileTeamId, expirationStr);
        }

        // 5. Certificado embebido sigue siendo una identidad válida del keychain
        String certSha1 = extractFirstDeveloperCertificateSha1(plist);
        if (certSha1 == null) {
            return new WdaCacheValidation(false, "No se pudo leer el certificado embebido en el perfil",
                    null, profileTeamId, expirationStr);
        }
        if (!isCertificateCurrentlyValid(certSha1)) {
            return new WdaCacheValidation(false,
                    "El certificado embebido (" + shortSha(certSha1) + ") ya no es una identidad válida en el keychain",
                    certSha1, profileTeamId, expirationStr);
        }

        return new WdaCacheValidation(true, "OK", certSha1, profileTeamId, expirationStr);
    }

    /** Busca el embedded.mobileprovision más reciente entre los builds de WebDriverAgent en DerivedData. */
    private static File findLatestWdaEmbeddedProfile() {
        File derivedDataRoot = new File(System.getProperty("user.home") + "/Library/Developer/Xcode/DerivedData");
        File[] wdaDirs = derivedDataRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("WebDriverAgent-"));
        if (wdaDirs == null || wdaDirs.length == 0) return null;

        File latest = null;
        for (File wdaDir : wdaDirs) {
            File productsDir = new File(wdaDir, "Build/Products");
            if (!productsDir.isDirectory()) continue;
            File[] suiteDirs = productsDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("Debug-iphoneos"));
            if (suiteDirs == null) continue;
            for (File suiteDir : suiteDirs) {
                File profile = new File(suiteDir, "WebDriverAgentRunner-Runner.app/embedded.mobileprovision");
                if (profile.isFile() && (latest == null || profile.lastModified() > latest.lastModified())) {
                    latest = profile;
                }
            }
        }
        return latest;
    }

    /** Decodifica un .mobileprovision (CMS-signed) a su plist XML en texto plano. */
    private static String decodeProfilePlist(File profile) {
        try {
            Process p = new ProcessBuilder("security", "cms", "-D", "-i", profile.getAbsolutePath())
                    .redirectErrorStream(false).start();
            String plist = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return (done && !plist.isBlank()) ? plist : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Devuelve el primer &lt;string&gt; que sigue a &lt;key&gt;{key}&lt;/key&gt; — sirve tanto para
     *  valores planos (application-identifier) como para el primer elemento de un &lt;array&gt;
     *  (TeamIdentifier). */
    private static String extractPlistArrayFirstString(String plist, String key) {
        String marker = "<key>" + key + "</key>";
        int keyIdx = plist.indexOf(marker);
        if (keyIdx < 0) return null;
        int from = keyIdx + marker.length();
        int to   = Math.min(plist.length(), from + 500);
        Matcher m = Pattern.compile("<string>([^<]+)</string>").matcher(plist.substring(from, to));
        return m.find() ? m.group(1).trim() : null;
    }

    private static Instant extractExpirationDate(String plist) {
        String marker = "<key>ExpirationDate</key>";
        int keyIdx = plist.indexOf(marker);
        if (keyIdx < 0) return null;
        int from = keyIdx + marker.length();
        int to   = Math.min(plist.length(), from + 200);
        Matcher m = Pattern.compile("<date>([^<]+)</date>").matcher(plist.substring(from, to));
        if (!m.find()) return null;
        try {
            return Instant.parse(m.group(1).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean containsProvisionedDevice(String plist, String udid) {
        int keyIdx = plist.indexOf("<key>ProvisionedDevices</key>");
        if (keyIdx < 0) return false;
        int endIdx = plist.indexOf("</array>", keyIdx);
        if (endIdx < 0) endIdx = plist.length();
        return plist.substring(keyIdx, Math.min(plist.length(), endIdx)).contains(udid);
    }

    /** SHA1 (hex, mayúsculas) del primer certificado embebido en DeveloperCertificates. */
    private static String extractFirstDeveloperCertificateSha1(String plist) {
        int keyIdx = plist.indexOf("<key>DeveloperCertificates</key>");
        if (keyIdx < 0) return null;
        int dataStart = plist.indexOf("<data>", keyIdx);
        if (dataStart < 0) return null;
        int dataEnd = plist.indexOf("</data>", dataStart);
        if (dataEnd < 0) return null;
        String base64 = plist.substring(dataStart + "<data>".length(), dataEnd).replaceAll("\\s+", "");
        try {
            byte[] der  = Base64.getDecoder().decode(base64);
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(der);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** ¿Sigue esta huella SHA1 entre las identidades de firma válidas del keychain? */
    private static boolean isCertificateCurrentlyValid(String sha1) {
        try {
            Process p = new ProcessBuilder("security", "find-identity", "-v", "-p", "codesigning")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(10, TimeUnit.SECONDS);
            return out.toUpperCase().contains(sha1.toUpperCase());
        } catch (Exception e) {
            return false;
        }
    }

    private static String shortSha(String sha1) {
        if (sha1 == null || sha1.isBlank()) return "desconocido";
        return sha1.substring(0, Math.min(12, sha1.length())) + "...";
    }

    // ── Limpieza automática de DerivedData ────────────────────────────────────

    /**
     * Elimina TODO el DerivedData de WebDriverAgent (todas las carpetas
     * WebDriverAgent-* en ~/Library/Developer/Xcode/DerivedData) para forzar una
     * recompilación completamente limpia — sin intervención manual, sin abrir Xcode.
     * Se invoca únicamente cuando validateCachedWda() determina que el binario
     * cacheado ya no es válido.
     */
    private static void cleanupStaleDerivedData(BackendClient client, String executionId) {
        File derivedDataRoot = new File(System.getProperty("user.home") + "/Library/Developer/Xcode/DerivedData");
        File[] wdaDirs = derivedDataRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("WebDriverAgent-"));
        if (wdaDirs == null || wdaDirs.length == 0) {
            client.sendTechLog(executionId, "[WDA cache] Sin DerivedData de WebDriverAgent que limpiar.");
            return;
        }
        for (File dir : wdaDirs) {
            try {
                deleteRecursive(dir.toPath());
                client.sendTechLog(executionId, "[WDA cache] DerivedData eliminado: " + dir.getName());
            } catch (Exception e) {
                client.sendTechLog(executionId,
                        "[WDA cache] No se pudo eliminar " + dir.getName() + ": " + e.getMessage());
            }
        }
    }

    private static void deleteRecursive(java.nio.file.Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (java.nio.file.Path child : (Iterable<java.nio.file.Path>) stream::iterator) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
