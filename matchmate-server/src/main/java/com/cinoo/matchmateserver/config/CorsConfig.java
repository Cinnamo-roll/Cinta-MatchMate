package com.cinoo.matchmateserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public CorsConfig(
            @Value("${matchmate.cors.allowed-origins}") String allowedOrigins,
            @Value("${matchmate.cors.allowed-origin-patterns:}") String allowedOriginPatterns) {
        this.allowedOrigins = splitCsv(allowedOrigins);
        this.allowedOriginPatterns = splitCsv(allowedOriginPatterns);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        if (allowedOrigins.length > 0) {
            registration.allowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            registration.allowedOriginPatterns(allowedOriginPatterns);
        }
    }

    private String[] splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return value.trim().split("\\s*,\\s*");
    }
}
