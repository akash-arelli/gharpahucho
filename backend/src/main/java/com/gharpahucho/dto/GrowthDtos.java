package com.gharpahucho.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Ride alerts, family links and the Festival Pass offer. */
public final class GrowthDtos {

    private GrowthDtos() {
    }

    // ------------------------------------------------------------------ ride alerts

    public record CreateAlertRequest(@NotNull UUID tripId, @Min(0) @Max(3) Integer flexDays) {
    }

    public record RideMatchDto(UUID tripId, UUID hostId, String host, LocalDate travelDate, LocalTime departureTime, String startsNear, int seats,
                               Integer costPerSeat, boolean fresh) {
    }

    public record AlertDto(UUID id, String route, LocalDate travelDate, int flexDays, List<RideMatchDto> rides, int newRides, Instant createdAt) {
    }

    /** {@code limit} is -1 when the user has unlimited alerts. */
    public record AlertsResponse(List<AlertDto> alerts, int limit, boolean unlimited, String upgradeMessage) {
    }

    // ------------------------------------------------------------------ family link

    public record CreateFamilyLinkRequest(@Size(max = 10) String language, @Size(max = 60) String familyName, @Size(max = 200) String message) {
    }

    public record FamilyStatusRequest(@NotBlank @Size(max = 20) String status, @Size(max = 200) String message) {
    }

    public record FamilyLinkDto(UUID id, String token, String path, String language, String familyName, String status, Instant statusUpdatedAt,
                                String message, int views, Instant lastViewedAt, Instant createdAt) {
    }

    /** What a family member sees. First name only: never an e-mail, phone number or exact location. */
    public record FamilyView(String travellerName, String familyName, String sourceCity, String destinationCity, LocalDate travelDate,
                             LocalTime departureTime, String meetingArea, String status, String statusLabel, String headline, String message,
                             Instant updatedAt, String language, String footer) {
    }

    // ------------------------------------------------------------------ Festival Pass

    public record Perk(String title, String detail, String free, String plus) {
    }

    public record PlansResponse(String currentPlan, int priceInr, String period, List<Perk> perks, boolean demoCheckoutAvailable) {
    }
}
