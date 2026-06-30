package qa.cinepolis.runner;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/**
 * iOS physical device discovery via xcrun devicectl (primary) and xcrun xctrace (fallback).
 *
 * === Xcode 26 / CoreDevice UUID vs Physical UDID ===
 *
 * Apple introduced two distinct device identifiers in Xcode 26:
 *
 *   Physical UDID (8-16 hex):  00008110-000129261482601E
 *     - Reported by: xcrun xctrace list devices, idevice_id -l
 *     - Stored in:   xcrun devicectl --json-output → hardwareProperties.udid
 *     - Required by: Appium appium:udid capability, xcodebuild (most versions)
 *
 *   CoreDevice UUID (8-4-4-4-12 RFC 4122):  554E89EA-E69D-54EE-9877-B26F70061A0A
 *     - Reported by: xcrun devicectl list devices (text output, Xcode 26+)
 *     - Stored in:   xcrun devicectl --json-output → identifier
 *     - Used by:     xcrun devicectl internal operations
 *
 * Appium XCUITest driver looks up devices by hardwareProperties.udid (physical UDID).
 * Passing a CoreDevice UUID as appium:udid causes "Unknown device or simulator UDID".
 *
 * Resolution strategy:
 *   1. scanWithDevicectl() detects devices (handles both old and Xcode 26 text output)
 *   2. resolvePhysicalUdids() cross-references via devicectl --json-output to replace
 *      CoreDevice UUIDs with physical UDIDs from hardwareProperties.udid
 *   3. If resolution fails, scan() falls back to scanWithXctrace() which always
 *      returns physical UDIDs directly
 *
 * No dependency on Appium, ADB, PATH, or any global npm package.
 */
public class IOSDeviceScanner {

    // iPhone/iPad UDID: exactly 8 hex chars, dash, exactly 16 hex chars.
    // This pattern does NOT match standard UUIDs (8-4-4-4-12) used by Macs and Simulators.
    private static final Pattern IPHONE_UDID = Pattern.compile(
            "\\b([0-9A-Fa-f]{8}-[0-9A-Fa-f]{16})\\b");

    // CoreDevice UUID (Xcode 26+): RFC 4122 8-4-4-4-12 format.
    // Xcode 26 changed 'xcrun devicectl' to return this format for physical iOS devices.
    // Must be paired with an iPhone/iPad/iPod line check — Macs and Simulators share this format.
    private static final Pattern COREDEVICE_UUID = Pattern.compile(
            "\\b([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\\b");

    // xctrace fixed-format: "Name (version) (UDID)"
    // Parentheses around UDID — previous code incorrectly used square brackets.
    private static final Pattern XCTRACE_DEVICE = Pattern.compile(
            "^(.+?)\\s+\\(([\\d.]+)\\)\\s+\\(([0-9A-Fa-f]{8}-[0-9A-Fa-f]{16})\\)\\s*$");

    // Digits-dot-digits version pattern
    private static final Pattern VERSION_PAT = Pattern.compile("\\b(\\d+\\.\\d+(?:\\.\\d+)?)\\b");

    // Words/phrases that indicate a non-iOS-device line
    private static final List<String> EXCLUDE_TOKENS = List.of(
            "simulator", "macos", "unavailable", "placeholder", "disconnected",
            "name", "udid", "identifier", "platform", "version", "os",
            "listing", "devices found", "no devices", "────", "----", "====");


    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns true if the UDID is a CoreDevice UUID (RFC 4122 8-4-4-4-12 format, 36 chars).
     * CoreDevice UUIDs are NOT accepted by Appium as appium:udid — physical UDIDs must be used.
     */
    // Package-private for unit testing
    static boolean isCoreDeviceUuid(String udid) {
        if (udid == null || udid.length() != 36) return false;
        return udid.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
    }

