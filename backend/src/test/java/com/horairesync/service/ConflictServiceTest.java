package com.horairesync.service;

import static com.horairesync.support.Schedules.exam;
import static com.horairesync.support.Schedules.section;
import static com.horairesync.support.Schedules.selected;
import static com.horairesync.support.Schedules.weekly;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.horairesync.model.Conflict;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Section;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConflictServiceTest {

    private final ConflictService service = new ConflictService();

    @Test
    void overlappingWeeklyMeetingsConflict() {
        Section a = section("INF1120", "010", weekly(MONDAY, "09:30", "12:30"));
        Section b = section("MAT1060", "020", weekly(MONDAY, "11:00", "14:00"));

        List<Conflict> conflicts = service.findConflicts(List.of(selected(a), selected(b)));

        assertThat(conflicts).singleElement().satisfies(c -> {
            assertThat(c.first().courseCode()).isEqualTo("INF1120");
            assertThat(c.second().courseCode()).isEqualTo("MAT1060");
            assertThat(c.day()).isEqualTo(MONDAY);
            assertThat(c.date()).isNull();
            assertThat(c.overlapStart()).isEqualTo(LocalTime.of(11, 0));
            assertThat(c.overlapEnd()).isEqualTo(LocalTime.of(12, 30));
        });
    }

    @Test
    void backToBackMeetingsDoNotConflict() {
        Section a = section("INF1120", "010", weekly(MONDAY, "09:30", "12:30"));
        Section b = section("MAT1060", "020", weekly(MONDAY, "12:30", "14:00"));

        assertThat(service.findConflicts(List.of(selected(a), selected(b)))).isEmpty();
    }

    @Test
    void differentDaysDoNotConflict() {
        Section a = section("INF1120", "010", weekly(MONDAY, "09:30", "12:30"));
        Section b = section("MAT1060", "020", weekly(TUESDAY, "09:30", "12:30"));

        assertThat(service.findConflicts(List.of(selected(a), selected(b)))).isEmpty();
    }

    @Test
    void weeklyMeetingsInDisjointDateRangesDoNotConflict() {
        Meeting firstHalf = new Meeting(MONDAY, LocalTime.of(9, 30), LocalTime.of(12, 30), null, null,
                MeetingType.LECTURE, null, LocalDate.of(2027, 1, 11), LocalDate.of(2027, 2, 28));
        Meeting secondHalf = new Meeting(MONDAY, LocalTime.of(9, 30), LocalTime.of(12, 30), null, null,
                MeetingType.LECTURE, null, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 2));

        assertThat(service.between(section("A", "1", firstHalf), section("B", "1", secondHalf))).isEmpty();
    }

    @Test
    void unknownDateRangeIsTreatedAsOpen() {
        Meeting noDates = new Meeting(MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0), null, null,
                MeetingType.LECTURE, null, null, null);

        assertThat(service.between(section("A", "1", noDates), section("B", "1", weekly(MONDAY, "09:30", "12:30"))))
                .hasSize(1);
    }

    @Test
    void examsOnTheSameDateAndTimeConflict() {
        LocalDate day = LocalDate.of(2027, 2, 27);
        Section a = section("INF1120", "010", exam(day, "09:30", "12:30"));
        Section b = section("MAT1060", "020", exam(day, "12:00", "15:00"), exam(LocalDate.of(2027, 3, 6), "09:30",
                "12:30"));

        List<Conflict> conflicts = service.findConflicts(List.of(selected(a), selected(b)));

        assertThat(conflicts).singleElement().satisfies(c -> {
            assertThat(c.date()).isEqualTo(day);
            assertThat(c.day()).isEqualTo(SATURDAY);
            assertThat(c.second().type()).isEqualTo(MeetingType.EXAM);
        });
    }

    @Test
    void examDuringAWeeklyMeetingConflictsOnlyInsideItsDateRange() {
        Section weeklySat = section("INF1120", "010", weekly(SATURDAY, "09:00", "12:00"));
        Section examInside = section("MAT1060", "020", exam(LocalDate.of(2027, 2, 27), "10:00", "11:00"));
        Section examAfter = section("ORH1163", "030", exam(LocalDate.of(2027, 5, 8), "10:00", "11:00"));

        assertThat(service.between(weeklySat, examInside)).hasSize(1);
        assertThat(service.between(examAfter, weeklySat)).isEmpty();
    }

    @Test
    void meetingsWithoutDayOrTimeAreSkipped() {
        Meeting unknown = new Meeting(null, null, null, null, null, MeetingType.OTHER, null, null, null);

        assertThat(service.between(section("A", "1", unknown), section("B", "1", weekly(MONDAY, "09:00", "12:00"))))
                .isEmpty();
    }

    @Test
    void sameSectionSelectedTwiceIsNotAConflict() {
        Section a = section("INF1120", "010", weekly(MONDAY, "09:30", "12:30"));

        assertThat(service.findConflicts(List.of(selected(a), selected(a)))).isEmpty();
    }

    @Test
    void reportsEveryPairAmongThreeSections() {
        Section a = section("A", "1", weekly(MONDAY, "09:00", "12:00"));
        Section b = section("B", "1", weekly(MONDAY, "10:00", "11:00"));
        Section c = section("C", "1", weekly(MONDAY, "11:30", "13:00"));

        assertThat(service.findConflicts(List.of(selected(a), selected(b), selected(c))))
                .extracting(x -> x.first().courseCode() + "-" + x.second().courseCode())
                .containsExactly("A-B", "A-C");
    }

    @Test
    void candidateIsComparedWithOtherCoursesOnly() {
        Section candidate = section("INF1120", "020", weekly(MONDAY, "09:30", "12:30"));
        Section sameCourse = section("INF1120", "010", weekly(MONDAY, "09:30", "12:30"));
        Section otherCourse = section("MAT1060", "010", weekly(MONDAY, "09:30", "12:30"));

        assertThat(service.conflictsWith(candidate, List.of(selected(sameCourse), selected(otherCourse))))
                .singleElement()
                .satisfies(c -> assertThat(c.second().courseCode()).isEqualTo("MAT1060"));
    }
}
