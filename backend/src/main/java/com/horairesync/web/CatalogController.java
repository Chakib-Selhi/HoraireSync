package com.horairesync.web;

import com.horairesync.model.Course;
import com.horairesync.model.CourseSections;
import com.horairesync.model.Program;
import com.horairesync.model.School;
import com.horairesync.model.Term;
import com.horairesync.service.CatalogService;
import com.horairesync.service.TermPolicy;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only catalog routes of the PRD, under {@code /api/v1}. */
@RestController
@RequestMapping("/api/v1/schools")
public class CatalogController {

    private final CatalogService catalog;
    private final TermPolicy termPolicy;

    public CatalogController(CatalogService catalog, TermPolicy termPolicy) {
        this.catalog = catalog;
        this.termPolicy = termPolicy;
    }

    @GetMapping
    public List<School> schools() {
        return catalog.schools();
    }

    /** Only the upcoming term: it is the only one whose sections are published and still to choose. */
    @GetMapping("/{schoolId}/terms")
    public List<Term> terms(@PathVariable String schoolId) {
        return termPolicy.plannable(catalog.terms(schoolId));
    }

    @GetMapping("/{schoolId}/programs")
    public List<Program> programs(@PathVariable String schoolId, @RequestParam(defaultValue = "") String q) {
        return catalog.searchPrograms(schoolId, q);
    }

    @GetMapping("/{schoolId}/programs/{code}/courses")
    public List<Course> programCourses(@PathVariable String schoolId, @PathVariable String code) {
        return catalog.programCourses(schoolId, code);
    }

    @GetMapping("/{schoolId}/terms/{termId}/courses/{code}/sections")
    public CourseSections sections(@PathVariable String schoolId, @PathVariable String termId,
                                   @PathVariable String code) {
        return catalog.sections(schoolId, termId, code);
    }
}
