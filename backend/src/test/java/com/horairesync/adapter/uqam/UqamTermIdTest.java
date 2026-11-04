package com.horairesync.adapter.uqam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UqamTermIdTest {

    @Test
    void parsesAndFormatsIds() {
        UqamTermId winter = UqamTermId.parse("20271").orElseThrow();

        assertThat(winter.year()).isEqualTo(2027);
        assertThat(winter.season()).isEqualTo(1);
        assertThat(winter.id()).isEqualTo("20271");
        assertThat(winter.label()).isEqualTo("Hiver 2027");
        assertThat(UqamTermId.parse("20262").orElseThrow().label()).isEqualTo("Été 2026");
        assertThat(UqamTermId.parse("20263").orElseThrow().label()).isEqualTo("Automne 2026");
    }

    @Test
    void rejectsInvalidIds() {
        assertThat(UqamTermId.parse("20274")).isEmpty();
        assertThat(UqamTermId.parse("H27")).isEmpty();
        assertThat(UqamTermId.parse(null)).isEmpty();
        assertThatThrownBy(() -> new UqamTermId(2027, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsLabelsFromText() {
        assertThat(UqamTermId.fromLabel("Trimestre d'été 2026").orElseThrow().id()).isEqualTo("20262");
        assertThat(UqamTermId.fromLabel("Horaire - Hiver 2027").orElseThrow().id()).isEqualTo("20271");
        assertThat(UqamTermId.fromLabel("Trimestre d'automne 2026").orElseThrow().id()).isEqualTo("20263");
        assertThat(UqamTermId.fromLabel("Calendrier 2026-2027")).isEmpty();
    }
}
