package qa.cinepolis.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the Android SDK automatically across macOS, Windows, and Linux.
 *
 * Search priority:
 *   1. ANDROID_HOME env var (already set by the user or CI)
 *   2. ANDROID_SDK_ROOT env var
 *   3. Standard Android Studio installation paths by OS
 *   4. Additional common paths (homebrew, package managers, etc.)
 *
 * Never relies on PATH.  Returns null if SDK cannot be found.
 */
public class AndroidSdkLocator {

    private AndroidSdkLocator() {}

    /** Returns the SDK root path, or null if not found. */
    public static String locate() {
        // ── 1. Respect existing env vars ──────────────────────────────────────
        String fromEnv = System.getenv("ANDROID_HOME");
        if (isValidSdk(fromEnv)) return new File(fromEnv).getAbsolutePath();

        fromEnv = System.getenv("ANDROID_SDK_ROOT");
        if (isValidSdk(fromEnv)) return new File(fromEnv).getAbsolutePath();

        // ── 2. Standard Android Studio paths ──────────────────────────────────
        for (String candidate : buildCandidates()) {
            if (isValidSdk(candidate)) return new File(candidate).getAbsolutePath();
        }

        return null;
    }

    /**
     * Validates that the path looks like a real Android SDK.
     * Minimum requirement: directory exists AND contains platform-tools/.
     */
    public static boolean isValidSdk(String path) {
        if (path == null || path.isBlank()) return false;
        File sdk = new File(path);
        return sdk.isDirectory() && new File(sdk, "platform-tools").isDirectory();
    }

    /**
     * Returns the absolute path to adb inside the given SDK root,
     * or null if platform-tools is missing.
     */
    public static String locateAdb(String sdkPath) {
        if (sdkPath == null) return null;
        boolean isWin = isWindows();
        File adb = new File(sdkPath, "platform-tools" + File.separator + (isWin ? "adb.exe" : "adb"));
        return adb.exists() ? adb.getAbsolutePath() : null;
    }

    /**
     * Finds the best available cmdline-tools/bin directory inside the SDK.
     * Tries "latest" first, then numeric versions in descending order.
     */
    public static String locateCmdlineToolsBin(String sdkPath) {
        if (sdkPath == null) return null;
        File cmdlineTools = new File(sdkPath, "cmdline-tools");
        if (!cmdlineTools.isDirectory()) return null;

        // Preferred names
        String[] preferred = {"latest", "11.0", "10.0", "9.0", "8.0", "7.0"};
        for (String v : preferred) {
            File bin = new File(cmdlineTools, v + File.separator + "bin");
            if (bin.isDirectory()) return bin.getAbsolutePath();
        }

        // Any subdirectory that has a bin/ inside
        File[] dirs = cmdlineTools.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                File bin = new File(dir, "bin");
                if (bin.isDirectory()) return bin.getAbsolutePath();
            }
        }
        return null;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private static List<String> buildCandidates() {
        List<String> list = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        String os   = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            // Standard Android Studio path (most common)
            list.add(home + "/Library/Android/sdk");
            // Alternative: some users or CI tools install here
            list.add(home + "/Android/Sdk");
            // Enterprise bundled SDK (Runner may ship its own copy here)
            list.add(home + "/Library/Application Support/AutomationQA/android-sdk");
            // Android Studio Giraffe+ may install here on Apple Silicon
            list.add("/Applications/Android Studio.app/Contents/sdk");
            list.add("/usr/local/share/android-sdk");
            list.add("/opt/homebrew/share/android-sdk");         // homebrew
            list.add("/usr/local/opt/android-sdk");              // homebrew intel

        } else if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String userProfile  = System.getenv("USERPROFILE");
            String programFiles  = System.getenv("ProgramFiles");
            String programFilesX = System.getenv("ProgramFiles(x86)");

            // Standard Android Studio path on Windows
            if (localAppData != null) list.add(localAppData + "\\Android\\Sdk");
            if (userProfile  != null) list.add(userProfile  + "\\Android\\Sdk");
            if (userProfile  != null) list.add(userProfile  + "\\AppData\\Local\\Android\\Sdk");
            if (programFiles  != null) list.add(programFiles  + "\\Android\\android-sdk");
            if (programFilesX != null) list.add(programFilesX + "\\Android\\android-sdk");

        } else {
            // Linux / ChromeOS
            list.add(home + "/Android/Sdk");
            list.add(home + "/android-sdk");
            list.add("/usr/lib/android-sdk");
            list.add("/opt/android-sdk");
            list.add("/snap/androidsdk/current/Android");        // snap package
        }

        // ── Last resort: Runner's embedded platform-tools directory ────────────
        // ~/.automationqa/platform-tools/adb is downloaded by PlatformToolsManager.
        // isValidSdk() only requires platform-tools/ to exist, so this minimal
        // layout works as ANDROID_HOME for adb and UiAutomator2 driver.
        String agentDataDir = System.getProperty("AGENT_DATA_DIR",
                home + "/.automationqa");
        list.add(agentDataDir);

        // Windows embedded location (under %LOCALAPPDATA%\AutomationQA\runner)
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null)
                list.add(localAppData + "\\AutomationQA\\runner");
        }

        return list;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
