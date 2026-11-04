package com.horairesync.model;

/**
 * A school shown in the school picker.
 *
 * @param id        stable identifier used in URLs (e.g. {@code uqam})
 * @param name      display name
 * @param supported {@code true} when an adapter can read its schedules; otherwise the user enters courses manually
 */
public record School(String id, String name, boolean supported) {}
