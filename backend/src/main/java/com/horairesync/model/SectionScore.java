package com.horairesync.model;

import java.util.List;

/**
 * Score of a section against the preferences.
 *
 * @param courseCode    course code
 * @param sectionNumber section number
 * @param score         0 to 100; 100 when no preference is set
 * @param compatible    {@code true} when no criterion is a mismatch and there is no conflict
 * @param criteria      result of each preference that was set
 * @param conflicts     conflicts with the already chosen sections
 */
public record SectionScore(String courseCode, String sectionNumber, int score, boolean compatible,
                           List<CriterionResult> criteria, List<Conflict> conflicts) {

    public SectionScore {
        criteria = List.copyOf(criteria);
        conflicts = List.copyOf(conflicts);
    }

    /** Outcome of one preference for one section. */
    public record CriterionResult(Criterion criterion, Outcome outcome) {}

    public enum Criterion { TIME_OF_DAY, MODE, CAMPUS, FREE_DAYS }

    /** {@link #UNKNOWN} when the source does not give the information needed to decide. */
    public enum Outcome { MATCH, MISMATCH, UNKNOWN }
}