    /**
     * Scans for connected physical iOS devices.
     *
     * Always returns physical UDIDs (8-16 hex format) that Appium recognizes.
     * CoreDevice UUIDs from Xcode 26 devicectl are resolved to physical UDIDs via
     * devicectl --json-output; if resolution fails, xctrace is used as fallback.
     */
    public static List<Map<String, String>> scan() {
        List<Map<String, String>> devicectlResult = scanWithDevicectl();

        // All devices have physical UDIDs (CoreDevice UUIDs were resolved) — preferred path
        if (!devicectlResult.isEmpty()
                && devicectlResult.stream().noneMatch(d -> isCoreDeviceUuid(d.getOrDefault("udid", "")))) {
            long avail = devicectlResult.stream().filter(d -> "AVAILABLE".equals(d.get("status"))).count();
            long disc  = devicectlResult.stream().filter(d -> "DISCOVERED".equals(d.get("status"))).count();
            System.out.printf("[IOS] %d dispositivo(s) via devicectl — AVAILABLE: %d, DISCOVERED: %d%n",
                    devicectlResult.size(), avail, disc);
            return devicectlResult;
        }

        // devicectl returned nothing OR CoreDevice UUIDs could not be resolved to physical UDIDs
        if (devicectlResult.isEmpty()) {
            System.out.println("[IOS] devicectl sin resultados — usando xctrace para UDID físico...");
        } else {
            System.out.println("[IOS] CoreDevice UUID(s) sin resolver — usando xctrace para UDID físico...");
        }

        // xctrace always returns physical UDIDs (8-16 format) — the format Appium requires
        List<Map<String, String>> xctrace = scanWithXctrace();
        if (!xctrace.isEmpty()) {
            System.out.printf("[IOS] %d dispositivo(s) via xctrace (UDID físico).%n", xctrace.size());
            return xctrace;
        }

        // Last resort: return CoreDevice UUIDs with a warning (Appium will likely reject them)
        if (!devicectlResult.isEmpty()) {
            System.out.println("[IOS] ⚠  xctrace sin resultados — retornando CoreDevice UUID(s). "
                    + "Appium puede rechazarlos. Verifica que el iPhone esté desbloqueado y "
                    + "Developer Mode esté activo.");
            return devicectlResult;
        }

        System.out.println("[IOS] Sin dispositivos iOS físicos detectados.");
        return devicectlResult; // empty
    }

    // ── devicectl (Xcode 15+, iOS 17+) ───────────────────────────────────

    /**
     * Parses 'xcrun devicectl list devices' output.
     *
     * Typical tabular format:
     *   UDID                                      Name                  Platform  OS
     *   ──────────────────────────────────────────────────────────────────────────────
     *   00008110-000129261482601E                 iPhone de Tester      iOS       18.0
     *
     * Alternative formats (Xcode versions differ) are handled by anchoring on the
     * iPhone UDID pattern rather than column position.
     */
    private static List<Map<String, String>> scanWithDevicectl() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xcrun", "devicectl", "list", "devices")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(12, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return result; }
            if (p.exitValue() != 0) return result; // devicectl not available — use xctrace

            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            parseDevicectlOutput(out, result);

