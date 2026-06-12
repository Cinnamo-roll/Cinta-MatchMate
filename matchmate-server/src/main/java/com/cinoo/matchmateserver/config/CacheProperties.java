package com.cinoo.matchmateserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "matchmate.cache")
@Data
public class CacheProperties {

    private boolean enabled = true;
    private Duration lockWait = Duration.ofSeconds(2);
    private Warmup warmup = new Warmup();

    @Data
    public static class Warmup {
        private boolean enabled = true;
        private List<Integer> recommendationLimits = new ArrayList<>(List.of(8));
    }
}
