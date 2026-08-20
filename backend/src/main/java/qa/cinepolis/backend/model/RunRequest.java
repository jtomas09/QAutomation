package qa.cinepolis.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RunRequest {

    private String suite;
    private String device;

    /**
     * Nombre/plataforma visibles del Device Target, tal como los conocía el
     * modal en el momento de la selección (ver ExecuteSuiteModal/ExecuteCaseModal
     * en SuitesPage.tsx) — PURAMENTE informativos, para que los logs [RunDevice]
     * y los mensajes de rechazo puedan mostrar "SM-A566E" en vez de solo el
     * UDID. La resolución real de qué hardware existe sigue siendo autoridad
     * exclusiva de DeviceStore (nunca se confía en el cliente para eso) —
     * `device` (el UDID) sigue siendo el único campo que participa en el match.
     */
    private String deviceName;
    private String devicePlatform;

    /** Presente solo cuando `suite` es una TestSuite de Record Studio — puramente
     *  informativo/trazabilidad (ver logs [SuiteExecution] en RunController). La
     *  identidad real de qué se ejecuta viaja en recordedCases, no aquí. */
    private String suiteId;

    // Frontend sends "env"; @JsonProperty maps it to this field
    @JsonProperty("env")
    private String environment;

    private String  country;

    @JsonProperty("videoEnabled")
    private boolean videoEnabled;

    private String  testClass;

    /**
     * Presente únicamente cuando la ejecución viene de un caso grabado en
     * Record Studio (Suites → Ejecutar) que todavía no existe como test
     * precompilado en el repo del Runner — ver JobExecutor (Runner): escribe
     * `source` como archivo .java en tests/QARecordStudio/ del workspace ya
     * clonado, corre Gradle apuntando directo a esa clase (sin pasar por
     * SUITE_MAP), y lo borra al terminar el job. Ausente (null) en cualquier
     * ejecución normal — mismo POST /api/run, mismo RUN-XXXX, mismo pipeline.
     */
    private RecordedCase recordedCase;

    /**
     * Presente únicamente cuando `suite` es una TestSuite de Record Studio con
     * N TestCases (Suites → Ejecutar suite) — ver JobExecutor (Runner):
     * resolveEffectiveRecordedCases() le da prioridad absoluta sobre SUITE_MAP,
     * escribe un .java por entrada y arma un --tests por cada una. Ausente
     * (null/vacío) en cualquier ejecución de una suite real preexistente.
     */
    private List<RecordedCase> recordedCases;

    public static class RecordedCase {
        /** Solo trazabilidad/logging ([SuiteExecution]/[Runner] Executing TestCase) — no participa en la resolución de qué ejecutar. */
        private String testCaseId;
        private String className;
        private String source;
        private String caseName;

        public String getTestCaseId()            { return testCaseId; }
        public void   setTestCaseId(String id)    { this.testCaseId = id; }
        public String getClassName()            { return className; }
        public void   setClassName(String c)     { this.className = c; }
        public String getSource()                { return source; }
        public void   setSource(String s)        { this.source = s; }
        public String getCaseName()              { return caseName; }
        public void   setCaseName(String c)      { this.caseName = c; }
    }

    public RecordedCase getRecordedCase()               { return recordedCase; }
    public void         setRecordedCase(RecordedCase r) { this.recordedCase = r; }

    public List<RecordedCase> getRecordedCases()             { return recordedCases; }
    public void               setRecordedCases(List<RecordedCase> r) { this.recordedCases = r; }

    public String  getSuiteId()               { return suiteId; }
    public void    setSuiteId(String suiteId) { this.suiteId = suiteId; }

    public String  getSuite()       { return suite; }
    public void    setSuite(String suite) { this.suite = suite; }

    public String  getDevice()      { return device; }
    public void    setDevice(String device) { this.device = device; }

    public String  getDeviceName()               { return deviceName; }
    public void    setDeviceName(String v)       { this.deviceName = v; }
    public String  getDevicePlatform()           { return devicePlatform; }
    public void    setDevicePlatform(String v)   { this.devicePlatform = v; }

    public String  getEnvironment() { return environment; }
    public void    setEnvironment(String environment) { this.environment = environment; }

    public String  getCountry()     { return country; }
    public void    setCountry(String country) { this.country = country; }

    public boolean isVideoEnabled()           { return videoEnabled; }
    public void    setVideoEnabled(boolean v) { this.videoEnabled = v; }

    public String  getTestClass()             { return testClass; }
    public void    setTestClass(String tc)    { this.testClass = tc; }
}
