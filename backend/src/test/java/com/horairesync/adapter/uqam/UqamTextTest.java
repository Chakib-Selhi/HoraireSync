package com.horairesync.adapter.uqam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UqamTextTest {

    @Test
    void cleanReplacesNonBreakingSpacesAndCollapsesWhitespace() {
        assertThat(UqamText.clean("De 09h30 à  12h30 \n")).isEqualTo("De 09h30 à 12h30");
        assertThat(UqamText.clean(null)).isEmpty();
        assertThat(UqamText.emptyToNull("  ")).isNull();
    }

    @Test
    void foldRemovesAccentsAndCase() {
        assertThat(UqamText.fold("Été Décembre")).isEqualTo("ete decembre");
    }

    @Test
    void parsesFrenchDays() {
        assertThat(UqamText.day("Mercredi ")).contains(DayOfWeek.WEDNESDAY);
        assertThat(UqamText.day("-")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"janvier,1", "févr.,2", "fevrier,2", "avr.,4", "août,8", "sept.,9", "déc.,12", "décembre,12"})
    void parsesFullAndAbbreviatedMonths(String text, int month) {
        assertThat(UqamText.month(text)).contains(month);
    }

    @Test
    void parsesFullDatesInOrder() {
        assertThat(UqamText.fullDates("Du 11 janvier 2027 au 2 mai 2027"))
                .containsExactly(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 5, 2));
        assertThat(UqamText.fullDate("Le 1er mars 2027")).contains(LocalDate.of(2027, 3, 1));
        assertThat(UqamText.fullDate("bientôt")).isEmpty();
    }

    @Test
    void expandsRangesWithoutYear() {
        assertThat(UqamText.datesWithoutYear("1er mars au 7 mars (selon facultés/écoles)", 2027))
                .hasSize(7).startsWith(LocalDate.of(2027, 3, 1)).endsWith(LocalDate.of(2027, 3, 7));
        assertThat(UqamText.datesWithoutYear("26 au 30 octobre (ESG seulement)", 2026))
                .hasSize(5).startsWith(LocalDate.of(2026, 10, 26));
    }

    @Test
    void listsSingleDatesWithoutYear() {
        assertThat(UqamText.datesWithoutYear("18 mai 24 juin 1er juillet", 2026)).containsExactly(
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 24), LocalDate.of(2026, 7, 1));
        assertThat(UqamText.datesWithoutYear("31 février", 2026)).isEmpty();
    }

    @Test
    void parsesTimesInBothFormats() {
        assertThat(UqamText.times("De 09h30 à 12h30"))
                .containsExactly(LocalTime.of(9, 30), LocalTime.of(12, 30));
        assertThat(UqamText.times("14:00 à 17:00")).containsExactly(LocalTime.of(14, 0), LocalTime.of(17, 0));
        assertThat(UqamText.times("25h00")).isEmpty();
    }
}
