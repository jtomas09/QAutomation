package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ReportEmailStore {

    private volatile boolean enabled = true;
    private volatile List<String> emails = new ArrayList<>(Arrays.asList(
        "jtomasb@ia.com.mx",
        "ygonzalez@ia.com.mx",
        "avelasco@ia.com.mx",
        "jurbina@ia.com.mx"
    ));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getEmails() { return new ArrayList<>(emails); }
    public void setEmails(List<String> emails) {
        this.emails = new ArrayList<>(emails);
    }

    public String getMailTo() {
        return String.join(",", emails);
    }
}
