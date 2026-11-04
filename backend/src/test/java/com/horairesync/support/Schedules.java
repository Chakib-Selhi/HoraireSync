package com.horairesync.support;

import com.horairesync.model.DeliveryMode;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Section;
import com.horairesync.model.SelectedSection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Small builders for schedule tests (Hiver 2027 dates by default). */
public final class Schedules {

    public static final LocalDate TERM_START = LocalDate.of(2027, 1, 11);
    public static final LocalDate TERM_END = LocalDate.of(2027, 5, 2);

    private Schedules() {}

    public static Meeting weekly(DayOfWeek day, String start, String end) {
        return weekly(day, start, end, "Campus de Montréal", MeetingType.LECTURE);
    }

    public static Meeting weekly(DayOfWeek day, String start, String end, String campus, MeetingType type) {
        return new Meeting(day, LocalTime.parse(start), LocalTime.parse(end), null, campus, type, null,
                TERM_START, TERM_END);
    }

    public static Meeting exam(LocalDate date, String start, String end) {
        return new Meeting(date.getDayOfWeek(), LocalTime.parse(start), LocalTime.parse(end), null, null,
                MeetingType.EXAM, date, date, date);
    }

    public static Section section(String course, String number, DeliveryMode mode, Meeting... meetings) {
        return new Section(course, number, "Ada Lovelace", mode, null, List.of(meetings), List.of(), List.of(),
                "https://etudier.uqam.ca/cours?sigle=" + course);
    }

    public static Section section(String course, String number, Meeting... meetings) {
        return section(course, number, DeliveryMode.IN_PERSON, meetings);
    }

    public static SelectedSection selected(Section section) {
        return new SelectedSection("Titre " + section.courseCode(), section);
    }
}
