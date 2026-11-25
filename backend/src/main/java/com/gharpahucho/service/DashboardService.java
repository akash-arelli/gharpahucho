package com.gharpahucho.service;

import com.gharpahucho.dto.DashboardDtos.ActivityDto;
import com.gharpahucho.dto.DashboardDtos.Dashboard;
import com.gharpahucho.dto.MatchDtos.MatchCandidate;
import com.gharpahucho.dto.PlanDtos.PlanRequest;
import com.gharpahucho.dto.PlanDtos.PlanResponse;
import com.gharpahucho.dto.TripDtos.TripDto;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.TripStatus;
import com.gharpahucho.entity.User;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.mapper.Mappers;
import com.gharpahucho.recommendation.TripPlanningService;
import com.gharpahucho.repository.ActivityEventRepository;
import com.gharpahucho.repository.TripRepository;
import com.gharpahucho.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Assembles the home screen. Each panel degrades independently: one failing dependency never blanks the dashboard. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final UserRepository users;
    private final TripRepository trips;
    private final ActivityEventRepository activities;
    private final TripService tripService;
    private final TravellerService travellers;
    private final GroupService groups;
    private final TripPlanningService planner;
    private final FeatureFlagService flags;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Dashboard load(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        List<Trip> upcoming = trips.findByOwnerIdAndStatusAndTravelDateGreaterThanEqualOrderByTravelDateAsc(userId, TripStatus.PLANNED, LocalDate.now(clock));
        List<TripDto> journeys = upcoming.stream().limit(5).map(Mappers::toDto).toList();
        Trip next = upcoming.isEmpty() ? null : upcoming.get(0);

        PlanResponse plan = null;
        if (next != null && flags.isEnabled(FeatureKey.SMART_TRIP_PLANNER, userId)) {
            try {
                plan = planner.plan(userId, new PlanRequest(next.getSourceCity(), next.getDestinationCity(), next.getTravelDate(), next.getReturnDate(),
                        next.getTravellers(), next.getMaxBudget(), next.getPreferredMode(), next.getPriority(), next.getArriveBy()));
            } catch (RuntimeException e) {
                log.warn("event=dashboard_plan_failed error={}", e.getMessage());
            }
        }

        List<MatchCandidate> compatible = List.of();
        if (next != null && flags.isEnabled(FeatureKey.TRAVEL_COMPANION_MATCHING, userId)) {
            try {
                compatible = travellers.matches(userId, next.getId(), 0, 4).matches();
            } catch (RuntimeException e) {
                log.warn("event=dashboard_matches_failed error={}", e.getMessage());
            }
        }

        return new Dashboard(
                user.getDisplayName().trim().split("\\s+")[0],
                journeys,
                tripService.savedTrips(userId).stream().limit(3).toList(),
                plan,
                compatible,
                groups.invitations(userId),
                groups.mine(userId),
                (int) travellers.pendingIncoming(userId),
                activities.findTop8ByUserIdOrderByCreatedAtDesc(userId).stream().map(a -> new ActivityDto(a.getType(), a.getMessage(), a.getCreatedAt())).toList());
    }
}
