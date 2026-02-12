package utils.config;
import java.util.List;
public class SmtpConfig {
    public Smtp smtp;
    public Mail mail;

    public static class Smtp {
        public String host;
        public String port;
        public String user;
        public String pass;
    }

    public static class Mail {
        public String from;
        public List<String> to;
    }
}

