package com.horairesync.model;

import java.time.LocalTime;

/** Preferred part of the day. A meeting is in the evening when it starts at 17:00 or later. */
public enum TimeOfDay {
    DAY, EVENING, ANY;

    public static final LocalTime EVENING_START = LocalTime.of(17, 0);

    /** {@code true} when a meeting starting at {@code start} fits this preference. */
    public boolean accepts(LocalTime start) {
        return switch (this) {
            case ANY -> true;
            case EVENING -> !start.isBefore(EVENING_START);
            case DAY -> start.isBefore(EVENING_START);
        };
    }
}
