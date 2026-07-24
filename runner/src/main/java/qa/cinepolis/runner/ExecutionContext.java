package qa.cinepolis.runner;

/**
 * Fachada por-ejecución sobre {@link ProcessRegistry} — ver su Javadoc para la causa
 * raíz que resuelve (abort que no detenía git/WDA, solo Gradle).
 *
 * JobExecutor crea una instancia al inicio de execute() y la usa para registrar cada
 * fase larga (git, WDA, Gradle) bajo el mismo executionId, de modo que un abort en
 * cualquier punto del flujo — no solo mientras Gradle corre — cancele lo que esté
 * activo en ese instante:
 *
 *   JobExecutor → ExecutionContext → ProcessRegistry → Git Process
 *                                                     → WDA (vía WdaLifecycleOwner)
 *                                                     → Gradle Process
 *
 * No introduce ninguna autoridad nueva sobre WDA (sigue siendo WdaLifecycleOwner) ni
 * cambia la lógica de decisión de WorkspaceManager — solo coordina el "cuándo".
 */
public final class ExecutionContext {

    public final String executionId;
    private final BackendClient client;

    public ExecutionContext(String executionId, BackendClient client) {
        this.executionId = executionId;
        this.client       = client;
    }

    /** Registra un proceso del sistema operativo (git, Gradle) — cancelar = matar su árbol. */
    public String registerProcess(String label, Process process) {
        return ProcessRegistry.registerProcess(executionId, label, process);
    }

    /** Registra un recurso cancelable a medida (p.ej. liberar un consumidor de WdaLifecycleOwner). */
    public String registerCancelable(String label, ProcessRegistry.Cancelable cancelable) {
        return ProcessRegistry.register(executionId, label, cancelable);
    }

    /** Quita un registro sin cancelarlo — el proceso ya terminó por su cuenta. */
    public void unregister(String token) {
        ProcessRegistry.unregister(executionId, token);
    }

    /** Cancela todo lo registrado ahora mismo para esta ejecución. */
    public void killAll() {
        ProcessRegistry.killAll(executionId);
    }

    public boolean hasActiveProcesses() {
        return ProcessRegistry.hasActive(executionId);
    }

    public boolean isAborted() {
        return client.isJobAborted(executionId);
    }
}
