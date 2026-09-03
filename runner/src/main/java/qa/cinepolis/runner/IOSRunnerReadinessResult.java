package qa.cinepolis.runner;

/**
 * Resultado estructurado de {@link IOSRunnerReadinessEngine#evaluate}.
 *
 * TAREA 2 de la refactorización iOS (ver auditoría previa): este es el primer
 * tipo de dato de la futura autoridad única de "readyForExecution". No
 * reemplaza todavía a {@code DeviceReadinessEvaluator.Readiness} ni a
 * {@code IosPreflightManager.IosPreflightResult} — ambos siguen siendo
 * consultados por el flujo real (JobExecutor). Este resultado es, por ahora,
 * un segundo punto de vista independiente, construido reutilizando las mismas
 * fuentes de datos, para poder compararlo contra el flujo existente antes de
 * migrar nada.
 *
 * IMPORTANTE — progresión conceptual explícita (para no confundir "conectado"
 * con "listo para ejecutar"):
 *
 *   DISCOVERED → CONNECTED → PAIRED → DEVELOPER_MODE → TRUSTED → PROVISIONED
 *   → WDA_READY → READY
 *
 * {@link #lastStageReached} indica hasta dónde llegó la evaluación antes de
 * detenerse (por diseño, la evaluación se detiene en la primera etapa que
 * bloquea — no continúa evaluando etapas posteriores con datos parciales).
 *
 * TAREA 4 — {@link #wdaErrorCode} transporta la clasificación estructurada
 * (ver {@link IOSWdaErrorClassifier}) del texto real de un fallo de WDA
 * ({@link #reason}), cuando existe uno. Es un campo ADICIONAL a {@link #errorCode}
 * (String, ya existente desde TAREA 2 para las etapas de conectividad/pairing/
 * developer mode/certificado — sin relación con un texto real de xcodebuild) —
 * no lo reemplaza, para no romper a quien ya compara {@code errorCode} como
 * String (p.ej. IOSReadinessShadowComparator). Vale {@link IOSWdaErrorCode#NONE}
 * en todo resultado que no tenga un error real de WDA que clasificar (READY,
 * OFFLINE, PAIRING/DEVELOPER_MODE/CERTIFICATE/PROVISIONING requeridos por
 * chequeo estructural, RECOVERING sin terminalReason) — nunca UNKNOWN ni
 * IOS_WDA_BUILD_FAILED por defecto.
 */
public final class IOSRunnerReadinessResult {

    /**
     * TAREA 6 — se agrega {@code NOT_READY}, distinto de {@code RECOVERING}:
     *   NOT_READY  = "Actualmente no está listo" (sin ninguna operación en curso).
     *   RECOVERING = "Existe una operación real de build/start en curso ahora
     *                 mismo" (verificable, p.ej. WdaLifecycleOwner.isBuildInFlight()).
     * Antes de TAREA 6, IOSRunnerReadinessEngine devolvía RECOVERING también
     * para "WDA simplemente no se ha intentado todavía" — sin ninguna operación
     * real en curso — mezclando diagnóstico con una recuperación inexistente.
     */
    public enum Status { READY, ACTION_REQUIRED, RECOVERING, NOT_READY, ERROR, OFFLINE }

    public enum Stage {
        DISCOVERED, CONNECTED, PAIRED, DEVELOPER_MODE, TRUSTED, PROVISIONED, WDA_READY, READY
    }

    public final String  deviceId;
    public final String  udid;
    public final String  deviceName;
    public final String  platform;
    public final String  transport;
    public final String  pairing;
    public final boolean developerMode;
    public final boolean developerTrust;
    public final String  tunnel;
    public final String  certificate;
    public final String  provisioning;
    public final String  wda;
    public final boolean wdaReachable;
    public final boolean readyForExecution;
    public final Status  status;
    public final Stage   lastStageReached;
    public final String  reason;
    public final String  errorCode;
    public final IOSWdaErrorCode wdaErrorCode;
    public final long    timestamp;

