package qa.cinepolis.runner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDto {
    public String  executionId;
    public String  suite;
    public String  testClass;
    public String  env;
    public String  device;         // user-selected device name (legacy / fallback)
    public String  country;

    @JsonProperty("videoEnabled")
    public boolean videoEnabled;

    @JsonProperty("sendMail")
    public boolean sendMail;

    public String  reportEmails;

    // ── Dynamic device capabilities (populated by backend DeviceStore) ──────
    public String  udid;            // physical device UDID from adb/xcrun discovery
    public String  platformVersion; // e.g. "15", "17.5"
    public String  deviceName;      // canonical discovered device name
    public String  platform;        // ANDROID | IOS

    // ── Per-device app config (populated by DeviceAppConfigStore) ────────────
    public String  appPackage;      // Android package name — overrides global RunnerConfig
    public String  bundleId;        // iOS bundle identifier
    public String  appMode;         // INSTALLED | APK | IPA

    // ── Caso grabado en Record Studio (Suites → Ejecutar) ────────────────────
    // Presente SOLO cuando esta ejecución viene de un caso grabado que todavía
    // no existe como test precompilado en el repo — ver JobExecutor: cuando
    // recordedCaseClassName != null, escribe recordedCaseSource como archivo
    // .java en tests/QARecordStudio/ del workspace ya clonado, apunta el
    // filtro --tests directo a esa clase (sin pasar por SUITE_MAP), y borra
    // el archivo al terminar — nunca modifica el repo base de forma permanente.
    public String  recordedCaseClassName;
    public String  recordedCaseSource;
    public String  recordedCaseName;
}
