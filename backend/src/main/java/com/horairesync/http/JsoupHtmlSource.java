package com.horairesync.http;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.horairesync.config.HoraireSyncProperties;
import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Downloads pages with Jsoup while being polite to the source sites.
 *
 * <ul>
 *   <li>Raw HTML is cached (24 h by default), so one course page serves every term and every user.</li>
 *   <li>Requests to the same host are spaced by the host's crawl delay.</li>
 *   <li>Every request carries a User-Agent that identifies the project.</li>
 * </ul>
 *
 * <p>The cache holds strings rather than {@link Document} objects because Jsoup documents are mutable.
 */
@Component
public class JsoupHtmlSource implements HtmlSource {

    private static final Logger log = LoggerFactory.getLogger(JsoupHtmlSource.class);

    private final HoraireSyncProperties.Http config;
    private final PageDownloader downloader;
    private final Cache<String, String> pages;
    private final Map<String, MinIntervalRateLimiter> limitersByHost = new ConcurrentHashMap<>();

    /** Performs the actual HTTP GET; replaced in tests. */
    @FunctionalInterface
    public interface PageDownloader {
        String download(String url, String userAgent, int timeoutMillis) throws IOException;
    }

    @Autowired
    public JsoupHtmlSource(HoraireSyncProperties properties) {
        this(properties, JsoupHtmlSource::downloadWithJsoup);
    }

    public JsoupHtmlSource(HoraireSyncProperties properties, PageDownloader downloader) {
        this.config = properties.http();
        this.downloader = downloader;
        this.pages = Caffeine.newBuilder()
                .expireAfterWrite(properties.cache().ttl())
                .maximumSize(properties.cache().maximumSize())
                .build();
    }

    @Override
    public Document get(String url) {
        // Caffeine runs the loader once per key even under concurrent calls; failures are not cached.
        String html = pages.get(url, this::fetch);
        return Jsoup.parse(html, url);
    }

    private String fetch(String url) {
        String host = URI.create(url).getHost();
        MinIntervalRateLimiter limiter = limitersByHost.computeIfAbsent(host,
                h -> new MinIntervalRateLimiter(config.crawlDelayFor(h)));
        try {
            limiter.acquire();
            log.info("GET {}", url);
            return downloader.download(url, config.userAgent(), (int) config.timeout().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(url, e);
        } catch (IOException e) {
            log.warn("GET {} failed: {}", url, e.toString());
            throw unavailable(url, e);
        }
    }

    private static HoraireSyncException unavailable(String url, Exception cause) {
        return new HoraireSyncException(ErrorCode.SOURCE_UNAVAILABLE,
                "Le site source ne répond pas pour le moment (" + URI.create(url).getHost() + ").", cause);
    }

    private static String downloadWithJsoup(String url, String userAgent, int timeoutMillis) throws IOException {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMillis)
                .followRedirects(true)
                .execute()
                .body();
    }
}
