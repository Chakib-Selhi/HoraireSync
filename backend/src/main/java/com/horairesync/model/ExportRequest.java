package com.horairesync.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Confirmed schedule to export as iCalendar.
 *
 * <p>The frontend sends everything the export needs, including values typed by the user, so the export also works in
 * manual mode for schools without adapter. {@code termStart} and {@code termEnd} are used for weekly meetings that
 * have no {@code firstDate}/{@code lastDate} of their own.
 *
 * @param schoolId      school identifier
 * @param termId        term identifier
 * @param termLabel     label shown as calendar name (e.g. "Hiver 2027"), optional
 * @param termStart     first day of the term, optional
 * @param termEnd       last day of the term, optional
 * @param excludedDates days without classes (holidays, reading week if the user keeps it excluded)
 * @param selections    chosen sections
 */
public record ExportRequest(String schoolId, String termId, String termLabel, LocalDate termStart, LocalDate termEnd,
                            List<LocalDate> excludedDates, List<SelectedSection> selections) {

    public ExportRequest {
        excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
        selections = selections == null ? List.of() : List.copyOf(selections);
    }
}
