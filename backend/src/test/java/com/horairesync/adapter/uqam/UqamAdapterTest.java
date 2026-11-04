package com.horairesync.adapter.uqam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.Course;
import com.horairesync.model.DeliveryMode;
import com.horairesync.model.Meeting;
import com.horairesync.model.MeetingType;
import com.horairesync.model.Program;
import com.horairesync.model.Section;
import com.horairesync.model.Term;
import com.horairesync.support.FixtureHtmlSource;
import com.horairesync.support.TestProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Parses the pages recorded in phase 0 ({@code src/test/resources/fixtures/uqam}). No network access. */
class UqamAdapterTest {

    private final FixtureHtmlSource fixtures = FixtureHtmlSource.uqam();
    private final UqamAdapter adapter = new UqamAdapter(fixtures, TestProperties.defaults());

    private static Section section(List<Section> sections, String number) {
        return sections.stream().filter(s -> s.sectionNumber().equals(number)).findFirst().orElseThrow();
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(code);
    }

    @Test
    void identifiesItself() {
        assertThat(adapter.school().id()).isEqualTo("uqam");
        assertThat(adapter.school().supported()).isTrue();
    }

    @Nested
    class Sections {

        @Test
        void listsTheSectionsOfTheRequestedTermOnly() {
            List<Section> winter = adapter.fetchSections("20271", "INF1120");
            List<Section> fall = adapter.fetchSections("20263", "INF1120");

            assertThat(winter).extracting(Section::sectionNumber).containsExactly("001", "010", "020", "021");
            assertThat(fall).extracting(Section::sectionNumber).containsExactly("001", "002", "010", "020");
            assertThat(winter).allSatisfy(s -> {
                assertThat(s.courseCode()).isEqualTo("INF1120");
                assertThat(s.sourceUrl()).isEqualTo("https://etudier.uqam.ca/cours?sigle=INF1120");
            });
        }

