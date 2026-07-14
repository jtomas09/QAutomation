package qa.cinepolis.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcasts execution log/status/done events to connected SSE clients.
 *
 * Also sends a periodic SSE comment (":keepalive", per the SSE spec — lines
 * starting with ':' are ignored by EventSource, no 'message'/named event
 * fires) so idle connections during long silent gaps in the Runner's output
 * (e.g. a slow git clone with no log lines for minutes) keep receiving bytes.
 * Without this, an intermediary proxy with an idle-connection timeout can
 * silently drop the stream well before the app's own 30-minute SseEmitter
 * timeout, which the frontend then reports as "SSE connection lost".
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    /** Comfortably below any plausible proxy idle-connection timeout (commonly ~300s). */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    private final ConcurrentHashMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();

    @PostConstruct
    void startHeartbeat() {
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("sse-heartbeat-");
        heartbeatScheduler.initialize();
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeats, HEARTBEAT_INTERVAL_SECONDS * 1000);
        log.info("[SseService] Heartbeat SSE iniciado (cada {}s).", HEARTBEAT_INTERVAL_SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeatScheduler.shutdown();
    }

    /** Sends a comment-only keepalive to every open emitter — never a named event. */
    private void sendHeartbeats() {
        if (emitters.isEmpty()) return;
        emitters.forEach((executionId, list) -> {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter em : list) {
                try {
                    em.send(SseEmitter.event().comment("keepalive"));
                } catch (Exception ignored) {
                    dead.add(em);
                }
            }
            dead.forEach(em -> remove(executionId, em));
        });
    }

    public SseEmitter register(String executionId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 min
        emitters.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(executionId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void broadcast(String executionId, String eventName, Object data) {
        List<SseEmitter> list = emitters.getOrDefault(executionId, List.of());
        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(eventName).data(json.writeValueAsString(data)));
            } catch (Exception ignored) {
                dead.add(em);
            }
        }
        dead.forEach(em -> remove(executionId, em));
    }

    public void complete(String executionId) {
        List<SseEmitter> list = emitters.remove(executionId);
        if (list != null) list.forEach(SseEmitter::complete);
    }

    private void remove(String executionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(executionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(executionId);
        }
    }
}
