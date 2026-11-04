package com.horairesync.service;

import com.horairesync.model.Term;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chooses which terms the user may plan.
 *
 * <p>Schools publish the sections of the upcoming term only (in November, UQAM publishes Winter): the term currently
 * running is already registered and later terms have no schedule yet. The policy keeps the first term whose start date
 * is after today, in the {@code America/Toronto} time zone.
 */
@Component
public class TermPolicy {

    private final Clock clock;

    public TermPolicy(Clock clock) {
        this.clock = clock;
    }

    /** The next term, or an empty list when no future term has a known start date. */
    public List<Term> plannable(List<Term> terms) {
        LocalDate today = LocalDate.now(clock);
        return terms.stream()
                .filter(t -> t.startDate() != null && t.startDate().isAfter(today))
                .min(Comparator.comparing(Term::startDate))
                .stream()
                .toList();
    }
}
