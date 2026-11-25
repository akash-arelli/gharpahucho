package com.gharpahucho.dto;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record ActivityDto(String type, String message, Instant createdAt) {
    }

    public record Dashboard(
            String greetingName,
            List<TripDtos.TripDto> upcomingJourneys,
            List<TripDtos.SavedTripDto> savedTrips,
            PlanDtos.PlanResponse recommendedPlan,
            List<MatchDtos.MatchCandidate> compatibleTravellers,
            List<GroupDtos.GroupSummary> groupInvitations,
            List<GroupDtos.GroupSummary> myGroups,
            int pendingRequests,
            List<ActivityDto> recentActivity) {
    }
}
