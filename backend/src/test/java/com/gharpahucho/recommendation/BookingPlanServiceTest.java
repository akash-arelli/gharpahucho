package com.gharpahucho.recommendation;

import com.gharpahucho.dto.BookingPlanDtos.BookingPlan;
import com.gharpahucho.dto.BookingPlanDtos.BookingWindow;
import com.gharpahucho.integration.transport.FestiveCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingPlanServiceTest {

    private final FestiveCalendar calendar = new FestiveCalendar();
    private final LocalDate today = LocalDate.of(2026, 11, 25);

    @Test
    void bookingOpensWindowDaysBeforeTravelAndIsConfigurable() {
        LocalDate travel = LocalDate.of(2027, 1, 13); // Pongal peak
        BookingWindow sixty = BookingPlanService.window(travel, today, 60);
        assertThat(sixty.opensOn()).isEqualTo(LocalDate.of(2026, 11, 14)); // 13 Jan minus 60 days: already open on 25 Nov
        assertThat(sixty.status()).isEqualTo("OPEN");
        // A shorter window (as Flagsmith could set it) opens later, so the same journey becomes "upcoming".
        BookingWindow thirty = BookingPlanService.window(travel, today, 30);
        assertThat(thirty.opensOn()).isEqualTo(LocalDate.of(2026, 12, 14));
        assertThat(thirty.status()).isEqualTo("UPCOMING");
        assertThat(thirty.daysUntilOpen()).isEqualTo(19);
    }

    @Test
    void windowStates() {
        assertThat(BookingPlanService.window(today.plusDays(90), today, 60).status()).isEqualTo("UPCOMING");
        assertThat(BookingPlanService.window(today.plusDays(40), today, 60).status()).isEqualTo("OPEN");
        assertThat(BookingPlanService.window(today.plusDays(1), today, 60).headline()).contains("tatkal");
        assertThat(BookingPlanService.window(today, today, 60).status()).isEqualTo("CLOSED");
    }

    @Test
    void freePlanSeesTheBookingDateButLaterStepsAreLockedAndEmpty() {
        BookingPlan plan = BookingPlanService.build(UUID.randomUUID(), "Bengaluru", "Hyderabad", LocalDate.of(2026, 12, 24), today, false, 60, calendar);
        assertThat(plan.fullAccess()).isFalse();
        assertThat(plan.lock()).isNotNull();
        assertThat(plan.days()).isEmpty();
        assertThat(plan.steps()).hasSize(4);
        assertThat(plan.steps().get(0).locked()).isFalse();
        assertThat(plan.steps().subList(1, 4)).allSatisfy(s -> {
            assertThat(s.locked()).isTrue();
            assertThat(s.detail()).isNull(); // the paid content must not leak to free users
            assertThat(s.when()).isNull();
            assertThat(s.title()).doesNotContainPattern("\\d"); // not even the recommended date
        });
    }

    @Test
    void paidPlanComparesDaysAndRecommendsACalmerOneOnTheFestivalPeak() {
        BookingPlan plan = BookingPlanService.build(UUID.randomUUID(), "Bengaluru", "Hyderabad", LocalDate.of(2026, 12, 24), today, true, 60, calendar);
        assertThat(plan.lock()).isNull();
        assertThat(plan.festival().name()).contains("Christmas");
        assertThat(plan.days()).isNotEmpty();
        assertThat(plan.days()).anySatisfy(d -> assertThat(d.chosen()).isTrue());
        assertThat(plan.days()).anySatisfy(d -> assertThat(d.calmer()).isTrue());
        assertThat(plan.steps().get(1).title()).startsWith("Consider leaving on");
        assertThat(plan.steps()).noneMatch(s -> s.locked());
    }

    @Test
    void anOrdinaryWeekGetsNoDateShiftAdvice() {
        BookingPlan plan = BookingPlanService.build(UUID.randomUUID(), "Chennai", "Madurai", LocalDate.of(2027, 2, 9), today, true, 60, calendar);
        assertThat(plan.festival()).isNull();
        assertThat(plan.steps().get(1).title()).contains("already one of the calmer days");
    }

    @Test
    void pressureLevelsAreOrdered() {
        assertThat(BookingPlanService.level(0)).isEqualTo("LOW");
        assertThat(BookingPlanService.level(30)).isEqualTo("MODERATE");
        assertThat(BookingPlanService.level(60)).isEqualTo("HIGH");
        assertThat(BookingPlanService.level(90)).isEqualTo("PEAK");
    }
}
