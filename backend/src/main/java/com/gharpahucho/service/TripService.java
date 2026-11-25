package com.gharpahucho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharpahucho.dto.TripDtos.*;
import com.gharpahucho.entity.*;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.GeoService;
import com.gharpahucho.mapper.Mappers;
import com.gharpahucho.repository.SavedTripRepository;
import com.gharpahucho.repository.TripRepository;
import com.gharpahucho.repository.UserPreferenceRepository;
import com.gharpahucho.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository trips;
    private final SavedTripRepository saved;
    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final GeoService geo;
    private final ActivityService activity;
    private final FeatureFlagService flags;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final RideAlertService rideAlerts;

    @Transactional
    public TripDto create(UUID userId, TripRequest req) {
        User owner = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        City from = geo.requireCity(req.sourceCity(), "source");
        City to = geo.requireCity(req.destinationCity(), "destination");
        if (from.name().equalsIgnoreCase(to.name())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SAME_CITY", "Source and destination are the same city");
        }
        LocalDate today = LocalDate.now(clock);
        if (req.travelDate().isBefore(today)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_IN_PAST", "The travel date is in the past");
        }
        if (req.returnDate() != null && req.returnDate().isBefore(req.travelDate())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RETURN_BEFORE_DEPARTURE", "The return date is before the departure date");
        }
        UserPreference pref = prefs.findByUserId(userId).orElse(null);

        Trip t = new Trip();
        t.setOwner(owner);
        t.setSourceCity(from.name());
        t.setDestinationCity(to.name());
        t.setDestinationLat(to.lat());
        t.setDestinationLon(to.lon());
        t.setSourceLat(from.lat());
        t.setSourceLon(from.lon());
        if (req.sourceLocality() != null && !req.sourceLocality().isBlank()) {
            t.setSourceLocality(req.sourceLocality().trim());
            geo.gazetteer().locality(from, req.sourceLocality()).ifPresent(l -> {
                t.setSourceLat(l.lat());
                t.setSourceLon(l.lon());
                t.setSourceLocality(l.name());
            });
        }
        t.setTravelDate(req.travelDate());
        t.setFlexDays(req.flexDays() == null ? 0 : req.flexDays());
        t.setReturnDate(req.returnDate());
        t.setTravellers(req.travellers() == null ? 1 : req.travellers());
        t.setMaxBudget(req.maxBudget());
        t.setPreferredMode(req.preferredMode() == null ? (pref == null ? TransportMode.ANY : pref.getPreferredMode()) : req.preferredMode());
        t.setPriority(req.priority() == null ? (pref == null ? Priority.CHEAPEST : pref.getPriority()) : req.priority());
        t.setArriveBy(req.arriveBy());
        t.setDepartureTime(req.departureTime());
        t.setSeatsAvailable(req.seatsAvailable() == null ? 0 : req.seatsAvailable());
        t.setCostPerSeat(req.costPerSeat());
        t.setWillingToShare(req.willingToShare() != null ? req.willingToShare() : t.getSeatsAvailable() > 0);
        t.setNotes(req.notes());
        // Privacy-first: trips are private unless the traveller explicitly publishes them.
        t.setVisibility(req.visibility() == null ? Visibility.PRIVATE : req.visibility());
        trips.save(t);

        activity.record(userId, "TRIP_CREATED", "You planned " + from.name() + " to " + to.name() + " on " + t.getTravelDate());
        flags.invalidate(userId);
        rideAlerts.onRidePosted(t);
        return Mappers.toDto(t);
    }

    @Transactional(readOnly = true)
    public TripDto get(UUID userId, UUID tripId) {
        return Mappers.toDto(owned(userId, tripId));
    }

    @Transactional(readOnly = true)
    public Page<TripDto> list(UUID userId, Pageable pageable) {
        return trips.findByOwnerIdAndStatusNotOrderByTravelDateAsc(userId, TripStatus.CANCELLED, pageable).map(Mappers::toDto);
    }

    @Transactional
    public TripDto patch(UUID userId, UUID tripId, TripPatchRequest req) {
        Trip t = owned(userId, tripId);
        boolean wasOffer = isOpenRide(t);
        if (req.visibility() != null) {
            t.setVisibility(req.visibility());
        }
        if (req.status() != null) {
            t.setStatus(req.status());
            if (req.status() == TripStatus.CANCELLED) {
                t.setVisibility(Visibility.PRIVATE);
            }
        }
        if (req.seatsAvailable() != null) {
            t.setSeatsAvailable(req.seatsAvailable());
        }
        if (req.costPerSeat() != null) {
            t.setCostPerSeat(req.costPerSeat());
        }
        if (req.willingToShare() != null) {
            t.setWillingToShare(req.willingToShare());
        }
        if (req.departureTime() != null) {
            t.setDepartureTime(req.departureTime());
        }
        if (req.sourceLocality() != null) {
            t.setSourceLocality(req.sourceLocality().isBlank() ? null : req.sourceLocality().trim());
        }
        if (req.notes() != null) {
            t.setNotes(req.notes());
        }
        if (req.visibility() == Visibility.DISCOVERABLE) {
            activity.record(userId, "TRIP_PUBLISHED", "Your journey to " + t.getDestinationCity() + " is now visible to compatible travellers");
        }
        if (!wasOffer && isOpenRide(t)) {
            rideAlerts.onRidePosted(t);
        }
        return Mappers.toDto(t);
    }

    private static boolean isOpenRide(Trip t) {
        return t.getVisibility() == Visibility.DISCOVERABLE && t.getStatus() == TripStatus.PLANNED && t.getSeatsAvailable() > 0;
    }

    // ------------------------------------------------------------------ saved trips

    @Transactional
    public SavedTripDto save(UUID userId, SaveTripRequest req) {
        User user = users.getReferenceById(userId);
        SavedTrip s = new SavedTrip();
        s.setUser(user);
        if (req.tripId() != null) {
            s.setTrip(owned(userId, req.tripId()));
        }
        s.setTitle(req.title());
        s.setSourceCity(req.sourceCity());
        s.setDestinationCity(req.destinationCity());
        s.setTravelDate(req.travelDate());
        try {
            s.setOptionSnapshotJson(mapper.writeValueAsString(req.option()));
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("The option could not be saved");
        }
        saved.save(s);
        activity.record(userId, "TRIP_SAVED", "Saved " + req.option().modeLabel().toLowerCase() + " option for " + req.destinationCity());
        return toDto(s);
    }

    @Transactional(readOnly = true)
    public List<SavedTripDto> savedTrips(UUID userId) {
        return saved.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void deleteSaved(UUID userId, UUID id) {
        SavedTrip s = saved.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Saved trip"));
        saved.delete(s);
    }

    private SavedTripDto toDto(SavedTrip s) {
        try {
            return new SavedTripDto(s.getId(), s.getTrip() == null ? null : s.getTrip().getId(), s.getTitle(), s.getSourceCity(),
                    s.getDestinationCity(), s.getTravelDate(),
                    mapper.readValue(s.getOptionSnapshotJson(), com.gharpahucho.dto.PlanDtos.OptionDto.class), s.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt saved option " + s.getId(), e);
        }
    }

    /** Ownership check. Unknown and foreign ids both answer 404 so other users' trip ids cannot be probed. */
    public Trip owned(UUID userId, UUID tripId) {
        return trips.findWithOwner(tripId)
                .filter(t -> t.getOwner().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Trip"));
    }
}
