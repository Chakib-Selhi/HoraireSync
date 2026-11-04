package com.horairesync.service;

import com.horairesync.model.Conflict;
import com.horairesync.model.DeliveryMode;
import com.horairesync.model.Meeting;
import com.horairesync.model.Preferences;
import com.horairesync.model.RankRequest;
import com.horairesync.model.Section;
import com.horairesync.model.SectionScore;
import com.horairesync.model.SectionScore.Criterion;
import com.horairesync.model.SectionScore.CriterionResult;
import com.horairesync.model.SectionScore.Outcome;
import com.horairesync.model.TimeOfDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Scores sections against the user's preferences (EF-07).
 *
 * <p>Each preference that is set is one criterion: MATCH counts 1, UNKNOWN 0.5 (the source does not say), MISMATCH 0.
 * The score is the average, as a percentage. A section is {@code compatible} when no criterion is a mismatch and it
 * does not conflict with an already chosen section. Results are sorted: compatible first, then by score, then by
 * section number. Only weekly meetings are considered: exam dates are not a choice criterion.
 */
@Service
public class RankingService {

    private final ConflictService conflictService;

    public RankingService(ConflictService conflictService) {
        this.conflictService = conflictService;
    }

    public List<SectionScore> rank(RankRequest request) {
        return request.sections().stream()
                .map(section -> score(section, request.preferences(), request))
                .sorted(Comparator.comparing(SectionScore::compatible).reversed()
                        .thenComparing(Comparator.comparingInt(SectionScore::score).reversed())
                        .thenComparing(SectionScore::sectionNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private SectionScore score(Section section, Preferences preferences, RankRequest request) {
        List<Meeting> weekly = section.meetings().stream().filter(m -> !m.isOneOff()).toList();
        List<CriterionResult> criteria = new ArrayList<>();
        if (preferences.timeOfDay() != TimeOfDay.ANY) {
            criteria.add(new CriterionResult(Criterion.TIME_OF_DAY, timeOfDay(weekly, preferences.timeOfDay())));
        }
        if (!preferences.modes().isEmpty()) {
            criteria.add(new CriterionResult(Criterion.MODE, mode(section, preferences)));
        }
        if (!preferences.campuses().isEmpty()) {
            criteria.add(new CriterionResult(Criterion.CAMPUS, campus(section, weekly, preferences)));
        }
        if (!preferences.freeDays().isEmpty()) {
            criteria.add(new CriterionResult(Criterion.FREE_DAYS, freeDays(weekly, preferences)));
        }

        List<Conflict> conflicts = conflictService.conflictsWith(section, request.chosen());
        double points = criteria.stream().mapToDouble(c -> switch (c.outcome()) {
            case MATCH -> 1.0;
            case UNKNOWN -> 0.5;
            case MISMATCH -> 0.0;
        }).sum();
        int score = criteria.isEmpty() ? 100 : (int) Math.round(100 * points / criteria.size());
        boolean compatible = conflicts.isEmpty() && criteria.stream().noneMatch(c -> c.outcome() == Outcome.MISMATCH);
        return new SectionScore(section.courseCode(), section.sectionNumber(), score, compatible, criteria, conflicts);
    }

    private static Outcome timeOfDay(List<Meeting> weekly, TimeOfDay wanted) {
        if (weekly.isEmpty() || weekly.stream().anyMatch(m -> m.start() == null)) {
            return Outcome.UNKNOWN;
        }
        return weekly.stream().allMatch(m -> wanted.accepts(m.start())) ? Outcome.MATCH : Outcome.MISMATCH;
    }

    private static Outcome mode(Section section, Preferences preferences) {
        if (section.mode() == null || section.mode() == DeliveryMode.UNKNOWN) {
            return Outcome.UNKNOWN;
        }
        return preferences.modes().contains(section.mode()) ? Outcome.MATCH : Outcome.MISMATCH;
    }

    private static Outcome campus(Section section, List<Meeting> weekly, Preferences preferences) {
        if (section.mode() == DeliveryMode.ONLINE) {
            return Outcome.MATCH; // no campus to travel to
        }
        List<String> campuses = weekly.stream().map(Meeting::campus).filter(Objects::nonNull).distinct().toList();
        if (campuses.isEmpty()) {
            return Outcome.UNKNOWN;
        }
        if (!preferences.campuses().containsAll(campuses)) {
            return Outcome.MISMATCH;
        }
        return weekly.stream().anyMatch(m -> m.campus() == null) ? Outcome.UNKNOWN : Outcome.MATCH;
    }

    private static Outcome freeDays(List<Meeting> weekly, Preferences preferences) {
        if (weekly.stream().anyMatch(m -> m.day() != null && preferences.freeDays().contains(m.day()))) {
            return Outcome.MISMATCH;
        }
        if (weekly.isEmpty() || weekly.stream().anyMatch(m -> m.day() == null)) {
            return Outcome.UNKNOWN;
        }
        return Outcome.MATCH;
    }
}
