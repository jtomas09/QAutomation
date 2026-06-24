package qa.cinepolis.runner;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plug-and-Play Android SDK environment configurator.
 *
 * Detects Android SDK automatically and produces the environment variable map
 * that must be injected into every ProcessBuilder that runs Gradle or any
 * Android-aware tool (adb, emulator, sdkmanager, etc.).
 *
 * Users never need to configure ANDROID_HOME, ANDROID_SDK_ROOT, or PATH.
 * The Runner calls this once per job and injects the result via
 * pb.environment().putAll(bootstrap.buildEnv()).
 */
public class AndroidEnvironmentBootstrap {

    /**
     * Singleton — SDK detection runs exactly once at JVM startup.
     * AppiumManager calls get() on every subprocess; creating a new instance
     * each time would repeat filesystem scans unnecessarily.
     */
    private static final AndroidEnvironmentBootstrap INSTANCE = new AndroidEnvironmentBootstrap();

    /** Returns the shared singleton. Use this instead of new AndroidEnvironmentBootstrap(). */
    public static AndroidEnvironmentBootstrap get() { return INSTANCE; }

    private final String sdkPath;

    public AndroidEnvironmentBootstrap() {
        this.sdkPath = AndroidSdkLocator.locate();
    }

    /** True when the Android SDK was found and is usable. */
    public boolean isValid() { return sdkPath != null; }

    /** Absolute path to the Android SDK root. Null if not found. */
    public String getSdkPath() { return sdkPath; }

    /**
     * Builds the environment variable map to inject into ProcessBuilder.
     *
     * Sets:
     *   ANDROID_HOME       — SDK root (required by Gradle Android plugin)
     *   ANDROID_SDK_ROOT   — same value (legacy alias, still read by some tools)
     *   PATH               — prepends platform-tools, emulator, cmdline-tools/bin
     *
     * If SDK is not found, returns an empty map (Gradle will fail with its own
     * error message rather than a silent environment gap).
     */
    public Map<String, String> buildEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        if (!isValid()) return env;

        env.put("ANDROID_HOME",     sdkPath);
        env.put("ANDROID_SDK_ROOT", sdkPath);

        boolean isWin = AndroidSdkLocator.isWindows();
        String  sep   = isWin ? ";" : ":";

        StringBuilder path = new StringBuilder();

        // platform-tools first — contains adb
        path.append(sdkPath).append(File.separator).append("platform-tools").append(sep);

        // emulator — needed for AVD targets
        File emulatorDir = new File(sdkPath, "emulator");
        if (emulatorDir.isDirectory()) {
            path.append(emulatorDir.getAbsolutePath()).append(sep);
        }

        // cmdline-tools/bin — sdkmanager, avdmanager
        String cmdlineBin = AndroidSdkLocator.locateCmdlineToolsBin(sdkPath);
        if (cmdlineBin != null) {
            path.append(cmdlineBin).append(sep);
        }

        // Preserve the inherited PATH so nothing breaks
        String currentPath = System.getenv("PATH");
        if (currentPath != null && !currentPath.isBlank()) {
            path.append(currentPath);
        }

        env.put("PATH", path.toString());
        return env;
    }

    /**
     * Logs the full SDK status to the execution log visible in the Dashboard.
     *
     * Happy path:
     *   ✅ Android SDK encontrado: /Users/…/Library/Android/sdk
     *   ✅ ANDROID_HOME configurado
     *   ✅ ANDROID_SDK_ROOT configurado
     *   ✅ ADB encontrado: …/platform-tools/adb
     *
     * Missing SDK:
     *   ❌ Android SDK no encontrado — instala Android Studio
     *
     * Missing platform-tools:
     *   ⚠️ platform-tools no encontrado — abre SDK Manager
     */
    public void logStatus(String executionId, BackendClient client) {
        if (!isValid()) {
            client.sendLog(executionId, "ERROR",
                "❌ Android SDK no encontrado.\n" +
                "  1. Instala Android Studio desde https://developer.android.com/studio\n" +
                "  2. Abre SDK Manager y acepta las licencias.\n" +
                "  Rutas buscadas:\n" +
                "    macOS:   ~/Library/Android/sdk\n" +
                "    Windows: %LOCALAPPDATA%\\Android\\Sdk\n" +
                "    Linux:   ~/Android/Sdk");
            return;
        }

        client.sendLog(executionId, "INFO", "✅ Android SDK encontrado: " + sdkPath);
        client.sendLog(executionId, "INFO", "✅ ANDROID_HOME configurado: " + sdkPath);
        client.sendLog(executionId, "INFO", "✅ ANDROID_SDK_ROOT configurado: " + sdkPath);

        String adb = AndroidSdkLocator.locateAdb(sdkPath);
        if (adb != null) {
            client.sendLog(executionId, "INFO", "✅ ADB encontrado: " + adb);
        } else {
            client.sendLog(executionId, "WARN",
                "⚠️ platform-tools no encontrado en SDK.\n" +
                "  Abre SDK Manager → SDK Tools → Android SDK Platform-Tools → Install.");
        }

        // Extra: warn if build-tools is missing (needed for APK compilation)
        File buildTools = new File(sdkPath, "build-tools");
        if (!buildTools.isDirectory() || buildTools.list() == null || buildTools.list().length == 0) {
            client.sendLog(executionId, "WARN",
                "⚠️ build-tools no encontrado en SDK — puede fallar la compilación.\n" +
                "  Abre SDK Manager → SDK Tools → Android SDK Build-Tools → Install.");
        }
    }
}
