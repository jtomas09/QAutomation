package qa.cinepolis.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RunController {

    @PostMapping("/run")
    public ResponseEntity<?> runSuite(
            @RequestBody Map<String, Object> payload
    ) {
        System.out.println("RUN REQUEST => " + payload);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Execution started",
                "payload", payload
        ));
    }
}
