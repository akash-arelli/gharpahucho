package com.gharpahucho.dto;

import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 80) String homeCity) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, Me user) {
    }

    public record PreferenceDto(
            TransportMode preferredMode,
            Priority priority,
            @Positive @Max(100000) Integer defaultMaxBudget,
            boolean willingToShare,
            boolean firstNameOnly,
            boolean discoverable,
            @Size(max = 400) String notes) {
    }

    public record Me(
            UUID id,
            String email,
            String displayName,
            String homeCity,
            String homeLocality,
            String preferredLanguage,
            boolean onboarded,
            boolean betaTester,
            boolean demoAccount,
            String plan,
            PreferenceDto preferences,
            Instant createdAt) {
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 80) String displayName,
            @Size(max = 80) String homeCity,
            @Size(max = 80) String homeLocality,
            @Pattern(regexp = "en|hi|te|ta|kn|ml|mr|bn|gu|pa|od") String preferredLanguage,
            Boolean betaTester,
            Boolean onboarded,
            @Valid PreferenceDto preferences) {
    }

    /** What other travellers may see. No e-mail, no phone, no exact address. */
    public record PublicProfile(
            UUID userId,
            String name,
            String homeArea,
            String preferredLanguage,
            Instant memberSince,
            List<PublicTrip> upcomingTrips) {
    }

    public record PublicTrip(
            UUID tripId,
            String sourceCity,
            String sourceArea,
            String destinationCity,
            java.time.LocalDate travelDate,
            TransportMode preferredMode,
            int seatsAvailable,
            Integer costPerSeat) {
    }
}
