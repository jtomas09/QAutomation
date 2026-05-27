package qa.cinepolis.backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import qa.cinepolis.backend.model.ScheduledJob;
import qa.cinepolis.backend.store.ScheduledJobStore;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final ScheduledJobStore store;
    private final ExecutionService  execService;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public SchedulerService(ScheduledJobStore store, ExecutionService execService) {
        this.store       = store;
        this.execService = execService;
    }

    @PostConstruct
    public void init() {
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduled-job-");
        scheduler.initialize();
        store.findAll().stream()
             .filter(ScheduledJob::isEnabled)
             .forEach(this::scheduleJob);
        log.info("[SchedulerService] Scheduler inicializado con {} job(s) activo(s).", futures.size());
    }

    public ScheduledJob createJob(ScheduledJob job) {
        job.setId(UUID.randomUUID().toString());
        job.setLastStatus("PENDING");
        store.save(job);
        if (job.isEnabled()) scheduleJob(job);
        log.info("[SchedulerService] Ejecución programada creada: '{}' cron='{}'",
                 job.getName(), job.getCronExpression());
        return job;
    }

    public ScheduledJob updateJob(String id, ScheduledJob updated) {
        ScheduledJob existing = store.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
        cancelJob(id);
        updated.setId(id);
        updated.setLastRun(existing.getLastRun());
        updated.setLastStatus(existing.getLastStatus());
        store.save(updated);
        if (updated.isEnabled()) scheduleJob(updated);
        log.info("[SchedulerService] Programación actualizada: '{}' cron='{}'",
                 updated.getName(), updated.getCronExpression());
        return updated;
    }

    public void deleteJob(String id) {
        cancelJob(id);
        store.deleteById(id);
        log.info("[SchedulerService] Ejecución programada eliminada: id='{}'", id);
    }

    public void runNow(String id) {
        ScheduledJob job = store.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
        log.info("[SchedulerService] Ejecución manual disparada: '{}'", job.getName());
        executeJob(job);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void scheduleJob(ScheduledJob job) {
        cancelJob(job.getId());
        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executeJob(job),
                    new CronTrigger(job.getCronExpression())
            );
            futures.put(job.getId(), future);
            log.info("[SchedulerService] Job '{}' programado con cron '{}'",
                     job.getName(), job.getCronExpression());
        } catch (Exception e) {
            log.error("[SchedulerService] Cron inválido para '{}': {}", job.getName(), e.getMessage());
        }
    }

    private void cancelJob(String id) {
        ScheduledFuture<?> f = futures.remove(id);
        if (f != null) f.cancel(false);
    }

    private void executeJob(ScheduledJob job) {
        log.info("[SchedulerService] Job encolado automáticamente: '{}' suite='{}'",
                 job.getName(), job.getSuite());
        job.setLastRun(Instant.now());
        store.save(job);

        try {
            var execution = execService.create(
                job.getSuite(),
                job.getEnvironment(),
                job.getDevice(),
                job.getCountry() != null ? job.getCountry() : "mexico",
                job.isVideoEnabled(),
                job.getTestClass()
            );
            job.setLastStatus("QUEUED");
            job.setLastExecutionId(execution.getExecutionId());
            store.save(job);
            log.info("[SchedulerService] Job '{}' encolado correctamente → {}", job.getName(), execution.getExecutionId());
        } catch (Exception e) {
            log.error("[SchedulerService] Error encolando job '{}': {}", job.getName(), e.getMessage());
            job.setLastStatus("ERROR");
            store.save(job);
        }
    }
}
