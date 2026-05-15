package utils.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Arrays;

public class ConfigLoader {
    private static final String CONFIG_PATH = "config/smtp-config.json";
    private static SmtpConfig smtpConfig;

    public static SmtpConfig getSmtpConfig() {
        if (smtpConfig != null) return smtpConfig;

        // Prioridad 1: variables de entorno (Railway, CI/CD)
        // Configurar en Railway Dashboard → Variables:
        //   SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, MAIL_FROM, MAIL_TO
        String envHost = System.getenv("SMTP_HOST");
        if (envHost != null && !envHost.isBlank()) {
            smtpConfig = new SmtpConfig();
            smtpConfig.smtp      = new SmtpConfig.Smtp();
            smtpConfig.smtp.host = envHost;
            smtpConfig.smtp.port = env("SMTP_PORT", "587");
            smtpConfig.smtp.user = env("SMTP_USER", "");
            smtpConfig.smtp.pass = env("SMTP_PASS", "");
            smtpConfig.mail      = new SmtpConfig.Mail();
            smtpConfig.mail.from = env("MAIL_FROM", "");
            String to = env("MAIL_TO", "");
            smtpConfig.mail.to   = to.isBlank()
                    ? java.util.Collections.emptyList()
                    : Arrays.asList(to.split(","));
            return smtpConfig;
        }

        // Prioridad 2: smtp-config.json (desarrollo local)
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CONFIG_PATH)) {

            if (is == null) {
                throw new RuntimeException(
                        "No se encontró '" + CONFIG_PATH + "' en el classpath.\n" +
                        "Opciones:\n" +
                        "  A) Crea src/test/resources/" + CONFIG_PATH + " (ver smtp-config.json.example)\n" +
                        "  B) Define las variables de entorno SMTP_HOST, SMTP_USER, SMTP_PASS, MAIL_FROM, MAIL_TO"
                );
            }

            smtpConfig = new ObjectMapper().readValue(is, SmtpConfig.class);

            if (smtpConfig.smtp != null) {
                smtpConfig.smtp.pass = PasswordEncryptor.decryptIfEncrypted(smtpConfig.smtp.pass);
            }

            return smtpConfig;

        } catch (Exception e) {
            throw new RuntimeException("Error leyendo " + CONFIG_PATH, e);
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}


