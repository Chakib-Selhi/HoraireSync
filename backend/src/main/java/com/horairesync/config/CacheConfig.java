package com.horairesync.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory cache of parsed results (24 h by default).
 *
 * <p>It sits on top of the raw page cache of {@link com.horairesync.http.JsoupHtmlSource}: a repeated request skips
 * both the network and the parsing, which keeps cached answers well under the 500 ms target of the PRD.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TERMS = "terms";
    public static final String PROGRAM_SEARCH = "programSearch";
    public static final String PROGRAM_COURSES = "programCourses";
    public static final String COURSE_SECTIONS = "courseSections";

    @Bean
    public CacheManager cacheManager(HoraireSyncProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager(TERMS, PROGRAM_SEARCH, PROGRAM_COURSES, COURSE_SECTIONS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.cache().ttl())
                .maximumSize(properties.cache().maximumSize()));
        return manager;
    }
}
