package com.horairesync.model;

import java.time.LocalDate;
import java.util.List;

/**
 * An academic term (session).
 *
 * <p>Dates that the source does not state unambiguously are {@code null} and their field name is listed in
 * {@link #missingFields()}.
 *
 * @param id                identifier, for UQAM {@code YYYYT} where T is 1 = winter, 2 = summer, 3 = fall
 * @param label             display label, e.g. "Hiver 2027"
 * @param startDate         first day of the term, or {@code null}
 * @param endDate           last day of the term, or {@code null}
 * @param excludedDates     days without classes for everyone (statutory holidays)
 * @param readingWeekDates  reading week days; they depend on the faculty, so the user can choose to exclude them
 * @param readingWeekNote   qualifier published with the reading week (e.g. "ESG seulement"), or {@code null}
 * @param missingFields     names of the fields above that could not be read
 */
public record Term(String id, String label, LocalDate startDate, LocalDate endDate,
                   List<LocalDate> excludedDates, List<LocalDate> readingWeekDates, String readingWeekNote,
                   List<String> missingFields) {

    public Term {
        excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
        readingWeekDates = readingWeekDates == null ? List.of() : List.copyOf(readingWeekDates);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
