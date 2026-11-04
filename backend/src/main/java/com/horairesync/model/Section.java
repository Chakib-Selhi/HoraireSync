package com.horairesync.model;

import java.util.List;

/**
 * A section (group) of a course in a given term.
 *
 * <p>{@code missingFields} uses simple paths: {@code instructor}, {@code mode}, {@code campus}, {@code meetings}
 * (no schedule published) and {@code meetings[i].room} for a field of the i-th meeting.
 *
 * @param courseCode    course code
 * @param sectionNumber section number as published (e.g. {@code 020})
 * @param instructor    instructor name(s), or {@code null}
 * @param mode          delivery mode
 * @param campus        campus shared by all meetings, or {@code null}
 * @param meetings      meetings, without duplicates
 * @param notes         free-text remarks published with the section
 * @param missingFields fields that could not be read
 * @param sourceUrl     page the data comes from
 */
public record Section(String courseCode, String sectionNumber, String instructor, DeliveryMode mode,
                      String campus, List<Meeting> meetings, List<String> notes, List<String> missingFields,
                      String sourceUrl) {

    public Section {
        meetings = List.copyOf(meetings);
        notes = List.copyOf(notes);
        missingFields = List.copyOf(missingFields);
    }
}
