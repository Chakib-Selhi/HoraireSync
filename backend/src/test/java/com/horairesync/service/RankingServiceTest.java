package com.horairesync.service;

import static com.horairesync.support.Schedules.section;
import static com.horairesync.support.Schedules.selected;
import static com.horairesync.support.Schedules.weekly;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.horairesync.model.DeliveryMode;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Preferences;
import com.horairesync.model.RankRequest;
import com.horairesync.model.Section;
import com.horairesync.model.SectionScore;
import com.horairesync.model.SectionScore.Criterion;
import com.horairesync.model.SectionScore.Outcome;
import com.horairesync.model.TimeOfDay;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RankingServiceTest {

    private final RankingService service = new RankingService(new ConflictService());

    private static final Section DAY_MONTREAL = section("INF1120", "010", weekly(MONDAY, "09:30", "12:30"));
    private static final Section EVENING_LAVAL = section("INF1120", "040",
            weekly(TUESDAY, "18:00", "21:00", "UQAM Campus de Laval", MeetingType.LECTURE));
    private static final Section ONLINE_FRIDAY = section("INF1120", "050", DeliveryMode.ONLINE,
            weekly(FRIDAY, "18:00", "21:00", null, MeetingType.LECTURE));

    private List<SectionScore> rank(Preferences preferences, Section... sections) {
        return service.rank(new RankRequest(List.of(sections), preferences, List.of()));
    }

    private static Outcome outcome(SectionScore score, Criterion criterion) {
        return score.criteria().stream().filter(c -> c.criterion() == criterion).findFirst().orElseThrow().outcome();
    }

    @Test
    void withoutPreferencesEverySectionScores100() {
        List<SectionScore> scores = rank(Preferences.none(), DAY_MONTREAL, EVENING_LAVAL);

        assertThat(scores).allSatisfy(s -> {
            assertThat(s.score()).isEqualTo(100);
            assertThat(s.compatible()).isTrue();
            assertThat(s.criteria()).isEmpty();
        });
        assertThat(scores).extracting(SectionScore::sectionNumber).containsExactly("010", "040");
    }

    @Test
    void eveningPreferencePutsEveningSectionsFirst() {
        Preferences evening = new Preferences(TimeOfDay.EVENING, null, null, null);

        List<SectionScore> scores = rank(evening, DAY_MONTREAL, EVENING_LAVAL);

        assertThat(scores.get(0).sectionNumber()).isEqualTo("040");
        assertThat(scores.get(0).score()).isEqualTo(100);
        assertThat(scores.get(1).compatible()).isFalse();
        assertThat(outcome(scores.get(1), Criterion.TIME_OF_DAY)).isEqualTo(Outcome.MISMATCH);
    }

    @Test
    void dayPreferenceBoundaryIsFivePm() {
        assertThat(TimeOfDay.DAY.accepts(LocalTime.of(16, 59))).isTrue();
        assertThat(TimeOfDay.DAY.accepts(LocalTime.of(17, 0))).isFalse();
        assertThat(TimeOfDay.EVENING.accepts(LocalTime.of(17, 0))).isTrue();
        assertThat(TimeOfDay.ANY.accepts(LocalTime.of(8, 0))).isTrue();
    }

    @Test
    void modeAndCampusCriteria() {
        Preferences prefs = new Preferences(null, Set.of(DeliveryMode.IN_PERSON, DeliveryMode.ONLINE),
                Set.of("Campus de Montréal"), null);

        List<SectionScore> scores = rank(prefs, DAY_MONTREAL, EVENING_LAVAL, ONLINE_FRIDAY);

        SectionScore laval = scores.stream().filter(s -> s.sectionNumber().equals("040")).findFirst().orElseThrow();
        SectionScore online = scores.stream().filter(s -> s.sectionNumber().equals("050")).findFirst().orElseThrow();
        assertThat(outcome(laval, Criterion.MODE)).isEqualTo(Outcome.MATCH);
        assertThat(outcome(laval, Criterion.CAMPUS)).isEqualTo(Outcome.MISMATCH);
        assertThat(laval.score()).isEqualTo(50);
        assertThat(outcome(online, Criterion.CAMPUS)).isEqualTo(Outcome.MATCH);
        assertThat(scores).extracting(SectionScore::sectionNumber).containsExactly("010", "050", "040");
    }

    @Test
    void unknownInformationCountsHalf() {
        Section unknown = section("INF1120", "060", DeliveryMode.UNKNOWN,
                weekly(MONDAY, "09:30", "12:30", null, MeetingType.LECTURE));
        Preferences prefs = new Preferences(null, Set.of(DeliveryMode.IN_PERSON), Set.of("Campus de Montréal"), null);

        SectionScore score = rank(prefs, unknown).get(0);

        assertThat(outcome(score, Criterion.MODE)).isEqualTo(Outcome.UNKNOWN);
        assertThat(outcome(score, Criterion.CAMPUS)).isEqualTo(Outcome.UNKNOWN);
        assertThat(score.score()).isEqualTo(50);
        assertThat(score.compatible()).isTrue();
    }

    @Test
    void partlyKnownCampusIsUnknownWhenTheKnownOnesMatch() {
        Section mixed = section("INF1120", "070", weekly(MONDAY, "09:30", "12:30"),
                weekly(TUESDAY, "09:30", "11:30", null, MeetingType.LAB));
        Preferences prefs = new Preferences(null, null, Set.of("Campus de Montréal"), null);

        assertThat(outcome(rank(prefs, mixed).get(0), Criterion.CAMPUS)).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void freeDaysCriterion() {
        Preferences keepFridayFree = new Preferences(null, null, null, Set.of(FRIDAY));

        List<SectionScore> scores = rank(keepFridayFree, ONLINE_FRIDAY, DAY_MONTREAL);

        assertThat(scores.get(0).sectionNumber()).isEqualTo("010");
        assertThat(outcome(scores.get(1), Criterion.FREE_DAYS)).isEqualTo(Outcome.MISMATCH);
    }

    @Test
    void sectionWithoutScheduleIsUnknownForTimeAndDays() {
        Section empty = section("INF1120", "090");
        Preferences prefs = new Preferences(TimeOfDay.DAY, null, null, Set.of(FRIDAY));

        SectionScore score = rank(prefs, empty).get(0);

        assertThat(score.criteria()).extracting(SectionScore.CriterionResult::outcome)
                .containsOnly(Outcome.UNKNOWN);
    }

    @Test
    void conflictWithAChosenSectionMakesItIncompatible() {
        Section chosenMath = section("MAT1060", "010", weekly(MONDAY, "10:00", "11:00"));

        List<SectionScore> scores = service.rank(new RankRequest(List.of(DAY_MONTREAL, EVENING_LAVAL),
                Preferences.none(), List.of(selected(chosenMath))));

        assertThat(scores.get(0).sectionNumber()).isEqualTo("040");
        SectionScore conflicting = scores.get(1);
        assertThat(conflicting.compatible()).isFalse();
        assertThat(conflicting.score()).isEqualTo(100);
        assertThat(conflicting.conflicts()).singleElement()
                .satisfies(c -> assertThat(c.second().courseCode()).isEqualTo("MAT1060"));
    }

    @Test
    void examsAreIgnoredForPreferences() {
        Meeting saturdayExam = com.horairesync.support.Schedules.exam(java.time.LocalDate.of(2027, 2, 27),
                "09:30", "12:30");
        Section withExam = section("INF1120", "011", weekly(MONDAY, "18:00", "21:00"), saturdayExam);
        Preferences prefs = new Preferences(TimeOfDay.EVENING, null, null, Set.of(java.time.DayOfWeek.SATURDAY));

        assertThat(rank(prefs, withExam).get(0).score()).isEqualTo(100);
    }
}
