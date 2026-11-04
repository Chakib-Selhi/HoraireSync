package com.horairesync.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code horairesync.*} section of {@code application.yml}. */
@ConfigurationProperties("horairesync")
public record HoraireSyncProperties(List<String> corsAllowedOrigins, Cache cache, Http http, Uqam uqam,
                                    List<SchoolEntry> unsupportedSchools) {

    public HoraireSyncProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        unsupportedSchools = unsupportedSchools == null ? List.of() : List.copyOf(unsupportedSchools);
    }

    /** Cache of downloaded pages and parsed results. */
    public record Cache(Duration ttl, long maximumSize) {}

    /** Outgoing HTTP settings. */
    public record Http(String userAgent, Duration timeout, Duration defaultCrawlDelay,
                       Map<String, Duration> crawlDelays) {

        public Http {
            crawlDelays = crawlDelays == null ? Map.of() : Map.copyOf(crawlDelays);
        }

        /** Delay to respect between two requests to {@code host}. */
        public Duration crawlDelayFor(String host) {
            return crawlDelays.getOrDefault(host, defaultCrawlDelay);
        }
    }

    /** UQAM adapter settings. */
    public record Uqam(String baseUrl) {}

    /** A school shown in the picker without adapter (manual mode). */
    public record SchoolEntry(String id, String name) {}
}
