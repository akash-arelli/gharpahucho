package com.gharpahucho.service;

import com.gharpahucho.dto.GroupDtos.*;
import com.gharpahucho.entity.*;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.GeoService;
import com.gharpahucho.integration.maps.RouteInfo;
import com.gharpahucho.integration.transport.Candidate;
import com.gharpahucho.integration.transport.RouteBasedCabProvider;
import com.gharpahucho.integration.transport.SimulatedInventoryProvider;
import com.gharpahucho.mapper.Mappers;
import com.gharpahucho.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Travel groups: create, invite, join, plan and chat. Only joined members can read messages; invited users can see the
 * group's route and date so they can decide, but not its chat. Contact details are never part of any group payload.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final TravelGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupMessageRepository messages;
    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final TripRepository trips;
    private final TravelInterestRepository interests;
    private final GeoService geo;
    private final SimulatedInventoryProvider inventory;
    private final RouteBasedCabProvider cab;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional
    public GroupDto create(UUID userId, CreateGroupRequest req) {
        User owner = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        City from = geo.requireCity(req.sourceCity(), "source");
        City to = geo.requireCity(req.destinationCity(), "destination");
        if (req.travelDate().isBefore(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_IN_PAST", "The travel date is in the past");
        }
        TravelGroup g = new TravelGroup();
        g.setName(req.name().trim());
        g.setOwner(owner);
        g.setSourceCity(from.name());
        g.setDestinationCity(to.name());
        g.setTravelDate(req.travelDate());
        g.setPlanMode(req.planMode() == null ? TransportMode.CAB : req.planMode());
        g.setPlanNotes(req.planNotes());
        g.setMeetingArea(req.meetingArea());
        g.setPlannedDepartureTime(req.plannedDepartureTime());
        g.setMaxMembers(req.maxMembers() == null ? 4 : req.maxMembers());
        groups.save(g);
        addMember(g, owner, MemberRole.OWNER, MemberStatus.JOINED);
        if (req.inviteUserIds() != null) {
            for (UUID invitee : req.inviteUserIds()) {
                invite(userId, g.getId(), invitee, g);
            }
        }
        activity.record(userId, "GROUP_CREATED", "You created the group \"" + g.getName() + "\"");
        return toDto(g, userId);
    }

    /**
     * Called when a traveller accepts an interest request: both travellers end up together in one group. Reuses an existing
     * group the accepting traveller already owns for the same route and day.
     */
    @Transactional
    public UUID groupForAcceptedInterest(TravelInterest interest) {
        Trip hostTrip = interest.getToTrip();
        User host = interest.getToUser();
        User guest = interest.getFromUser();
        TravelGroup group = groups.findForUser(host.getId(), List.of(MemberStatus.JOINED)).stream()
                .filter(g -> g.getOwner().getId().equals(host.getId())
                        && g.getSourceCity().equals(hostTrip.getSourceCity())
                        && g.getDestinationCity().equals(hostTrip.getDestinationCity())
                        && g.getTravelDate().equals(hostTrip.getTravelDate())
                        && g.getStatus() == GroupStatus.FORMING)
                .findFirst().orElse(null);
        if (group == null) {
            group = new TravelGroup();
            group.setName(hostTrip.getSourceCity() + " → " + hostTrip.getDestinationCity() + " · " + DAY.format(hostTrip.getTravelDate()));
            group.setOwner(host);
            group.setSourceCity(hostTrip.getSourceCity());
            group.setDestinationCity(hostTrip.getDestinationCity());
            group.setTravelDate(hostTrip.getTravelDate());
            group.setPlanMode(hostTrip.getPreferredMode() == TransportMode.TRAIN || hostTrip.getPreferredMode() == TransportMode.BUS
                    ? hostTrip.getPreferredMode() : TransportMode.CAB);
            group.setPlannedDepartureTime(hostTrip.getDepartureTime());
            groups.save(group);
            addMember(group, host, MemberRole.OWNER, MemberStatus.JOINED);
            activity.record(host.getId(), "GROUP_CREATED", "Group \"" + group.getName() + "\" was created");
        }
        Optional<GroupMember> existing = members.findByGroupIdAndUserId(group.getId(), guest.getId());
        if (existing.isEmpty()) {
            if (members.countByGroupIdAndStatus(group.getId(), MemberStatus.JOINED) >= group.getMaxMembers()) {
                return group.getId();
            }
            addMember(group, guest, MemberRole.MEMBER, MemberStatus.JOINED);
        } else if (existing.get().getStatus() != MemberStatus.JOINED) {
            existing.get().setStatus(MemberStatus.JOINED);
            existing.get().setJoinedAt(Instant.now());
        }
        activity.record(guest.getId(), "GROUP_JOINED", "You joined \"" + group.getName() + "\"");
        return group.getId();
    }

    @Transactional(readOnly = true)
    public GroupDto get(UUID userId, UUID groupId) {
        TravelGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        memberOrThrow(groupId, userId, false);
        return toDto(g, userId);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> mine(UUID userId) {
        return groups.findForUser(userId, List.of(MemberStatus.JOINED)).stream().map(g -> summary(g, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> invitations(UUID userId) {
        return groups.findForUser(userId, List.of(MemberStatus.INVITED)).stream().map(g -> summary(g, userId)).toList();
    }

    @Transactional
    public GroupDto update(UUID userId, UUID groupId, UpdateGroupRequest req) {
        TravelGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        GroupMember m = memberOrThrow(groupId, userId, true);
        if (m.getRole() != MemberRole.OWNER) {
            throw ApiException.forbidden("Only the group owner can change the plan");
        }
        if (req.name() != null) {
            g.setName(req.name().trim());
        }
        if (req.status() != null) {
            g.setStatus(req.status());
        }
        if (req.planMode() != null) {
            g.setPlanMode(req.planMode());
        }
        if (req.planNotes() != null) {
            g.setPlanNotes(req.planNotes());
        }
        if (req.meetingArea() != null) {
            g.setMeetingArea(req.meetingArea());
        }
        if (req.plannedDepartureTime() != null) {
            g.setPlannedDepartureTime(req.plannedDepartureTime());
        }
        return toDto(g, userId);
    }

    @Transactional
    public GroupDto invite(UUID userId, UUID groupId, UUID inviteeId) {
        TravelGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        memberOrThrow(groupId, userId, true);
        invite(userId, groupId, inviteeId, g);
        return toDto(g, userId);
    }

    private void invite(UUID inviterId, UUID groupId, UUID inviteeId, TravelGroup g) {
        if (inviterId.equals(inviteeId)) {
            return;
        }
        User invitee = users.findById(inviteeId).orElseThrow(() -> ApiException.notFound("Traveller"));
        if (!canInvite(inviterId, invitee, g)) {
            throw ApiException.forbidden("You can only invite travellers you have connected with or who are publicly looking for the same journey");
        }
        if (members.findByGroupIdAndUserId(groupId, inviteeId).isPresent()) {
            return;
        }
        if (members.countByGroupIdAndStatus(groupId, MemberStatus.JOINED) >= g.getMaxMembers()) {
            throw ApiException.conflict("This group is full");
        }
        addMember(g, invitee, MemberRole.MEMBER, MemberStatus.INVITED);
        activity.record(inviteeId, "GROUP_INVITED", "You were invited to \"" + g.getName() + "\"");
    }

    /** Invitees must either have an accepted connection with the inviter or have publicly published a matching journey. */
    private boolean canInvite(UUID inviterId, User invitee, TravelGroup g) {
        boolean connected = interests.findIncoming(inviterId).stream().anyMatch(i -> i.getStatus() == InterestStatus.ACCEPTED && i.getFromUser().getId().equals(invitee.getId()))
                || interests.findOutgoing(inviterId).stream().anyMatch(i -> i.getStatus() == InterestStatus.ACCEPTED && i.getToUser().getId().equals(invitee.getId()));
        if (connected) {
            return true;
        }
        return trips.findByOwnerIdAndStatusAndTravelDateGreaterThanEqualOrderByTravelDateAsc(invitee.getId(), TripStatus.PLANNED, LocalDate.now(clock)).stream()
                .anyMatch(t -> t.getVisibility() == Visibility.DISCOVERABLE && t.getDestinationCity().equals(g.getDestinationCity()));
    }

    @Transactional
    public GroupDto respond(UUID userId, UUID groupId, boolean accept) {
        TravelGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        GroupMember m = memberOrThrow(groupId, userId, false);
        if (m.getStatus() != MemberStatus.INVITED) {
            throw ApiException.conflict("There is no pending invitation to answer");
        }
        if (accept) {
            if (members.countByGroupIdAndStatus(groupId, MemberStatus.JOINED) >= g.getMaxMembers()) {
                throw ApiException.conflict("This group is full");
            }
            m.setStatus(MemberStatus.JOINED);
            m.setJoinedAt(Instant.now());
            activity.record(userId, "GROUP_JOINED", "You joined \"" + g.getName() + "\"");
            activity.record(g.getOwner().getId(), "GROUP_MEMBER_JOINED", users.getReferenceById(userId).publicName() + " joined \"" + g.getName() + "\"");
        } else {
            m.setStatus(MemberStatus.DECLINED);
        }
        return toDto(g, userId);
    }

    @Transactional
    public void leave(UUID userId, UUID groupId) {
        GroupMember m = memberOrThrow(groupId, userId, false);
        if (m.getRole() == MemberRole.OWNER) {
            throw ApiException.conflict("The owner cannot leave. Cancel the group instead.");
        }
        m.setStatus(MemberStatus.LEFT);
    }

    // ------------------------------------------------------------------ messages

    @Transactional(readOnly = true)
    public List<MessageDto> messages(UUID userId, UUID groupId) {
        memberOrThrow(groupId, userId, true);
        return messages.findByGroup(groupId).stream()
                .map(m -> new MessageDto(m.getId(), m.getSender().getId(), Mappers.publicName(m.getSender(), null), m.getBody(),
                        m.getCreatedAt(), m.getSender().getId().equals(userId)))
                .toList();
    }

    @Transactional
    public MessageDto post(UUID userId, UUID groupId, String body) {
        TravelGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        memberOrThrow(groupId, userId, true);
        GroupMessage m = new GroupMessage();
        m.setGroup(g);
        User sender = users.getReferenceById(userId);
        m.setSender(sender);
        m.setBody(body.trim());
        messages.save(m);
        return new MessageDto(m.getId(), userId, Mappers.publicName(users.findById(userId).orElseThrow(), null), m.getBody(), m.getCreatedAt(), true);
    }

    // ------------------------------------------------------------------ helpers

    private void addMember(TravelGroup g, User u, MemberRole role, MemberStatus status) {
        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUser(u);
        m.setRole(role);
        m.setStatus(status);
        if (status == MemberStatus.JOINED) {
            m.setJoinedAt(Instant.now());
        }
        members.save(m);
    }

    /** @param mustBeJoined chat and planning need a joined member; viewing the summary only needs an invitation. */
    private GroupMember memberOrThrow(UUID groupId, UUID userId, boolean mustBeJoined) {
        GroupMember m = members.findByGroupIdAndUserId(groupId, userId)
                .filter(x -> x.getStatus() == MemberStatus.JOINED || x.getStatus() == MemberStatus.INVITED)
                .orElseThrow(() -> ApiException.notFound("Group"));
        if (mustBeJoined && m.getStatus() != MemberStatus.JOINED) {
            throw ApiException.forbidden("Join the group to do this");
        }
        return m;
    }

    private GroupSummary summary(TravelGroup g, UUID userId) {
        long joined = members.countByGroupIdAndStatus(g.getId(), MemberStatus.JOINED);
        MemberStatus mine = members.findByGroupIdAndUserId(g.getId(), userId).map(GroupMember::getStatus).orElse(null);
        return new GroupSummary(g.getId(), g.getName(), g.getSourceCity(), g.getDestinationCity(), g.getTravelDate(), g.getStatus(),
                (int) joined, mine, Mappers.publicName(g.getOwner(), null));
    }

    private GroupDto toDto(TravelGroup g, UUID userId) {
        List<GroupMember> all = members.findByGroup(g.getId());
        Map<UUID, UserPreference> pref = prefs.findByUserIdIn(all.stream().map(m -> m.getUser().getId()).toList()).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));
        List<MemberDto> dtos = all.stream()
                .filter(m -> m.getStatus() == MemberStatus.JOINED || m.getStatus() == MemberStatus.INVITED)
                .map(m -> new MemberDto(m.getUser().getId(), Mappers.publicName(m.getUser(), pref.get(m.getUser().getId())),
                        Mappers.homeArea(m.getUser()), m.getRole(), m.getStatus()))
                .toList();
        int joined = (int) dtos.stream().filter(d -> d.status() == MemberStatus.JOINED).count();
        GroupMember mine = all.stream().filter(m -> m.getUser().getId().equals(userId)).findFirst().orElse(null);
        return new GroupDto(g.getId(), g.getName(), g.getSourceCity(), g.getDestinationCity(), g.getTravelDate(), g.getStatus(),
                g.getPlanMode(), g.getPlanNotes(), g.getMeetingArea(), g.getPlannedDepartureTime(), g.getMaxMembers(),
                Mappers.publicName(g.getOwner(), null), g.getOwner().getId().equals(userId),
                mine == null ? null : mine.getStatus(), dtos, costSharing(g, Math.max(1, joined)), g.getCreatedAt());
    }

    /**
     * Cost-sharing estimate. Cab: routing-engine distance times the configured rate, split across members (one cab per
     * four people). Train/bus: the cheapest SAMPLE fare for that mode on the group's date. Always labelled with its source.
     */
    private CostSharingDto costSharing(TravelGroup g, int memberCount) {
        try {
            City from = geo.requireCity(g.getSourceCity(), "source");
            City to = geo.requireCity(g.getDestinationCity(), "destination");
            if (g.getPlanMode() == TransportMode.TRAIN || g.getPlanMode() == TransportMode.BUS) {
                Optional<Candidate> cheapest = inventory.searchBetween(from.name(), to.name(), g.getTravelDate(), g.getTravelDate(),
                                LocalDateTime.of(2000, 1, 1, 0, 0)).stream()
                        .filter(c -> c.mode() == g.getPlanMode()).min(Comparator.comparingInt(Candidate::costPerPerson));
                if (cheapest.isPresent()) {
                    int per = cheapest.get().costPerPerson();
                    return new CostSharingDto(g.getPlanMode().label() + " fare per person", per * memberCount, per, memberCount,
                            DataSource.SIMULATED_INVENTORY, "Sample fare for demonstration; each traveller pays their own ticket.");
                }
            }
            RouteInfo route = geo.route(from, to);
            int cabs = Math.max(1, (int) Math.ceil(memberCount / (double) RouteBasedCabProvider.SEATS_PER_CAB));
            int total = cab.estimateCabTotal(route) * cabs;
            int per = (int) Math.ceil(total / (double) memberCount);
            return new CostSharingDto("Cab shared equally by " + memberCount + " member" + (memberCount == 1 ? "" : "s"), total, per,
                    memberCount, DataSource.ROUTE_CALCULATION,
                    "Estimate: " + Math.round(route.distanceKm()) + " km " + (route.measured() ? "(routed by " + route.engine() + ")" : "(straight-line estimate)")
                            + " at an assumed per-km rate. Actual fares vary.");
        } catch (Exception e) {
            return new CostSharingDto("Unavailable", null, null, memberCount, DataSource.ROUTE_CALCULATION, "Cost estimate unavailable right now.");
        }
    }
}
