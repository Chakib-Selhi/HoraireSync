package com.horairesync.support;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.http.HtmlSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** {@link HtmlSource} backed by HTML files in {@code src/test/resources}; records requested URLs. */
public final class FixtureHtmlSource implements HtmlSource {

    private final Map<String, String> fixturesByUrl = new HashMap<>();
    private final List<String> requestedUrls = new ArrayList<>();

    /** Maps the standard UQAM URLs to the fixtures recorded in phase 0. */
    public static FixtureHtmlSource uqam() {
        String base = "https://etudier.uqam.ca";
        return new FixtureHtmlSource()
                .with(base + "/cours?sigle=INF1120", "fixtures/uqam/cours-INF1120.html")
                .with(base + "/cours?sigle=ORH1163", "fixtures/uqam/cours-ORH1163.html")
                .with(base + "/cours?sigle=ZZZ9999", "fixtures/uqam/cours-inexistant.html")
                .with(base + "/programme?code=7416", "fixtures/uqam/programme-7416.html")
                .with(base + "/programme?code=7084", "fixtures/uqam/programme-inexistant.html")
                .with(base + "/programmes", "fixtures/uqam/programmes.html")
                .with(base + "/calendriers", "fixtures/uqam/calendriers.html");
    }

    public FixtureHtmlSource with(String url, String resource) {
        fixturesByUrl.put(url, resource);
        return this;
    }

    @Override
    public Document get(String url) {
        requestedUrls.add(url);
        String resource = fixturesByUrl.get(url);
        if (resource == null) {
            throw new HoraireSyncException(ErrorCode.SOURCE_UNAVAILABLE, "No fixture for " + url);
        }
        return Jsoup.parse(read(resource), url);
    }

    public List<String> requestedUrls() {
        return List.copyOf(requestedUrls);
    }

    public static String read(String resource) {
        try (InputStream in = FixtureHtmlSource.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
