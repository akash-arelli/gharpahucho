package com.gharpahucho.integration.transport;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Festive-season demand model. It shapes the simulated fares (festival weeks cost more) and drives the booking plan's
 * "how crowded is each day" estimate. It is a calendar-based estimate, not market data, and the UI labels it as such.
 */
@Component
public class FestiveCalendar {

    public record Festival(String name, LocalDate start, LocalDate end, LocalDate peak) {
    }

    public record Demand(Festival festival, double multiplier, int level) {
        public static final Demand NONE = new Demand(null, 1.0, 0);
    }

    private static final double MAX_UPLIFT = 0.60;

    private final List<Festival> festivals = List.of(
            f("Dussehra", 2026, 10, 16, 2026, 10, 22, 2026, 10, 20),
            f("Diwali", 2026, 11, 5, 2026, 11, 12, 2026, 11, 8),
            f("Christmas & New Year", 2026, 12, 20, 2027, 1, 2, 2026, 12, 24),
            f("Sankranti / Pongal", 2027, 1, 9, 2027, 1, 18, 2027, 1, 13),
            f("Holi", 2027, 3, 19, 2027, 3, 24, 2027, 3, 21),
            f("Dussehra", 2027, 10, 5, 2027, 10, 11, 2027, 10, 9),
            f("Diwali", 2027, 10, 25, 2027, 11, 1, 2027, 10, 29));

    private static Festival f(String name, int sy, int sm, int sd, int ey, int em, int ed, int py, int pm, int pd) {
        return new Festival(name, LocalDate.of(sy, sm, sd), LocalDate.of(ey, em, ed), LocalDate.of(py, pm, pd));
    }

    public Demand demandOn(LocalDate date) {
        Optional<Festival> active = festivals.stream().filter(fs -> !date.isBefore(fs.start()) && !date.isAfter(fs.end())).findFirst();
        double uplift = 0;
        int level = 0;
        Festival festival = null;
        if (active.isPresent()) {
            festival = active.get();
            long toPeak = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, festival.peak()));
            long span = Math.max(1, Math.max(
                    java.time.temporal.ChronoUnit.DAYS.between(festival.start(), festival.peak()),
                    java.time.temporal.ChronoUnit.DAYS.between(festival.peak(), festival.end())));
            double tent = Math.max(0.35, 1.0 - (double) toPeak / (span + 1));
            uplift = MAX_UPLIFT * tent;
            level = (int) Math.round(tent * 100);
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SUNDAY) {
            uplift += 0.05;
        }
        return new Demand(festival, 1.0 + uplift, level);
    }

    /** The festival that contains the date, or the next one starting within {@code marginDays} after it. */
    public Optional<Festival> around(LocalDate date, int marginDays) {
        return festivals.stream()
                .filter(fs -> !date.isBefore(fs.start()) && !date.isAfter(fs.end())
                        || (date.isBefore(fs.start()) && !date.plusDays(marginDays).isBefore(fs.start())))
                .min(java.util.Comparator.comparing(Festival::start));
    }

    /** 0 on a normal day, 100 at the model's maximum festival uplift. An estimate from the calendar, not live fares. */
    public static int pressure(Demand demand) {
        double uplift = demand.multiplier() - 1.0;
        return (int) Math.max(0, Math.min(100, Math.round(uplift / (MAX_UPLIFT + 0.05) * 100)));
    }

    public String noteFor(Demand demand) {
        if (demand.festival() == null || demand.multiplier() < 1.08) {
            return null;
        }
        long pct = Math.round((demand.multiplier() - 1.0) * 100);
        return demand.festival().name() + " demand: sample fares are about " + pct + "% above a normal day";
    }
}