        @Test
        void readsWeeklyMeetingsWithDatesTimesAndTypes() {
            Section s = section(adapter.fetchSections("20271", "INF1120"), "001");

            Meeting lecture = s.meetings().get(0);
            assertThat(lecture.day()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(lecture.start()).isEqualTo(LocalTime.of(9, 30));
            assertThat(lecture.end()).isEqualTo(LocalTime.of(12, 30));
            assertThat(lecture.type()).isEqualTo(MeetingType.LECTURE);
            assertThat(lecture.campus()).isEqualTo("UQAM Campus de Laval");
            assertThat(lecture.firstDate()).isEqualTo(LocalDate.of(2027, 1, 11));
            assertThat(lecture.lastDate()).isEqualTo(LocalDate.of(2027, 5, 2));
            assertThat(lecture.isOneOff()).isFalse();

            Meeting lab = s.meetings().get(1);
            assertThat(lab.day()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(lab.end()).isEqualTo(LocalTime.of(11, 30));
            assertThat(lab.type()).isEqualTo(MeetingType.LAB);
            assertThat(lab.campus()).isNull();
        }

        @Test
        void unavailableInstructorIsNullAndReportedMissing() {
            Section s = section(adapter.fetchSections("20271", "INF1120"), "001");

            assertThat(s.instructor()).isNull();
            assertThat(s.missingFields()).contains("instructor");
        }

        @Test
        void knownInstructorIsKept() {
            Section s = section(adapter.fetchSections("20263", "INF1120"), "001");

            assertThat(s.instructor()).isEqualTo("Mélanie Lord");
            assertThat(s.missingFields()).doesNotContain("instructor");
        }

        @Test
        void removesDuplicateMeetingRows() {
            Section s = section(adapter.fetchSections("20271", "INF1120"), "010");

            List<Meeting> weekly = s.meetings().stream().filter(m -> !m.isOneOff()).toList();
            assertThat(weekly).hasSize(2);
            assertThat(weekly).extracting(Meeting::day).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        }

        @Test
        void readsExamsFromRemarksAsOneOffMeetings() {
            Section s = section(adapter.fetchSections("20271", "INF1120"), "001");

            List<Meeting> exams = s.meetings().stream().filter(Meeting::isOneOff).toList();
            assertThat(exams).hasSize(2);
            assertThat(exams.get(0).onlyDate()).isEqualTo(LocalDate.of(2027, 2, 27));
            assertThat(exams.get(0).day()).isEqualTo(DayOfWeek.SATURDAY);
            assertThat(exams.get(0).start()).isEqualTo(LocalTime.of(9, 30));
            assertThat(exams.get(0).end()).isEqualTo(LocalTime.of(12, 30));
            assertThat(exams.get(0).type()).isEqualTo(MeetingType.EXAM);
            assertThat(exams.get(1).onlyDate()).isEqualTo(LocalDate.of(2027, 5, 1));
        }

        @Test
        void fallExamsUseTheFallYear() {
            Section s = section(adapter.fetchSections("20263", "INF1120"), "001");

            assertThat(s.meetings()).filteredOn(Meeting::isOneOff).extracting(Meeting::onlyDate)
                    .containsExactly(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 12, 22));
        }

        @Test
        void takesRoomFromAnExplicitRemarkForTheNamedMeetingTypeOnly() {
            Section winter001 = section(adapter.fetchSections("20271", "INF1120"), "001");
            Section fall002 = section(adapter.fetchSections("20263", "INF1120"), "002");

            // "Les ateliers ont lieu dans le laboratoire D-4030"
            assertThat(winter001.meetings().get(1).room()).isEqualTo("D-4030");
            assertThat(winter001.meetings().get(0).room()).isNull();
            // "Le cours magistral a lieu dans le local D-4119"
            Meeting lecture = fall002.meetings().stream().filter(m -> m.type() == MeetingType.LECTURE)
                    .findFirst().orElseThrow();
            assertThat(lecture.room()).isEqualTo("D-4119");
            assertThat(fall002.meetings()).filteredOn(m -> m.type() == MeetingType.LAB)
                    .extracting(Meeting::room).containsOnlyNulls();
        }

        @Test
        void remarkWithoutRoomCodeLeavesRoomEmpty() {
            // "Ateliers: laboratoire informatique - Cours: salle de cours"
            Section s = section(adapter.fetchSections("20263", "INF1120"), "001");

            assertThat(s.meetings()).extracting(Meeting::room).containsOnlyNulls();
            assertThat(s.missingFields()).contains("meetings[0].room", "meetings[1].room");
        }

        @Test
        void sectionCampusIsSetOnlyWhenAllMeetingsShareIt() {
            Section mixed = section(adapter.fetchSections("20271", "INF1120"), "001");
            Section sameCampus = section(adapter.fetchSections("20263", "INF1120"), "010");

            assertThat(mixed.campus()).isNull();
            assertThat(mixed.missingFields()).contains("campus", "meetings[1].campus");
            assertThat(sameCampus.campus()).isEqualTo("Campus de Montréal");
            assertThat(sameCampus.missingFields()).doesNotContain("campus");
        }

        @Test
        void keepsRemarksAsNotes() {
            Section s = section(adapter.fetchSections("20271", "INF1120"), "010");

            assertThat(s.notes()).contains("Si cours plein écrire à bigl@uqam.ca");
        }

        @Test
        void readsTheThreeDeliveryModes() {
            List<Section> sections = adapter.fetchSections("20271", "ORH1163");

            assertThat(section(sections, "001").mode()).isEqualTo(DeliveryMode.IN_PERSON);
            assertThat(section(sections, "007").mode()).isEqualTo(DeliveryMode.HYBRID);
            assertThat(section(sections, "012").mode()).isEqualTo(DeliveryMode.ONLINE);
        }

        @Test
        void onlineSectionIgnoresRoomAndCampusAndDoesNotReportThemMissing() {
            Section online = section(adapter.fetchSections("20271", "ORH1163"), "012");

            assertThat(online.campus()).isNull();
            assertThat(online.meetings()).extracting(Meeting::campus).containsOnlyNulls();
            assertThat(online.missingFields()).noneMatch(f -> f.endsWith("room") || f.endsWith("campus"));
            assertThat(online.instructor()).isEqualTo("Alina Nusa Stamate");
        }

        @Test
        void sectionWithoutPublishedScheduleHasNoMeetings() {
            Section s = section(adapter.fetchSections("20262", "ORH1163"), "018");

            assertThat(s.meetings()).isEmpty();
            assertThat(s.missingFields()).contains("meetings");
            assertThat(s.notes()).contains("CASABLANCA-MAROC (Cohorte 5)");
        }

        @Test
        void essentialFieldsAreNeverMissingOnWellFormedRows() {
            List<Section> all = adapter.fetchSections("20271", "ORH1163");

            assertThat(all).flatExtracting(Section::missingFields)
                    .noneMatch(f -> f.endsWith(".day") || f.endsWith(".start") || f.endsWith(".end"));
        }

        @Test
        void unpublishedTermGivesEmptyList() {
            assertThat(adapter.fetchSections("20272", "INF1120")).isEmpty();
        }

        @Test
        void acceptsLowerCaseCourseCode() {
            assertThat(adapter.fetchSections("20271", " inf1120 ")).hasSize(4);
        }

        @Test
        void unknownCourseIsNotFound() {
            assertError(() -> adapter.fetchSections("20271", "ZZZ9999"), ErrorCode.COURSE_NOT_FOUND);
        }

        @Test
        void invalidCodesAreRejectedWithoutRequest() {
            assertError(() -> adapter.fetchSections("20271", "INF-1120"), ErrorCode.COURSE_NOT_FOUND);
            assertError(() -> adapter.fetchSections("H27", "INF1120"), ErrorCode.INVALID_REQUEST);
            assertThat(fixtures.requestedUrls()).isEmpty();
        }
    }

    @Nested
    class CourseDetails {

