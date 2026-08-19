package qa.cinepolis.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunRequest {

    private String suite;
    private String device;

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

    public static class RecordedCase {
        private String className;
        private String source;
        private String caseName;

        public String getClassName()            { return className; }
        public void   setClassName(String c)     { this.className = c; }
        public String getSource()                { return source; }
        public void   setSource(String s)        { this.source = s; }
        public String getCaseName()              { return caseName; }
        public void   setCaseName(String c)      { this.caseName = c; }
    }

    public RecordedCase getRecordedCase()               { return recordedCase; }
    public void         setRecordedCase(RecordedCase r) { this.recordedCase = r; }

    public String  getSuite()       { return suite; }
    public void    setSuite(String suite) { this.suite = suite; }

    public String  getDevice()      { return device; }
    public void    setDevice(String device) { this.device = device; }

    public String  getEnvironment() { return environment; }
    public void    setEnvironment(String environment) { this.environment = environment; }

    public String  getCountry()     { return country; }
    public void    setCountry(String country) { this.country = country; }

    public boolean isVideoEnabled()           { return videoEnabled; }
    public void    setVideoEnabled(boolean v) { this.videoEnabled = v; }

    public String  getTestClass()             { return testClass; }
    public void    setTestClass(String tc)    { this.testClass = tc; }
}
