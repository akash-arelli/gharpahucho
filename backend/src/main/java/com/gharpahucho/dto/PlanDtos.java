package com.gharpahucho.dto;

import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlanDtos {
    private PlanDtos() {
    }

    public record PlanRequest(
            @NotBlank @Size(max = 80) String sourceCity,
            @NotBlank @Size(max = 80) String destinationCity,
            @NotNull LocalDate travelDate,
            LocalDate returnDate,
            @Min(1) @Max(12) Integer travellers,
            @Positive @Max(100000) Integer maxBudget,
            TransportMode preferredMode,
            Priority priority,
            LocalTime arriveBy) {
    }

    public record CityDto(String name, String state, double lat, double lon) {
    }

    public record RouteDto(
            CityDto from,
            CityDto to,
            double distanceKm,
            int durationMinutes,
            /** OSRM, GOOGLE_ROUTES or ESTIMATED_STRAIGHT_LINE */
            String engine,
            /** true only when a routing engine produced the figures. */
            boolean measured,
            String note,
            List<double[]> geometry) {
    }

    public record LegDto(
            TransportMode mode,
            String from,
            String to,
            LocalDateTime departure,
            LocalDateTime arrival,
            String operator,
            String serviceName) {
    }

    public record OptionDto(
            String id,
            TransportMode mode,
            String modeLabel,
            String title,
            String operator,
            List<LegDto> legs,
            LocalDateTime departure,
            LocalDateTime arrival,
            int durationMinutes,
            int costPerPerson,
            int totalCost,
            int transfers,
            String stops,
            DataSource dataSource,
            String dataSourceLabel,
            String dataSourceNote,
            /** e.g. SAMPLE_UNVERIFIED, SEATS_POSTED, ON_DEMAND. Never "live". */
            String availability,
            String availabilityLabel,
            boolean meetsConstraints,
            List<String> violations,
            /** Score (0-100) under the traveller's chosen priority. */
            int score,
            /** Balanced score (0-100) used for the "Best overall" ranking. */
            int overallScore,
            List<String> tags,
            List<String> reasons,
            int comfort,
            Integer seatsLeft,
            UUID hostTripId,
            String hostName,
            String festiveNote) {
    }

    public record BudgetTip(
            String type,
            String title,
            String detail,
            int savingPerPerson,
            LocalDate suggestedDate,
            String optionId) {
    }

    public record FestiveInfo(String name, int demandLevel, String note) {
    }

    public record ExplainRequest(
            @jakarta.validation.Valid @NotNull PlanRequest plan,
            @NotBlank String optionId,
            String language) {
    }

    public record ExplainResponse(
            String optionId,
            String explanation,
            /** SARVAM_AI when the model wrote it; UNAVAILABLE when it could not (nothing is invented). */
            String source,
            boolean available,
            String notice) {
    }

    public record PlanResponse(
            PlanRequest request,
            RouteDto route,
            List<OptionDto> options,
            List<BudgetTip> budgetTips,
            Map<String, Boolean> appliedFlags,
            List<String> notices,
            int totalCandidates,
            int withinConstraints,
            FestiveInfo festive,
            String disclaimer,
            Instant generatedAt) {
    }
}
