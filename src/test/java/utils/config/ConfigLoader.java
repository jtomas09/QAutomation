package utils.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Carga la configuración SMTP con un orden de prioridad claro y sin fallos
 * silenciosos — cada resultado (éxito o vacío) queda registrado con un
 * mensaje [SMTP] explícito, nunca solo una excepción genérica:
 *
 *   1. Variables de entorno (SMTP_HOST/PORT/USER/PASS/FROM, + SMTP_TLS/SMTP_SSL
 *      opcionales) — solo se usan si las 5 obligatorias están presentes.
 *   2. config/smtp-config.json (filesystem, relativo a la raíz del proyecto)
 *      — si no existe, se genera una plantilla vacía UNA sola vez y se avisa
 *      qué falta completar; nunca se sobrescribe un archivo ya existente.
 *
 * Los destinatarios (MAIL_TO / ReportEmailStore) son un dato dinámico por
 * ejecución y se resuelven aparte, en AllureReportSender — no viven en este
 * archivo estático de credenciales del servidor SMTP.
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String CONFIG_RELATIVE_PATH = "config/smtp-config.json";

    private static final String TEMPLATE_JSON =
            "{\n" +
            "  \"smtp\": {\n" +
            "    \"host\": \"\",\n" +
            "    \"port\": 587,\n" +
            "    \"user\": \"\",\n" +
            "    \"pass\": \"\",\n" +
            "    \"from\": \"\",\n" +
            "    \"tls\": true,\n" +
            "    \"ssl\": false\n" +
            "  }\n" +
            "}\n";

    private static SmtpConfig smtpConfig;

    public static SmtpConfig getSmtpConfig() {
        if (smtpConfig != null) return smtpConfig;

        SmtpConfig fromEnv = tryLoadFromEnv();
        if (fromEnv != null) {
            log.info("[SMTP] Configuración cargada desde variables de entorno");
            smtpConfig = fromEnv;
            return smtpConfig;
        }

        smtpConfig = loadFromFile();
        return smtpConfig;
    }

    /** Fuerza una nueva lectura en la próxima llamada — usado por tests/diagnóstico. */
    public static void reset() {
        smtpConfig = null;
    }

    // ── 1) Variables de entorno ─────────────────────────────────────────────

    /**
     * Devuelve una config solo si las 5 variables sin default razonable
     * (host/port/user/pass/from) están TODAS presentes. TLS/SSL son opcionales
     * (true/false por defecto) — exigirlas también obligaría a declarar dos
     * variables casi siempre iguales al default solo para poder usar el resto.
     * Si falta alguna de las 5 obligatorias, se cae a smtp-config.json completo
     * (no se mezclan fuentes a medias — evita una configuración parcialmente
     * de env y parcialmente de archivo, difícil de depurar).
     */
    private static SmtpConfig tryLoadFromEnv() {
        String host = env("SMTP_HOST", null);
        String port = env("SMTP_PORT", null);
        String user = env("SMTP_USER", null);
        String pass = env("SMTP_PASS", null);
        String from = env("SMTP_FROM", null);

        if (isBlank(host) || isBlank(port) || isBlank(user) || isBlank(pass) || isBlank(from)) {
            return null;
        }

        SmtpConfig cfg = new SmtpConfig();
        cfg.smtp      = new SmtpConfig.Smtp();
        cfg.smtp.host = host;
        cfg.smtp.port = port;
        cfg.smtp.user = user;
        cfg.smtp.pass = pass;
        cfg.smtp.from = from;
        cfg.smtp.tls  = Boolean.parseBoolean(env("SMTP_TLS", "true"));
        cfg.smtp.ssl  = Boolean.parseBoolean(env("SMTP_SSL", "false"));
        return cfg;
    }

    // ── 2) config/smtp-config.json ──────────────────────────────────────────

    private static SmtpConfig loadFromFile() {
        Path configPath = resolveProjectRoot().resolve(CONFIG_RELATIVE_PATH);

        if (!Files.exists(configPath)) {
            boolean created = createTemplateIfAbsent(configPath);
            if (created) {
                log.warn("[SMTP] smtp-config.json no encontrado. Archivo generado automáticamente en: {}",
                        configPath.toAbsolutePath());
                log.warn("[SMTP] Complete host/user/pass/from en {} o defina las variables de entorno "
                        + "SMTP_HOST/SMTP_PORT/SMTP_USER/SMTP_PASS/SMTP_FROM.", configPath.toAbsolutePath());
            } else {
                log.error("[SMTP] No existe configuración SMTP válida. El envío de correo será omitido. "
                        + "No se pudo crear la plantilla en: {}", configPath.toAbsolutePath());
            }
            return emptyConfig();
        }

        SmtpConfig cfg;
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            byte[] bytes = Files.readAllBytes(configPath);
            cfg = mapper.readValue(bytes, SmtpConfig.class);
            if (cfg.smtp == null) cfg.smtp = new SmtpConfig.Smtp();
        } catch (Exception e) {
            // Nunca solo la excepción cruda: siempre acompañada de la ruta y el motivo probable.
            log.error("[SMTP] No se pudo leer {} (JSON inválido o ilegible): {}. "
                    + "El envío de correo será omitido.", configPath.toAbsolutePath(), e.getMessage(), e);
            return emptyConfig();
        }

        try {
            cfg.smtp.pass = PasswordEncryptor.decryptIfEncrypted(cfg.smtp.pass);
        } catch (Exception e) {
            log.error("[SMTP] No se pudo descifrar smtp.pass en {}: {}. "
                    + "El envío de correo será omitido.", configPath.toAbsolutePath(), e.getMessage(), e);
            return emptyConfig();
        }

        if (cfg.isValid()) {
            log.info("[SMTP] Configuración cargada desde smtp-config.json ({})", configPath.toAbsolutePath());
        } else {
            log.error("[SMTP] No existe configuración SMTP válida. El envío de correo será omitido. "
                    + "Complete host/user/pass/from en: {}", configPath.toAbsolutePath());
        }
        return cfg;
    }

    /** Crea la plantilla solo si el archivo no existe todavía — nunca sobrescribe uno existente. */
    private static boolean createTemplateIfAbsent(Path configPath) {
        try {
            if (Files.exists(configPath)) return true; // otro hilo/proceso ya lo creó
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, TEMPLATE_JSON, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            return true;
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return true; // condición de carrera benigna: ya existe, no se sobrescribe
        } catch (Exception e) {
            log.error("[SMTP] Error creando plantilla {}: {}", configPath.toAbsolutePath(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Resuelve la raíz del proyecto buscando gradlew(.bat) hacia arriba desde el
     * directorio de trabajo actual — mismo criterio ya usado en AllureReportSender
     * para funcionar tanto en `gradlew test` (cwd = raíz) como en el ejecutable
     * empaquetado del Runner (cwd = build/launch4j/).
     */
    private static Path resolveProjectRoot() {
        String override = System.getProperty("cinepolis.project.root");
        File start = new File(override != null && !override.isBlank() ? override : System.getProperty("user.dir"));

        File candidate = start;
        while (candidate != null
                && !new File(candidate, "gradlew").exists()
                && !new File(candidate, "gradlew.bat").exists()) {
            candidate = candidate.getParentFile();
        }
        return (candidate != null ? candidate : start).toPath();
    }

    private static SmtpConfig emptyConfig() {
        SmtpConfig cfg = new SmtpConfig();
        cfg.smtp = new SmtpConfig.Smtp();
        return cfg;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
