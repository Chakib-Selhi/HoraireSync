package com.horairesync.support;

import com.horairesync.config.HoraireSyncProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Builds {@link HoraireSyncProperties} for unit tests. */
public final class TestProperties {

    private TestProperties() {}

    public static HoraireSyncProperties withCrawlDelay(Duration delay) {
        return new HoraireSyncProperties(
                List.of("http://localhost:5173"),
                new HoraireSyncProperties.Cache(Duration.ofHours(24), 100),
                new HoraireSyncProperties.Http("HoraireSync-test", Duration.ofSeconds(1), delay, Map.of()),
                new HoraireSyncProperties.Uqam("https://etudier.uqam.ca"),
                List.of(new HoraireSyncProperties.SchoolEntry("mcgill", "McGill University")));
    }

    public static HoraireSyncProperties defaults() {
        return withCrawlDelay(Duration.ZERO);
    }
}
