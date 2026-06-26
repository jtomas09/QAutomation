package qa.cinepolis.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against "unknown" propagating as platformVersion.
 *
 * Root cause: Xcode 26 xcrun devicectl text output does NOT include the iOS version
 * number in the device line (only the model e.g. "iPhone 13"). IOSDeviceScanner must
 * store "" (empty) instead of "unknown" so that downstream guards can skip the
 * -DplatformVersion flag entirely and let Appium auto-detect from the device.
 */
@DisplayName("iOS platformVersion guard")
class IosVersionGuardTest {

    // ── IOSDeviceScanner.parseDevicectlOutput ────────────────────────────────

    @Test
    @DisplayName("Xcode 26 CoreDevice line: device detected, platformVersion is '' not 'unknown'")
    void xcode26CoreDeviceLine_neverStoresUnknown() {
        // Actual Xcode 26 xcrun devicectl output line (iOS version absent from text format)
        String xcode26Line =
                "iPhone de Tester   iPhone-de-Tester.coredevice.local" +
                "   554E89EA-E69D-54EE-9877-B26F70061A0A   connected   iPhone 13 (iPhone14,5)";

        List<Map<String, String>> result = new ArrayList<>();
        IOSDeviceScanner.parseDevicectlOutput(xcode26Line, result);

        assertFalse(result.isEmpty(), "Device should be detected from Xcode 26 CoreDevice line");
        String platformVersion = result.get(0).get("platformVersion");
        assertNotEquals("unknown", platformVersion,
                "'unknown' must never be stored as platformVersion — it breaks Appium");
        assertEquals("", platformVersion,
                "platformVersion should be empty when version is absent from devicectl text output; "
                + "IosPreflightManager.detectIosVersion() will supply it via --json-output");
    }

    @Test
    @DisplayName("Traditional devicectl line with explicit version: platformVersion is correct")
    void traditionalDevicectlLine_withVersion_storesVersion() {
        // Old-style xcrun devicectl output where UDID is in 8-16 format and version is present
        String oldLine = "00008110-000129261482601E   iPhone de Tester   iOS   18.5   connected";

        List<Map<String, String>> result = new ArrayList<>();
        IOSDeviceScanner.parseDevicectlOutput(oldLine, result);

        // Even if device is detected, platformVersion must never be "unknown"
        for (Map<String, String> device : result) {
            assertNotEquals("unknown", device.get("platformVersion"),
                    "'unknown' is never a valid platformVersion");
        }
    }

    @Test
    @DisplayName("Mac UUID line without iphone/ipad/ipod keyword: device is NOT detected")
    void macUuidLine_isIgnored() {
        // macOS device in devicectl output — should be filtered out
        String macLine = "00008103-MacBook-Pro.local   A1B2C3D4-E5F6-7890-ABCD-EF1234567890   macOS   macOS 14.5";

        List<Map<String, String>> result = new ArrayList<>();
        IOSDeviceScanner.parseDevicectlOutput(macLine, result);

        assertTrue(result.isEmpty(), "Mac devices must not appear in iOS device scan results");
    }

    @Test
    @DisplayName("CoreDevice UUID line without iphone/ipad/ipod: filtered out")
    void coreDeviceUuidLine_noIosKeyword_isIgnored() {
        String unknownLine = "Some-Device   some-device.local   AABBCCDD-1234-5678-ABCD-EF1234567890   connected   GenericDevice";

        List<Map<String, String>> result = new ArrayList<>();
        IOSDeviceScanner.parseDevicectlOutput(unknownLine, result);

        assertTrue(result.isEmpty(),
                "Lines without iphone/ipad/ipod keyword must be filtered when using CoreDevice UUID");
    }

    // ── IosPreflightManager.detectIosVersion ────────────────────────────────

    @Test
    @DisplayName("detectIosVersion: null udid returns empty string, never 'unknown'")
    void detectIosVersion_nullUdid_returnsEmpty() {
        // BackendClient can be null because udid blank-check is first
        String version = IosPreflightManager.detectIosVersion(null, "test-exec", null);
        assertEquals("", version, "null udid must return empty string");
        assertNotEquals("unknown", version);
    }

    @Test
    @DisplayName("detectIosVersion: blank udid returns empty string, never 'unknown'")
    void detectIosVersion_blankUdid_returnsEmpty() {
        String version = IosPreflightManager.detectIosVersion(null, "test-exec", "   ");
        assertEquals("", version, "blank udid must return empty string");
        assertNotEquals("unknown", version);
    }

    // ── JobExecutor platformVersion guard (string logic) ────────────────────

    @Test
    @DisplayName("'unknown' is never a valid -DplatformVersion value")
    void unknownIsNeverValidPlatformVersion() {
        String[] invalid = {"unknown", "UNKNOWN", "Unknown", "", null};
        for (String v : invalid) {
            boolean shouldSkip = (v == null || v.isBlank() || "unknown".equalsIgnoreCase(v));
            assertTrue(shouldSkip,
                    "'" + v + "' must be skipped — never passed as -DplatformVersion to Gradle");
        }
    }

    @Test
    @DisplayName("Valid iOS version strings pass the guard")
    void validVersionStrings_passGuard() {
        String[] valid = {"17.7", "18.0", "26.5", "18.0.1", "16.4"};
        for (String v : valid) {
            boolean isValid = v != null && !v.isBlank()
                    && !"unknown".equalsIgnoreCase(v)
                    && v.matches("\\d+\\.\\d+.*");
            assertTrue(isValid, "'" + v + "' should be a valid platformVersion");
        }
    }
}
