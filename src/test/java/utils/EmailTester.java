package utils;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import utils.config.ConfigLoader;
import utils.config.SmtpConfig;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Herramienta de diagnóstico para verificar la configuración SMTP.
 * Uso: .\gradlew.bat testEmail
 */
public class EmailTester {

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================");
        System.out.println("  TEST DE CORREO — Diagnóstico SMTP");
        System.out.println("====================================================");

        // 1. Leer configuración
        SmtpConfig cfg;
        try {
            cfg = ConfigLoader.getSmtpConfig();
            System.out.println("[OK] smtp-config.json leído correctamente");
        } catch (Exception e) {
            System.out.println("[ERROR] No se pudo leer smtp-config.json: " + e.getMessage());
            System.exit(1);
            return;
        }

        String smtpHost = safe(cfg.smtp.host, "email-smtp.us-east-1.amazonaws.com");
        String smtpPort = safe(cfg.smtp.port, "587");
        String smtpUser = safe(cfg.smtp.user, "");
        String smtpPass = safe(cfg.smtp.pass, "");
        String from     = safe(cfg.mail.from, "automation_android@ia.com.mx");

        // Destinatario: MAIL_TO env var > primer destinatario en config > from
        String mailToEnv = System.getenv("MAIL_TO");
        String to;
        if (mailToEnv != null && !mailToEnv.isBlank()) {
            to = mailToEnv.trim();
            System.out.println("[INFO] Usando MAIL_TO env var: " + to);
        } else if (cfg.mail.to != null && !cfg.mail.to.isEmpty()) {
            to = cfg.mail.to.get(0).trim();
            System.out.println("[INFO] Usando primer destinatario de config: " + to);
        } else {
            to = from;
            System.out.println("[WARN] Sin destinatarios configurados; enviando al remitente: " + to);
        }

        System.out.println("----------------------------------------------------");
        System.out.println("  Host SMTP : " + smtpHost);
        System.out.println("  Puerto    : " + smtpPort);
        System.out.println("  Usuario   : " + smtpUser);
        System.out.println("  Password  : " + (smtpPass.isBlank() ? "[VACÍA - ERROR]" : "****** (configurada)"));
        System.out.println("  From      : " + from);
        System.out.println("  To        : " + to);
        System.out.println("----------------------------------------------------");

        if (smtpPass.isBlank()) {
            System.out.println("[ERROR] La contraseña SMTP está vacía. Verifica smtp-config.json.");
            System.exit(1);
        }

        // 2. Conectar y enviar
        System.out.println("[INFO] Intentando conectar al servidor SMTP...");

        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");

        final String finalPass = smtpPass;
        final String finalUser = smtpUser;
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(finalUser.trim(), finalPass.trim());
            }
        });
        session.setDebug(false);

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("✅ Test SMTP — Automation QA Cinépolis", "UTF-8");

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String html = "<html><body style='font-family:Arial;color:#222;'>"
                    + "<h2 style='color:#2ea043;'>✅ Correo de prueba exitoso</h2>"
                    + "<p>Este correo confirma que la configuración SMTP del sistema de Automation QA está funcionando correctamente.</p>"
                    + "<table border='1' cellpadding='8' style='border-collapse:collapse;'>"
                    + "<tr><td><b>Host</b></td><td>" + smtpHost + ":" + smtpPort + "</td></tr>"
                    + "<tr><td><b>Usuario</b></td><td>" + smtpUser + "</td></tr>"
                    + "<tr><td><b>Remitente</b></td><td>" + from + "</td></tr>"
                    + "<tr><td><b>Generado</b></td><td>" + now + "</td></tr>"
                    + "</table></body></html>";

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html; charset=UTF-8");
            Multipart mp = new MimeMultipart();
            mp.addBodyPart(htmlPart);
            message.setContent(mp);

            Transport.send(message);
            System.out.println("====================================================");
            System.out.println("  [ÉXITO] Correo enviado correctamente a: " + to);
            System.out.println("====================================================");

        } catch (AuthenticationFailedException e) {
            System.out.println("====================================================");
            System.out.println("  [ERROR] Autenticación SMTP fallida");
            System.out.println("  Verifica que el usuario y contraseña sean correctos.");
            System.out.println("  Para AWS SES: la contraseña SMTP es diferente a la");
            System.out.println("  clave secreta de IAM. Se genera en la consola de SES.");
            System.out.println("  Detalle: " + e.getMessage());
            System.out.println("====================================================");
            System.exit(1);
        } catch (MessagingException e) {
            System.out.println("====================================================");
            System.out.println("  [ERROR] Fallo de conexión SMTP: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Causa: " + e.getCause().getMessage());
            }
            System.out.println("====================================================");
            System.exit(1);
        }
    }

    private static String safe(String v, String def) {
        return (v != null && !v.isBlank()) ? v : def;
    }
}
