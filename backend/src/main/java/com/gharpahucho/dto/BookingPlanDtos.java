package com.gharpahucho.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** "When should I book?" for a journey, and the dashboard radar built from the same rules. */
public final class BookingPlanDtos {

    private BookingPlanDtos() {
    }

    public record BookingPlan(
            UUID tripId,
            String sourceCity,
            String destinationCity,
            LocalDate travelDate,
            LocalDate today,
            /** False when the user's plan does not include the full festival plan (Flagsmith: festival_booking_plan). */
            boolean fullAccess,
            FestivalContext festival,
            BookingWindow railWindow,
            /** Empty when locked. */
            List<DayPressure> days,
            List<Step> steps,
            Lock lock,
            List<String> notes) {
    }

    public record FestivalContext(String name, LocalDate start, LocalDate peak, LocalDate end, boolean travelDuringFestival, int daysToStart) {
    }

    /** {@code status}: UPCOMING (opens later), OPEN (book now) or CLOSED (advance window has passed for that date). */
    public record BookingWindow(String status, int windowDays, LocalDate opensOn, String opensAt, long daysUntilOpen, String headline) {
    }

    /** {@code level}: LOW, MODERATE, HIGH or PEAK. {@code vsChosenPct} is negative for calmer days than the chosen one. */
    public record DayPressure(LocalDate date, String weekday, int pressure, String level, int vsChosenPct, boolean chosen, boolean calmer) {
    }

    public record Step(int order, String title, String detail, LocalDate when, boolean locked) {
    }

    public record Lock(String requiredPlan, String message) {
    }

    public record RadarItem(UUID tripId, String route, LocalDate travelDate, String status, LocalDate opensOn, long daysUntilOpen,
                            String headline, boolean urgent) {
    }

    public record Radar(List<RadarItem> items, int hiddenByPlan, boolean fullAccess, String upgradeMessage) {
    }
}
