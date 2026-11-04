package com.horairesync.adapter.uqam;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.Course;
import com.horairesync.model.DeliveryMode;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Section;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Parses a course page {@code /cours?sigle=XXX0000}. See {@code docs/phase0-uqam.md} for the page structure.
 *
 * <p>Rules applied (validated in phase 0):
 * <ul>
 *   <li>"Non disponible" and empty cells become {@code null} plus an entry in {@code missingFields}.</li>
 *   <li>Duplicate meeting rows are removed.</li>
 *   <li>A room is taken from the remarks only when a sentence names exactly one room and one meeting type.</li>
 *   <li>Exams are read from the "Examens:" remark as one-off meetings; the year is the term's year.</li>
 *   <li>For distance courses, rooms and campuses are ignored as the page itself instructs.</li>
 * </ul>
 */
final class UqamCourseParser {

    private static final List<String> EXPECTED_HEADERS = List.of("jour", "date", "heure", "lieu", "type");
    private static final String NOT_AVAILABLE = "non disponible";

    private static final Pattern SECTION_NUMBER = Pattern.compile("Groupe\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDITS = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern ROOM = Pattern.compile("\\b(?i:local|laboratoire|salle)\\s+([A-Z]{1,2}-?[A-Z]?\\d{3,4}(?:\\.\\d+)?)\\b");
    private static final Pattern EXAM_REMARK = Pattern.compile("^examens?\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXAM_SLOT = Pattern.compile(
            "(\\d{1,2})(?:er)?\\s+" + UqamText.MONTH_REGEX + "\\s+(\\d{1,2}[:h]\\d{2})\\s+à\\s+(\\d{1,2}[:h]\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private UqamCourseParser() {}

    /** Throws {@code COURSE_NOT_FOUND} or {@code PARSING_FAILED} when the page is not a usable course page. */
    static Course parseCourse(Document doc, String courseCode) {
        String title = requireCourseTitle(doc, courseCode);
        Integer credits = doc.select("li").stream()
                .filter(li -> UqamText.fold(li.select("span.label").text()).startsWith("nombre de credits"))
                .findFirst()
                .map(li -> li.ownText())
                .map(CREDITS::matcher)
                .filter(Matcher::find)
                .map(m -> (int) Double.parseDouble(m.group(1).replace(',', '.')))
                .orElse(null);
        List<String> offered = doc.select("[id^=groupes_wrapper]").stream()
                .filter(w -> !w.select("div.groupe").isEmpty())
                .map(w -> w.id().substring("groupes_wrapper".length()))
                .toList();
        return new Course(courseCode, title, credits, null, offered);
    }

    /** Sections of the course for the term; empty when the term has no published schedule. */
    static List<Section> parseSections(Document doc, String courseCode, UqamTermId term, String sourceUrl) {
        requireCourseTitle(doc, courseCode);
        Element wrapper = doc.getElementById("groupes_wrapper" + term.id());
        if (wrapper == null) {
            return List.of();
        }
        List<Section> sections = new ArrayList<>();
        for (Element group : wrapper.select("div.groupe")) {
            sections.add(parseSection(group, courseCode, term, sourceUrl));
        }
        return sections;
    }

    private static String requireCourseTitle(Document doc, String courseCode) {
        // Example: "Cours INF1120 | Programmation I | UQAM". An unknown code gives "Cours | UQAM" with HTTP 200.
        String[] parts = UqamText.clean(doc.title()).split("\\s*\\|\\s*");
        if (parts.length < 2 || !parts[0].equalsIgnoreCase("Cours " + courseCode)) {
            throw new HoraireSyncException(ErrorCode.COURSE_NOT_FOUND,
                    "Le cours " + courseCode + " est introuvable sur le site de l'UQAM.");
        }
        if (doc.selectFirst(".node-cours") == null) {
            throw parsingFailed(courseCode, "bloc .node-cours absent");
        }
        return parts[1];
    }

    private static Section parseSection(Element group, String courseCode, UqamTermId term, String sourceUrl) {
        Element numberElement = group.selectFirst("h3.no_groupe");
        Matcher numberMatcher = numberElement == null ? null : SECTION_NUMBER.matcher(numberElement.text());
        if (numberMatcher == null || !numberMatcher.find()) {
            throw parsingFailed(courseCode, "numéro de groupe introuvable");
        }
        String sectionNumber = numberMatcher.group(1);
        List<String> missing = new ArrayList<>();

        String instructor = listItemsUnder(group, "enseignant").stream()
                .filter(name -> !UqamText.fold(name).equals(NOT_AVAILABLE))
                .collect(Collectors.collectingAndThen(Collectors.joining(", "), UqamText::emptyToNull));
        if (instructor == null) {
            missing.add("instructor");
        }

        DeliveryMode mode = parseMode(group.selectFirst("div.avertissement"));
        if (mode == DeliveryMode.UNKNOWN) {
            missing.add("mode");
        }
        boolean online = mode == DeliveryMode.ONLINE;

        List<String> notes = listItemsUnder(group, "remarque");
        List<Meeting> weekly = parseMeetingRows(group, courseCode, online);
        weekly = applyRoomsFromNotes(weekly, notes, online);
        List<Meeting> exams = parseExams(notes, term);

        List<Meeting> meetings = new ArrayList<>(weekly);
        meetings.addAll(exams);

        String campus = null;
        Set<String> campuses = weekly.stream().map(Meeting::campus)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (campuses.size() == 1 && !campuses.contains(null)) {
            campus = campuses.iterator().next();
        } else if (!online) {
            missing.add("campus");
        }
        if (weekly.isEmpty()) {
            missing.add("meetings");
        }
        for (int i = 0; i < meetings.size(); i++) {
            missing.addAll(missingMeetingFields(meetings.get(i), i, online));
        }
        return new Section(courseCode, sectionNumber, instructor, mode, campus, meetings, notes, missing, sourceUrl);
    }

    /** Texts of the {@code li} elements in the {@code div.ligne} whose {@code h3} has the given title. */
    private static List<String> listItemsUnder(Element group, String foldedTitle) {
        for (Element line : group.select("div.ligne")) {
            Element title = line.selectFirst("h3");
            if (title != null && UqamText.fold(title.text()).equals(foldedTitle)) {
                return line.select("ul li").stream()
                        .map(li -> UqamText.clean(li.text()))
                        .filter(t -> !t.isEmpty())
                        .toList();
            }
        }
        return List.of();
    }

    static DeliveryMode parseMode(Element warning) {
        if (warning == null) {
            return DeliveryMode.UNKNOWN;
        }
        String text = UqamText.fold(warning.text());
        if (!text.startsWith("ce cours est donne")) {
            return DeliveryMode.UNKNOWN;
        }
        if (text.contains("hybride")) {
            return DeliveryMode.HYBRID;
        }
        if (text.contains("a distance") || text.contains("en ligne")) {
            return DeliveryMode.ONLINE;
        }
        if (text.contains("presentiel")) {
            return DeliveryMode.IN_PERSON;
        }
        return DeliveryMode.UNKNOWN;
    }

    private static List<Meeting> parseMeetingRows(Element group, String courseCode, boolean online) {
        Element table = group.select("table").stream()
                .filter(t -> !t.hasClass("alignement"))
                .findFirst()
                .orElse(null);
        if (table == null) {
            return List.of();
        }
        Elements rows = table.select("tr");
        List<String> headers = rows.isEmpty() ? List.of()
                : rows.get(0).select("th").stream().map(th -> UqamText.fold(th.text())).toList();
        boolean headersOk = headers.size() == EXPECTED_HEADERS.size();
        for (int i = 0; headersOk && i < headers.size(); i++) {
            headersOk = headers.get(i).startsWith(EXPECTED_HEADERS.get(i));
        }
        if (!headersOk) {
            throw parsingFailed(courseCode, "en-têtes du tableau d'horaire inattendus " + headers);
        }

        Set<Meeting> meetings = new LinkedHashSet<>();
        for (Element row : rows.subList(1, rows.size())) {
            Elements cells = row.select("td");
            if (cells.size() != EXPECTED_HEADERS.size()) {
                throw parsingFailed(courseCode, "ligne d'horaire à " + cells.size() + " colonnes");
            }
            if (cells.stream().allMatch(c -> UqamText.clean(c.text()).equals("-"))) {
                continue; // no schedule published for this section
            }
            meetings.add(parseMeetingRow(cells, online));
        }
        return List.copyOf(meetings);
    }

    private static Meeting parseMeetingRow(Elements cells, boolean online) {
        DayOfWeek day = UqamText.day(cells.get(0).text()).orElse(null);

        String dateText = cells.get(1).text();
        List<LocalDate> dates = UqamText.fullDates(dateText);
        LocalDate first = dates.isEmpty() ? null : dates.get(0);
        LocalDate last = dates.size() >= 2 ? dates.get(1) : null;
        LocalDate onlyDate = null;
        if (dates.size() == 1 && UqamText.fold(dateText).startsWith("le ")) {
            onlyDate = first; // single-date row, e.g. "Le 27 février 2027"
            last = first;
        }

        List<LocalTime> times = UqamText.times(cells.get(2).text());
        LocalTime start = times.size() == 2 ? times.get(0) : null;
        LocalTime end = times.size() == 2 ? times.get(1) : null;

        String campus = online ? null : UqamText.emptyToNull(cells.get(3).text());
        MeetingType type = meetingType(cells.get(4).text());
        if (day == null && onlyDate != null) {
            day = onlyDate.getDayOfWeek();
        }
        return new Meeting(day, start, end, null, campus, type, onlyDate, first, last);
    }

    static MeetingType meetingType(String text) {
        String folded = UqamText.fold(text);
        if (folded.contains("magistral")) {
            return MeetingType.LECTURE;
        }
        if (folded.contains("laboratoire") || folded.contains("atelier") || folded.contains("exercice")) {
            return MeetingType.LAB;
        }
        if (folded.contains("examen")) {
            return MeetingType.EXAM;
        }
        return MeetingType.OTHER;
    }

    /**
     * Applies rooms stated in remarks such as "Le cours magistral a lieu dans le local D-4119". A sentence is used
     * only when it contains exactly one room code and refers to exactly one meeting type.
     */
    private static List<Meeting> applyRoomsFromNotes(List<Meeting> meetings, List<String> notes, boolean online) {
        if (online || meetings.isEmpty()) {
            return meetings;
        }
        List<Meeting> result = new ArrayList<>(meetings);
        for (String note : notes) {
            Matcher m = ROOM.matcher(note);
            List<String> rooms = new ArrayList<>();
            while (m.find()) {
                rooms.add(m.group(1));
            }
            Optional<MeetingType> type = singleTypeMentioned(note);
            if (rooms.size() != 1 || type.isEmpty()) {
                continue;
            }
            for (int i = 0; i < result.size(); i++) {
                Meeting meeting = result.get(i);
                if (meeting.type() == type.get() && meeting.room() == null) {
                    result.set(i, meeting.withRoom(rooms.get(0)));
                }
            }
        }
        return List.copyOf(result);
    }

    private static Optional<MeetingType> singleTypeMentioned(String note) {
        String folded = UqamText.fold(note);
        boolean lecture = folded.contains("magistral");
        boolean lab = folded.contains("atelier") || folded.contains("exercice")
                || folded.matches(".*\\blabo(ratoire)?s?\\b.*");
        if (lecture && !lab) {
            return Optional.of(MeetingType.LECTURE);
        }
        if (lab && !lecture) {
            return Optional.of(MeetingType.LAB);
        }
        return Optional.empty();
    }

    /** Reads "Examens: 27 févr. 09:30 à 12:30 et 01 mai 09:30 à 12:30" as one-off meetings. */
    static List<Meeting> parseExams(List<String> notes, UqamTermId term) {
        List<Meeting> exams = new ArrayList<>();
        for (String note : notes) {
            if (!EXAM_REMARK.matcher(note).find()) {
                continue;
            }
            Matcher m = EXAM_SLOT.matcher(note);
            while (m.find()) {
                Optional<LocalDate> date = UqamText.safeDate(term.year(), m.group(2), Integer.parseInt(m.group(1)));
                List<LocalTime> start = UqamText.times(m.group(3));
                List<LocalTime> end = UqamText.times(m.group(4));
                if (date.isEmpty() || start.size() != 1 || end.size() != 1) {
                    continue;
                }
                LocalDate d = date.get();
                exams.add(new Meeting(d.getDayOfWeek(), start.get(0), end.get(0), null, null, MeetingType.EXAM,
                        d, d, d));
            }
        }
        return exams;
    }

    private static List<String> missingMeetingFields(Meeting meeting, int index, boolean online) {
        List<String> missing = new ArrayList<>();
        String prefix = "meetings[" + index + "].";
        if (meeting.day() == null) {
            missing.add(prefix + "day");
        }
        if (meeting.start() == null) {
            missing.add(prefix + "start");
        }
        if (meeting.end() == null) {
            missing.add(prefix + "end");
        }
        if (meeting.firstDate() == null) {
            missing.add(prefix + "firstDate");
        }
        if (meeting.lastDate() == null) {
            missing.add(prefix + "lastDate");
        }
        if (!online && meeting.room() == null) {
            missing.add(prefix + "room");
        }
        if (!online && meeting.campus() == null) {
            missing.add(prefix + "campus");
        }
        return missing;
    }

    private static HoraireSyncException parsingFailed(String courseCode, String detail) {
        return new HoraireSyncException(ErrorCode.PARSING_FAILED,
                "La page du cours " + courseCode + " a une structure inattendue (" + detail + ").");
    }
}
