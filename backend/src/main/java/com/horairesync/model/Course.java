package com.horairesync.model;

import java.util.List;

/**
 * A course.
 *
 * @param code            course code (e.g. {@code INF1120})
 * @param title           course title
 * @param credits         number of credits, or {@code null} when the page does not show it
 * @param termId          term this course instance belongs to, or {@code null} for a program listing
 * @param offeredTermIds  terms in which the source says the course is offered
 */
public record Course(String code, String title, Integer credits, String termId, List<String> offeredTermIds) {

    public Course {
        offeredTermIds = offeredTermIds == null ? List.of() : List.copyOf(offeredTermIds);
    }
}
