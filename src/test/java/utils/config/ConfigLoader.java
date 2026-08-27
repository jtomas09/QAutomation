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
 *      opcionales) — solo se usan si HOST/PORT/USER/FROM están presentes.
 *      SMTP_PASS admite dos orígenes: la variable de entorno misma, o —si no
 *      está definida— el Keychain de macOS (cuenta=SMTP_USER, servicio
 *      "automationqa-smtp"), para que la contraseña nunca necesite existir
 *      como texto plano en el .plist ni en ningún archivo.
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
        String from = env("SMTP_FROM", null);

        if (isBlank(host) || isBlank(port) || isBlank(user) || isBlank(from)) {
            return null;
        }

        String pass = resolveSmtpPass(user);
        if (isBlank(pass)) {
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

    /**
     * SMTP_PASS: primero la variable de entorno (compatibilidad con quien la
     * defina así); si no está, se busca en el Keychain de macOS. Así la
     * contraseña real puede vivir fuera del .plist por completo.
     */
    private static String resolveSmtpPass(String user) {
        String pass = env("SMTP_PASS", null);
        return !isBlank(pass) ? pass : readPassFromKeychain(user);
    }

    // ── Keychain de macOS — fuente opcional y preferida para SMTP_PASS ──────

    private static final String KEYCHAIN_SERVICE = "automationqa-smtp";

    /**
     * Lee la contraseña SMTP desde el Keychain (cifrado por el sistema
     * operativo, nunca un archivo de texto plano) — cuenta = SMTP_USER,
     * servicio = "automationqa-smtp". Si `security` no existe (no-macOS) o
     * no hay entrada guardada, retorna null silenciosamente: no es un error,
     * solo significa que esta fuente no aplica y se sigue con las demás.
     */
    private static String readPassFromKeychain(String account) {
        if (isBlank(account)) return null;
        try {
            Process p = new ProcessBuilder(
                    "security", "find-generic-password",
                    "-a", account, "-s", KEYCHAIN_SERVICE, "-w")
                    .redirectErrorStream(false)
                    .start();

            String out;
            try (var in = p.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.warn("[SMTP] Lectura de Keychain excedió el tiempo de espera (cuenta={})", account);
                return null;
            }
            return (p.exitValue() == 0 && !out.isBlank()) ? out : null;
        } catch (Exception e) {
            log.warn("[SMTP] No se pudo leer SMTP_PASS desde Keychain (cuenta={}): {}", account, e.getMessage());
            return null;
        }
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

    // ── Diagnóstico seguro (nunca expone valores, solo OK/MISSING) ──────────

    /**
     * Reporte de estado en UNA sola línea, formato {@code CAMPO=OK} /
     * {@code CAMPO=MISSING} — nunca el valor real de ningún campo (ni siquiera
     * host/user/from, aunque no sean secretos, para mantener un único formato
     * consistente en todos los logs de diagnóstico SMTP).
     *
     * Comprueba PRIMERO la variable de entorno cruda y, si no está definida, el
     * valor final ya cargado en {@code cfg} (que puede venir del archivo). Esto
     * importa porque {@link #tryLoadFromEnv()} es todo-o-nada (ver su comentario:
     * si falta UNA de las obligatorias, se descarta el env por completo y se
     * cae a smtp-config.json) — sin esta doble verificación, definir el resto
     * de las variables de entorno reportaría erróneamente todas como MISSING
     * en vez de señalar la única que realmente falta.
     *
     * Para SMTP_PASS específicamente se comprueba además el Keychain de macOS
     * (misma cuenta=SMTP_USER que usa {@link #readPassFromKeychain}) — así el
     * reporte es preciso incluso cuando la contraseña vive solo ahí.
     */
    public static String reporteEstado(SmtpConfig cfg, String mailTo) {
        SmtpConfig.Smtp smtp = cfg != null ? cfg.smtp : null;
        return String.format(
                "SMTP_HOST=%s SMTP_PORT=%s SMTP_USER=%s SMTP_PASS=%s SMTP_FROM=%s MAIL_TO=%s",
                estado(env("SMTP_HOST", null), smtp != null ? smtp.host : null),
                estado(env("SMTP_PORT", null), smtp != null ? smtp.port : null),
                estado(env("SMTP_USER", null), smtp != null ? smtp.user : null),
                estado(env("SMTP_PASS", null), readPassFromKeychain(env("SMTP_USER", null)), smtp != null ? smtp.pass : null),
                estado(env("SMTP_FROM", null), cfg != null ? cfg.resolvedFrom() : null),
                estado(mailTo, null));
    }

    private static String estado(String... candidatos) {
        for (String c : candidatos) {
            if (c != null && !c.isBlank()) return "OK";
        }
        return "MISSING";
    }
}
