package com.cinoo.matchmateserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "matchmate.retention")
@Data
public class RetentionProperties {

    private Duration chatMessageAge = Duration.ofDays(1);
}