    private IOSRunnerReadinessResult(Builder b) {
        this.deviceId          = b.deviceId;
        this.udid              = b.udid;
        this.deviceName        = b.deviceName;
        this.platform          = b.platform;
        this.transport         = b.transport;
        this.pairing           = b.pairing;
        this.developerMode     = b.developerMode;
        this.developerTrust    = b.developerTrust;
        this.tunnel            = b.tunnel;
        this.certificate       = b.certificate;
        this.provisioning      = b.provisioning;
        this.wda               = b.wda;
        this.wdaReachable      = b.wdaReachable;
        this.readyForExecution = b.readyForExecution;
        this.status            = b.status;
        this.lastStageReached  = b.lastStageReached;
        this.reason            = b.reason;
        this.errorCode         = b.errorCode;
        this.wdaErrorCode      = b.wdaErrorCode;
        this.timestamp         = b.timestamp;
    }

    @Override
    public String toString() {
        return "IOSRunnerReadinessResult{"
                + "udid=" + udid
                + ", status=" + status
                + ", stage=" + lastStageReached
                + ", readyForExecution=" + readyForExecution
                + ", transport=" + transport
                + ", pairing=" + pairing
                + ", developerMode=" + developerMode
                + ", developerTrust=" + developerTrust
                + ", certificate=" + certificate
                + ", provisioning=" + provisioning
                + ", wdaReachable=" + wdaReachable
                + ", errorCode=" + errorCode
                + ", wdaErrorCode=" + wdaErrorCode
                + ", reason=" + reason
                + '}';
    }

    static Builder builder() { return new Builder(); }

    static final class Builder {
        private String  deviceId;
        private String  udid;
        private String  deviceName    = "";
        private String  platform      = "iOS";
        private String  transport     = "UNKNOWN";
        private String  pairing       = "unknown";
        private boolean developerMode = false;
        private boolean developerTrust = true; // optimista hasta que se demuestre lo contrario
        private String  tunnel        = "unknown";
        private String  certificate   = "unknown";
        private String  provisioning  = "unknown";
        private String  wda           = "unknown";
        private boolean wdaReachable  = false;
        private boolean readyForExecution = false;
        private Status  status        = Status.OFFLINE;
        private Stage   lastStageReached = Stage.DISCOVERED;
        private String  reason;
        private String  errorCode;
        private IOSWdaErrorCode wdaErrorCode = IOSWdaErrorCode.NONE;
        private long    timestamp     = System.currentTimeMillis();

        Builder deviceId(String v)       { this.deviceId = v; return this; }
        Builder udid(String v)           { this.udid = v; return this; }
        Builder deviceName(String v)     { this.deviceName = v; return this; }
        Builder platform(String v)       { this.platform = v; return this; }
        Builder transport(String v)      { this.transport = v; return this; }
        Builder pairing(String v)        { this.pairing = v; return this; }
        Builder developerMode(boolean v) { this.developerMode = v; return this; }
        Builder developerTrust(boolean v){ this.developerTrust = v; return this; }
        Builder tunnel(String v)         { this.tunnel = v; return this; }
        Builder certificate(String v)    { this.certificate = v; return this; }
        Builder provisioning(String v)   { this.provisioning = v; return this; }
        Builder wda(String v)            { this.wda = v; return this; }
        Builder wdaReachable(boolean v)  { this.wdaReachable = v; return this; }
        Builder readyForExecution(boolean v) { this.readyForExecution = v; return this; }
        Builder status(Status v)         { this.status = v; return this; }
        Builder lastStageReached(Stage v){ this.lastStageReached = v; return this; }
        Builder reason(String v)         { this.reason = v; return this; }
        Builder errorCode(String v)      { this.errorCode = v; return this; }
        Builder wdaErrorCode(IOSWdaErrorCode v) { this.wdaErrorCode = v; return this; }

        IOSRunnerReadinessResult build() { return new IOSRunnerReadinessResult(this); }
    }
}
