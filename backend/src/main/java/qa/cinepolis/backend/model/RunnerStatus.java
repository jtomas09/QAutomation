package qa.cinepolis.backend.model;

public enum RunnerStatus {
    ONLINE, OFFLINE, BUSY, STARTING, STOPPING,
    /** Runner conectado pero con componentes criticos faltantes (ej. ADB, Appium). */
    DEGRADED
}
