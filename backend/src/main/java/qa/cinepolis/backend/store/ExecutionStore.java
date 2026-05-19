package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.Execution;
import qa.cinepolis.backend.model.ExecutionStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class ExecutionStore {

    private final ConcurrentHashMap<String, Execution> map     = new ConcurrentHashMap<>();
    private final AtomicInteger                        counter = new AtomicInteger(1000);

    public Execution create(String suite, String env, String device, String country) {
        String id = "RUN-" + counter.incrementAndGet();
        Execution exec = new Execution();
        exec.setExecutionId(id);
        exec.setSuite(suite);
        exec.setEnv(env);
        exec.setDevice(device);
        exec.setCountry(country);
        exec.setStatus(ExecutionStatus.PENDING);
        exec.setStartTime(Instant.now());
        exec.setLogs(new CopyOnWriteArrayList<>());
        map.put(id, exec);
        return exec;
    }

    public Optional<Execution> findById(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public Optional<Execution> findNextPending() {
        return map.values().stream()
                .filter(e -> e.getStatus() == ExecutionStatus.PENDING)
                .min(Comparator.comparing(Execution::getStartTime));
    }

    public List<Execution> findAll() {
        return map.values().stream()
                .sorted(Comparator.comparing(Execution::getStartTime).reversed())
                .collect(Collectors.toList());
    }
}
