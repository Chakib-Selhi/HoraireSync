package com.horairesync.model;

/**
 * Points to one meeting of a section.
 *
 * @param courseCode    course code
 * @param sectionNumber section number
 * @param meetingIndex  index in {@link Section#meetings()}
 * @param type          meeting type, for display
 */
public record MeetingRef(String courseCode, String sectionNumber, int meetingIndex, MeetingType type) {}
