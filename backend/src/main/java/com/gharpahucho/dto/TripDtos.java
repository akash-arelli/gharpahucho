package com.gharpahucho.dto;

import com.gharpahucho.entity.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class TripDtos {
    private TripDtos() {
    }

    public record TripRequest(
            @NotBlank @Size(max = 80) String sourceCity,
            @Size(max = 80) String sourceLocality,
            @NotBlank @Size(max = 80) String destinationCity,
            @NotNull LocalDate travelDate,
            @Min(0) @Max(7) Integer flexDays,
            LocalDate returnDate,
            @Min(1) @Max(12) Integer travellers,
            @Positive @Max(100000) Integer maxBudget,
            TransportMode preferredMode,
            Priority priority,
            LocalTime arriveBy,
            LocalTime departureTime,
            Boolean willingToShare,
            @Min(0) @Max(12) Integer seatsAvailable,
            @Positive @Max(100000) Integer costPerSeat,
            @Size(max = 400) String notes,
            Visibility visibility) {
    }

    public record TripDto(
            UUID id,
            String sourceCity,
            String sourceLocality,
            String destinationCity,
            LocalDate travelDate,
            int flexDays,
            LocalDate returnDate,
            int travellers,
            Integer maxBudget,
            TransportMode preferredMode,
            Priority priority,
            LocalTime arriveBy,
            LocalTime departureTime,
            boolean willingToShare,
            int seatsAvailable,
            Integer costPerSeat,
            String notes,
            Visibility visibility,
            TripStatus status,
            DataOrigin dataOrigin,
            Instant createdAt) {
    }

    /** Partial update: only non-null fields change. Used to publish a journey, offer seats or cancel. */
    public record TripPatchRequest(
            Visibility visibility,
            TripStatus status,
            @Min(0) @Max(12) Integer seatsAvailable,
            @Positive @Max(100000) Integer costPerSeat,
            Boolean willingToShare,
            LocalTime departureTime,
            @Size(max = 80) String sourceLocality,
            @Size(max = 400) String notes) {
    }

    public record SaveTripRequest(
            UUID tripId,
            @NotBlank @Size(max = 160) String title,
            @NotBlank String sourceCity,
            @NotBlank String destinationCity,
            @NotNull LocalDate travelDate,
            @NotNull PlanDtos.OptionDto option) {
    }

    public record SavedTripDto(
            UUID id,
            UUID tripId,
            String title,
            String sourceCity,
            String destinationCity,
            LocalDate travelDate,
            PlanDtos.OptionDto option,
            Instant createdAt) {
    }
}
