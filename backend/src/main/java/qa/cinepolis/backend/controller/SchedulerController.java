package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import qa.cinepolis.backend.model.ScheduledJob;
import qa.cinepolis.backend.service.SchedulerService;
import qa.cinepolis.backend.store.ScheduledJobStore;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
@CrossOrigin(origins = "*")
public class SchedulerController {

    private final SchedulerService service;
    private final ScheduledJobStore store;

    public SchedulerController(SchedulerService service, ScheduledJobStore store) {
        this.service = service;
        this.store = store;
    }

    @GetMapping("/jobs")
    public List<ScheduledJob> listJobs() {
        return store.findAll();
    }

    @PostMapping("/jobs")
    public ResponseEntity<ScheduledJob> createJob(@RequestBody ScheduledJob job) {
        return ResponseEntity.ok(service.createJob(job));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<ScheduledJob> updateJob(
            @PathVariable String id,
            @RequestBody ScheduledJob job) {
        return ResponseEntity.ok(service.updateJob(id, job));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable String id) {
        service.deleteJob(id);
        return ResponseEntity.ok(Map.of("result", "deleted"));
    }

    @PostMapping("/jobs/{id}/run")
    public ResponseEntity<Map<String, String>> runNow(@PathVariable String id) {
        service.runNow(id);
        return ResponseEntity.ok(Map.of("result", "triggered"));
    }
}
