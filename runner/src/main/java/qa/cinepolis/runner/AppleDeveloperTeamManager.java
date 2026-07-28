package qa.cinepolis.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Selección automática, persistente y multi-cuenta del Apple Developer Team
 * usado para firmar WebDriverAgent.
 *
 * Reemplaza el antiguo enfoque de IosPreflightManager.detectAppleTeamId(), que
 * asumía una única cuenta Apple en la Mac y devolvía el PRIMER Team ID que
 * encontraba entre 4 estrategias en cascada — sin enumerar todos los Teams
 * existentes, sin persistencia entre ejecuciones y sin validar consistencia
 * cruzada entre certificado/perfil/Team.
 *
 * Problema real que motiva esto: una misma Mac puede tener varios Apple ID
 * logueados en Xcode a lo largo del tiempo (varias cuentas → varios Team ID).
 * Si el Team usado para compilar y firmar WebDriverAgent cambia entre
 * ejecuciones, iOS rechaza la reinstalación con MInstallerErrorDomain Code 64 /
 * MismatchedApplicationIdentifierEntitlement, porque el application-identifier
 * del binario incluye el Team ID como prefijo.
 *
 * Refinamiento posterior (certificados huérfanos): un certificado puede seguir
 * criptográficamente vigente en el Keychain mucho después de que su cuenta
 * Apple fue removida de Xcode → Settings → Accounts. En ese caso Xcode
 * responde "No Account for Team X" al compilar, aunque `security find-identity`
 * lo siga listando como válido. Por eso "certificado válido" ya NO significa
 * únicamente vigencia criptográfica — significa "utilizable": vigente en
 * Keychain Y con una cuenta Apple actualmente autenticada en Xcode, verificado
 * vía AppleDeveloperAccountProvider (XcodeAccountProvider es la única
 * implementación — esta clase nunca conoce el plist de Xcode directamente).
 *
 * Política de selección (en orden — la primera regla que aplica gana):
 *  1. Team configurado explícitamente por el usuario (userOverrideTeamId) —
 *     puede apuntar a cualquier Team descubierto, incluso uno huérfano, porque
 *     es una decisión humana explícita, no automática.
 *  2. Team persistido de una ejecución anterior, SI sigue siendo utilizable
 *     hoy (certificado utilizable o provisioning profile vigente) — si dejó
 *     de serlo, se redescubre automáticamente sin intervención manual.
 *  3. Team respaldado por un provisioning profile io.qautomation.wda.*
 *     vigente (sin expirar).
 *  4. Team respaldado por un certificado UTILIZABLE (vigente + autenticado en
 *     Xcode).
 *  5. Único Team utilizable detectado en la Mac (bootstrap en una Mac nueva).
 * Un Team cuyo ÚNICO respaldo es un certificado huérfano (ORPHAN_CERTIFICATE)
 * nunca participa en las reglas 2-5 — solo un override explícito puede
 * forzarlo.
 *
 * Persistencia: ~/.qautomation/apple-team.properties — global, no por-UDID,
 * ya que el Team es una propiedad de la cuenta/Mac, no del dispositivo iOS.
 *
 * Se invoca desde IosPreflightManager.detectAppleTeamId(), el único call site
 * existente, en cada preflight — real (JobExecutor) u on-demand (Mirror) — por
 * lo que el redescubrimiento/cambio automático ocurre sin ningún watcher o
 * polling adicional. No toca Android, DriverFactory, JobExecutor ni el Mirror.
 */
final class AppleDeveloperTeamManager {

    private static final File CONFIG_FILE =
            new File(System.getProperty("user.home") + "/.qautomation/apple-team.properties");

    /** Único punto de acceso a "qué Team tiene cuenta autenticada en Xcode ahora". */
    private static final AppleDeveloperAccountProvider accountProvider = new XcodeAccountProvider();

    private AppleDeveloperTeamManager() {}

    record TeamCandidate(String teamId, String account, boolean certUsable, boolean hasValidWdaProfile) {}

    private record Selection(TeamCandidate candidate, String reason) {}

    /** Un certificado encontrado en Keychain, ya cruzado contra las cuentas autenticadas en Xcode. */
    private record CertLogEntry(String account, String teamId, boolean usable, String note) {}

