package com.horairesync.adapter.uqam;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.Term;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Parses the academic calendar {@code /calendriers}.
 *
 * <p>Each term is a {@code div.titre_slider} ("Trimestre d'hiver 2027") whose {@code data-target} points to a block
 * of tables. The first cell of a table is its label ("Début du trimestre…", "Congés", "Semaine de lecture…",
 * "Fin du trimestre"), the rest holds dates without year. A date is kept only when it is unambiguous: "23 avril ou
 * 30 avril ou 2 mai" leaves {@code endDate} empty.
 */
final class UqamCalendarParser {

    private static final Pattern PARENTHESIS = Pattern.compile("\\(([^)]*)\\)");

    private UqamCalendarParser() {}

    static List<Term> parse(Document doc) {
        Map<String, Term> terms = new LinkedHashMap<>();
        for (Element slider : doc.select("div.titre_slider")) {
            Optional<UqamTermId> id = UqamTermId.fromLabel(slider.text())
                    .filter(t -> UqamText.fold(slider.text()).startsWith("trimestre"));
            String target = slider.attr("data-target");
            if (id.isEmpty() || !target.startsWith("#")) {
                continue;
            }
            Element block = doc.getElementById(target.substring(1));
            if (block != null) {
                terms.putIfAbsent(id.get().id(), parseTerm(id.get(), block));
            }
        }
        if (terms.isEmpty()) {
            throw new HoraireSyncException(ErrorCode.PARSING_FAILED,
                    "Le calendrier universitaire de l'UQAM a une structure inattendue.");
        }
        return terms.values().stream().sorted(Comparator.comparing(Term::id)).toList();
    }

    private static Term parseTerm(UqamTermId id, Element block) {
        LocalDate start = null;
        LocalDate end = null;
        List<LocalDate> holidays = new ArrayList<>();
        List<LocalDate> readingWeek = new ArrayList<>();
        String readingWeekNote = null;
        boolean holidaysFound = false;

        for (Element table : block.select("table")) {
            Element firstCell = table.selectFirst("tr > th, tr > td");
            if (firstCell == null) {
                continue;
            }
            String label = UqamText.fold(firstCell.text());
            String value = UqamText.clean(table.text()).substring(Math.min(
                    UqamText.clean(firstCell.text()).length(), UqamText.clean(table.text()).length()));
            List<LocalDate> dates = UqamText.datesWithoutYear(value, id.year());

            if (label.startsWith("debut du trimestre")) {
                start = dates.size() == 1 ? dates.get(0) : null;
            } else if (label.startsWith("fin du trimestre")) {
                end = dates.size() == 1 ? dates.get(0) : null;
            } else if (label.startsWith("conges")) {
                holidaysFound = true;
                holidays.addAll(dates);
            } else if (label.startsWith("semaine de lecture") || label.contains("relache")) {
                readingWeek.addAll(dates);
                Matcher note = PARENTHESIS.matcher(value);
                readingWeekNote = note.find() ? UqamText.emptyToNull(note.group(1)) : null;
            }
        }

        List<String> missing = new ArrayList<>();
        if (start == null) {
            missing.add("startDate");
        }
        if (end == null) {
            missing.add("endDate");
        }
        if (!holidaysFound) {
            missing.add("excludedDates");
        }
        return new Term(id.id(), id.label(), start, end, holidays.stream().distinct().sorted().toList(),
                readingWeek.stream().distinct().sorted().toList(), readingWeekNote, missing);
    }
}
