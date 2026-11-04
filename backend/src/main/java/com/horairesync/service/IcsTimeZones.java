package com.horairesync.service;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.Daylight;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.component.Standard;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.TzName;
import net.fortuna.ical4j.model.property.TzOffsetFrom;
import net.fortuna.ical4j.model.property.TzOffsetTo;

/**
 * Builds a compact VTIMEZONE from the JDK's current rules for a zone.
 *
 * <p>ical4j can provide a VTIMEZONE, but it lists every rule since 1895 (about 20 sub-components for Toronto).
 * Calendar apps only need the rules in force, so this class writes one STANDARD and one DAYLIGHT block with a yearly
 * RRULE, like the files exported by Google Calendar. For America/Toronto: DST from the 2nd Sunday of March to the
 * 1st Sunday of November, at 02:00 local time.
 */
final class IcsTimeZones {

    private IcsTimeZones() {}

    static VTimeZone vTimeZone(ZoneId zone) {
        ZoneRules rules = zone.getRules();
        List<ZoneOffsetTransitionRule> transitionRules = rules.getTransitionRules();
        if (transitionRules.isEmpty()) {
            throw new IllegalArgumentException("Zone without recurring rules is not supported: " + zone);
        }
        List<ZoneOffsetTransition> history = rules.getTransitions();
        int firstYear = history.isEmpty() ? 1970 : history.get(history.size() - 1).getDateTimeAfter().getYear() + 1;

        List<Observance> observances = new ArrayList<>();
        for (ZoneOffsetTransitionRule rule : transitionRules) {
            ZoneOffsetTransition first = rule.createTransition(firstYear);
            boolean daylight = first.getOffsetAfter().getTotalSeconds() > first.getOffsetBefore().getTotalSeconds();
            PropertyList props = new PropertyList(List.of(
                    new TzOffsetFrom(first.getOffsetBefore()),
                    new TzOffsetTo(first.getOffsetAfter()),
                    new TzName(shortName(zone, daylight)),
                    new DtStart<>(first.getDateTimeBefore()),
                    new RRule<>(new Recur<>(yearlyRule(rule)))));
            observances.add(daylight ? new Daylight(props) : new Standard(props));
        }
        return new VTimeZone(new PropertyList(List.of(new TzId(zone.getId()))), new ComponentList<>(observances));
    }

    /** Abbreviation such as EST or EDT, from the JDK's time zone names. */
    private static String shortName(ZoneId zone, boolean daylight) {
        return TimeZone.getTimeZone(zone).getDisplayName(daylight, TimeZone.SHORT, Locale.ENGLISH);
    }

    /** RRULE value for a JDK transition rule, e.g. {@code FREQ=YEARLY;BYMONTH=3;BYDAY=2SU}. */
    static String yearlyRule(ZoneOffsetTransitionRule rule) {
        int month = rule.getMonth().getValue();
        int indicator = rule.getDayOfMonthIndicator();
        DayOfWeek dayOfWeek = rule.getDayOfWeek();
        if (dayOfWeek == null) {
            return "FREQ=YEARLY;BYMONTH=" + month + ";BYMONTHDAY=" + indicator;
        }
        String day = dayOfWeek.name().substring(0, 2).toUpperCase(Locale.ROOT);
        if (indicator < 0) {
            return "FREQ=YEARLY;BYMONTH=" + month + ";BYDAY=" + indicator + day;
        }
        if ((indicator - 1) % 7 != 0) {
            throw new IllegalArgumentException("Unsupported transition rule: " + rule);
        }
        return "FREQ=YEARLY;BYMONTH=" + month + ";BYDAY=" + ((indicator - 1) / 7 + 1) + day;
    }
}
