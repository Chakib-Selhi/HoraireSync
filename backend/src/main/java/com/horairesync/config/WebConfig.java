package com.horairesync.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows the frontend origins configured in {@code horairesync.cors-allowed-origins} to call the API. */
@Configuration
@EnableConfigurationProperties(HoraireSyncProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final HoraireSyncProperties properties;

    public WebConfig(HoraireSyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.corsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .exposedHeaders("Content-Disposition");
    }
}
