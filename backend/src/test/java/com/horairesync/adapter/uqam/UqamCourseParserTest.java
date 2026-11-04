package com.horairesync.adapter.uqam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.DeliveryMode;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Section;
import java.time.LocalDate;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Edge cases built from small HTML snippets that mimic the UQAM markup. */
class UqamCourseParserTest {

    private static final UqamTermId WINTER = new UqamTermId(2027, 1);

    private static Document page(String groupHtml) {
        return Jsoup.parse("""
                <html><head><title>Cours ABC1234 | Cours fictif | UQAM</title></head><body>
                <div class="node-cours"><div id="groupes_wrapper20271">%s</div></div></body></html>
                """.formatted(groupHtml));
    }

    private static String group(String header, String rows, String extra) {
        return """
                <div class="groupe">
                  <h3 class="no_groupe">Groupe 030</h3>
                  <div class="ligne"><table class="alignement"><tr><td><h3>Enseignant</h3></td>
                    <td><ul><li>Ada Lovelace</li></ul></td></tr></table></div>
                  <div class="ligne"><h3>Horaire et lieu</h3>%s
                    <table><tr>%s</tr>%s</table></div>
                </div>
                """.formatted(extra, header, rows);
    }

    private static final String HEADER = "<th>Jour</th><th>Date</th><th>Heure</th><th>Lieu (Local)</th><th>Type</th>";

    private static List<Section> parse(Document doc) {
        return UqamCourseParser.parseSections(doc, "ABC1234", WINTER, "https://example.org");
    }

    @Test
    void unexpectedTableHeadersFailInsteadOfReturningWrongData() {
        Document doc = page(group("<th>Day</th><th>Time</th>", "", ""));

        assertThatThrownBy(() -> parse(doc))
                .isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(ErrorCode.PARSING_FAILED);
    }

    @Test
    void rowWithWrongColumnCountFails() {
        Document doc = page(group(HEADER, "<tr><td>Lundi</td><td>x</td></tr>", ""));

        assertThatThrownBy(() -> parse(doc)).isInstanceOf(HoraireSyncException.class)
                .hasMessageContaining("colonnes");
    }

    @Test
    void groupWithoutNumberFails() {
        Document doc = page("<div class=\"groupe\"><h3>???</h3></div>");

        assertThatThrownBy(() -> parse(doc)).isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(ErrorCode.PARSING_FAILED);
    }

    @Test
    void pageWithCourseTitleButWithoutCourseBlockFails() {
        Document doc = Jsoup.parse("<html><head><title>Cours ABC1234 | Cours fictif | UQAM</title></head></html>");

        assertThatThrownBy(() -> parse(doc)).isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(ErrorCode.PARSING_FAILED);
    }

    @Test
    void unreadableCellsBecomeMissingFieldsNotGuesses() {
        String row = "<tr><td>Bientôt</td><td>À confirmer</td><td>Variable</td><td></td><td>Séminaire</td></tr>";
        Section s = parse(page(group(HEADER, row, ""))).get(0);

        Meeting m = s.meetings().get(0);
        assertThat(m.day()).isNull();
        assertThat(m.start()).isNull();
        assertThat(m.end()).isNull();
        assertThat(m.type()).isEqualTo(MeetingType.OTHER);
        assertThat(s.mode()).isEqualTo(DeliveryMode.UNKNOWN);
        assertThat(s.missingFields()).contains("mode", "meetings[0].day", "meetings[0].start",
                "meetings[0].end", "meetings[0].firstDate", "meetings[0].lastDate");
    }

    @Test
    void singleDateRowIsAOneOffMeeting() {
        String row = "<tr><td>Samedi</td><td>Le 27 février 2027</td><td>De 09h30 à 12h30</td>"
                + "<td>Campus de Montréal</td><td>Examen</td></tr>";
        Section s = parse(page(group(HEADER, row,
                "<div class=\"avertissement\">Ce cours est donné en présentiel.</div>"))).get(0);

        Meeting m = s.meetings().get(0);
        assertThat(m.onlyDate()).isEqualTo(LocalDate.of(2027, 2, 27));
        assertThat(m.lastDate()).isEqualTo(m.firstDate());
        assertThat(m.type()).isEqualTo(MeetingType.EXAM);
    }

    @Test
    void ambiguousRoomRemarkIsIgnored() {
        String rows = "<tr><td>Lundi</td><td>Du 11 janvier 2027 au 2 mai 2027</td><td>De 09h30 à 12h30</td>"
                + "<td>Campus de Montréal</td><td>Cours magistral</td></tr>";
        String html = group(HEADER, rows, "").replace("</div>\n</div>", "</div>"
                + "<div class=\"ligne\"><h3>Remarque</h3><ul>"
                + "<li>Cours magistral et laboratoire dans le local D-4119</li></ul></div></div>");
        Section s = parse(page(html)).get(0);

        assertThat(s.notes()).containsExactly("Cours magistral et laboratoire dans le local D-4119");
        assertThat(s.meetings().get(0).room()).isNull();
    }

    @Test
    void examRemarkInUnexpectedFormatIsSkipped() {
        assertThat(UqamCourseParser.parseExams(List.of("Examens: à déterminer"), WINTER)).isEmpty();
        assertThat(UqamCourseParser.parseExams(List.of("Examen: 31 févr. 09:30 à 12:30"), WINTER)).isEmpty();
    }

    @Test
    void modeTextVariants() {
        assertThat(UqamCourseParser.parseMode(null)).isEqualTo(DeliveryMode.UNKNOWN);
        assertThat(UqamCourseParser.parseMode(Jsoup.parse("<p>Ce cours est donné en ligne.</p>").selectFirst("p")))
                .isEqualTo(DeliveryMode.ONLINE);
        assertThat(UqamCourseParser.parseMode(Jsoup.parse("<p>Autre texte</p>").selectFirst("p")))
                .isEqualTo(DeliveryMode.UNKNOWN);
    }
}
