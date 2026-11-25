package com.gharpahucho.dto;

import com.gharpahucho.entity.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class GroupDtos {
    private GroupDtos() {
    }

    public record CreateGroupRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 80) String sourceCity,
            @NotBlank @Size(max = 80) String destinationCity,
            @NotNull LocalDate travelDate,
            TransportMode planMode,
            @Size(max = 600) String planNotes,
            @Size(max = 120) String meetingArea,
            LocalTime plannedDepartureTime,
            @Min(2) @Max(12) Integer maxMembers,
            List<UUID> inviteUserIds) {
    }

    public record UpdateGroupRequest(
            @Size(min = 1, max = 120) String name,
            GroupStatus status,
            TransportMode planMode,
            @Size(max = 600) String planNotes,
            @Size(max = 120) String meetingArea,
            LocalTime plannedDepartureTime) {
    }

    public record InviteRequest(@NotNull UUID userId) {
    }

    public record RespondInviteRequest(boolean accept) {
    }

    public record MemberDto(UUID userId, String name, String homeArea, MemberRole role, MemberStatus status) {
    }

    public record CostSharingDto(
            String basis,
            Integer estimatedTotal,
            Integer perPerson,
            int memberCount,
            DataSource dataSource,
            String note) {
    }

    public record GroupDto(
            UUID id,
            String name,
            String sourceCity,
            String destinationCity,
            LocalDate travelDate,
            GroupStatus status,
            TransportMode planMode,
            String planNotes,
            String meetingArea,
            LocalTime plannedDepartureTime,
            int maxMembers,
            String ownerName,
            boolean owner,
            MemberStatus myStatus,
            List<MemberDto> members,
            CostSharingDto costSharing,
            Instant createdAt) {
    }

    public record GroupSummary(
            UUID id,
            String name,
            String sourceCity,
            String destinationCity,
            LocalDate travelDate,
            GroupStatus status,
            int joinedMembers,
            MemberStatus myStatus,
            String ownerName) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 1000) String body) {
    }

    public record MessageDto(UUID id, UUID senderId, String senderName, String body, Instant createdAt, boolean mine) {
    }
}
