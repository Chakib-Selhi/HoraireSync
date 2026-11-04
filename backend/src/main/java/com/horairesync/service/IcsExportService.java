package com.horairesync.service;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.DeliveryMode;
import com.horairesync.model.ExportRequest;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Section;
import com.horairesync.model.SelectedSection;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import org.springframework.stereotype.Service;

/**
 * Generates the iCalendar file (RFC 5545) of a confirmed schedule with ical4j.
 *
 * <ul>
 *   <li>One VEVENT per weekly meeting with {@code RRULE:FREQ=WEEKLY;UNTIL=…} and {@code EXDATE} for excluded days.</li>
 *   <li>One VEVENT per one-off meeting (exam).</li>
 *   <li>Times are local to {@code America/Toronto}, declared in a VTIMEZONE block.</li>
 *   <li>No {@code METHOD} property: it belongs to iTIP scheduling (RFC 5546), where {@code PUBLISH} requires an
 *       {@code ORGANIZER} we do not have. Without it the file is a plain RFC 5545 calendar to import.</li>
 *   <li>Each UID is derived from the meeting (school, term, course, section, type, day, start), so importing the file
 *       again updates the events instead of duplicating them in calendars that honour UIDs.</li>
 *   <li>Optional values (instructor, room) are left out when unknown; the export is refused with
 *       {@code INVALID_SCHEDULE} when a required value (day, start, end, dates) is missing.</li>
 * </ul>
 */
@Service
public class IcsExportService {

    public static final ZoneId ZONE = ZoneId.of("America/Toronto");
    static final String PRODUCT_ID = "-//HoraireSync//HoraireSync 0.1//FR";
    private static final DateTimeFormatter UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter COMPACT_TIME = DateTimeFormatter.ofPattern("HHmm");

    private final Clock clock;

    public IcsExportService() {
        this(Clock.systemUTC());
    }

    IcsExportService(Clock clock) {
        this.clock = clock;
    }

    /** Generated file content and suggested file name. */
    public record IcsFile(String filename, String content) {}

    public IcsFile export(ExportRequest request) {
        validate(request);

        Calendar calendar = new Calendar()
                .withProdId(PRODUCT_ID)
                .withProperty(ImmutableVersion.VERSION_2_0)
                .withProperty(ImmutableCalScale.GREGORIAN)
                .withProperty(new XProperty("X-WR-CALNAME", calendarName(request)))
                .withProperty(new XProperty("X-WR-TIMEZONE", ZONE.getId()))
                .withComponent(IcsTimeZones.vTimeZone(ZONE))
                .getFluentTarget();

        Set<String> uids = new LinkedHashSet<>();
        for (SelectedSection selection : request.selections()) {
            for (Meeting meeting : selection.section().meetings()) {
                VEvent event = meeting.isOneOff()
                        ? oneOffEvent(request, selection, meeting)
                        : weeklyEvent(request, selection, meeting);
                if (event != null && uids.add(event.getUid().map(Uid::getValue).orElseThrow())) {
                    calendar.add(event);
                }
            }
        }
        return new IcsFile(filename(request), write(calendar));
    }

    private VEvent weeklyEvent(ExportRequest request, SelectedSection selection, Meeting meeting) {
        LocalDate from = meeting.firstDate() != null ? meeting.firstDate() : request.termStart();
        LocalDate to = meeting.lastDate() != null ? meeting.lastDate() : request.termEnd();
        LocalDate firstOccurrence = from.with(TemporalAdjusters.nextOrSame(meeting.day()));
        if (firstOccurrence.isAfter(to)) {
            return null; // the weekday never occurs in the range
        }
        VEvent event = baseEvent(request, selection, meeting, firstOccurrence);
        // UNTIL must be in UTC when DTSTART has a TZID (RFC 5545, 3.3.10); end of the last day, local time.
        String until = ZonedDateTime.of(to, LocalTime.of(23, 59, 59), ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).format(UTC_STAMP);
        event.add(new RRule<>(new Recur<ZonedDateTime>("FREQ=WEEKLY;UNTIL=" + until)));

        List<ZonedDateTime> excluded = request.excludedDates().stream()
                .filter(d -> d.getDayOfWeek() == meeting.day())
                .filter(d -> !d.isBefore(firstOccurrence) && !d.isAfter(to))
                .distinct()
                .sorted()
                .map(d -> ZonedDateTime.of(d, meeting.start(), ZONE))
                .toList();
        if (!excluded.isEmpty()) {
            ParameterList tz = new ParameterList(List.of(new TzId(ZONE.getId())));
            event.add(new ExDate<>(tz, new DateList<>(excluded)));
        }
        return event;
    }

    private VEvent oneOffEvent(ExportRequest request, SelectedSection selection, Meeting meeting) {
        return baseEvent(request, selection, meeting, meeting.onlyDate());
    }

