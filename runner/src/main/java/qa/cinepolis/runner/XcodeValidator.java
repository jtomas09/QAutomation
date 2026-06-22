package qa.cinepolis.runner;

import java.util.concurrent.TimeUnit;

/**
 * Validates Xcode installation on macOS.
 * Runs xcode-select, xcodebuild, and xcrun to confirm full functionality.
 * Result is cached; call invalidateCache() before a re-check.
 */
public class XcodeValidator {

    public static class XcodeInfo {
        public final boolean installed;
        public final String  xcodePath;
        public final String  xcodeVersion;
        public final boolean xcrunFunctional;

        XcodeInfo(boolean installed, String path, String version, boolean xcrun) {
            this.installed       = installed;
            this.xcodePath       = path;
            this.xcodeVersion    = version;
            this.xcrunFunctional = xcrun;
        }

        public static final XcodeInfo NOT_INSTALLED =
                new XcodeInfo(false, null, "unavailable", false);
    }

    private static volatile XcodeInfo cached = null;

    public static XcodeInfo validate() {
        if (cached != null) return cached;
        return cached = doValidate();
    }

    public static void invalidateCache() { cached = null; }

    private static XcodeInfo doValidate() {
        // xcode-select -p: primary existence check
        String xcodePath = runCapture("xcode-select", "-p");
        if (xcodePath == null || xcodePath.isBlank()) {
            System.out.println("[Xcode] xcode-select -p: no encontrado — Xcode no instalado.");
            return XcodeInfo.NOT_INSTALLED;
        }
        xcodePath = xcodePath.trim();

        // xcodebuild -version: extract version string
        String xcodeVersion = "unknown";
        String rawVer = runCapture("xcodebuild", "-version");
        if (rawVer != null) {
            for (String line : rawVer.split("\n")) {
                if (line.startsWith("Xcode ")) {
                    xcodeVersion = line.substring("Xcode ".length()).trim();
                    break;
                }
            }
        }

        // xcrun --version: confirms command-line tools are available
        boolean xcrunOk = runCapture("xcrun", "--version") != null;

        System.out.printf("[Xcode] Path: %s | Version: %s | xcrun: %s%n",
                xcodePath, xcodeVersion, xcrunOk ? "OK" : "FAIL");

        return new XcodeInfo(xcrunOk, xcodePath, xcodeVersion, xcrunOk);
    }

    private static String runCapture(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return null; }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return (p.exitValue() == 0 && !out.isBlank()) ? out : null;
        } catch (Exception e) { return null; }
    }
}
