package com.horairesync.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.support.TestProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class JsoupHtmlSourceTest {

    private final List<String> requests = new ArrayList<>();
    private final List<String> userAgents = new ArrayList<>();

    @Test
    void downloadsOnceThenServesFromCache() {
        JsoupHtmlSource source = new JsoupHtmlSource(TestProperties.defaults(), (url, ua, timeout) -> {
            requests.add(url);
            userAgents.add(ua);
            return "<html><head><title>Page</title></head><body><p>ok</p></body></html>";
        });

        Document first = source.get("https://etudier.uqam.ca/cours?sigle=INF1120");
        Document second = source.get("https://etudier.uqam.ca/cours?sigle=INF1120");

        assertThat(first.title()).isEqualTo("Page");
        assertThat(second.select("p").text()).isEqualTo("ok");
        assertThat(requests).hasSize(1);
        assertThat(userAgents).containsExactly("HoraireSync-test");
    }

    @Test
    void cachedDocumentsAreIndependentCopies() {
        JsoupHtmlSource source = new JsoupHtmlSource(TestProperties.defaults(),
                (url, ua, timeout) -> "<html><body><p>ok</p></body></html>");

        source.get("https://example.org/a").select("p").remove();

        assertThat(source.get("https://example.org/a").select("p")).hasSize(1);
    }

    @Test
    void ioErrorBecomesSourceUnavailableAndIsNotCached() {
        int[] calls = {0};
        JsoupHtmlSource source = new JsoupHtmlSource(TestProperties.defaults(), (url, ua, timeout) -> {
            calls[0]++;
            if (calls[0] == 1) {
                throw new IOException("timeout");
            }
            return "<html><body>back</body></html>";
        });

        assertThatThrownBy(() -> source.get("https://etudier.uqam.ca/cours?sigle=INF1120"))
                .isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
        assertThat(source.get("https://etudier.uqam.ca/cours?sigle=INF1120").body().text()).isEqualTo("back");
        assertThat(calls[0]).isEqualTo(2);
    }
}
