package qa.cinepolis.runner.mirror;

/**
 * Diagnóstico de arranque del Mirror — se ejecuta UNA VEZ al iniciar el
 * Runner (ver RunnerAgent), nunca en el camino caliente de una conexión de
 * mirror. Descarga ffmpeg/scrcpy automáticamente si faltan (sin ninguna
 * acción del usuario — ver MirrorDependencyManager) y deja un checklist
 * claro en el log para diagnóstico, exactamente como pediste:
 *
 *   ✓ FFmpeg disponible
 *   ✓ scrcpy disponible
 *   ✓ libimobiledevice disponible
 *   ✓ AVFoundation disponible
 *
 * Nunca bloquea el arranque del Runner de forma indefinida ni falla el
 * proceso si algo no está disponible — cada línea faltante simplemente
 * significa que esa capa de la cadena de fallback quedará deshabilitada
 * (ver FallbackChainProvider), el Mirror sigue funcionando con lo que sí
 * esté disponible.
 */
public final class MirrorStartupCheck {

    private MirrorStartupCheck() { }

    public static void runAndLog(String agentDataDir) {
        System.out.println("==========================");
        System.out.println("MIRROR STARTUP CHECK");
        System.out.println("==========================");

        boolean ffmpegOk = false;
        boolean scrcpyOk = false;

        if (agentDataDir != null) {
            MirrorDependencyManager deps = new MirrorDependencyManager(java.nio.file.Path.of(agentDataDir));
            ffmpegOk = deps.ensureFfmpeg();
            scrcpyOk = deps.ensureScrcpy();
        } else {
            System.out.println("[MirrorStartupCheck] Sin agentDataDir — se omite la descarga automática, "
                    + "solo se busca en Homebrew/PATH.");
            ffmpegOk = BinaryLocator.resolve("ffmpeg") != null;
            scrcpyOk = BinaryLocator.resolve("scrcpy") != null;
        }

        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        boolean libimobiledeviceOk = BinaryLocator.resolve("idevicescreenshot") != null;
        boolean avfoundationOk = ffmpegOk && isMac;

        logLine("FFmpeg disponible", ffmpegOk);
        logLine("scrcpy disponible", scrcpyOk);
        logLine("libimobiledevice disponible (fallback iOS)", libimobiledeviceOk);
        logLine("AVFoundation disponible (mirror iOS sin WDA)", avfoundationOk);

        if (!avfoundationOk && !libimobiledeviceOk && isMac) {
            System.out.println("[MirrorStartupCheck] Ningún mecanismo de mirror sin WDA disponible para iOS todavía — "
                    + "el mirror de iPhones no se mostrará hasta que ffmpeg (auto-descargado arriba) "
                    + "o libimobiledevice (opcional) estén listos.");
        }
        if (!scrcpyOk) {
            System.out.println("[MirrorStartupCheck] scrcpy no disponible — Android usará ADB screencap "
                    + "(funcional, menor calidad/latencia que scrcpy).");
        }

        System.out.println("==========================");
    }

    private static void logLine(String label, boolean ok) {
        System.out.println((ok ? "✓ " : "✗ ") + label);
    }
}
