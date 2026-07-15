package qa.cinepolis.runner;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Primitivos de parseo de certificados de firma y provisioning profiles de Apple,
 * compartidos por IosPreflightManager (validación del WDA cacheado) y
 * AppleDeveloperTeamManager (descubrimiento y selección de Apple Developer Team).
 *
 * Extraído de IosPreflightManager para eliminar duplicación cuando se agregó el
 * descubrimiento multi-Team — antes estos métodos solo devolvían la PRIMERA
 * coincidencia (un certificado, un perfil); aquí se agregan además los métodos
 * discoverXxx() que enumeran TODAS las coincidencias, necesarios para poder
 * distinguir entre varios Apple Developer Teams presentes en la misma Mac.
 *
 * IMPORTANTE: el Team ID nunca se lee del Common Name (CN) de un certificado —
 * el CN de "Apple Development: email (XXXXXXXXXX)" contiene un identificador de
 * cuenta/persona, NO el Team ID. La fuente autoritativa es siempre el
 * TeamIdentifier de un provisioning profile, o el OU (Organizational Unit) del
 * subject X.509 del certificado.
 */
final class AppleSigningUtils {

    private AppleSigningUtils() {}

    // ── Modelos de descubrimiento ────────────────────────────────────────────

    /** Un certificado de firma encontrado en el keychain, con su Team (OU) y validez. */
    record CertTeam(String teamId, String account, String certSha1, boolean valid) {}

    /** Un provisioning profile instalado, con su Team y si respalda WDA sin expirar. */
    record ProfileTeam(String teamId, String bundleId, boolean wdaMatch, boolean unexpired, File file) {}

    // ── Descubrimiento de certificados ───────────────────────────────────────