    static String selectTeam(BackendClient client, String executionId) {
        AppleDeveloperAccountProvider.DiscoveryResult xcodeAccounts = accountProvider.discoverAuthenticatedTeams();
        logXcodeAccounts(client, executionId, xcodeAccounts);

        List<TeamCandidate> allCandidates = discoverCandidates(client, executionId, xcodeAccounts);

        if (allCandidates.isEmpty()) {
            client.sendLog(executionId, "WARN",
                    "⚠️  Team ID no encontrado por ninguna estrategia.\n"
                    + "   WDA no podrá firmarse sin Team ID en dispositivos físicos.\n"
                    + "   Solución: Xcode → Settings → Accounts → agrega tu Apple ID\n"
                    + "   y descarga tus certificados de desarrollo.");
            return "";
        }

        // Un Team respaldado ÚNICAMENTE por un certificado huérfano no participa en
        // ninguna regla automática (2-5) — solo un override explícito puede forzarlo.
        List<TeamCandidate> selectable = allCandidates.stream()
                .filter(c -> c.certUsable() || c.hasValidWdaProfile())
                .toList();

        Properties config     = loadConfig();
        String userOverride   = config.getProperty("userOverrideTeamId", "").trim();
        String previouslyUsed = config.getProperty("selectedTeamId", "").trim();

        Selection selection = null;

        // 1. Override explícito del usuario — puede apuntar a cualquier Team descubierto
        if (!userOverride.isBlank()) {
            TeamCandidate match = findById(allCandidates, userOverride);
            if (match != null) {
                selection = new Selection(match, "Configuración explícita del usuario");
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️  userOverrideTeamId=" + userOverride + " configurado en "
                        + CONFIG_FILE.getAbsolutePath() + ", pero ese Team ya no está disponible en esta Mac. Se ignora.");
            }
        }

        // 2. Team persistido de una ejecución anterior, si sigue siendo utilizable
        if (selection == null && !previouslyUsed.isBlank()) {
            TeamCandidate match = findById(selectable, previouslyUsed);
            if (match != null) {
                selection = new Selection(match, "Configuración persistida");
            } else if (findById(allCandidates, previouslyUsed) == null) {
                client.sendLog(executionId, "WARN",
                        "⚠️  Team persistido (" + previouslyUsed + ") ya no existe en esta Mac — redescubriendo automáticamente...");
            } else {
                client.sendLog(executionId, "WARN",
                        "⚠️  Team persistido (" + previouslyUsed + ") ya no tiene certificado utilizable ni provisioning "
                        + "profile válido (¿cuenta removida de Xcode?) — redescubriendo automáticamente...");
            }
        }

        // 3. Team respaldado por un provisioning profile io.qautomation.wda.* vigente
        if (selection == null) {
            TeamCandidate match = selectable.stream().filter(TeamCandidate::hasValidWdaProfile).findFirst().orElse(null);
            if (match != null) selection = new Selection(match, "Provisioning Profile válido");
        }

        // 4. Team respaldado por un certificado UTILIZABLE (vigente + autenticado en Xcode)
        if (selection == null) {
            List<TeamCandidate> usable = selectable.stream().filter(TeamCandidate::certUsable).toList();
            if (!usable.isEmpty()) {
                boolean xcodeFilterApplied = xcodeAccounts.available();
                String reason = usable.size() == 1
                        ? (xcodeFilterApplied ? "Único Team autenticado en Xcode" : "Certificado válido en Keychain")
                        : "Certificado válido";
                selection = new Selection(usable.get(0), reason);
                if (usable.size() > 1) {
                    client.sendLog(executionId, "WARN",
                            "⚠️  Más de un Team tiene certificado utilizable — se eligió " + usable.get(0).teamId()
                            + " (el primero detectado). Configura userOverrideTeamId en "
                            + CONFIG_FILE.getAbsolutePath() + " si prefieres otro.");
                }
            }
        }

        // 5. Único Team utilizable disponible (bootstrap en Mac nueva)
        if (selection == null && selectable.size() == 1) {
            selection = new Selection(selectable.get(0), "Único Team disponible");
        }

        if (selection == null) {
            client.sendLog(executionId, "WARN",
                    "⚠️  " + allCandidates.size() + " Team(s) detectados, pero ninguno tiene certificado utilizable "
                    + "ni provisioning profile válido — probablemente todos son certificados huérfanos "
                    + "(cuentas ya removidas de Xcode). Revisa Xcode → Settings → Accounts, o configura "
                    + "userOverrideTeamId en " + CONFIG_FILE.getAbsolutePath() + ".");
            return "";
        }

        boolean changed = !previouslyUsed.isBlank() && !selection.candidate().teamId().equals(previouslyUsed);
        persist(selection.candidate(), selection.reason());

        client.sendTechLog(executionId,
                "──────────────────────────────\n"
                + "Team seleccionado\n\n"
                + selection.candidate().teamId()
                + (changed ? "  (cambió automáticamente desde " + previouslyUsed + ")" : "") + "\n\n"
                + "Motivo\n"
                + selection.reason());

