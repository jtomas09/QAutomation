package qa.cinepolis.backend.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuración de "Enviar reporte por correo" desde el Dashboard.
 *
 * Persistida en un archivo JSON (data/report-email-settings.json) — antes vivía
 * únicamente en campos volatile, así que cualquier reinicio del backend (redeploy,
 * crash, restart de servicio) descartaba silenciosamente lo que el usuario hubiera
 * configurado y volvía a los defaults hardcodeados. Ahora el archivo es la fuente
 * de verdad: se lee una vez al arrancar y se reescribe en cada cambio.
 */
@Component
public class ReportEmailStore {

    private static final Logger log = LoggerFactory.getLogger(ReportEmailStore.class);

    private static final Path STORE_PATH = Paths.get("data", "report-email-settings.json");

    private static final List<String> DEFAULT_EMAILS = Arrays.asList(
        "jtomasb@ia.com.mx",
        "ygonzalez@ia.com.mx",
        "avelasco@ia.com.mx",
        "jurbina@ia.com.mx"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    private volatile boolean enabled = true;
    private volatile List<String> emails = new ArrayList<>(DEFAULT_EMAILS);

    @PostConstruct
    void loadFromDisk() {
        if (!Files.exists(STORE_PATH)) {
            log.info("[ReportEmailStore] {} no existe todavía — usando defaults ({}).",
                    STORE_PATH.toAbsolutePath(), enabled ? "habilitado" : "deshabilitado");
            persist(); // crea el archivo con los defaults para que exista desde el primer arranque
            return;
        }
        try {
            Persisted p = mapper.readValue(STORE_PATH.toFile(), Persisted.class);
            this.enabled = p.enabled;
            this.emails  = (p.emails != null) ? new ArrayList<>(p.emails) : new ArrayList<>();
            log.info("[ReportEmailStore] Configuración cargada desde {} — enabled={} emails={}",
                    STORE_PATH.toAbsolutePath(), enabled, emails);
        } catch (Exception e) {
            log.error("[ReportEmailStore] No se pudo leer {} ({}: {}) — usando defaults en memoria "
                    + "sin sobrescribir el archivo existente.",
                    STORE_PATH.toAbsolutePath(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        persist();
    }

    public List<String> getEmails() { return new ArrayList<>(emails); }

    public void setEmails(List<String> emails) {
        this.emails = new ArrayList<>(emails);
        persist();
    }

    public String getMailTo() {
        return String.join(",", emails);
    }

    private synchronized void persist() {
        try {
            File parent = STORE_PATH.getParent().toFile();
            if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new java.io.IOException("No se pudo crear el directorio " + parent.getAbsolutePath());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(STORE_PATH.toFile(), new Persisted(enabled, emails));
        } catch (Exception e) {
            log.error("[ReportEmailStore] No se pudo persistir la configuración en {}: {}. "
                    + "El cambio queda activo solo en memoria para esta sesión del backend.",
                    STORE_PATH.toAbsolutePath(), e.getMessage(), e);
        }
    }

    /** Forma serializada en disco — separada del estado interno para no acoplar el JSON a la API pública. */
    private static class Persisted {
        public boolean enabled;
        public List<String> emails;

        public Persisted() {}
        public Persisted(boolean enabled, List<String> emails) {
            this.enabled = enabled;
            this.emails  = emails;
        }
    }
}
