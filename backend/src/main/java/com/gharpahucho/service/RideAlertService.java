package com.gharpahucho.service;

import com.gharpahucho.dto.GrowthDtos.AlertDto;
import com.gharpahucho.dto.GrowthDtos.AlertsResponse;
import com.gharpahucho.dto.GrowthDtos.RideMatchDto;
import com.gharpahucho.entity.RideAlert;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.TripStatus;
import com.gharpahucho.entity.Visibility;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.repository.RideAlertRepository;
import com.gharpahucho.repository.TripRepository;
import com.gharpahucho.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * "Tell me when someone posts a ride on my route." Everything here is real marketplace data: rides are trips that
 * other travellers published with seats to spare. The free plan keeps a few alerts; Flagsmith decides the limit
 * (config free_ride_alerts) and who is unlimited (flag unlimited_ride_alerts).
 */
@Service
public class RideAlertService {

    static final String UPGRADE = "Get the Festival Pass for unlimited ride alerts.";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final RideAlertRepository alerts;
    private final TripRepository trips;
    private final UserRepository users;
    private final ActivityService activity;
    private final FeatureFlagService flags;
    private final Clock clock;

    public RideAlertService(RideAlertRepository alerts, TripRepository trips, UserRepository users, ActivityService activity,
                            FeatureFlagService flags, Clock clock) {
        this.alerts = alerts;
        this.trips = trips;
        this.users = users;
        this.activity = activity;
        this.flags = flags;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AlertsResponse list(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        List<AlertDto> out = alerts.findByUserIdAndActiveTrueOrderByTravelDateAsc(userId).stream()
                .filter(a -> !a.getTravelDate().plusDays(a.getFlexDays()).isBefore(today))
                .map(a -> toDto(a, userId, today)).toList();
        boolean unlimited = flags.isEnabled(FeatureKey.UNLIMITED_RIDE_ALERTS, userId);
        return new AlertsResponse(out, unlimited ? -1 : flags.config(userId).freeRideAlerts(), unlimited, unlimited ? null : UPGRADE);
    }

    @Transactional
    public AlertDto create(UUID userId, UUID tripId, Integer flexDays) {
        Trip t = trips.findWithOwner(tripId).filter(x -> x.getOwner().getId().equals(userId)).orElseThrow(() -> ApiException.notFound("Trip"));
        int flex = flexDays == null ? 1 : flexDays;
        LocalDate today = LocalDate.now(clock);
        for (RideAlert existing : alerts.findByUserIdAndActiveTrueOrderByTravelDateAsc(userId)) {
            if (existing.getSourceCity().equalsIgnoreCase(t.getSourceCity()) && existing.getDestinationCity().equalsIgnoreCase(t.getDestinationCity())
                    && existing.getTravelDate().equals(t.getTravelDate())) {
                return toDto(existing, userId, today);
            }
        }
        if (!flags.isEnabled(FeatureKey.UNLIMITED_RIDE_ALERTS, userId)) {
            int limit = flags.config(userId).freeRideAlerts();
            if (alerts.countByUserIdAndActiveTrue(userId) >= limit) {
                throw new ApiException(HttpStatus.FORBIDDEN, "PLAN_LIMIT",
                        "The free plan includes " + limit + " ride alert" + (limit == 1 ? "" : "s") + ". " + UPGRADE);
            }
        }
        RideAlert a = new RideAlert();
        a.setUser(users.getReferenceById(userId));
        a.setSourceCity(t.getSourceCity());
        a.setDestinationCity(t.getDestinationCity());
        a.setTravelDate(t.getTravelDate());
        a.setFlexDays(flex);
        alerts.save(a);
        activity.record(userId, "RIDE_ALERT_SET", "We'll tell you when a ride appears from " + t.getSourceCity() + " to " + t.getDestinationCity()
                + " around " + DAY.format(t.getTravelDate()));
        return toDto(a, userId, today);
    }

    @Transactional
    public void delete(UUID userId, UUID alertId) {
        RideAlert a = alerts.findByIdAndUserId(alertId, userId).orElseThrow(() -> ApiException.notFound("Alert"));
        a.setActive(false);
    }

    @Transactional
    public void markSeen(UUID userId, UUID alertId) {
        alerts.findByIdAndUserId(alertId, userId).orElseThrow(() -> ApiException.notFound("Alert")).setLastSeenAt(Instant.now());
    }

    /** Called when a traveller starts offering seats on a route: tell everyone who asked to hear about it. */
    @Transactional
    public int onRidePosted(Trip ride) {
        if (ride.getVisibility() != Visibility.DISCOVERABLE || ride.getSeatsAvailable() <= 0 || ride.getStatus() != TripStatus.PLANNED) {
            return 0;
        }
        int notified = 0;
        for (RideAlert a : alerts.findOnRoute(ride.getOwner().getId(), ride.getSourceCity(), ride.getDestinationCity())) {
            if (!withinWindow(a, ride.getTravelDate()) || !flags.isEnabled(FeatureKey.RIDE_ALERTS, a.getUser().getId())) {
                continue;
            }
            activity.record(a.getUser().getId(), "RIDE_ALERT", "A ride just appeared: " + ride.getSourceCity() + " to " + ride.getDestinationCity()
                    + " on " + DAY.format(ride.getTravelDate()) + ", " + ride.getSeatsAvailable() + " seat" + (ride.getSeatsAvailable() == 1 ? "" : "s")
                    + (ride.getCostPerSeat() == null ? "" : ", ₹" + ride.getCostPerSeat() + " each"));
            notified++;
        }
        return notified;
    }

    static boolean withinWindow(RideAlert a, LocalDate rideDate) {
        return !rideDate.isBefore(a.getTravelDate().minusDays(a.getFlexDays())) && !rideDate.isAfter(a.getTravelDate().plusDays(a.getFlexDays()));
    }

    private AlertDto toDto(RideAlert a, UUID userId, LocalDate today) {
        LocalDate from = a.getTravelDate().minusDays(a.getFlexDays());
        LocalDate to = a.getTravelDate().plusDays(a.getFlexDays());
        List<RideMatchDto> rides = trips.findRideOffers(Visibility.DISCOVERABLE, TripStatus.PLANNED, from.isBefore(today) ? today : from, to).stream()
                .filter(r -> !r.getOwner().getId().equals(userId))
                .filter(r -> r.getSourceCity().equalsIgnoreCase(a.getSourceCity()) && r.getDestinationCity().equalsIgnoreCase(a.getDestinationCity()))
                .map(r -> new RideMatchDto(r.getId(), r.getOwner().getId(), r.getOwner().publicName(), r.getTravelDate(), r.getDepartureTime(), r.getSourceLocality(),
                        r.getSeatsAvailable(), r.getCostPerSeat(), r.getCreatedAt().isAfter(a.getLastSeenAt())))
                .toList();
        return new AlertDto(a.getId(), a.getSourceCity() + " → " + a.getDestinationCity(), a.getTravelDate(), a.getFlexDays(), rides,
                (int) rides.stream().filter(RideMatchDto::fresh).count(), a.getCreatedAt());
    }
}
