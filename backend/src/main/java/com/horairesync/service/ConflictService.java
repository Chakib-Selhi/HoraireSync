package com.horairesync.service;

import com.horairesync.model.Conflict;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingRef;
import com.horairesync.model.Section;
import com.horairesync.model.SelectedSection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Finds overlapping meetings between chosen sections.
 *
 * <p>Two meetings conflict when their times overlap on the same day and their dates can coincide:
 * <ul>
 *   <li>two weekly meetings: same weekday and overlapping date ranges;</li>
 *   <li>a weekly meeting and a one-off (exam): the one-off falls on that weekday within the weekly range;</li>
 *   <li>two one-offs: same date.</li>
 * </ul>
 * A missing date bound is treated as open, so an unknown range never hides a conflict. A meeting without day, start
 * or end cannot be placed and is skipped; the frontend asks the user to fill it first. Meetings touching end to start
 * (10:00 to 12:00 then 12:00 to 13:00) do not conflict.
 */
@Service
public class ConflictService {

    /** All conflicts between the selections, each pair reported once. */
    public List<Conflict> findConflicts(List<SelectedSection> selections) {
        List<Section> sections = selections.stream().map(SelectedSection::section).filter(Objects::nonNull).toList();
        List<Conflict> conflicts = new ArrayList<>();
        for (int a = 0; a < sections.size(); a++) {
            for (int b = a + 1; b < sections.size(); b++) {
                if (!sameSection(sections.get(a), sections.get(b))) {
                    conflicts.addAll(between(sections.get(a), sections.get(b)));
                }
            }
        }
        return conflicts;
    }

    /** Conflicts between a candidate and the chosen sections of other courses. */
    public List<Conflict> conflictsWith(Section candidate, List<SelectedSection> chosen) {
        List<Conflict> conflicts = new ArrayList<>();
        for (SelectedSection selected : chosen) {
            Section other = selected.section();
            if (other != null && !Objects.equals(other.courseCode(), candidate.courseCode())) {
                conflicts.addAll(between(candidate, other));
            }
        }
        return conflicts;
    }

    /** Conflicts between every meeting of {@code a} and every meeting of {@code b}. */
    public List<Conflict> between(Section a, Section b) {
        List<Conflict> conflicts = new ArrayList<>();
        for (int i = 0; i < a.meetings().size(); i++) {
            for (int j = 0; j < b.meetings().size(); j++) {
                Optional<Conflict> conflict = compare(a, i, b, j);
                conflict.ifPresent(conflicts::add);
            }
        }
        return conflicts;
    }

    private static Optional<Conflict> compare(Section sa, int i, Section sb, int j) {
        Meeting a = sa.meetings().get(i);
        Meeting b = sb.meetings().get(j);
        if (!placeable(a) || !placeable(b) || a.day() != b.day()) {
            return Optional.empty();
        }
        if (!a.start().isBefore(b.end()) || !b.start().isBefore(a.end())) {
            return Optional.empty();
        }
        LocalDate date = null;
        if (a.isOneOff() && b.isOneOff()) {
            if (!a.onlyDate().equals(b.onlyDate())) {
                return Optional.empty();
            }
            date = a.onlyDate();
        } else if (a.isOneOff() || b.isOneOff()) {
            Meeting oneOff = a.isOneOff() ? a : b;
            Meeting weekly = a.isOneOff() ? b : a;
            if (!within(oneOff.onlyDate(), weekly.firstDate(), weekly.lastDate())) {
                return Optional.empty();
            }
            date = oneOff.onlyDate();
        } else if (!rangesOverlap(a, b)) {
            return Optional.empty();
        }
        LocalTime overlapStart = a.start().isAfter(b.start()) ? a.start() : b.start();
        LocalTime overlapEnd = a.end().isBefore(b.end()) ? a.end() : b.end();
        return Optional.of(new Conflict(ref(sa, i), ref(sb, j), a.day(), date, overlapStart, overlapEnd));
    }

    private static boolean placeable(Meeting m) {
        return m.day() != null && m.start() != null && m.end() != null && m.start().isBefore(m.end());
    }

    private static boolean within(LocalDate date, LocalDate first, LocalDate last) {
        return (first == null || !date.isBefore(first)) && (last == null || !date.isAfter(last));
    }

    private static boolean rangesOverlap(Meeting a, Meeting b) {
        boolean aEndsBeforeB = a.lastDate() != null && b.firstDate() != null && a.lastDate().isBefore(b.firstDate());
        boolean bEndsBeforeA = b.lastDate() != null && a.firstDate() != null && b.lastDate().isBefore(a.firstDate());
        return !aEndsBeforeB && !bEndsBeforeA;
    }

    private static boolean sameSection(Section a, Section b) {
        return Objects.equals(a.courseCode(), b.courseCode()) && Objects.equals(a.sectionNumber(), b.sectionNumber());
    }

    private static MeetingRef ref(Section section, int index) {
        return new MeetingRef(section.courseCode(), section.sectionNumber(), index, section.meetings().get(index).type());
    }
}
