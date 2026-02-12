package utils.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import utils.config.SmtpConfig;
public class ConfigLoader {
    private static final String CONFIG_PATH = "config/smtp-config.json";
    private static SmtpConfig smtpConfig;

    public static SmtpConfig getSmtpConfig() {
        if (smtpConfig != null) return smtpConfig;

        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CONFIG_PATH)) {

            if (is == null) {
                throw new RuntimeException(
                        "No se encontró '" + CONFIG_PATH + "' en el classpath.\n" +
                                "Debe estar en: src/test/resources/" + CONFIG_PATH
                );
            }

            smtpConfig = new ObjectMapper().readValue(is, SmtpConfig.class);
            return smtpConfig;

        } catch (Exception e) {
            throw new RuntimeException("Error leyendo " + CONFIG_PATH, e);
        }
    }
}


