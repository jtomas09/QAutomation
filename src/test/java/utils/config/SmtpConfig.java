package utils.config;
import java.util.List;

/**
 * Estructura de config/smtp-config.json. "mail" se conserva únicamente por
 * compatibilidad con archivos generados por versiones anteriores (from/to
 * anidados ahí) — la plantilla nueva usa smtp.from directamente y ya no
 * necesita smtp.to (los destinatarios ahora vienen de ReportEmailStore /
 * MAIL_TO, resueltos en tiempo de ejecución, no en este archivo estático).
 */
public class SmtpConfig {
    public Smtp smtp;
    public Mail mail;

    public static class Smtp {
        public String  host;
        public String  port;
        public String  user;
        public String  pass;
        public String  from;
        public Boolean tls;
        public Boolean ssl;
    }

    public static class Mail {
        public String       from;
        public List<String> to;
    }

    /** true cuando hay suficiente información para intentar una conexión SMTP real. */
    public boolean isValid() {
        return smtp != null
                && notBlank(smtp.host)
                && notBlank(smtp.user)
                && notBlank(smtp.pass)
                && notBlank(resolvedFrom());
    }

    /** smtp.from (plantilla nueva) con fallback a mail.from (archivos antiguos). */
    public String resolvedFrom() {
        if (smtp != null && notBlank(smtp.from)) return smtp.from;
        if (mail != null && notBlank(mail.from)) return mail.from;
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

