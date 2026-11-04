package com.horairesync.adapter.uqam;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers for French content published by UQAM: whitespace cleanup, days, months, dates and times.
 *
 * <p>Every parse method returns {@link Optional#empty()} rather than guessing when the text does not match.
 */
final class UqamText {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("janv", 1),
            Map.entry("fevrier", 2), Map.entry("fevr", 2), Map.entry("fev", 2),
            Map.entry("mars", 3),
            Map.entry("avril", 4), Map.entry("avr", 4),
            Map.entry("mai", 5),
            Map.entry("juin", 6),
            Map.entry("juillet", 7), Map.entry("juil", 7),
            Map.entry("aout", 8),
            Map.entry("septembre", 9), Map.entry("sept", 9),
            Map.entry("octobre", 10), Map.entry("oct", 10),
            Map.entry("novembre", 11), Map.entry("nov", 11),
            Map.entry("decembre", 12), Map.entry("dec", 12));

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "lundi", DayOfWeek.MONDAY, "mardi", DayOfWeek.TUESDAY, "mercredi", DayOfWeek.WEDNESDAY,
            "jeudi", DayOfWeek.THURSDAY, "vendredi", DayOfWeek.FRIDAY, "samedi", DayOfWeek.SATURDAY,
            "dimanche", DayOfWeek.SUNDAY);

    /** Month names as they appear (with accents and optional abbreviation dot). */
    static final String MONTH_REGEX =
            "(janvier|janv\\.?|f[ée]vrier|f[ée]vr\\.?|f[ée]v\\.?|mars|avril|avr\\.?|mai|juin|juillet|juil\\.?"
                    + "|ao[ûu]t|septembre|sept\\.?|octobre|oct\\.?|novembre|nov\\.?|d[ée]cembre|d[ée]c\\.?)";

    /** A day of month followed by a month, e.g. "1er mars", "27 févr.". */
    private static final Pattern DAY_MONTH = Pattern.compile("(\\d{1,2})(?:er)?\\s+" + MONTH_REGEX,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A range such as "1er mars au 7 mars" or "26 au 30 octobre". */
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})(?:er)?(?:\\s+" + MONTH_REGEX + ")?\\s+au\\s+(\\d{1,2})(?:er)?\\s+" + MONTH_REGEX,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern FULL_DATE = Pattern.compile(
            "(\\d{1,2})(?:er)?\\s+" + MONTH_REGEX + "\\s+(\\d{4})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TIME = Pattern.compile("(\\d{1,2})\\s*[h:]\\s*(\\d{2})");

    private UqamText() {}

    /** Replaces non-breaking spaces, collapses whitespace and trims. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Lower case without accents, for comparisons. */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(clean(text), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** {@code null} when the cleaned text is empty. */
    static String emptyToNull(String text) {
        String cleaned = clean(text);
        return cleaned.isEmpty() ? null : cleaned;
    }

    static Optional<DayOfWeek> day(String text) {
        return Optional.ofNullable(DAYS.get(fold(text)));
    }

    static Optional<Integer> month(String text) {
        return Optional.ofNullable(MONTHS.get(fold(text).replace(".", "")));
    }

    /** Parses "11 janvier 2027". */
    static Optional<LocalDate> fullDate(String text) {
        Matcher m = FULL_DATE.matcher(clean(text));
        if (!m.find()) {
            return Optional.empty();
        }
        return safeDate(Integer.parseInt(m.group(3)), m.group(2), Integer.parseInt(m.group(1)));
    }

    /** All full dates ("11 janvier 2027") in order of appearance. */
    static List<LocalDate> fullDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher m = FULL_DATE.matcher(clean(text));
        while (m.find()) {
            safeDate(Integer.parseInt(m.group(3)), m.group(2), Integer.parseInt(m.group(1))).ifPresent(dates::add);
        }
        return dates;
    }

    /**
     * Dates without year ("4 mai", "1er mars au 7 mars") in order of appearance; ranges are expanded day by day.
     */
    static List<LocalDate> datesWithoutYear(String text, int year) {
        String cleaned = clean(text);
        List<LocalDate> dates = new ArrayList<>();
        Matcher range = RANGE.matcher(cleaned);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (range.find()) {
            String startMonth = range.group(2) != null ? range.group(2) : range.group(4);
            Optional<LocalDate> from = safeDate(year, startMonth, Integer.parseInt(range.group(1)));
            Optional<LocalDate> to = safeDate(year, range.group(4), Integer.parseInt(range.group(3)));
            if (from.isPresent() && to.isPresent() && !to.get().isBefore(from.get())) {
                from.get().datesUntil(to.get().plusDays(1)).forEach(dates::add);
            }
            rest.append(cleaned, last, range.start()).append(' ');
            last = range.end();
        }
        rest.append(cleaned.substring(last));
        Matcher single = DAY_MONTH.matcher(rest);
        while (single.find()) {
            safeDate(year, single.group(2), Integer.parseInt(single.group(1))).ifPresent(dates::add);
        }
        dates.sort(null);
        return dates;
    }

    /** All times ("09h30", "14:00") in order of appearance. */
    static List<LocalTime> times(String text) {
        List<LocalTime> times = new ArrayList<>();
        Matcher m = TIME.matcher(clean(text));
        while (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            if (hour < 24 && minute < 60) {
                times.add(LocalTime.of(hour, minute));
            }
        }
        return times;
    }

    static Optional<LocalDate> safeDate(int year, String monthText, int day) {
        return month(monthText).flatMap(month -> {
            try {
                return Optional.of(LocalDate.of(year, month, day));
            } catch (java.time.DateTimeException e) {
                return Optional.empty();
            }
        });
    }
}
