package com.gharpahucho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharpahucho.dto.AssistantDtos.TripDraft;
import com.gharpahucho.dto.MatchDtos.*;
import com.gharpahucho.entity.*;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.FeatureSnapshot;
import com.gharpahucho.integration.flagsmith.RuntimeConfig;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.GeoService;
import com.gharpahucho.mapper.Mappers;
import com.gharpahucho.matching.MatchingEngine;
import com.gharpahucho.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traveller discovery and connection requests. Privacy rules enforced here: only DISCOVERABLE trips of users who allow
 * discovery are ever returned, names follow the user's privacy setting, and only coarse areas are exposed.
 */
@Service
@RequiredArgsConstructor
public class TravellerService {

    private final TripRepository trips;
    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final TravelInterestRepository interests;
    private final TravelMatchRepository matchCache;
    private final MatchingEngine engine;
    private final FeatureFlagService flags;
    private final TripService tripService;
    private final GroupService groupService;
    private final ActivityService activity;
    private final GeoService geo;
    private final ObjectMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ discovery

    @Transactional
    public MatchResponse matches(UUID userId, UUID tripId, int page, int size) {
        Trip seeker = tripService.owned(userId, tripId);
        return run(userId, seeker, true, page, size);
    }

    /** Matches for an unsaved journey described in conversation (assistant). Nothing is persisted. */
    @Transactional
    public MatchResponse matchesForDraft(UUID userId, TripDraft d) {
        if (d.sourceCity() == null || d.destinationCity() == null || d.travelDate() == null) {
            throw ApiException.badRequest("Source, destination and date are needed to find travellers");
        }
        City from = geo.requireCity(d.sourceCity(), "source");
        City to = geo.requireCity(d.destinationCity(), "destination");
        Trip t = new Trip();
        t.setOwner(users.getReferenceById(userId));
        t.setSourceCity(from.name());
        t.setSourceLat(from.lat());
        t.setSourceLon(from.lon());
        t.setDestinationCity(to.name());
        t.setDestinationLat(to.lat());
        t.setDestinationLon(to.lon());
        t.setTravelDate(d.travelDate());
        t.setTravellers(d.travellers() == null ? 1 : d.travellers());
        t.setMaxBudget(d.maxBudget());
        t.setPreferredMode(d.preferredMode() == null ? TransportMode.ANY : d.preferredMode());
        t.setWillingToShare(d.willingToShare() == null || d.willingToShare());
        return run(userId, t, false, 0, 20);
    }

