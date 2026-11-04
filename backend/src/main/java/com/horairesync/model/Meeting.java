package com.horairesync.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One meeting slot of a section.
 *
 * <p>A weekly meeting repeats from {@code firstDate} to {@code lastDate}. A one-off meeting (e.g. a final exam) has
 * {@code onlyDate} set, and {@code firstDate} and {@code lastDate} equal to it. Any field may be {@code null} when the
 * source does not publish it; the owning {@link Section} then lists it in its missing fields.
 *
 * @param day       day of the week
 * @param start     start time
 * @param end       end time
 * @param room      room code (e.g. D-4119)
 * @param campus    campus name
 * @param type      meeting type
 * @param onlyDate  date of a one-off meeting, {@code null} for a weekly meeting
 * @param firstDate first date of the meeting
 * @param lastDate  last date of the meeting
 */
public record Meeting(DayOfWeek day, LocalTime start, LocalTime end, String room, String campus,
                      MeetingType type, LocalDate onlyDate, LocalDate firstDate, LocalDate lastDate) {

    /** Returns a copy with another room. */
    public Meeting withRoom(String newRoom) {
        return new Meeting(day, start, end, newRoom, campus, type, onlyDate, firstDate, lastDate);
    }

    /** Returns {@code true} for a one-off meeting such as an exam. */
    public boolean isOneOff() {
        return onlyDate != null;
    }
}
