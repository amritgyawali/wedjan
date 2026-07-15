package com.wedjan.api.system;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final String version;

    public HealthController(@Value("${wedjan.version:0.1.0}") String version) {
        this.version = version;
    }

    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "version", version);
    }
}