    private VEvent baseEvent(ExportRequest request, SelectedSection selection, Meeting meeting, LocalDate date) {
        Section section = selection.section();
        VEvent event = new VEvent(ZonedDateTime.of(date, meeting.start(), ZONE),
                ZonedDateTime.of(date, meeting.end(), ZONE), summary(selection, meeting));
        event.replace(new DtStamp(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        event.add(new Uid(uid(request, section, meeting)));
        String location = location(section, meeting);
        if (location != null) {
            event.add(new Location(location));
        }
        event.add(new Description(description(section)));
        if (section.sourceUrl() != null && section.sourceUrl().startsWith("http")) {
            event.add(new Url(URI.create(section.sourceUrl())));
        }
        return event;
    }

    /** "INF1120 Programmation I (Gr. 20)", with " - Laboratoire" for labs and "Examen : " for exams. */
    static String summary(SelectedSection selection, Meeting meeting) {
        Section section = selection.section();
        StringBuilder title = new StringBuilder(section.courseCode());
        if (selection.courseTitle() != null && !selection.courseTitle().isBlank()) {
            title.append(' ').append(selection.courseTitle().trim());
        }
        if (section.sectionNumber() != null && !section.sectionNumber().isBlank()) {
            title.append(" (Gr. ").append(stripLeadingZeros(section.sectionNumber())).append(')');
        }
        if (meeting.type() == MeetingType.LAB) {
            title.append(" - Laboratoire");
        }
        return meeting.type() == MeetingType.EXAM ? "Examen : " + title : title.toString();
    }

    static String location(Section section, Meeting meeting) {
        if (section.mode() == DeliveryMode.ONLINE) {
            return "En ligne";
        }
        List<String> parts = new ArrayList<>();
        if (meeting.room() != null && !meeting.room().isBlank()) {
            parts.add(meeting.room().trim());
        }
        String campus = meeting.campus() != null ? meeting.campus() : section.campus();
        if (campus != null && !campus.isBlank()) {
            parts.add(campus.trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static String description(Section section) {
        List<String> lines = new ArrayList<>();
        if (section.instructor() != null && !section.instructor().isBlank()) {
            lines.add("Enseignant : " + section.instructor().trim());
        }
        String mode = switch (section.mode() == null ? DeliveryMode.UNKNOWN : section.mode()) {
            case IN_PERSON -> "Présentiel";
            case ONLINE -> "En ligne";
            case HYBRID -> "Hybride";
            case UNKNOWN -> null;
        };
        if (mode != null) {
            lines.add("Mode : " + mode);
        }
        if (section.sourceUrl() != null) {
            lines.add("Source : " + section.sourceUrl());
        }
        lines.add("Généré par HoraireSync");
        return String.join("\n", lines);
    }

    static String uid(ExportRequest request, Section section, Meeting meeting) {
        String when = meeting.isOneOff()
                ? meeting.onlyDate().format(COMPACT_DATE)
                : meeting.day().name().substring(0, 3);
        String raw = String.join("-", nullToDash(request.schoolId()), nullToDash(request.termId()),
                section.courseCode(), nullToDash(section.sectionNumber()), meeting.type() == null ? "other"
                        : meeting.type().name(), when, meeting.start().format(COMPACT_TIME));
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "") + "@horairesync";
    }

    private static String calendarName(ExportRequest request) {
        return request.termLabel() == null || request.termLabel().isBlank()
                ? "Horaire" : "Horaire " + request.termLabel().trim();
    }

    static String filename(ExportRequest request) {
        String name = String.join("-", "horaire", nullToDash(request.schoolId()), nullToDash(request.termId()))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return name + ".ics";
    }

    private static String write(Calendar calendar) {
        try (StringWriter writer = new StringWriter()) {
            // validation is done by our own rules above; ical4j folds lines and writes CRLF
            new CalendarOutputter(false).output(calendar, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Rejects a schedule that would produce a wrong or empty calendar. */
    static void validate(ExportRequest request) {
        List<String> problems = new ArrayList<>();
        if (request.selections().isEmpty()) {
            problems.add("aucun groupe sélectionné");
        }
        for (SelectedSection selection : request.selections()) {
            Section section = selection == null ? null : selection.section();
            if (section == null || section.courseCode() == null || section.courseCode().isBlank()) {
                problems.add("groupe sans sigle de cours");
                continue;
            }
            String label = section.sectionNumber() == null || section.sectionNumber().isBlank()
                    ? section.courseCode() : section.courseCode() + " gr. " + section.sectionNumber();
            if (section.meetings().isEmpty()) {
                problems.add(label + " : aucune séance");
            }
            for (int i = 0; i < section.meetings().size(); i++) {
                problems.addAll(meetingProblems(request, label + " séance " + (i + 1), section.meetings().get(i)));
            }
        }
        if (!problems.isEmpty()) {
            throw new HoraireSyncException(ErrorCode.INVALID_SCHEDULE,
                    "Horaire incomplet : " + String.join(" ; ", problems) + ".");
        }
    }

    private static List<String> meetingProblems(ExportRequest request, String label, Meeting meeting) {
        List<String> problems = new ArrayList<>();
        if (meeting.day() == null) {
            problems.add(label + " : jour manquant");
        }
        if (meeting.start() == null) {
            problems.add(label + " : heure de début manquante");
        }
        if (meeting.end() == null) {
            problems.add(label + " : heure de fin manquante");
        }
        if (meeting.start() != null && meeting.end() != null && !meeting.start().isBefore(meeting.end())) {
            problems.add(label + " : l'heure de fin précède l'heure de début");
        }
        if (meeting.isOneOff()) {
            if (meeting.day() != null && meeting.onlyDate().getDayOfWeek() != meeting.day()) {
                problems.add(label + " : le jour ne correspond pas à la date");
            }
            return problems;
        }
        LocalDate from = meeting.firstDate() != null ? meeting.firstDate() : request.termStart();
        LocalDate to = meeting.lastDate() != null ? meeting.lastDate() : request.termEnd();
        if (from == null) {
            problems.add(label + " : date de début de session manquante");
        }
        if (to == null) {
            problems.add(label + " : date de fin de session manquante");
        }
        if (from != null && to != null && to.isBefore(from)) {
            problems.add(label + " : la date de fin précède la date de début");
        }
        return problems;
    }

    private static String stripLeadingZeros(String number) {
        String stripped = number.trim().replaceFirst("^0+(?=.)", "");
        return stripped.isEmpty() ? number : stripped;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "x" : value.trim();
    }
}
