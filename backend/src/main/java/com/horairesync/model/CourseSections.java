package com.horairesync.model;

import java.util.List;

/**
 * Response of the sections endpoint: the course itself (title, credits) and its sections for one term.
 *
 * @param course   course details
 * @param termId   requested term
 * @param sections sections, empty when the term schedule is not published
 */
public record CourseSections(Course course, String termId, List<Section> sections) {

    public CourseSections {
        sections = List.copyOf(sections);
    }
}
