package qa.cinepolis.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private final ConcurrentHashMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();

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
