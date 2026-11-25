package com.gharpahucho.controller;

import com.gharpahucho.dto.BookingPlanDtos;
import com.gharpahucho.recommendation.BookingPlanService;
import com.gharpahucho.dto.PlanDtos.ExplainRequest;
import com.gharpahucho.dto.PlanDtos.ExplainResponse;
import com.gharpahucho.dto.PlanDtos.PlanRequest;
import com.gharpahucho.dto.PlanDtos.PlanResponse;
import com.gharpahucho.dto.TripDtos.*;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RequiresFeature;
import com.gharpahucho.recommendation.TripPlanningService;
import com.gharpahucho.security.AiRateLimiter;
import com.gharpahucho.security.CurrentUser;
import com.gharpahucho.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService trips;
    private final TripPlanningService planner;
    private final AiRateLimiter aiLimiter;
    private final BookingPlanService bookingPlans;

    // ------------------------------------------------------------------ planner (Flagsmith: smart_trip_planner)

    @PostMapping("/plan")
    @RequiresFeature(FeatureKey.SMART_TRIP_PLANNER)
    public PlanResponse plan(@Valid @RequestBody PlanRequest request) {
        return planner.plan(CurrentUser.id(), request);
    }

    /** AI explanation of one option (Flagsmith: ai_trip_explanations). Rate limited because it spends Sarvam credits. */
    @PostMapping("/plan/explain")
    @RequiresFeature(FeatureKey.AI_TRIP_EXPLANATIONS)
    public ExplainResponse explain(@Valid @RequestBody ExplainRequest request) {
        aiLimiter.check(CurrentUser.id());
        return planner.explain(CurrentUser.id(), request);
    }

    // ------------------------------------------------------------------ journeys

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto create(@Valid @RequestBody TripRequest request) {
        return trips.create(CurrentUser.id(), request);
    }

    @GetMapping
    public Page<TripDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return trips.list(CurrentUser.id(), PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size))));
    }

    @GetMapping("/{id}")
    public TripDto get(@PathVariable UUID id) {
        return trips.get(CurrentUser.id(), id);
    }

    /** When to book. Plan-aware: Flagsmith (festival_booking_plan) decides how much of it this user gets. */
    @GetMapping("/{id}/booking-plan")
    public BookingPlanDtos.BookingPlan bookingPlan(@PathVariable UUID id) {
        return bookingPlans.forTrip(CurrentUser.id(), id);
    }

    /** Dashboard booking radar across the user's upcoming journeys. */
    @GetMapping("/booking-radar")
    public BookingPlanDtos.Radar bookingRadar() {
        return bookingPlans.radar(CurrentUser.id());
    }

    @PatchMapping("/{id}")
    public TripDto patch(@PathVariable UUID id, @Valid @RequestBody TripPatchRequest request) {
        return trips.patch(CurrentUser.id(), id, request);
    }

    // ------------------------------------------------------------------ saved options

    @GetMapping("/saved")
    public List<SavedTripDto> saved() {
        return trips.savedTrips(CurrentUser.id());
    }

    @PostMapping("/saved")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedTripDto save(@Valid @RequestBody SaveTripRequest request) {
        return trips.save(CurrentUser.id(), request);
    }

    @DeleteMapping("/saved/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSaved(@PathVariable UUID id) {
        trips.deleteSaved(CurrentUser.id(), id);
    }
}
