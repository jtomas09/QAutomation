package qa.cinepolis.backend.store;

import org.springframework.stereotype.Component;
import qa.cinepolis.backend.model.ScheduledJob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScheduledJobStore {

    private final ConcurrentHashMap<String, ScheduledJob> jobs = new ConcurrentHashMap<>();

    public List<ScheduledJob> findAll() {
        return new ArrayList<>(jobs.values());
    }

    public Optional<ScheduledJob> findById(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public ScheduledJob save(ScheduledJob job) {
        jobs.put(job.getId(), job);
        return job;
    }

    public boolean deleteById(String id) {
        return jobs.remove(id) != null;
    }
}
