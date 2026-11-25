package com.gharpahucho.recommendation;

import com.gharpahucho.dto.BookingPlanDtos.*;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.TripStatus;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RuntimeConfig;
import com.gharpahucho.integration.transport.FestiveCalendar;
import com.gharpahucho.integration.transport.FestiveCalendar.Demand;
import com.gharpahucho.repository.TripRepository;
import com.gharpahucho.service.TripService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * "When should I book?" Built only from things we can stand behind: the rail advance-booking window (a Flagsmith
 * remote-config value, because Indian Railways changes it), the festival calendar, and simple date arithmetic. It never
 * claims seat availability or live fares. What is included is decided per user by Flagsmith (festival_booking_plan).
 */
@Service
public class BookingPlanService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final String OPENS_AT = "08:00";
    private static final String UPGRADE = "Unlock the day-by-day comparison, date-shift savings and every step with the Festival Pass.";

    private final TripService trips;
    private final TripRepository tripRepository;
    private final FeatureFlagService flags;
    private final FestiveCalendar calendar;
    private final Clock clock;

    public BookingPlanService(TripService trips, TripRepository tripRepository, FeatureFlagService flags, FestiveCalendar calendar, Clock clock) {
        this.trips = trips;
        this.tripRepository = tripRepository;
        this.flags = flags;
        this.calendar = calendar;
        this.clock = clock;
    }

    public BookingPlan forTrip(UUID userId, UUID tripId) {
        Trip t = trips.owned(userId, tripId);
        boolean full = flags.isEnabled(FeatureKey.FESTIVAL_BOOKING_PLAN, userId);
        RuntimeConfig cfg = flags.config(userId);
        return build(t.getId(), t.getSourceCity(), t.getDestinationCity(), t.getTravelDate(), today(), full, cfg.railBookingWindowDays(), calendar);
    }

    public Radar radar(UUID userId) {
        boolean full = flags.isEnabled(FeatureKey.FESTIVAL_BOOKING_PLAN, userId);
        RuntimeConfig cfg = flags.config(userId);
        LocalDate today = today();
        List<RadarItem> all = new ArrayList<>();
        for (Trip t : tripRepository.findByOwnerIdAndStatusAndTravelDateGreaterThanEqualOrderByTravelDateAsc(userId, TripStatus.PLANNED, today)) {
            all.add(radarItem(t, today, cfg.railBookingWindowDays()));
        }
        all.sort(Comparator.comparing(RadarItem::urgent).reversed().thenComparing(RadarItem::travelDate));
        int limit = full ? all.size() : Math.min(all.size(), cfg.freeRadarTrips());
        return new Radar(List.copyOf(all.subList(0, limit)), all.size() - limit, full, full ? null : UPGRADE);
    }

    // ------------------------------------------------------------------ pure logic (unit tested)

    static RadarItem radarItem(Trip t, LocalDate today, int windowDays) {
        BookingWindow w = window(t.getTravelDate(), today, windowDays);
        boolean urgent = ("UPCOMING".equals(w.status()) && w.daysUntilOpen() <= 7)
                || ("OPEN".equals(w.status()) && ChronoUnit.DAYS.between(today, t.getTravelDate()) <= 30);
        return new RadarItem(t.getId(), t.getSourceCity() + " → " + t.getDestinationCity(), t.getTravelDate(), w.status(), w.opensOn(),
                w.daysUntilOpen(), w.headline(), urgent);
    }

    static BookingPlan build(UUID tripId, String source, String destination, LocalDate travel, LocalDate today, boolean full,
                             int windowDays, FestiveCalendar calendar) {
        BookingWindow window = window(travel, today, windowDays);
        FestivalContext festival = calendar.around(travel, 10).map(f -> new FestivalContext(f.name(), f.start(), f.peak(), f.end(),
                !travel.isBefore(f.start()) && !travel.isAfter(f.end()),
                (int) Math.max(0, ChronoUnit.DAYS.between(travel, f.start())))).orElse(null);

        Demand chosen = calendar.demandOn(travel);
        List<DayPressure> days = new ArrayList<>();
        for (int offset = -3; offset <= 3; offset++) {
            LocalDate d = travel.plusDays(offset);
            if (!d.isAfter(today)) {
                continue;
            }
            Demand demand = calendar.demandOn(d);
            int vs = (int) Math.round((demand.multiplier() / chosen.multiplier() - 1.0) * 100);
            int p = FestiveCalendar.pressure(demand);
            days.add(new DayPressure(d, d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), p, level(p), vs, offset == 0, vs <= -5));
        }

        List<Step> steps = new ArrayList<>();
        steps.add(bookingStep(window, travel));
        steps.add(new Step(2, dateShiftTitle(days, travel), dateShiftDetail(days, travel, festival), bestDay(days).map(DayPressure::date).orElse(null), !full));
        steps.add(new Step(3, "Keep a backup before you commit",
                "If your train is waitlisted, hold a shared ride or a bus as a fallback. Travellers going your way can share the cost, so check Companions before you decide.", null, !full));
        steps.add(new Step(4, "Know your last-minute options",
                "Tatkal opens the day before travel, usually 10:00 for AC and 11:00 for Sleeper. Rules change, so confirm on IRCTC.", null, !full));
        if (!full) {
            // Locked steps keep a generic teaser title only: the actual advice (even the recommended date) is paid content.
            steps = steps.stream().map(s -> s.locked() ? new Step(s.order(), lockedTitle(s.order()), null, null, true) : s).toList();
        }

        List<String> notes = List.of(
                "Crowding is an estimate from the festival calendar. It is not live fares or seat availability.",
                "Booking opens " + windowDays + " days before travel (maintained by us; confirm on IRCTC, rules change).");
        return new BookingPlan(tripId, source, destination, travel, today, full, festival, window, full ? days : List.of(), steps,
                full ? null : new Lock("plus", UPGRADE), notes);
    }

    private static String lockedTitle(int order) {
        return switch (order) {
            case 2 -> "Find the calmer day to leave";
            case 3 -> "Keep a backup before you commit";
            default -> "Know your last-minute options";
        };
    }

    static BookingWindow window(LocalDate travel, LocalDate today, int windowDays) {
        LocalDate opens = travel.minusDays(windowDays);
        long toOpen = ChronoUnit.DAYS.between(today, opens);
        long toTravel = ChronoUnit.DAYS.between(today, travel);
        if (toTravel <= 0) {
            return new BookingWindow("CLOSED", windowDays, opens, OPENS_AT, 0, "This journey is today or in the past.");
        }
        if (toOpen > 0) {
            String when = toOpen == 1 ? "tomorrow" : "in " + toOpen + " days";
            return new BookingWindow("UPCOMING", windowDays, opens, OPENS_AT, toOpen,
                    "Booking opens " + when + " (" + SHORT.format(opens) + " at " + OPENS_AT + "). Be ready.");
        }
        String headline = toTravel <= 1
                ? "Only last-minute (tatkal) tickets are likely left. Look at shared rides too."
                : toTravel <= 5 ? "Booking is open and travel is " + toTravel + " days away. Move quickly, or try tatkal and shared rides."
                : "Booking is open now. Festival trains fill fast, so book as early as you can.";
        return new BookingWindow("OPEN", windowDays, opens, OPENS_AT, 0, headline);
    }

    private static Step bookingStep(BookingWindow w, LocalDate travel) {
        return switch (w.status()) {
            case "UPCOMING" -> new Step(1, "Set a reminder for " + SHORT.format(w.opensOn()),
                    "Booking for " + DAY.format(travel) + " opens at " + w.opensAt() + " on " + DAY.format(w.opensOn()) + ". Seats on festival trains go within minutes.", w.opensOn(), false);
            case "OPEN" -> new Step(1, "Book now",
                    "The advance window is open for " + DAY.format(travel) + ". Compare options in the planner and book the one you prefer today.", null, false);
            default -> new Step(1, "Pick another date", "This date can no longer be planned in advance.", null, false);
        };
    }

    private static Optional<DayPressure> bestDay(List<DayPressure> days) {
        return days.stream().filter(DayPressure::calmer).min(Comparator.comparingInt(DayPressure::vsChosenPct));
    }

    private static String dateShiftTitle(List<DayPressure> days, LocalDate travel) {
        return bestDay(days).map(d -> "Consider leaving on " + DAY.format(d.date()))
                .orElse("Your date is already one of the calmer days");
    }

    private static String dateShiftDetail(List<DayPressure> days, LocalDate travel, FestivalContext festival) {
        String festivalNote = festival == null ? "" : " around " + festival.name();
        return bestDay(days).map(d -> "Typically about " + Math.abs(d.vsChosenPct()) + "% less crowded than " + DAY.format(travel) + festivalNote
                        + ". Fewer people usually means better seats and fairer fares. Check the planner for real options on that date.")
                .orElse("No nearby day looks meaningfully calmer than " + DAY.format(travel) + festivalNote + ". Book early rather than shifting.");
    }

    static String level(int pressure) {
        return pressure < 20 ? "LOW" : pressure < 45 ? "MODERATE" : pressure < 75 ? "HIGH" : "PEAK";
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(IST));
    }
}
