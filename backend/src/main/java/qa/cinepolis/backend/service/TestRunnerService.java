package qa.cinepolis.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import qa.cinepolis.backend.model.LogEvent;
import qa.cinepolis.backend.model.RunRequest;
import qa.cinepolis.backend.store.ReportEmailStore;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TestRunnerService {

    @Value("${cinepolis.tests.jar}")
    private String testsJar;

    @Value("${appium.mode}")
    private String appiumMode;

    @Autowired
    private ReportEmailStore reportEmailStore;

    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService exec = Executors.newCachedThreadPool();

    private volatile Process currentProcess;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Lanza los tests como proceso externo y transmite los logs via SSE.
     * FASE 4: el React frontend se suscribe a GET /api/run con EventSource.
     */
    public SseEmitter run(RunRequest req) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 min timeout

        if (!running.compareAndSet(false, true)) {
            try { emitter.send(toSseEvent(LogEvent.of("ERROR", "Ya hay una ejecución en curso"))); }
            catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }

        exec.submit(() -> {
            try {
                safeSend(emitter, LogEvent.of("INFO", "▶ Iniciando suite [" + req.getSuite() + "]  env=" + req.getEnvironment() + "  device=" + req.getDevice()));
                safeSend(emitter, LogEvent.of("INFO", "Modo Appium: " + appiumMode));

                List<String> cmd = buildCommand(req);
                safeSend(emitter, LogEvent.of("INFO", "Ejecutando: " + String.join(" ", cmd)));

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.environment().put("EXECUTION_NAME", req.getSuite());
                if (reportEmailStore.isEnabled() && !reportEmailStore.getMailTo().isBlank()) {
                    pb.environment().put("MAIL_TO", reportEmailStore.getMailTo());
                }
                currentProcess = pb.start();

                try (BufferedReader br = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        LogLevel lvl = detectLevel(line);
                        safeSend(emitter, LogEvent.of(lvl.name(), line));
                    }
                }

                int exit = currentProcess.waitFor();
                safeSend(emitter, LogEvent.of(exit == 0 ? "PASS" : "FAIL",
                        exit == 0 ? "✅ Suite completada correctamente" : "❌ Suite terminó con errores (exit " + exit + ")"));

                try { emitter.send(SseEmitter.event().name("done").data("{\"exit\":" + exit + "}")); }
                catch (Exception ignored) {}

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                safeSend(emitter, LogEvent.of("ERROR", "Error interno: " + e.getMessage()));
            } finally {
                running.set(false);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(() -> { stop(); emitter.complete(); });
        emitter.onError(ex -> { stop(); running.set(false); });

        return emitter;
    }

    public void stop() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
        running.set(false);
    }

    public boolean isRunning() { return running.get(); }

    // ── helpers ────────────────────────────────────────────────────────────────

    private List<String> buildCommand(RunRequest req) {
        List<String> cmd = new ArrayList<>(List.of(
            "java",
            "-jar", testsJar,
            "-Dappium.mode="       + appiumMode,
            "-DsuiteId="           + req.getSuite(),
            "-Denv="               + req.getEnvironment(),
            "-DdeviceName="        + req.getDevice(),
            "-Dcountry="           + (req.getCountry() != null ? req.getCountry() : "mexico"),
            "-DbrowserStack.user=" + System.getenv("BS_USER"),
            "-DbrowserStack.key="  + System.getenv("BS_KEY")
        ));
        if (req.getTestClass() != null && !req.getTestClass().isBlank()) {
            cmd.add("-DtestClass=" + req.getTestClass());
        }
        if (reportEmailStore.isEnabled() && !reportEmailStore.getMailTo().isBlank()) {
            cmd.add("-DsendMail=true");
        }
        return cmd;
    }

    private void send(SseEmitter emitter, LogEvent event) throws IOException {
        emitter.send(SseEmitter.event().name("log").data(json.writeValueAsString(event)));
    }

    /** Sends an SSE log event, silently ignoring any error (e.g. no active HTTP connection). */
    private void safeSend(SseEmitter emitter, LogEvent event) {
        try { send(emitter, event); } catch (Exception ignored) {}
    }

    private String toSseEvent(LogEvent e) throws IOException {
        return json.writeValueAsString(e);
    }

    private enum LogLevel { INFO, WARN, ERROR, PASS, FAIL }

    private LogLevel detectLevel(String line) {
        String u = line.toUpperCase();
        if (u.contains("ERROR") || u.contains("FAILED") || u.contains("❌")) return LogLevel.FAIL;
        if (u.contains("WARN"))                                                return LogLevel.WARN;
        if (u.contains("PASSED") || u.contains("✓") || u.contains("✅"))       return LogLevel.PASS;
        return LogLevel.INFO;
    }
}
