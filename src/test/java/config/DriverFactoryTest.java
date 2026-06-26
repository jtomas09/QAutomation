package config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DriverFactory.isValidPlatformVersion().
 *
 * Ensures that "unknown" and other invalid values are NEVER sent to Appium
 * as the platformVersion capability, which would cause SessionNotCreatedException.
 */
@DisplayName("DriverFactory.isValidPlatformVersion")
class DriverFactoryTest {

    // ── Invalid values — must return false ───────────────────────────────────

    @ParameterizedTest(name = "null/empty → false")
    @NullAndEmptySource
    void nullOrEmpty_returnsFalse(String value) {
        assertFalse(DriverFactory.isValidPlatformVersion(value),
                "null/empty must not be sent to Appium as platformVersion");
    }

    @ParameterizedTest(name = "''{0}'' is invalid → false")
    @ValueSource(strings = {"unknown", "UNKNOWN", "Unknown", "uNkNoWn"})
    void unknown_allCases_returnsFalse(String value) {
        assertFalse(DriverFactory.isValidPlatformVersion(value),
                "'unknown' in any case must never reach Appium as platformVersion");
    }

    @ParameterizedTest(name = "''{0}'' has no dot → false")
    @ValueSource(strings = {"17", "18", "abc", "ios18", "latest", "auto", "   "})
    void missingDot_returnsFalse(String value) {
        assertFalse(DriverFactory.isValidPlatformVersion(value),
                "Non-numeric or missing-dot values must not be sent to Appium");
    }

    @Test
    @DisplayName("whitespace-only string → false")
    void whitespaceOnly_returnsFalse() {
        assertFalse(DriverFactory.isValidPlatformVersion("   "));
    }

    // ── Valid values — must return true ──────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' is valid → true")
    @ValueSource(strings = {"17.7", "18.0", "26.5", "18.0.1", "16.4", "15.0", "26.0"})
    void validVersionStrings_returnTrue(String value) {
        assertTrue(DriverFactory.isValidPlatformVersion(value),
                "'" + value + "' is a valid iOS version and should be sent to Appium");
    }

    @Test
    @DisplayName("version with trailing spaces is trimmed and accepted")
    void trimmedVersion_isAccepted() {
        assertTrue(DriverFactory.isValidPlatformVersion("  18.5  "),
                "Leading/trailing whitespace should not disqualify a valid version");
    }
}
