package qa.cinepolis.runner.model;

/**
 * Configuración de Network Monitoring (captura HTTP/HTTPS del dispositivo bajo
 * prueba, vía mitmproxy). Poblada en {@link RunnerConfig#fromEnv()} con el mismo
 * patrón {@code env(key, default)} que el resto de esa clase — no introduce un
 * sistema de configuración nuevo (sin YAML, sin JSON de config separado).
 *
 * {@code enabled=false} (default) deja el comportamiento de ejecución EXACTAMENTE
 * igual al actual: ningún proceso mitmproxy se arranca, ninguna llamada adb de
 * proxy se ejecuta, ninguna capability nueva llega a Appium.
 */
public class NetworkMonitoringConfig {

    public boolean enabled;
    public boolean captureRequestBody;
    public boolean captureResponseBody;
    public long    maxResponseBodySize;
    public boolean attachToAllure;
    public boolean saveAllTraffic;
    public boolean saveErrors;
    public boolean redactSensitiveData;

    public static NetworkMonitoringConfig fromEnv(RunnerConfigEnvReader env) {
        NetworkMonitoringConfig c = new NetworkMonitoringConfig();
        c.enabled              = Boolean.parseBoolean(env.get("NETWORK_MONITORING_ENABLED", "false"));
        c.captureRequestBody   = Boolean.parseBoolean(env.get("NETWORK_MONITORING_CAPTURE_REQUEST_BODY", "true"));
        c.captureResponseBody  = Boolean.parseBoolean(env.get("NETWORK_MONITORING_CAPTURE_RESPONSE_BODY", "true"));
        c.maxResponseBodySize  = Long.parseLong(env.get("NETWORK_MONITORING_MAX_RESPONSE_BODY_SIZE", "1048576"));
        c.attachToAllure       = Boolean.parseBoolean(env.get("NETWORK_MONITORING_ATTACH_TO_ALLURE", "true"));
        c.saveAllTraffic       = Boolean.parseBoolean(env.get("NETWORK_MONITORING_SAVE_ALL_TRAFFIC", "true"));
        c.saveErrors           = Boolean.parseBoolean(env.get("NETWORK_MONITORING_SAVE_ERRORS", "true"));
        c.redactSensitiveData  = Boolean.parseBoolean(env.get("NETWORK_MONITORING_REDACT_SENSITIVE_DATA", "true"));
        return c;
    }

    /** Pequeña interfaz para reusar el resolvedor privado `env(key,def)` de RunnerConfig sin duplicarlo. */
    public interface RunnerConfigEnvReader {
        String get(String key, String def);
    }
}
