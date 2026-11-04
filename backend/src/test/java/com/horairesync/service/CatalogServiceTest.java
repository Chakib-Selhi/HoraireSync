package com.horairesync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.horairesync.adapter.SchoolAdapter;
import com.horairesync.adapter.SchoolAdapterRegistry;
import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.Course;
import com.horairesync.model.CourseSections;
import com.horairesync.model.School;
import com.horairesync.support.TestProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    private final SchoolAdapter uqam = mock(SchoolAdapter.class);
    private CatalogService service;

    @BeforeEach
    void setUp() {
        when(uqam.school()).thenReturn(new School("uqam", "UQAM", true));
        service = new CatalogService(new SchoolAdapterRegistry(List.of(uqam)), TestProperties.defaults());
    }

    @Test
    void listsSupportedSchoolsFirstThenManualOnes() {
        assertThat(service.schools()).containsExactly(
                new School("uqam", "UQAM", true),
                new School("mcgill", "McGill University", false));
    }

    @Test
    void unsupportedSchoolCannotBeRead() {
        assertThatThrownBy(() -> service.terms("mcgill"))
                .isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(ErrorCode.SCHOOL_NOT_SUPPORTED);
    }

    @Test
    void sectionsCombineCourseDetailsAndSections() {
        Course course = new Course("INF1120", "Programmation I", 3, null, List.of("20271"));
        when(uqam.fetchCourse("inf1120")).thenReturn(course);
        when(uqam.fetchSections("20271", "INF1120")).thenReturn(List.of());

        CourseSections result = service.sections("uqam", " 20271 ", "inf1120");

        assertThat(result.course()).isEqualTo(course);
        assertThat(result.termId()).isEqualTo("20271");
        assertThat(result.sections()).isEmpty();
    }

    @Test
    void delegatesProgramCallsWithTrimmedInput() {
        service.searchPrograms("uqam", "  génie ");
        service.searchPrograms("uqam", null);
        service.programCourses("uqam", " 7416 ");
        service.terms("uqam");

        verify(uqam).searchPrograms("génie");
        verify(uqam).searchPrograms("");
        verify(uqam).fetchProgramCourses("7416");
        verify(uqam).fetchTerms();
    }
}
