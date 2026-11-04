package com.horairesync.model;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Optional user preferences. An empty set or {@code null}/{@link TimeOfDay#ANY} means "no preference".
 *
 * @param timeOfDay preferred part of the day
 * @param modes     accepted delivery modes
 * @param campuses  accepted campuses (exact names as published)
 * @param freeDays  days to keep free
 */
public record Preferences(TimeOfDay timeOfDay, Set<DeliveryMode> modes, Set<String> campuses,
                          Set<DayOfWeek> freeDays) {

    public Preferences {
        timeOfDay = timeOfDay == null ? TimeOfDay.ANY : timeOfDay;
        modes = modes == null ? Set.of() : Set.copyOf(modes);
        campuses = campuses == null ? Set.of() : Set.copyOf(campuses);
        freeDays = freeDays == null ? Set.of() : Set.copyOf(freeDays);
    }

    public static Preferences none() {
        return new Preferences(TimeOfDay.ANY, Set.of(), Set.of(), Set.of());
    }
}
