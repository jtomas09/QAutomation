package qa.cinepolis.runner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Única clase del Runner que conoce la ubicación y el formato del registro de
 * cuentas Apple Developer que mantiene Xcode.
 *
 * Fuente: ~/Library/Preferences/com.apple.dt.Xcode.plist, clave
 * IDEProvisioningTeamByIdentifier — el mismo registro que Xcode consulta antes
 * de compilar para decidir si tiene una cuenta autenticada para un Team dado.
 * Confirmado empíricamente en esta investigación: un Team cuyo certificado
 * sigue vigente en el keychain, pero cuya cuenta fue removida de Xcode →
 * Settings → Accounts, deja de aparecer aquí — y es exactamente cuando
 * xcodebuild responde "No Account for Team X".
 *
 * Resiliente por diseño: si el archivo no existe, cambia de formato, o la
 * clave desaparece en una versión futura de Xcode, discoverAuthenticatedTeams()
 * devuelve DiscoveryResult.unavailable(...) en vez de lanzar — el llamador
 * (AppleDeveloperTeamManager) decide cómo degradar, nunca bloquea al Runner.
 */
final class XcodeAccountProvider implements AppleDeveloperAccountProvider {

    private static final File XCODE_PREFS =
            new File(System.getProperty("user.home") + "/Library/Preferences/com.apple.dt.Xcode.plist");

    private static final Pattern TEAM_ID_ENTRY = Pattern.compile(
            "<key>teamID</key>\\s*<string>([A-Z0-9]{10})</string>");
    private static final Pattern TEAM_NAME = Pattern.compile(
            "<key>teamName</key>\\s*<string>([^<]*)</string>");

    @Override
    public DiscoveryResult discoverAuthenticatedTeams() {
        try {
            if (!XCODE_PREFS.isFile()) {
                return DiscoveryResult.unavailable(
                        "com.apple.dt.Xcode.plist no existe (¿Xcode nunca se ha abierto en esta Mac?)");
            }

            String xml = convertToXml(XCODE_PREFS);
            if (xml == null) {
                return DiscoveryResult.unavailable(
                        "No se pudo leer/convertir com.apple.dt.Xcode.plist a XML");
            }

            String block = extractDictBlock(xml, "IDEProvisioningTeamByIdentifier");
            if (block == null) {
                return DiscoveryResult.unavailable(
                        "La clave IDEProvisioningTeamByIdentifier no existe en el plist de Xcode "
                        + "(¿cambió de formato en esta versión de Xcode?)");
            }

            Map<String, String> teams = extractTeamEntries(block);
            if (teams.isEmpty()) {
                return DiscoveryResult.unavailable(
                        "IDEProvisioningTeamByIdentifier existe pero no contiene ningún Team reconocible");
            }

            return DiscoveryResult.available(teams);
        } catch (Exception e) {
            return DiscoveryResult.unavailable("Error inesperado leyendo cuentas de Xcode: " + e.getMessage());
        }
    }

    /** Convierte el plist binario/XML de Xcode a texto XML vía `plutil`, sin modificar el original. */
    private static String convertToXml(File plist) {
        try {
            Process p = new ProcessBuilder("plutil", "-convert", "xml1", "-o", "-", plist.getAbsolutePath())
                    .redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return (done && out.contains("<plist")) ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extrae el bloque &lt;dict&gt;...&lt;/dict&gt; que es el VALOR de la clave dada,
     * contando anidamiento para hallar el cierre correcto (el valor puede contener
     * sus propios dict/array anidados, como es el caso aquí).
     */
    private static String extractDictBlock(String plistXml, String key) {
        int keyIdx = plistXml.indexOf("<key>" + key + "</key>");
        if (keyIdx < 0) return null;
        int dictStart = plistXml.indexOf("<dict>", keyIdx);
        if (dictStart < 0) return null;

        Matcher m = Pattern.compile("<dict>|</dict>").matcher(plistXml);
        m.region(dictStart, plistXml.length());
        int depth = 0;
        while (m.find()) {
            if (m.group().equals("<dict>")) {
                depth++;
            } else {
                depth--;
                if (depth == 0) return plistXml.substring(dictStart, m.end());
            }
        }
        return null; // dict sin cerrar — plist corrupto o truncado
    }

    /**
     * Dentro del bloque de IDEProvisioningTeamByIdentifier, cada Team autenticado
     * aparece como un dict hijo con teamID + teamName (entre otros campos). Se
     * escanea cada teamID y se busca su teamName en una ventana cercana en vez de
     * parsear el dict completo — ambos campos siempre están en la misma entrada
     * pequeña, y evita escribir un parser de plist genérico para un único uso.
     */
    private static Map<String, String> extractTeamEntries(String dictBlock) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher idM = TEAM_ID_ENTRY.matcher(dictBlock);
        while (idM.find()) {
            String teamId = idM.group(1);
            int from = Math.max(0, idM.start() - 300);
            int to   = Math.min(dictBlock.length(), idM.end() + 300);
            Matcher nameM = TEAM_NAME.matcher(dictBlock.substring(from, to));
            String teamName = nameM.find() ? nameM.group(1).trim() : "desconocido";
            result.putIfAbsent(teamId, teamName);
        }
        return result;
    }
}
