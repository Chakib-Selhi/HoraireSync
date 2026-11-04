package com.horairesync.service;

import com.horairesync.adapter.SchoolAdapter;
import com.horairesync.adapter.SchoolAdapterRegistry;
import com.horairesync.config.CacheConfig;
import com.horairesync.config.HoraireSyncProperties;
import com.horairesync.model.Course;
import com.horairesync.model.CourseSections;
import com.horairesync.model.Program;
import com.horairesync.model.School;
import com.horairesync.model.Section;
import com.horairesync.model.Term;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Read side of the catalog: delegates to the school's adapter and caches the parsed results.
 *
 * <p>The service knows nothing about HTML; it only picks the adapter. Cache keys are normalized (trimmed, upper-case
 * course codes, lower-case queries) so that "inf1120" and "INF1120" share one entry.
 */
@Service
public class CatalogService {

    private final SchoolAdapterRegistry registry;
    private final List<HoraireSyncProperties.SchoolEntry> unsupportedSchools;

    public CatalogService(SchoolAdapterRegistry registry, HoraireSyncProperties properties) {
        this.registry = registry;
        this.unsupportedSchools = properties.unsupportedSchools();
    }

    /** Supported schools first, then the schools available in manual mode, each group sorted by name. */
    public List<School> schools() {
        List<School> schools = new ArrayList<>(registry.all().stream()
                .map(SchoolAdapter::school)
                .sorted(Comparator.comparing(School::name))
                .toList());
        unsupportedSchools.stream()
                .filter(entry -> registry.find(entry.id()).isEmpty())
                .map(entry -> new School(entry.id(), entry.name(), false))
                .sorted(Comparator.comparing(School::name))
                .forEach(schools::add);
        return List.copyOf(schools);
    }

    @Cacheable(cacheNames = CacheConfig.TERMS, key = "#schoolId")
    public List<Term> terms(String schoolId) {
        return registry.require(schoolId).fetchTerms();
    }

    @Cacheable(cacheNames = CacheConfig.PROGRAM_SEARCH,
            key = "#schoolId + ':' + (#query == null ? '' : #query.trim().toLowerCase())")
    public List<Program> searchPrograms(String schoolId, String query) {
        return registry.require(schoolId).searchPrograms(query == null ? "" : query.trim());
    }

    @Cacheable(cacheNames = CacheConfig.PROGRAM_COURSES, key = "#schoolId + ':' + #programCode.trim()")
    public List<Course> programCourses(String schoolId, String programCode) {
        return registry.require(schoolId).fetchProgramCourses(programCode.trim());
    }

    @Cacheable(cacheNames = CacheConfig.COURSE_SECTIONS,
            key = "#schoolId + ':' + #termId.trim() + ':' + #courseCode.trim().toUpperCase()")
    public CourseSections sections(String schoolId, String termId, String courseCode) {
        SchoolAdapter adapter = registry.require(schoolId);
        Course course = adapter.fetchCourse(courseCode);
        List<Section> sections = adapter.fetchSections(termId.trim(), course.code());
        return new CourseSections(course, termId.trim(), sections);
    }
}