    /**
     * Enumera TODOS los certificados "Apple Development" e "iPhone Developer" (legado)
     * del keychain — a diferencia de `security find-certificate -c`, que solo devuelve
     * el primero. Cada uno se cruza contra `security find-identity -v -p codesigning`
     * (identidades de firma vigentes, no expiradas/revocadas) para determinar validez.
     */
    static List<CertTeam> discoverCertificateTeams() {
        List<CertTeam> result = new ArrayList<>();
        Set<String> validSha1s = listValidCodesigningSha1s();
        for (String certName : new String[]{"Apple Development", "iPhone Developer"}) {
            try {
                Process p = new ProcessBuilder("/bin/sh", "-c",
                        "security find-certificate -a -c '" + certName + "' -p 2>/dev/null")
                        .start();
                String pemDump = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor(15, TimeUnit.SECONDS);
                for (String block : splitPemBlocks(pemDump)) {
                    String[] info = subjectAndFingerprint(block);
                    if (info == null || info[0] == null) continue;
                    String subject = info[0];
                    String sha1    = info[1];
                    String ou = extractOuFromSubject(subject);
                    if (ou == null) continue;
                    String account = extractAccountFromSubject(subject);
                    boolean valid  = sha1 != null && validSha1s.contains(sha1);
                    result.add(new CertTeam(ou, account, sha1, valid));
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    /** SHA1 (sin ':', mayúsculas) de cada identidad de firma vigente en el keychain. */
    static Set<String> listValidCodesigningSha1s() {
        Set<String> out = new LinkedHashSet<>();
        try {
            Process p = new ProcessBuilder("security", "find-identity", "-v", "-p", "codesigning")
                    .redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(10, TimeUnit.SECONDS);
            Matcher m = Pattern.compile("\\)\\s+([0-9A-Fa-f]{40})\\s+\"").matcher(text);
            while (m.find()) out.add(m.group(1).toUpperCase());
        } catch (Exception ignored) {}
        return out;
    }

    private static List<String> splitPemBlocks(String pemDump) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile(
                "-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", Pattern.DOTALL).matcher(pemDump);
        while (m.find()) blocks.add(m.group());
        return blocks;
    }

    /** @return {subject, sha1SinDosPuntos} del bloque PEM, o null si openssl no pudo parsearlo. */
    private static String[] subjectAndFingerprint(String pemBlock) {
        try {
            Process p = new ProcessBuilder("openssl", "x509", "-noout", "-subject", "-fingerprint", "-sha1")
                    .start();
            try (var os = p.getOutputStream()) {
                os.write(pemBlock.getBytes(StandardCharsets.UTF_8));
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(10, TimeUnit.SECONDS);

            Matcher subjM = Pattern.compile("subject\\s*=\\s*(.+)").matcher(out);
            Matcher fpM   = Pattern.compile("SHA1 Fingerprint=([0-9A-Fa-f:]+)").matcher(out);
            String subject = subjM.find() ? subjM.group(1).trim() : null;
            String sha1    = fpM.find() ? fpM.group(1).replace(":", "").toUpperCase() : null;
            if (subject == null) return null;
            return new String[]{subject, sha1};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extrae ÚNICAMENTE el campo OU (Organizational Unit) de un subject X.509 —
     * jamás el CN, jamás el UID. Ambos formatos de openssl son soportados:
     *   legado (LibreSSL/macOS):  /UID=.../CN=.../OU=TEAMID/O=.../C=US
     *   RFC2253 (orden inverso):  CN=...,OU=TEAMID,O=...,C=US,UID=...
     */
    static String extractOuFromSubject(String subject) {
        Pattern slashOu = Pattern.compile("/OU=([A-Z0-9]{10})(?:/|$)");
        Matcher m = slashOu.matcher(subject);
        if (m.find()) return m.group(1);

        Pattern commaOu = Pattern.compile("(?:^|,\\s*)OU=([A-Z0-9]{10})(?:,|$)");
        Matcher mc = commaOu.matcher(subject);
        if (mc.find()) return mc.group(1);

        return null;
    }

    /**
     * Extrae la cuenta (email o nombre) del CN — solo para mostrar en logs, nunca
     * usado como Team ID. Formato: "Apple Development: user@email.com (TEAMID)" o
     * "iPhone Developer: Name (TEAMID)".
     */
    static String extractAccountFromSubject(String subject) {
        Matcher m = Pattern.compile(
                "CN=(?:Apple Development|iPhone Developer):\\s*([^(/,]+?)\\s*\\(").matcher(subject);
        return m.find() ? m.group(1).trim() : "cuenta desconocida";
    }

    // ── Descubrimiento de provisioning profiles ──────────────────────────────

    /** Enumera TODOS los .mobileprovision instalados, con su Team, bundle y expiración. */
    static List<ProfileTeam> discoverProfileTeams() {
        List<ProfileTeam> result = new ArrayList<>();
        for (File dir : provisioningProfileDirs()) {
            if (!dir.isDirectory()) continue;
            File[] profiles = dir.listFiles((d, n) -> n.endsWith(".mobileprovision"));
            if (profiles == null) continue;
            for (File f : profiles) {
                String plist = decodeProfilePlist(f);
                if (plist == null) continue;
                String teamId = extractPlistArrayFirstString(plist, "TeamIdentifier");
                if (teamId == null) continue;
                String bundleId = extractPlistArrayFirstString(plist, "application-identifier");
                boolean wdaMatch = bundleId != null && bundleId.contains("io.qautomation.wda");
                Instant exp = extractExpirationDate(plist);
                boolean unexpired = exp != null && Instant.now().isBefore(exp);
                result.add(new ProfileTeam(teamId, bundleId, wdaMatch, unexpired, f));
            }
        }
        return result;
    }

    static File[] provisioningProfileDirs() {
        return new File[]{
            new File(System.getProperty("user.home") + "/Library/MobileDevice/Provisioning Profiles"),
            new File(System.getProperty("user.home") + "/Library/Developer/Xcode/UserData/Provisioning Profiles"),
        };
    }

    /** Busca el embedded.mobileprovision más reciente entre los builds de WebDriverAgent en DerivedData. */
    static File findLatestWdaEmbeddedProfile() {
        File derivedDataRoot = new File(System.getProperty("user.home") + "/Library/Developer/Xcode/DerivedData");
        File[] wdaDirs = derivedDataRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("WebDriverAgent-"));
        if (wdaDirs == null || wdaDirs.length == 0) return null;

        File latest = null;
        for (File wdaDir : wdaDirs) {
            File productsDir = new File(wdaDir, "Build/Products");
            if (!productsDir.isDirectory()) continue;
            File[] suiteDirs = productsDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("Debug-iphoneos"));
            if (suiteDirs == null) continue;
            for (File suiteDir : suiteDirs) {
                File profile = new File(suiteDir, "WebDriverAgentRunner-Runner.app/embedded.mobileprovision");
                if (profile.isFile() && (latest == null || profile.lastModified() > latest.lastModified())) {
                    latest = profile;
                }
            }
        }
        return latest;
    }

    // ── Parseo de plist (.mobileprovision) ───────────────────────────────────

    /** Decodifica un .mobileprovision (CMS-signed) a su plist XML en texto plano. */
    static String decodeProfilePlist(File profile) {
        try {
            Process p = new ProcessBuilder("security", "cms", "-D", "-i", profile.getAbsolutePath())
                    .redirectErrorStream(false).start();
            String plist = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return (done && !plist.isBlank()) ? plist : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Devuelve el primer &lt;string&gt; que sigue a &lt;key&gt;{key}&lt;/key&gt; — sirve tanto para
     *  valores planos (application-identifier) como para el primer elemento de un &lt;array&gt;
     *  (TeamIdentifier). */
    static String extractPlistArrayFirstString(String plist, String key) {
        String marker = "<key>" + key + "</key>";
        int keyIdx = plist.indexOf(marker);
        if (keyIdx < 0) return null;
        int from = keyIdx + marker.length();
        int to   = Math.min(plist.length(), from + 500);
        Matcher m = Pattern.compile("<string>([^<]+)</string>").matcher(plist.substring(from, to));
        return m.find() ? m.group(1).trim() : null;
    }

    static Instant extractExpirationDate(String plist) {
        String marker = "<key>ExpirationDate</key>";
        int keyIdx = plist.indexOf(marker);
        if (keyIdx < 0) return null;
        int from = keyIdx + marker.length();
        int to   = Math.min(plist.length(), from + 200);
        Matcher m = Pattern.compile("<date>([^<]+)</date>").matcher(plist.substring(from, to));
        if (!m.find()) return null;
        try {
            return Instant.parse(m.group(1).trim());
        } catch (Exception e) {
            return null;
        }
    }

    static boolean containsProvisionedDevice(String plist, String udid) {
        int keyIdx = plist.indexOf("<key>ProvisionedDevices</key>");
        if (keyIdx < 0) return false;
        int endIdx = plist.indexOf("</array>", keyIdx);
        if (endIdx < 0) endIdx = plist.length();
        return plist.substring(keyIdx, Math.min(plist.length(), endIdx)).contains(udid);
    }

    /** SHA1 (hex, mayúsculas) del primer certificado embebido en DeveloperCertificates. */
    static String extractFirstDeveloperCertificateSha1(String plist) {
        int keyIdx = plist.indexOf("<key>DeveloperCertificates</key>");
        if (keyIdx < 0) return null;
        int dataStart = plist.indexOf("<data>", keyIdx);
        if (dataStart < 0) return null;
        int dataEnd = plist.indexOf("</data>", dataStart);
        if (dataEnd < 0) return null;
        String base64 = plist.substring(dataStart + "<data>".length(), dataEnd).replaceAll("\\s+", "");
        try {
            byte[] der  = Base64.getDecoder().decode(base64);
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(der);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** ¿Sigue esta huella SHA1 entre las identidades de firma válidas del keychain? */
    static boolean isCertificateCurrentlyValid(String sha1) {
        return sha1 != null && listValidCodesigningSha1s().contains(sha1.toUpperCase());
    }

    static String shortSha(String sha1) {
        if (sha1 == null || sha1.isBlank()) return "desconocido";
        return sha1.substring(0, Math.min(12, sha1.length())) + "...";
    }
}
