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

    // Pattern to extract physical UDID from devicectl --json-output (hardwareProperties.udid)
    private static final Pattern PHYSICAL_UDID_JSON =
            Pattern.compile("\"udid\"\\s*:\\s*\"([0-9A-Fa-f]{8}-[0-9A-Fa-f]{16})\"");

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
            System.out.printf("[IOS] %d dispositivo(s) via devicectl (UDID físico resuelto).%n",
                    devicectlResult.size());
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

        for (Map<String, String> device : devices) {
            String coreDeviceId = device.get("udid");
            if (!isCoreDeviceUuid(coreDeviceId)) continue;

            if (!json.contains(coreDeviceId)) {
                System.err.printf("[IOS] CoreDevice UUID %s no encontrado en JSON — usará xctrace%n", coreDeviceId);
                continue;
            }

            // CoreDevice UUID appears as "identifier": "554E89EA-..." in the JSON.
            // Physical UDID is in hardwareProperties.udid nearby (within same device object).
            // Search 2000 chars on each side — hardwareProperties may appear before OR after identifier.
            int idx    = json.indexOf(coreDeviceId);
            String reg = json.substring(Math.max(0, idx - 2000), Math.min(json.length(), idx + 2000));
            Matcher m  = PHYSICAL_UDID_JSON.matcher(reg);

            if (m.find()) {
                String physicalUdid = m.group(1);
                device.put("coreDeviceId", coreDeviceId);  // Keep for reference/logging
                device.put("udid",         physicalUdid);  // Physical UDID — what Appium expects
                System.out.printf("[IOS] 🔗 CoreDevice ID  : %s%n", coreDeviceId);
                System.out.printf("[IOS] 🔗 Physical UDID  : %s  ← enviado a Appium%n", physicalUdid);
                // Update version from JSON if empty (Xcode 26 text output omits iOS version)
                if (device.getOrDefault("platformVersion", "").isBlank()) {
                    String region2k = json.substring(Math.max(0, idx - 2000),
                            Math.min(json.length(), idx + 500));
                    for (String field : new String[]{"osVersionNumber", "operatingSystemVersion", "softwareVersion"}) {
                        Matcher vm = Pattern.compile(
                                "\"" + field + "\"\\s*:\\s*\"([\\d]+\\.[\\d.]+)\"").matcher(region2k);
                        if (vm.find()) {
                            device.put("platformVersion", vm.group(1));
                            System.out.printf("[IOS] 🔗 iOS version    : %s (vía devicectl JSON)%n", vm.group(1));
                            break;
                        }
                    }
                }
            } else {
                System.err.printf("[IOS] Physical UDID no encontrado en JSON para CoreDevice UUID %s%n",
                        coreDeviceId);
            }
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
        result.add(d);
        System.out.printf("[IOS] ✓ %s | iOS %s | %s%n", name, version, udid);
    }
}
