package qa.cinepolis.runner.mirror;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Localiza binarios externos opcionales (ffmpeg, scrcpy, idevicescreenshot)
 * usados por los providers de Mirror sin WDA. A diferencia de ADB (ver
 * PlatformToolsManager — binario embebido, descarga automática), estas son
 * herramientas de terceros que el usuario instala una sola vez (típicamente
 * vía Homebrew) — este localizador solo las BUSCA en las rutas de
 * instalación habituales, nunca las descarga ni las gestiona.
 *
 * Reutilizado por AVFoundationMirrorProvider, ScrcpyMirrorProvider y
 * LibimobiledeviceMirrorProvider — mismo criterio de búsqueda para los tres,
 * en vez de triplicar esta lógica.
 */
final class BinaryLocator {

    private static final String[] COMMON_DIRS = {
        "/opt/homebrew/bin/",   // Homebrew en Apple Silicon
        "/usr/local/bin/",      // Homebrew en Intel / instalaciones manuales
        "/usr/bin/",
        "",                     // último recurso: confiar en PATH tal cual
    };

    private BinaryLocator() { }

    /**
     * @return ruta absoluta al binario si existe en alguna ubicación conocida
     *         o resuelve en PATH (verificado con {@code --version}), o null
     *         si no se encontró en ningún lado.
     */
    static String resolve(String binaryName) {
        for (String dir : COMMON_DIRS) {
            String candidate = dir.isEmpty() ? binaryName : dir + binaryName;
            if (dir.isEmpty()) {
                if (respondsToVersion(candidate)) return candidate;
            } else if (new File(candidate).exists() && respondsToVersion(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean respondsToVersion(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            p.getInputStream().readAllBytes(); // drena el buffer para no bloquear
            p.destroyForcibly();
            return done;
        } catch (Exception e) {
            return false;
        }
    }
}
