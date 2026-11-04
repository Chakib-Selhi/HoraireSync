package com.horairesync.adapter.uqam;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UQAM term identifier {@code YYYYT}: T is 1 for winter (Hiver), 2 for summer (Été), 3 for fall (Automne).
 *
 * @param year   calendar year of the term
 * @param season 1, 2 or 3
 */
record UqamTermId(int year, int season) {

    private static final Pattern ID = Pattern.compile("(\\d{4})([123])");
    private static final Pattern LABEL = Pattern.compile("(hiver|ete|automne)\\s+(\\d{4})");

    UqamTermId {
        if (season < 1 || season > 3) {
            throw new IllegalArgumentException("season must be 1, 2 or 3");
        }
    }

    static Optional<UqamTermId> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Matcher m = ID.matcher(id.trim());
        return m.matches()
                ? Optional.of(new UqamTermId(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))))
                : Optional.empty();
    }

    /** Finds "Hiver 2027", "été 2026", "d'automne 2026"… in a text. */
    static Optional<UqamTermId> fromLabel(String text) {
        Matcher m = LABEL.matcher(UqamText.fold(text));
        if (!m.find()) {
            return Optional.empty();
        }
        int season = switch (m.group(1)) {
            case "hiver" -> 1;
            case "ete" -> 2;
            default -> 3;
        };
        return Optional.of(new UqamTermId(Integer.parseInt(m.group(2)), season));
    }

    String id() {
        return String.format(Locale.ROOT, "%04d%d", year, season);
    }

    String label() {
        String name = switch (season) {
            case 1 -> "Hiver";
            case 2 -> "Été";
            default -> "Automne";
        };
        return name + " " + year;
    }
}
