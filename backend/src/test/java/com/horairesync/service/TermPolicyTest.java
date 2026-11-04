package com.horairesync.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.horairesync.model.Term;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermPolicyTest {

    private static Term term(String id, LocalDate start) {
        return new Term(id, id, start, null, List.of(), List.of(), null, List.of());
    }

    private static final List<Term> TERMS = List.of(
            term("20262", LocalDate.of(2026, 5, 4)),
            term("20263", LocalDate.of(2026, 9, 8)),
            term("20271", LocalDate.of(2027, 1, 11)),
            term("20272", LocalDate.of(2027, 5, 3)),
            term("unknown", null));

    private static TermPolicy at(String date) {
        ZoneId zone = ZoneId.of("America/Toronto");
        return new TermPolicy(Clock.fixed(LocalDate.parse(date).atStartOfDay(zone).toInstant(), zone));
    }

    @Test
    void duringFallOnlyWinterCanBePlanned() {
        assertThat(at("2026-11-04").plannable(TERMS)).extracting(Term::id).containsExactly("20271");
    }

    @Test
    void theStartDayItselfIsNoLongerUpcoming() {
        assertThat(at("2027-01-11").plannable(TERMS)).extracting(Term::id).containsExactly("20272");
        assertThat(at("2027-01-10").plannable(TERMS)).extracting(Term::id).containsExactly("20271");
    }

    @Test
    void noFutureTermGivesEmptyList() {
        assertThat(at("2030-01-01").plannable(TERMS)).isEmpty();
    }
}