        return selection.candidate().teamId();
    }

    private static TeamCandidate findById(List<TeamCandidate> candidates, String teamId) {
        return candidates.stream().filter(c -> c.teamId().equals(teamId)).findFirst().orElse(null);
    }

    // ── Descubrimiento unificado ──────────────────────────────────────────────

    private static List<TeamCandidate> discoverCandidates(
            BackendClient client, String executionId, AppleDeveloperAccountProvider.DiscoveryResult xcodeAccounts) {

        Map<String, String>  accountByTeam    = new LinkedHashMap<>();
        Map<String, Boolean> certUsableByTeam = new LinkedHashMap<>();
        Map<String, Boolean> wdaProfileByTeam = new LinkedHashMap<>();
        List<CertLogEntry>   certLog          = new ArrayList<>();

        for (AppleSigningUtils.CertTeam ct : AppleSigningUtils.discoverCertificateTeams()) {
            accountByTeam.putIfAbsent(ct.teamId(), ct.account());

            boolean usable;
            String  note = null;
            if (!ct.valid()) {
                usable = false;
                note   = "Certificado no vigente en el Keychain";
            } else if (!xcodeAccounts.available()) {
                // Degradación resiliente: sin registro de Xcode disponible, se conserva
                // el comportamiento anterior (solo vigencia criptográfica).
                usable = true;
            } else if (xcodeAccounts.isTeamAuthenticated(ct.teamId())) {
                usable = true;
            } else {
                usable = false;
                note   = "El Team ya no está autenticado en Xcode";
            }

            certUsableByTeam.merge(ct.teamId(), usable, Boolean::logicalOr);
            certLog.add(new CertLogEntry(ct.account(), ct.teamId(), usable, note));
        }

        for (AppleSigningUtils.ProfileTeam pt : AppleSigningUtils.discoverProfileTeams()) {
            accountByTeam.putIfAbsent(pt.teamId(), "cuenta desconocida (solo provisioning profile)");
            boolean validWda = pt.wdaMatch() && pt.unexpired();
            wdaProfileByTeam.merge(pt.teamId(), validWda, Boolean::logicalOr);
        }

        logCertificates(client, executionId, certLog);

        List<TeamCandidate> result = new ArrayList<>();
        for (String teamId : accountByTeam.keySet()) {
            result.add(new TeamCandidate(
                    teamId,
                    accountByTeam.get(teamId),
                    certUsableByTeam.getOrDefault(teamId, false),
                    wdaProfileByTeam.getOrDefault(teamId, false)));
        }
        return result;
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    private static void logXcodeAccounts(
            BackendClient client, String executionId, AppleDeveloperAccountProvider.DiscoveryResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("🍎 Apple Developer Discovery\n");
        sb.append("──────────────────────────────\n");
        sb.append("Cuentas autenticadas en Xcode\n\n");

        if (!r.available()) {
            sb.append("⚠ No se pudo determinar — ").append(r.unavailableReason()).append('\n');
            sb.append("   Se usará únicamente la vigencia del certificado en Keychain, como antes.");
            client.sendLog(executionId, "WARN", sb.toString());
            return;
        }
        if (r.teamNamesById().isEmpty()) {
            sb.append("⚠ Ninguna cuenta autenticada detectada en Xcode");
        } else {
            r.teamNamesById().forEach((teamId, name) ->
                    sb.append("✔ Team: ").append(teamId).append(" (").append(name).append(")\n"));
        }
        client.sendTechLog(executionId, sb.toString().stripTrailing());
    }

    private static void logCertificates(BackendClient client, String executionId, List<CertLogEntry> entries) {
        if (entries.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("──────────────────────────────\n");
        sb.append("Certificados encontrados\n\n");
        for (CertLogEntry e : entries) {
            sb.append(e.usable() ? "✔" : "⚠").append(" Apple Development\n");
            sb.append("   Cuenta: ").append(e.account()).append('\n');
            sb.append("   Team: ").append(e.teamId()).append('\n');
            sb.append("   Estado: ").append(e.usable() ? "USABLE" : "ORPHAN_CERTIFICATE").append('\n');
            if (e.note() != null) sb.append("   Motivo: ").append(e.note()).append('\n');
            sb.append('\n');
        }
        client.sendTechLog(executionId, sb.toString().stripTrailing());
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    private static Properties loadConfig() {
        Properties p = new Properties();
        if (CONFIG_FILE.isFile()) {
            try (InputStream in = new FileInputStream(CONFIG_FILE)) {
                p.load(in);
            } catch (Exception ignored) {}
        }
        return p;
    }

    private static void persist(TeamCandidate candidate, String reason) {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            Properties p = loadConfig(); // preserva userOverrideTeamId si ya estaba configurado
            p.setProperty("selectedTeamId",     candidate.teamId());
            p.setProperty("selectedAccount",    candidate.account());
            p.setProperty("selectedReason",     reason);
            p.setProperty("selectedAtMs",       String.valueOf(System.currentTimeMillis()));
            p.setProperty("userOverrideTeamId", p.getProperty("userOverrideTeamId", ""));
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                p.store(out, "QAutomation Apple Developer Team - seleccion automatica. "
                        + "Deja userOverrideTeamId vacio para seleccion automatica, "
                        + "o pon un Team ID explicito para forzarlo.");
            }
        } catch (Exception e) {
            System.err.println("[AppleDeveloperTeamManager] No se pudo persistir seleccion: " + e.getMessage());
        }
    }
}
