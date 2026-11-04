package com.horairesync.model;

/**
 * A section chosen by the user, sent back by the frontend with the values the user filled in manually.
 *
 * @param courseTitle title of the course, used in event titles
 * @param section     the section, possibly completed by the user
 */
public record SelectedSection(String courseTitle, Section section) {}
