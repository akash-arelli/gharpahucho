package com.gharpahucho.dto;

import com.gharpahucho.entity.InterestStatus;
import com.gharpahucho.entity.TransportMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MatchDtos {
    private MatchDtos() {
    }

    public record MatchCandidate(
            UUID tripId,
            UUID userId,
            String name,
            String homeArea,
            String sourceCity,
            String sourceArea,
            String destinationCity,
            LocalDate travelDate,
            int flexDays,
            TransportMode preferredMode,
            Integer maxBudget,
            int travellers,
            int seatsAvailable,
            Integer costPerSeat,
            boolean willingToShare,
            String notes,
            int score,
            Map<String, Integer> breakdown,
            List<String> reasons,
            String algorithm,
            InterestStatus interestStatus,
            boolean demoData) {
    }

    public record MatchResponse(
            UUID tripId,
            String algorithm,
            int minScore,
            double radiusKm,
            int total,
            List<MatchCandidate> matches,
            Map<String, Integer> weights,
            List<String> notices) {
    }

    public record InterestRequest(
            @NotNull UUID fromTripId,
            @NotNull UUID toTripId,
            @Size(max = 300) String message) {
    }

    public record RespondRequest(boolean accept) {
    }

    public record InterestDto(
            UUID id,
            String direction,
            InterestStatus status,
            UUID otherUserId,
            String otherName,
            String sourceCity,
            String destinationCity,
            LocalDate travelDate,
            String message,
            Instant createdAt,
            UUID groupId) {
    }

    public record InterestInbox(List<InterestDto> incoming, List<InterestDto> outgoing) {
    }
}