    private MatchResponse run(UUID userId, Trip seeker, boolean persist, int page, int size) {
        FeatureSnapshot snap = flags.snapshot(userId);
        RuntimeConfig cfg = snap.runtime();
        boolean experimental = snap.enabled(FeatureKey.EXPERIMENTAL_MATCHING_ALGORITHM);
        boolean shared = snap.enabled(FeatureKey.SHARED_RIDE_MATCHING);
        MatchingEngine.Params params = new MatchingEngine.Params(cfg, shared, experimental);

        int window = cfg.dateFlexibilityDays() + seeker.getFlexDays() + 7;
        List<Trip> pool = trips.findCandidates(Visibility.DISCOVERABLE, TripStatus.PLANNED, userId,
                seeker.getTravelDate().minusDays(window), seeker.getTravelDate().plusDays(window));
        Set<UUID> ownerIds = pool.stream().map(t -> t.getOwner().getId()).collect(Collectors.toSet());
        Map<UUID, UserPreference> prefByUser = prefs.findByUserIdIn(ownerIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        Map<UUID, InterestStatus> interestByTrip = new HashMap<>();
        if (seeker.getId() != null) {
            interests.findOutgoing(userId).stream().filter(i -> i.getFromTrip().getId().equals(seeker.getId()))
                    .forEach(i -> interestByTrip.put(i.getToTrip().getId(), i.getStatus()));
        }

        List<MatchingEngine.Result> results = new ArrayList<>();
        for (Trip candidate : pool) {
            UserPreference pref = prefByUser.get(candidate.getOwner().getId());
            if (pref != null && !pref.isDiscoverable()) {
                continue;
            }
            engine.score(seeker, candidate, params).filter(r -> r.score() >= cfg.minCompatibilityScore()).ifPresent(results::add);
        }
        results.sort(Comparator.comparingInt(MatchingEngine.Result::score).reversed()
                .thenComparing(r -> r.candidate().getTravelDate()));

        if (persist) {
            for (MatchingEngine.Result r : results) {
                cache(seeker, r);
            }
        }
        int from = Math.min(results.size(), Math.max(0, page) * Math.max(1, size));
        int to = Math.min(results.size(), from + Math.max(1, size));
        List<MatchCandidate> cards = results.subList(from, to).stream()
                .map(r -> toCard(r, prefByUser.get(r.candidate().getOwner().getId()), interestByTrip.get(r.candidate().getId())))
                .toList();

        List<String> notices = new ArrayList<>();
        if (experimental) {
            notices.add("Experimental matching v2 is on for you (feature flag experimental_matching_algorithm): route-corridor aware.");
        }
        if (!shared) {
            notices.add("Ride sharing is ignored in scores because shared_ride_matching is off for you.");
        }
        return new MatchResponse(seeker.getId(), experimental ? MatchingEngine.V2 : MatchingEngine.V1, cfg.minCompatibilityScore(),
                cfg.matchingRadiusKm(), results.size(), cards, cfg.weights().asPercentages(), notices);
    }

    private void cache(Trip seeker, MatchingEngine.Result r) {
        try {
            TravelMatch m = matchCache.findByTripIdAndCandidateTripId(seeker.getId(), r.candidate().getId()).orElseGet(TravelMatch::new);
            m.setTrip(seeker);
            m.setCandidateTrip(r.candidate());
            m.setScore(r.score());
            m.setBreakdownJson(mapper.writeValueAsString(r.breakdown()));
            m.setReasonsJson(mapper.writeValueAsString(r.reasons()));
            m.setAlgorithmVersion(r.algorithm().length() > 20 ? r.algorithm().substring(0, 20) : r.algorithm());
            m.setComputedAt(Instant.now());
            matchCache.save(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MatchCandidate toCard(MatchingEngine.Result r, UserPreference pref, InterestStatus interest) {
        Trip t = r.candidate();
        User u = t.getOwner();
        return new MatchCandidate(t.getId(), u.getId(), Mappers.publicName(u, pref), Mappers.homeArea(u), t.getSourceCity(),
                t.getSourceLocality() == null ? t.getSourceCity() : t.getSourceLocality(), t.getDestinationCity(), t.getTravelDate(),
                t.getFlexDays(), t.getPreferredMode(), t.getMaxBudget(), t.getTravellers(), t.getSeatsAvailable(), t.getCostPerSeat(),
                t.isWillingToShare(), t.getNotes(), r.score(), r.breakdown(), r.reasons(), r.algorithm(), interest,
                t.getDataOrigin() == DataOrigin.DEMO_SEED);
    }

    // ------------------------------------------------------------------ interest requests

    @Transactional
    public InterestDto express(UUID userId, InterestRequest req) {
        Trip mine = tripService.owned(userId, req.fromTripId());
        Trip theirs = trips.findWithOwner(req.toTripId()).orElseThrow(() -> ApiException.notFound("Trip"));
        if (theirs.getVisibility() != Visibility.DISCOVERABLE || theirs.getStatus() != TripStatus.PLANNED) {
            throw ApiException.notFound("Trip");
        }
        if (theirs.getOwner().getId().equals(userId)) {
            throw ApiException.badRequest("You cannot send a request to yourself");
        }
        if (interests.findByFromTripIdAndToTripId(mine.getId(), theirs.getId()).isPresent()) {
            throw ApiException.conflict("You have already sent a request for this journey");
        }
        TravelInterest i = new TravelInterest();
        i.setFromTrip(mine);
        i.setToTrip(theirs);
        i.setFromUser(mine.getOwner());
        i.setToUser(theirs.getOwner());
        i.setMessage(req.message() == null || req.message().isBlank() ? null : req.message().trim());
        interests.save(i);
        activity.record(userId, "INTEREST_SENT", "You asked " + theirs.getOwner().publicName() + " to travel together to " + theirs.getDestinationCity());
        activity.record(theirs.getOwner().getId(), "INTEREST_RECEIVED",
                mine.getOwner().publicName() + " wants to travel with you to " + theirs.getDestinationCity());
        return toDto(i, userId);
    }

    @Transactional(readOnly = true)
    public InterestInbox inbox(UUID userId) {
        return new InterestInbox(
                interests.findIncoming(userId).stream().map(i -> toDto(i, userId)).toList(),
                interests.findOutgoing(userId).stream().map(i -> toDto(i, userId)).toList());
    }

    @Transactional
    public InterestDto respond(UUID userId, UUID interestId, boolean accept) {
        TravelInterest i = interests.findDetailed(interestId).orElseThrow(() -> ApiException.notFound("Request"));
        if (!i.getToUser().getId().equals(userId)) {
            throw ApiException.notFound("Request");
        }
        if (i.getStatus() != InterestStatus.PENDING) {
            throw ApiException.conflict("This request has already been answered");
        }
        i.setStatus(accept ? InterestStatus.ACCEPTED : InterestStatus.REJECTED);
        i.setRespondedAt(Instant.now());
        if (accept) {
            if (flags.isEnabled(FeatureKey.TRAVEL_GROUP_CREATION, userId)) {
                i.setGroupId(groupService.groupForAcceptedInterest(i));
            }
            activity.record(i.getFromUser().getId(), "INTEREST_ACCEPTED", i.getToUser().publicName() + " accepted your request");
        } else {
            activity.record(i.getFromUser().getId(), "INTEREST_DECLINED", i.getToUser().publicName() + " declined your request");
        }
        return toDto(i, userId);
    }

    @Transactional
    public InterestDto withdraw(UUID userId, UUID interestId) {
        TravelInterest i = interests.findDetailed(interestId).orElseThrow(() -> ApiException.notFound("Request"));
        if (!i.getFromUser().getId().equals(userId)) {
            throw ApiException.notFound("Request");
        }
        if (i.getStatus() != InterestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Only pending requests can be withdrawn");
        }
        i.setStatus(InterestStatus.WITHDRAWN);
        i.setRespondedAt(Instant.now());
        return toDto(i, userId);
    }

    public long pendingIncoming(UUID userId) {
        return interests.countByToUserIdAndStatus(userId, InterestStatus.PENDING);
    }

    private InterestDto toDto(TravelInterest i, UUID viewer) {
        boolean incoming = i.getToUser().getId().equals(viewer);
        User other = incoming ? i.getFromUser() : i.getToUser();
        UserPreference pref = prefs.findByUserId(other.getId()).orElse(null);
        Trip trip = incoming ? i.getToTrip() : i.getToTrip();
        return new InterestDto(i.getId(), incoming ? "INCOMING" : "OUTGOING", i.getStatus(), other.getId(), Mappers.publicName(other, pref),
                trip.getSourceCity(), trip.getDestinationCity(), trip.getTravelDate(), i.getMessage(), i.getCreatedAt(), i.getGroupId());
    }

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }
}
