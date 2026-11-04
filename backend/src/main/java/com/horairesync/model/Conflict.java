package com.horairesync.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Two meetings that overlap.
 *
 * @param first        first meeting
 * @param second       second meeting
 * @param day          day of the week of the overlap
 * @param date         date of the overlap when one of the meetings is a one-off (exam), otherwise {@code null}
 * @param overlapStart start of the overlapping period
 * @param overlapEnd   end of the overlapping period
 */
public record Conflict(MeetingRef first, MeetingRef second, DayOfWeek day, LocalDate date,
                       LocalTime overlapStart, LocalTime overlapEnd) {}
