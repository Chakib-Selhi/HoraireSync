package com.horairesync.adapter;

import com.horairesync.model.Course;
import com.horairesync.model.Program;
import com.horairesync.model.School;
import com.horairesync.model.Section;
import com.horairesync.model.Term;
import java.util.List;

/**
 * Reads the public schedule of one school. Only implementations know the school's HTML.
 *
 * <p>Implementations are Spring beans discovered automatically by {@link SchoolAdapterRegistry}. They must never
 * guess: a value that cannot be read is {@code null} and its name goes into {@code missingFields}. When the page
 * structure is not the expected one, they throw a {@code PARSING_FAILED} error instead of returning wrong data.
 */
public interface SchoolAdapter {

    /** The school served by this adapter; {@link School#id()} is the registry key. */
    School school();

    /** Terms currently published, with their dates when available. */
    List<Term> fetchTerms();

    /** Programs whose name or code matches the query (case and accent insensitive). */
    List<Program> searchPrograms(String query);

    /** Courses listed in a program. */
    List<Course> fetchProgramCourses(String programCode);

    /** Sections of a course in a term; empty when the term schedule is not published. */
    List<Section> fetchSections(String termId, String courseCode);
}
