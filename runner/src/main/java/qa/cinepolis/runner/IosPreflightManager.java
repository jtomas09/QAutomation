package qa.cinepolis.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
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

        // 6. WDA cache — invalidated if iOS version changed
        Properties cache  = loadWdaCache(udid, iosVersion, client, executionId);
        String  wdaBundleId;
        boolean wdaCached;
        if (cache != null) {
            wdaBundleId = cache.getProperty("bundleId", generateWdaBundleId(udid));
            wdaCached   = true;
            client.sendLog(executionId, "INFO",
                    "✅ WebDriverAgent precompilado detectado — saltando compilación.");
            client.sendTechLog(executionId,
                    "[WDA caché] bundle: " + wdaBundleId
                    + " | iOS: " + cache.getProperty("iosVersion", "?")
                    + " | built: " + cache.getProperty("builtAt", "?"));
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
    // Apple Team IDs are always 10 uppercase alphanumeric characters (e.g. 5TQQL22JSV).
    // They appear in multiple places on the system; we try them all in order and log
    // each attempt so failures are diagnosable even when find-identity returns empty.

    /** Apple Team ID pattern: exactly 10 uppercase letters and digits. */
    private static final Pattern TEAM_ID = Pattern.compile("[A-Z0-9]{10}");

    public static String detectAppleTeamId(BackendClient client, String executionId) {
        client.sendTechLog(executionId, "[TeamID] Buscando Apple Developer Team ID...");

        String id;

        // ── Estrategia 1: security find-identity -v -p codesigning ────────────
        client.sendTechLog(executionId, "[TeamID] [1/6] security find-identity -v -p codesigning");
        id = strategy1FindIdentity(client, executionId);
        if (!id.isBlank()) return reportFound(client, executionId, id, "estrategia 1");

        // ── Estrategia 2: security find-certificate -c "Apple Development" -Z ──
        client.sendTechLog(executionId, "[TeamID] [2/6] security find-certificate -c \"Apple Development\" -Z");
        id = strategy2FindCertByLabel(client, executionId, "Apple Development");
        if (!id.isBlank()) return reportFound(client, executionId, id, "estrategia 2");

        // ── Estrategia 3: X.509 OU field vía openssl ──────────────────────────
        client.sendTechLog(executionId, "[TeamID] [3/6] security find-certificate -p | openssl x509 -subject");
        id = strategy3OpensslSubject(client, executionId, "Apple Development");
        if (!id.isBlank()) return reportFound(client, executionId, id, "estrategia 3");

        // ── Estrategia 4: legacy "iPhone Developer" certificate name ─────────
        client.sendTechLog(executionId, "[TeamID] [4/6] security find-certificate -c \"iPhone Developer\" -Z");
        id = strategy2FindCertByLabel(client, executionId, "iPhone Developer");
        if (!id.isBlank()) return reportFound(client, executionId, id, "estrategia 4 (iPhone Developer)");

        // ── Estrategia 5: perfiles de provisioning instalados ─────────────────
        client.sendTechLog(executionId, "[TeamID] [5/6] ~/Library/MobileDevice/Provisioning Profiles/*.mobileprovision");
        id = strategy5ProvisioningProfiles(client, executionId);
        if (!id.isBlank()) return reportFound(client, executionId, id, "estrategia 5 (provisioning profile)");

        // ── Estrategia 6: security find-certificate -a (todos los certificados) ─
        client.sendTechLog(executionId, "[TeamID] [6/6] security find-certificate -a (keychain completo)");
        id = strategy6AllCertificates(client, executionId);
        if (!id.isBlank()) return reportFound(client, executionId, id, "estrategia 6 (keychain scan)");

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

    // ── Estrategia 1: find-identity ───────────────────────────────────────────

    private static String strategy1FindIdentity(BackendClient client, String executionId) {
        try {
            Process p = new ProcessBuilder("security", "find-identity", "-v", "-p", "codesigning")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);

            // "Apple Development: Name (TEAMID)" in double-quoted label
            Pattern pat = Pattern.compile(
                    "\"(?:Apple Development|iPhone Developer):[^(]+\\(([A-Z0-9]{10})\\)\"");
            Matcher m = pat.matcher(out);
            if (m.find()) return m.group(1);

            // Any line containing a 10-char alphanum in parens after an Apple cert prefix
            Pattern loose = Pattern.compile(
                    "Apple(?:\\s+Development|\\s+Developer|\\s+Distribution)[^(]*\\(([A-Z0-9]{10})\\)");
            Matcher ml = loose.matcher(out);
            if (ml.find()) return ml.group(1);

            if (!out.isBlank())
                client.sendTechLog(executionId,
                        "[TeamID] Sin coincidencia: "
                        + out.substring(0, Math.min(120, out.length())).replace("\n", " | "));
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] Error estrategia 1: " + e.getMessage());
        }
        return "";
    }

    // ── Estrategia 2: find-certificate por nombre ─────────────────────────────

    private static String strategy2FindCertByLabel(BackendClient client, String executionId,
                                                    String certName) {
        try {
            Process p = new ProcessBuilder("security", "find-certificate",
                    "-c", certName, "-Z")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);

            if (out.isBlank()) {
                client.sendTechLog(executionId, "[TeamID] Certificado \"" + certName + "\" no encontrado.");
                return "";
            }

            // "labl"<blob>="Apple Development: Name (TEAMID)" — preferred
            Pattern labelled = Pattern.compile(
                    "\"(?:Apple Development|iPhone Developer):[^(]+\\(([A-Z0-9]{10})\\)\"");
            Matcher m = labelled.matcher(out);
            if (m.find()) return m.group(1);

            // Fallback: any (TEAMID) pattern in the output
            Pattern paren = Pattern.compile("\\(([A-Z0-9]{10})\\)");
            Matcher mp = paren.matcher(out);
            if (mp.find()) return mp.group(1);

            client.sendTechLog(executionId,
                    "[TeamID] Certificado encontrado pero sin Team ID extraíble: "
                    + out.substring(0, Math.min(120, out.length())).replace("\n", " | "));
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] Error estrategia 2: " + e.getMessage());
        }
        return "";
    }

    // ── Estrategia 3: openssl X.509 subject OU/UID ────────────────────────────

    private static String strategy3OpensslSubject(BackendClient client, String executionId,
                                                   String certName) {
        try {
            // Shell pipe: security outputs PEM → openssl reads subject
            String cmd = "security find-certificate -c '" + certName + "' -p 2>/dev/null"
                       + " | openssl x509 -noout -subject 2>/dev/null";
            Process p = new ProcessBuilder("/bin/sh", "-c", cmd).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);

            if (out.isBlank()) {
                client.sendTechLog(executionId, "[TeamID] Sin output de openssl.");
                return "";
            }

            // subject=UID=5TQQL22JSV, CN=Apple Development: ..., OU=5TQQL22JSV, O=..., C=US
            Pattern ouUid = Pattern.compile("(?:OU|UID)\\s*=\\s*([A-Z0-9]{10})(?:[,\\s/]|$)");
            Matcher m = ouUid.matcher(out);
            if (m.find()) return m.group(1);

            // openssl legacy format: /OU=TEAMID/
            Pattern slash = Pattern.compile("/(?:OU|UID)=([A-Z0-9]{10})(?:/|$)");
            Matcher ms = slash.matcher(out);
            if (ms.find()) return ms.group(1);

            client.sendTechLog(executionId,
                    "[TeamID] Subject sin OU/UID de 10 chars: " + out.substring(0, Math.min(120, out.length())));
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] openssl no disponible: " + e.getMessage());
        }
        return "";
    }

    // ── Estrategia 5: perfiles de provisioning ────────────────────────────────

    private static String strategy5ProvisioningProfiles(BackendClient client, String executionId) {
        try {
            File dir = new File(System.getProperty("user.home")
                    + "/Library/MobileDevice/Provisioning Profiles");
            if (!dir.exists()) {
                client.sendTechLog(executionId, "[TeamID] Directorio de perfiles no existe.");
                return "";
            }
            File[] profiles = dir.listFiles((d, n) -> n.endsWith(".mobileprovision"));
            if (profiles == null || profiles.length == 0) {
                client.sendTechLog(executionId, "[TeamID] Sin perfiles de provisioning instalados.");
                return "";
            }

            client.sendTechLog(executionId, "[TeamID] Analizando " + profiles.length + " perfil(es)...");

            for (File f : profiles) {
                try {
                    // Decode CMS-signed plist to readable XML
                    Process p = new ProcessBuilder("security", "cms", "-D", "-i", f.getAbsolutePath())
                            .redirectErrorStream(false).start();
                    String plist = new String(p.getInputStream().readAllBytes());
                    p.waitFor(10, TimeUnit.SECONDS);

                    // <key>TeamIdentifier</key><array><string>5TQQL22JSV</string></array>
                    int keyIdx = plist.indexOf("TeamIdentifier");
                    if (keyIdx < 0) continue;
                    String region = plist.substring(keyIdx,
                            Math.min(plist.length(), keyIdx + 300));
                    Pattern sp = Pattern.compile("<string>([A-Z0-9]{10})</string>");
                    Matcher sm = sp.matcher(region);
                    if (sm.find()) {
                        client.sendTechLog(executionId, "[TeamID] Encontrado en perfil: " + f.getName());
                        return sm.group(1);
                    }
                } catch (Exception ignored) {}
            }
            client.sendTechLog(executionId, "[TeamID] Team ID no encontrado en ningún perfil.");
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] Error estrategia 5: " + e.getMessage());
        }
        return "";
    }

    // ── Estrategia 6: todos los certificados del keychain ────────────────────

    private static String strategy6AllCertificates(BackendClient client, String executionId) {
        try {
            Process p = new ProcessBuilder("security", "find-certificate", "-a", "-Z")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(20, TimeUnit.SECONDS);

            // Look for Apple Development / iPhone Developer labels in the full dump
            Pattern labeled = Pattern.compile(
                    "\"(?:Apple Development|iPhone Developer|Apple Worldwide Developer):"
                    + "[^(]+\\(([A-Z0-9]{10})\\)\"");
            Matcher m = labeled.matcher(out);
            if (m.find()) return m.group(1);

            // Fallback: scan for any (TEAMID) pattern near the word "Development"
            Pattern nearDev = Pattern.compile(
                    "(?:Development|Developer)[^(]{0,60}\\(([A-Z0-9]{10})\\)");
            Matcher mn = nearDev.matcher(out);
            if (mn.find()) return mn.group(1);

            client.sendTechLog(executionId, "[TeamID] Sin certificados Apple Development en ningún keychain.");
        } catch (Exception e) {
            client.sendTechLog(executionId, "[TeamID] Error estrategia 6: " + e.getMessage());
        }
        return "";
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
}
