package qa.cinepolis.runner;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/**
 * iOS physical device discovery via xcrun devicectl (primary) and xcrun xctrace (fallback).
 *
 * Resolution order:
 *   1. xcrun devicectl list devices  (Xcode 15+, iOS 17+ devices — most reliable)
 *   2. xcrun xctrace list devices    (works on all Xcode versions)
 *
 * Filtering rules:
 *   - Include only physical iOS devices (iPhone, iPad, iPod)
 *   - Exclude Simulators, macOS (the Mac itself), Placeholder devices, Unavailable devices
 *
 * iPhone/iPad UDID format: 8 hex chars + dash + 16 hex chars  (e.g. 00008110-000129261482601E)
 * Mac/Simulator UUID format: 8-4-4-4-12 hex chars — this is how we distinguish them.
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
     * Scans for connected physical iOS devices.
     * Tries devicectl first; falls back to xctrace if devicectl finds nothing.
     */
    public static List<Map<String, String>> scan() {
        List<Map<String, String>> result = scanWithDevicectl();
        if (!result.isEmpty()) {
            System.out.printf("[IOS] Encontrados %d dispositivo(s) via devicectl.%n", result.size());
            return result;
        }
        System.out.println("[IOS] devicectl sin resultados — usando fallback xctrace...");
        result = scanWithXctrace();
        if (!result.isEmpty()) {
            System.out.printf("[IOS] Encontrados %d dispositivo(s) via xctrace.%n", result.size());
        } else {
            System.out.println("[IOS] Sin dispositivos iOS físicos detectados.");
        }
        return result;
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
        } catch (Exception e) {
            System.err.println("[IOS] devicectl no disponible: " + e.getMessage());
        }
        return result;
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
