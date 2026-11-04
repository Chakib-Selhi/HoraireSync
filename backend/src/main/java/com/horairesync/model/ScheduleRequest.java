package com.horairesync.model;

import java.util.List;

/**
 * Sections chosen for one term.
 *
 * @param schoolId   school identifier (may be an unsupported school in manual mode)
 * @param termId     term identifier
 * @param selections chosen sections
 */
public record ScheduleRequest(String schoolId, String termId, List<SelectedSection> selections) {

    public ScheduleRequest {
        selections = selections == null ? List.of() : List.copyOf(selections);
    }
}