            // Xcode 26+: devicectl text output returns CoreDevice UUIDs instead of physical UDIDs.
            // Appium requires physical UDIDs — resolve them via devicectl --json-output.
            if (!result.isEmpty()) resolvePhysicalUdids(result);
        } catch (Exception e) {
            System.err.println("[IOS] devicectl no disponible: " + e.getMessage());
        }
        return result;
    }

    /**
     * Resolves CoreDevice UUIDs (Xcode 26+ RFC 4122 format) to physical UDIDs (8-16 hex format).
     *
     * Xcode 26 changed 'xcrun devicectl list devices' text output to use CoreDevice UUIDs as the
     * primary identifier. Appium however validates devices by their physical UDID stored in
     * hardwareProperties.udid inside the JSON output of the same command.
     *
     * For each device still holding a CoreDevice UUID, this method:
     *   1. Finds the CoreDevice UUID in the devicectl JSON output
     *   2. Extracts the physical UDID from the nearby hardwareProperties.udid field
     *   3. Replaces the device map's "udid" with the physical UDID
     *   4. Stores the CoreDevice UUID under "coreDeviceId" for logging/reference
     */
    private static void resolvePhysicalUdids(List<Map<String, String>> devices) {
        boolean needsResolution = devices.stream()
                .anyMatch(d -> isCoreDeviceUuid(d.getOrDefault("udid", "")));
        if (!needsResolution) return;

        String json;
        try {
            Process p = new ProcessBuilder("xcrun", "devicectl", "list", "devices", "--json-output", "-")
                    .redirectErrorStream(false).start();
            json = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(12, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return; }
        } catch (Exception e) {
            System.err.println("[IOS] devicectl --json-output no disponible: " + e.getMessage());
            return;
        }

        // Build a map from CoreDevice UUID → DeviceInfo using structured JSON parsing.
        // DevicectlParser.findAllPhysical locates each device via hardwareProperties.udid,
        // so the lookup is never confused by UDID appearances in potentialHostnames.
        Map<String, DevicectlParser.DeviceInfo> byCoreId = new HashMap<>();
        for (DevicectlParser.DeviceInfo info : DevicectlParser.findAllPhysical(json)) {
            if (!info.coreDeviceId.isEmpty()) byCoreId.put(info.coreDeviceId, info);
        }

        for (Map<String, String> device : devices) {
            String coreDeviceId = device.get("udid");
            if (!isCoreDeviceUuid(coreDeviceId)) continue;

            DevicectlParser.DeviceInfo info = byCoreId.get(coreDeviceId);
            if (info == null) {
                System.err.printf("[IOS] CoreDevice UUID %s no encontrado en JSON — usará xctrace%n",
                        coreDeviceId);
                continue;
            }
            if (info.physicalUdid.isBlank()) {
                System.err.printf("[IOS] Physical UDID vacío para CoreDevice UUID %s%n", coreDeviceId);
                continue;
            }

            device.put("coreDeviceId", coreDeviceId);
            device.put("udid",         info.physicalUdid);
            if (device.getOrDefault("platformVersion", "").isBlank() && !info.osVersion.isBlank()) {
                device.put("platformVersion", info.osVersion);
            }
            applyDeviceInfo(device, info, coreDeviceId);
        }
    }

    // Package-private for unit testing
    static void parseDevicectlOutput(String out, List<Map<String, String>> result) {
        // devicectl may output multiple formats. Anchor on iPhone UDID per line.
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Fast reject: skip obvious header/separator/non-device lines
            String lower = line.toLowerCase(Locale.ROOT);
            if (EXCLUDE_TOKENS.stream().anyMatch(lower::contains)) continue;

            Matcher udidM = IPHONE_UDID.matcher(line);
            if (!udidM.find()) {
                // Xcode 26+: devicectl returns CoreDevice UUIDs (8-4-4-4-12).
                // Require "iphone"/"ipad"/"ipod" to exclude Mac/Simulator entries.
                boolean isIosLine = lower.contains("iphone") || lower.contains("ipad") || lower.contains("ipod");
                if (!isIosLine) continue;
                udidM = COREDEVICE_UUID.matcher(line);
                if (!udidM.find()) continue;
            }

            String udid = udidM.group(1);
            String before = line.substring(0, udidM.start()).trim();
            String after  = line.substring(udidM.end()).trim();

            // Exclude macOS lines (the Mac itself shows up too)
            if (lower.contains("macos")) continue;

            // Version: first digits.digits pattern after UDID
            Matcher verM = VERSION_PAT.matcher(after);
            // Empty string when version is not in the text line (e.g. Xcode 26 CoreDevice format).
            // IosPreflightManager.detectIosVersion() uses devicectl --json-output to get the real value.
            // Never use "unknown" — it propagates to -DplatformVersion=unknown and breaks Appium.
            String version = verM.find() ? verM.group(1) : "";

            // Name: text before UDID (common in tabular format where UDID is first)
            // or text after UDID but before the version/platform tokens
            String name = extractName(before, after, udid);
            if (name == null) continue;

            addDevice(result, udid, name, version, "devicectl");
        }
    }

    /**
     * Attempts to extract device name from the text surrounding the UDID.
     * Returns null if the line doesn't look like a device row.
     */
    private static String extractName(String before, String after, String udid) {
        if (!before.isBlank()) {
            // UDID is not the first token — 'before' is the device name
            String name = before.replaceAll("\\s+", " ").trim();
            if (!looksLikeHeader(name)) return name;
        }
        // UDID is first — name comes after version/platform tokens
        // Strip platform/version/status tokens from 'after'
        String name = after
                .replaceAll("(?i)\\b(iOS|iPadOS)\\b", "")
                .replaceAll("\\d+\\.\\d+(?:\\.\\d+)?", "")
                .replaceAll("(?i)\\b(connected|available|wired|wireless|usb|bt)\\b", "")
                .replaceAll("\\s+", " ").trim();
        if (name.isBlank() || looksLikeHeader(name)) return null;
        return name;
    }

    private static boolean looksLikeHeader(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.equals("name") || lower.equals("identifier") || lower.equals("udid")
                || lower.startsWith("listing") || lower.startsWith("device");
    }

    // ── xctrace fallback (all Xcode versions) ─────────────────────────────

    /**
     * Parses 'xcrun xctrace list devices' output.
     *
     * Format:
     *   == Devices ==
     *   iPhone de Tester (26.5) (00008110-000129261482601E)
     *   MacBook Pro (14.5) (Mac-UUID-standard-format)   ← excluded by UDID pattern
     *
     *   == Simulators ==
     *   iPhone 15 (17.0) (Sim-UUID)   ← excluded by section boundary
     *
     * The UDID pattern [0-9A-Fa-f]{8}-[0-9A-Fa-f]{16} matches iPhones but NOT
     * Macs/Simulators (whose UUIDs use the standard 8-4-4-4-12 format).
     */
    private static List<Map<String, String>> scanWithXctrace() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            boolean done = p.waitFor(15, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return result; }

            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean inDevices = false;

            for (String raw : out.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("== Devices ==")) {
                    inDevices = true;
                    continue;
                }
                if (line.startsWith("==")) {
                    inDevices = false; // entered another section (Simulators etc.)
                    continue;
                }
                if (!inDevices) continue;

                Matcher m = XCTRACE_DEVICE.matcher(line);
                if (!m.matches()) continue;

                String name    = m.group(1).trim();
                String version = m.group(2).trim();
                String udid    = m.group(3).trim();

                // The UDID regex already guarantees this is an iPhone/iPad (not Mac/Simulator)
                addDevice(result, udid, name, version, "xctrace");
            }
        } catch (Exception e) {
            System.err.println("[IOS] xctrace error: " + e.getMessage());
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void addDevice(List<Map<String, String>> result,
                                  String udid, String name, String version, String source) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("udid",            udid);
        d.put("deviceName",      name);
        d.put("model",           name);
        d.put("manufacturer",    "Apple");
        d.put("platform",        "IOS");
        d.put("platformVersion", version);
        d.put("status",          "AVAILABLE");
        d.put("source",          source);
        // xctrace-discovered devices: no DeviceInfo available — evaluate system health only
        if ("xctrace".equals(source)) {
            DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluateXctrace();
            d.put("presence",          r.presence.name());
            d.put("tunnel",            r.tunnel.name());
            d.put("readyForExecution", String.valueOf(r.readyForExecution));
            if (r.notReadyReason != null) d.put("notReadyReason", r.notReadyReason);
        }
        result.add(d);
        System.out.printf("[IOS] ✓ %s | iOS %s | %s | fuente: %s%n", name, version, udid, source);
    }

    /**
     * Classifies device status and logs the origin/reason for each assignment.
     *
     * Status rules:
     *   WIRED                              → AVAILABLE  (USB cable — can create Appium session)
     *   LOCAL_NETWORK + tunnel=connected   → AVAILABLE  (WiFi tunnel active)
     *   LOCAL_NETWORK + tunnel!=connected  → DISCOVERED (WiFi paired but no active tunnel)
     *   UNKNOWN transport                  → DISCOVERED (cannot confirm Appium readiness)
     *
     * DISCOVERED devices are registered on the backend so the dashboard reflects they exist,
     * but they are never marked AVAILABLE so the scheduler will not dispatch test jobs to them.
     */
    private static void applyDeviceInfo(Map<String, String> device,
                                         DevicectlParser.DeviceInfo info,
                                         String coreDeviceId) {
        String transport = info.transportType.name();
        String tunnel    = info.tunnelState;

        String status, source, reason;

        if (info.transportType == DevicectlParser.TransportType.WIRED) {
            status = "AVAILABLE";
            source = "devicectl-wired";
            reason = "USB cable detectado";
        } else if (info.transportType == DevicectlParser.TransportType.LOCAL_NETWORK) {
            if ("connected".equalsIgnoreCase(tunnel)) {
                status = "AVAILABLE";
                source = "devicectl-wifi";
                reason = "WiFi / Bonjour — túnel activo (tunnelState=connected)";
            } else {
                status = "DISCOVERED";
                source = "devicectl-wifi-offline";
                reason = "WiFi emparejado pero túnel desconectado (tunnelState=" + tunnel
                       + ") — no puede iniciar sesión Appium";
            }
        } else {
            status = "DISCOVERED";
            source = "devicectl-unknown-transport";
            reason = "transportType=" + transport + " desconocido — disponibilidad no confirmada";
        }

        device.put("status",        status);
        device.put("source",        source);
        device.put("transportType", transport);
        device.put("tunnelState",   tunnel);

        // DeviceAvailability model: Presence, TunnelStatus, ReadyForExecution
        DeviceReadinessEvaluator.Readiness r = DeviceReadinessEvaluator.evaluate(info);
        device.put("presence",          r.presence.name());
        device.put("tunnel",            r.tunnel.name());
        device.put("readyForExecution", String.valueOf(r.readyForExecution));
        if (r.notReadyReason != null) device.put("notReadyReason", r.notReadyReason);
        else                          device.remove("notReadyReason");

        String physUdid  = device.get("udid");
        String icon      = "AVAILABLE".equals(status) ? "✅" : "⚠️ ";
        String coreStr   = coreDeviceId != null ? " | CoreDevice: " + coreDeviceId : "";
        String readyStr  = r.readyForExecution ? "ready" : "not-ready";

        System.out.printf("[IOS] %s %-26s | transport=%-14s | tunnel=%-12s | → %-10s | %s%s%n",
                icon, physUdid, transport, tunnel, status, readyStr, coreStr);
        System.out.printf("[IOS]    Origen: %s — %s%n", source, reason);
        if (!r.readyForExecution && r.notReadyReason != null) {
            System.out.printf("[IOS]    No listo: %s%n", r.notReadyReason);
        }
    }
}
