package com.horairesync.service;

import static com.horairesync.support.Schedules.section;
import static com.horairesync.support.Schedules.selected;
import static com.horairesync.support.Schedules.weekly;
import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horairesync.adapter.uqam.UqamAdapter;
import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.DeliveryMode;
import com.horairesync.model.ExportRequest;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Section;
import com.horairesync.model.SelectedSection;
import com.horairesync.model.Term;
import com.horairesync.support.FixtureHtmlSource;
import com.horairesync.support.TestProperties;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Generates a file, then reads it back with ical4j's parser to check what a calendar app would see:
 * occurrences, excluded dates, time zone, titles and stable UIDs.
 */
class IcsExportServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private final IcsExportService service = new IcsExportService(FIXED);

    private static Calendar parse(String ics) throws Exception {
        return new CalendarBuilder().build(new StringReader(ics));
    }

    private static List<VEvent> events(Calendar calendar) {
        return calendar.getComponents(Component.VEVENT).stream().map(VEvent.class::cast).toList();
    }

    private static VEvent eventWithSummary(Calendar calendar, String summary) {
        return events(calendar).stream()
                .filter(e -> e.getSummary().getValue().equals(summary))
                .findFirst().orElseThrow(() -> new AssertionError("No event " + summary));
    }

    /** Local dates of all occurrences between January and June 2027. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<LocalDate> occurrences(VEvent event) {
        Period<ZonedDateTime> window = new Period<>(
                ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, IcsExportService.ZONE),
                ZonedDateTime.of(2027, 7, 1, 0, 0, 0, 0, IcsExportService.ZONE));
        Set<LocalDate> dates = new TreeSet<>();
        for (Object p : event.calculateRecurrenceSet(window)) {
            ZonedDateTime start = ZonedDateTime.from(((Period) p).getStart());
            dates.add(start.withZoneSameInstant(IcsExportService.ZONE).toLocalDate());
        }
        return dates;
    }

    private static String property(VEvent event, String name) {
        return event.getProperty(name).map(Property::getValue).orElse(null);
    }

    /** End-to-end: INF1120 gr. 001 Hiver 2027 read from the fixtures, holidays and reading week excluded. */
    @Nested
    class WithUqamFixtures {

        private final UqamAdapter adapter = new UqamAdapter(FixtureHtmlSource.uqam(), TestProperties.defaults());
        private final Term winter = adapter.fetchTerms().stream().filter(t -> t.id().equals("20271"))
                .findFirst().orElseThrow();
        private final Section section001 = adapter.fetchSections("20271", "INF1120").get(0);

        private ExportRequest request() {
            List<LocalDate> excluded = new ArrayList<>(winter.excludedDates());
            excluded.addAll(winter.readingWeekDates());
            return new ExportRequest("uqam", "20271", "Hiver 2027", winter.startDate(), winter.endDate(), excluded,
                    List.of(new SelectedSection("Programmation I", section001)));
        }

        @Test
        void fileIsReadBackWithOneEventPerMeeting() throws Exception {
            IcsExportService.IcsFile file = service.export(request());
            Calendar calendar = parse(file.content());

            assertThat(file.filename()).isEqualTo("horaire-uqam-20271.ics");
            assertThat(events(calendar)).hasSize(4); // lecture, lab, two exams
            assertThat(property(calendar, "X-WR-CALNAME")).isEqualTo("Horaire Hiver 2027");
            assertThat(calendar.getProperty(Property.PRODID).orElseThrow().getValue())
                    .isEqualTo(IcsExportService.PRODUCT_ID);
            assertThat(calendar.getProperty(Property.VERSION).orElseThrow().getValue()).isEqualTo("2.0");
        }

        @Test
        void passesIcal4jValidation() throws Exception {
            Calendar calendar = parse(service.export(request()).content());

            assertThat(calendar.validate().hasErrors()).as(calendar.validate().toString()).isFalse();
        }

        @Test
        void weeklyLectureRepeatsUntilTheEndOfTermExceptExcludedDays() throws Exception {
            Calendar calendar = parse(service.export(request()).content());
            VEvent lecture = eventWithSummary(calendar, "INF1120 Programmation I (Gr. 1)");

            Set<LocalDate> dates = occurrences(lecture);

            // Mondays from Jan 11 to Apr 26 (16), minus Mar 1 (reading week) and Mar 29 (holiday)
            assertThat(dates).hasSize(14)
                    .contains(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 4, 26))
                    .doesNotContain(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 29));
            assertThat(dates).allMatch(d -> d.getDayOfWeek() == MONDAY);
        }

        @Test
        void eventsKeepLocalTimeAcrossTheDaylightSavingChange() throws Exception {
            Calendar calendar = parse(service.export(request()).content());
            VEvent lecture = eventWithSummary(calendar, "INF1120 Programmation I (Gr. 1)");

            ZonedDateTime start = ZonedDateTime.from(lecture.getDateTimeStart().getDate());
            assertThat(start.toLocalDateTime()).isEqualTo(LocalDate.of(2027, 1, 11).atTime(9, 30));
            assertThat(start.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
            // Apr 12 is after the switch to EDT on Mar 14: still 09:30 local, now UTC-4
            assertThat(occurrences(lecture)).contains(LocalDate.of(2027, 4, 12));
        }

        @Test
        void labTitleLocationAndExams() throws Exception {
            Calendar calendar = parse(service.export(request()).content());

            VEvent lab = eventWithSummary(calendar, "INF1120 Programmation I (Gr. 1) - Laboratoire");
            assertThat(property(lab, Property.LOCATION)).isEqualTo("D-4030");
            assertThat(occurrences(lab)).hasSize(15).doesNotContain(LocalDate.of(2027, 3, 3));

            List<VEvent> exams = events(calendar).stream()
                    .filter(e -> e.getSummary().getValue().startsWith("Examen : INF1120")).toList();
            assertThat(exams).hasSize(2);
            assertThat(exams).allSatisfy(e -> assertThat(e.getProperty(Property.RRULE)).isEmpty());
            assertThat(exams).extracting(e -> ZonedDateTime.from(e.getDateTimeStart().getDate()).toLocalDate())
                    .containsExactly(LocalDate.of(2027, 2, 27), LocalDate.of(2027, 5, 1));
        }

        @Test
        void unknownInstructorIsOmittedFromTheDescription() throws Exception {
            Calendar calendar = parse(service.export(request()).content());
            VEvent lecture = eventWithSummary(calendar, "INF1120 Programmation I (Gr. 1)");

            assertThat(property(lecture, Property.DESCRIPTION))
                    .doesNotContain("Enseignant")
                    .contains("Mode : Présentiel")
                    .contains("Source : https://etudier.uqam.ca/cours?sigle=INF1120");
            assertThat(property(lecture, Property.LOCATION)).isEqualTo("UQAM Campus de Laval");
            assertThat(property(lecture, Property.URL)).isEqualTo("https://etudier.uqam.ca/cours?sigle=INF1120");
        }

        @Test
        void uidsAreUniqueAndStableBetweenExports() throws Exception {
            List<String> first = events(parse(service.export(request()).content())).stream()
                    .map(e -> e.getUid().orElseThrow().getValue()).toList();
            List<String> second = events(parse(new IcsExportService().export(request()).content())).stream()
                    .map(e -> e.getUid().orElseThrow().getValue()).toList();

            assertThat(first).doesNotHaveDuplicates().isEqualTo(second);
            assertThat(first).contains("uqam-20271-inf1120-001-lecture-mon-0930@horairesync",
                    "uqam-20271-inf1120-001-exam-20270227-0930@horairesync");
        }
    }

    @Nested
    class RawFormat {

        private final Section section = section("INF1120", "020", weekly(MONDAY, "09:30", "12:30"));
        private final ExportRequest request = new ExportRequest("uqam", "20271", null, null, null,
                List.of(LocalDate.of(2027, 3, 1)), List.of(new SelectedSection("Programmation I", section)));

        @Test
        void usesCrlfTimeZoneReferencesAndUtcUntil() {
            String ics = service.export(request).content();

            assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
            assertThat(ics).contains("VERSION:2.0\r\n", "CALSCALE:GREGORIAN\r\n",
                    "X-WR-TIMEZONE:America/Toronto\r\n");
            assertThat(ics).doesNotContain("METHOD:");
            assertThat(ics).contains("DTSTART;TZID=America/Toronto:20270111T093000\r\n");
            assertThat(ics).contains("DTEND;TZID=America/Toronto:20270111T123000\r\n");
            // 2 May 2027 23:59:59 EDT is 3 May 03:59:59 UTC
            assertThat(ics).contains("RRULE:FREQ=WEEKLY;UNTIL=20270503T035959Z\r\n");
            assertThat(ics).contains("EXDATE;TZID=America/Toronto:20270301T093000\r\n");
            assertThat(ics).contains("DTSTAMP:20261104T120000Z\r\n");
            assertThat(ics).contains("SUMMARY:INF1120 Programmation I (Gr. 20)\r\n");
        }

        @Test
        void compactVTimeZoneWithCurrentRules() {
            String ics = service.export(request).content();
            String vtimezone = ics.substring(ics.indexOf("BEGIN:VTIMEZONE"), ics.indexOf("END:VTIMEZONE"));

            assertThat(vtimezone).contains("TZID:America/Toronto", "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU",
                    "RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU", "TZOFFSETFROM:-0500", "TZOFFSETTO:-0400",
                    "TZNAME:EDT", "TZNAME:EST");
            assertThat(vtimezone.split("BEGIN:DAYLIGHT", -1)).hasSize(2);
            assertThat(vtimezone.split("BEGIN:STANDARD", -1)).hasSize(2);
        }

        @Test
        void longLinesAreFoldedAndTextIsEscaped() throws Exception {
            Section longOne = new Section("INF1120", "020", "Prof, avec; virgule", DeliveryMode.HYBRID,
                    "Campus de Montréal", List.of(weekly(MONDAY, "09:30", "12:30")), List.of(), List.of(),
                    "https://etudier.uqam.ca/cours?sigle=INF1120");
            String title = "Un titre de cours particulièrement long pour vérifier le pliage des lignes à 75 octets";
            ExportRequest req = new ExportRequest("uqam", "20271", null, null, null, List.of(),
                    List.of(new SelectedSection(title, longOne)));

            String ics = service.export(req).content();

            assertThat(ics.split("\r\n")).allSatisfy(line -> assertThat(line.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
            VEvent event = events(parse(ics)).get(0);
            assertThat(event.getSummary().getValue()).isEqualTo("INF1120 " + title + " (Gr. 20)");
            assertThat(property(event, Property.DESCRIPTION)).contains("Enseignant : Prof, avec; virgule")
                    .contains("Mode : Hybride");
        }
    }

    @Nested
    class ManualMode {

        @Test
        void termDatesTypedByTheUserBoundMeetingsWithoutDates() throws Exception {
            Meeting noDates = new Meeting(MONDAY, LocalTime.of(18, 0), LocalTime.of(21, 0), "B-2100", null,
                    MeetingType.LECTURE, null, null, null);
            Section manual = new Section("GLO-2004", "A", null, DeliveryMode.IN_PERSON, null, List.of(noDates),
                    List.of(), List.of(), null);
            ExportRequest req = new ExportRequest("ulaval", "h27", "Hiver 2027", LocalDate.of(2027, 1, 11),
                    LocalDate.of(2027, 1, 31), List.of(), List.of(new SelectedSection("Génie logiciel", manual)));

            Calendar calendar = parse(service.export(req).content());
            VEvent event = events(calendar).get(0);

            assertThat(occurrences(event)).containsExactly(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 18),
                    LocalDate.of(2027, 1, 25));
            assertThat(property(event, Property.LOCATION)).isEqualTo("B-2100");
            assertThat(property(event, Property.URL)).isNull();
            assertThat(event.getSummary().getValue()).isEqualTo("GLO-2004 Génie logiciel (Gr. A)");
        }

        @Test
        void onlineSectionLocationIsOnline() {
            Section online = section("ORH1163", "012", DeliveryMode.ONLINE,
                    weekly(MONDAY, "18:00", "21:00", null, MeetingType.LECTURE));

            assertThat(IcsExportService.location(online, online.meetings().get(0))).isEqualTo("En ligne");
        }

        @Test
        void weekdayNeverInRangeProducesNoEvent() throws Exception {
            Meeting friday = new Meeting(java.time.DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), null,
                    null, MeetingType.LECTURE, null, LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 13));
            ExportRequest req = new ExportRequest("uqam", "20271", null, null, null, List.of(),
                    List.of(selected(section("A", "1", friday))));

            assertThat(events(parse(service.export(req).content()))).isEmpty();
        }

        @Test
        void duplicateMeetingsProduceOneEvent() throws Exception {
            Section twice = section("INF1120", "020", weekly(MONDAY, "09:30", "12:30"));
            ExportRequest req = new ExportRequest("uqam", "20271", null, null, null, List.of(),
                    List.of(selected(twice), selected(twice)));

            assertThat(events(parse(service.export(req).content()))).hasSize(1);
        }
    }

    @Nested
    class Validation {

        private void assertInvalid(ThrowingCallable call, String... fragments) {
            assertThatThrownBy(call)
                    .isInstanceOf(HoraireSyncException.class)
                    .satisfies(e -> assertThat(((HoraireSyncException) e).code()).isEqualTo(ErrorCode.INVALID_SCHEDULE))
                    .hasMessageContainingAll(fragments);
        }

        private ExportRequest with(Meeting meeting, LocalDate termStart, LocalDate termEnd) {
            return new ExportRequest("uqam", "20271", null, termStart, termEnd, List.of(),
                    List.of(selected(section("INF1120", "020", meeting))));
        }

        @Test
        void emptyScheduleIsRejected() {
            assertInvalid(() -> service.export(new ExportRequest("uqam", "20271", null, null, null, null, null)),
                    "aucun groupe");
        }

        @Test
        void missingDayOrTimesAreRejected() {
            Meeting incomplete = new Meeting(null, null, null, null, null, MeetingType.LECTURE, null,
                    LocalDate.of(2027, 1, 11), LocalDate.of(2027, 5, 2));

            assertInvalid(() -> service.export(with(incomplete, null, null)),
                    "jour manquant", "heure de début manquante", "heure de fin manquante");
        }

        @Test
        void missingTermDatesAreRejectedOnlyWhenTheMeetingHasNone() {
            Meeting noDates = new Meeting(MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), null, null,
                    MeetingType.LECTURE, null, null, null);

            assertInvalid(() -> service.export(with(noDates, null, null)),
                    "date de début de session manquante", "date de fin de session manquante");
            assertThat(service.export(with(noDates, LocalDate.of(2027, 1, 11), LocalDate.of(2027, 5, 2))).content())
                    .contains("BEGIN:VEVENT");
        }

        @Test
        void invertedTimesAndDatesAreRejected() {
            Meeting inverted = new Meeting(MONDAY, LocalTime.of(12, 0), LocalTime.of(9, 0), null, null,
                    MeetingType.LECTURE, null, LocalDate.of(2027, 5, 2), LocalDate.of(2027, 1, 11));

            assertInvalid(() -> service.export(with(inverted, null, null)),
                    "l'heure de fin précède", "la date de fin précède");
        }

        @Test
        void sectionWithoutMeetingsOrCodeIsRejected() {
            ExportRequest noMeetings = new ExportRequest("uqam", "20271", null, null, null, List.of(),
                    List.of(selected(section("INF1120", "020"))));
            ExportRequest noCode = new ExportRequest("uqam", "20271", null, null, null, List.of(),
                    List.of(new SelectedSection("x", null)));

            assertInvalid(() -> service.export(noMeetings), "INF1120 gr. 020 : aucune séance");
            assertInvalid(() -> service.export(noCode), "groupe sans sigle");
        }

        @Test
        void oneOffWithWrongWeekdayIsRejected() {
            LocalDate saturday = LocalDate.of(2027, 2, 27);
            Meeting wrong = new Meeting(MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), null, null,
                    MeetingType.EXAM, saturday, saturday, saturday);

            assertInvalid(() -> service.export(with(wrong, null, null)), "le jour ne correspond pas");
        }
    }

    private static String property(Calendar calendar, String name) {
        return calendar.getProperty(name).map(Property::getValue).orElse(null);
    }
}