        @Test
        void readsTitleCreditsAndOfferedTerms() {
            Course course = adapter.fetchCourse("INF1120");

            assertThat(course.code()).isEqualTo("INF1120");
            assertThat(course.title()).isEqualTo("Programmation I");
            assertThat(course.credits()).isEqualTo(3);
            assertThat(course.offeredTermIds()).containsExactly("20262", "20263", "20271");
        }

        @Test
        void unknownCourseIsNotFound() {
            assertError(() -> adapter.fetchCourse("ZZZ9999"), ErrorCode.COURSE_NOT_FOUND);
        }
    }

    @Nested
    class Programs {

        @Test
        void listsProgramCoursesWithoutDuplicates() {
            List<Course> courses = adapter.fetchProgramCourses("7416");

            assertThat(courses).hasSize(47); // 48 rows, INF6200 listed twice
            assertThat(courses).extracting(Course::code).doesNotHaveDuplicates().contains("INF1120", "INF5151");
            Course inf1120 = courses.stream().filter(c -> c.code().equals("INF1120")).findFirst().orElseThrow();
            assertThat(inf1120.title()).isEqualTo("Programmation I");
            assertThat(inf1120.credits()).isNull();
        }

        @Test
        void unknownOrInvalidProgramIsNotFound() {
            assertError(() -> adapter.fetchProgramCourses("7084"), ErrorCode.PROGRAM_NOT_FOUND);
            assertError(() -> adapter.fetchProgramCourses("abc"), ErrorCode.PROGRAM_NOT_FOUND);
        }

        @Test
        void searchesByCode() {
            List<Program> results = adapter.searchPrograms("7416");

            assertThat(results).first().satisfies(p -> {
                assertThat(p.code()).isEqualTo("7416");
                assertThat(p.name()).isEqualTo("Informatique et génie logiciel");
                assertThat(p.type()).isEqualTo("Baccalauréat");
                assertThat(p.schoolId()).isEqualTo("uqam");
            });
        }

        @Test
        void searchesByWordsIgnoringCaseAndAccents() {
            List<Program> results = adapter.searchPrograms("GENIE logiciel");

            assertThat(results).extracting(Program::code).contains("7416");
            assertThat(results).allSatisfy(p -> assertThat(UqamText.fold(p.name() + " " + p.type()))
                    .contains("genie").contains("logiciel"));
        }

        @Test
        void searchByProgramTypeWorks() {
            assertThat(adapter.searchPrograms("certificat informatique")).extracting(Program::code).contains("4702");
        }

        @Test
        void blankQueryReturnsTheFirstResultsAndNoMatchReturnsEmpty() {
            assertThat(adapter.searchPrograms("")).hasSize(UqamAdapter.MAX_PROGRAM_RESULTS);
            assertThat(adapter.searchPrograms("zzzzzz")).isEmpty();
        }

        @Test
        void listIsReadOnceFromTheSamePage() {
            adapter.searchPrograms("info");
            adapter.searchPrograms("math");

            assertThat(fixtures.requestedUrls()).containsOnly("https://etudier.uqam.ca/programmes");
        }
    }

    @Nested
    class Terms {

        private Term term(String id) {
            return adapter.fetchTerms().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
        }

        @Test
        void listsTermsInChronologicalOrder() {
            List<Term> terms = adapter.fetchTerms();

            assertThat(terms).extracting(Term::id).startsWith("20262", "20263", "20271").isSorted();
            assertThat(term("20271").label()).isEqualTo("Hiver 2027");
        }

        @Test
        void winterTermHasStartHolidaysAndReadingWeekButAmbiguousEnd() {
            Term winter = term("20271");

            assertThat(winter.startDate()).isEqualTo(LocalDate.of(2027, 1, 11));
            // "23 avril ou 30 avril ou 2 mai": several possible ends, so none is chosen
            assertThat(winter.endDate()).isNull();
            assertThat(winter.missingFields()).containsExactly("endDate");
            assertThat(winter.excludedDates()).containsExactly(LocalDate.of(2027, 3, 26), LocalDate.of(2027, 3, 29));
            assertThat(winter.readingWeekDates()).hasSize(7).startsWith(LocalDate.of(2027, 3, 1));
            assertThat(winter.readingWeekNote()).isEqualTo("selon facultés/écoles");
        }

        @Test
        void fallTermHolidaysIgnoreTheExplanatoryText() {
            Term fall = term("20263");

            assertThat(fall.startDate()).isEqualTo(LocalDate.of(2026, 9, 8));
            assertThat(fall.excludedDates()).containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 30),
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
            assertThat(fall.readingWeekNote()).isEqualTo("ESG seulement");
        }

        @Test
        void summerTermHasTwoPossibleEnds() {
            Term summer = term("20262");

            assertThat(summer.startDate()).isEqualTo(LocalDate.of(2026, 5, 4));
            assertThat(summer.endDate()).isNull();
            assertThat(summer.excludedDates()).containsExactly(LocalDate.of(2026, 5, 18),
                    LocalDate.of(2026, 6, 24), LocalDate.of(2026, 7, 1));
            assertThat(summer.readingWeekDates()).isEmpty();
        }
    }
}
