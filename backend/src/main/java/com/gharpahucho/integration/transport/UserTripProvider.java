package com.gharpahucho.integration.transport;

import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.entity.TripStatus;
import com.gharpahucho.entity.Visibility;
import com.gharpahucho.integration.maps.GeoUtil;
import com.gharpahucho.repository.TripRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Real, user-generated supply: seats that other GharPahucho travellers have published for their own car journeys
 * ({@link DataSource#USER_TRIP}). Availability is "seats posted": GharPahucho cannot guarantee them, so the label says
 * the traveller must confirm. Only a first name and last initial is ever exposed.
 */
@Component
public class UserTripProvider implements TransportProvider {

    private static final double DESTINATION_RADIUS_KM = 40;

    private final TripRepository trips;
    private final RouteBasedCabProvider cab;

    public UserTripProvider(TripRepository trips, RouteBasedCabProvider cab) {
        this.trips = trips;
        this.cab = cab;
    }

    @Override
    public String id() {
        return "user-trips";
    }

    @Override
    public DataSource dataSource() {
        return DataSource.USER_TRIP;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> search(SearchContext ctx) {
        List<Trip> offers = trips.findRideOffers(Visibility.DISCOVERABLE, TripStatus.PLANNED,
                ctx.searchFrom(), ctx.searchTo().plusDays(ctx.flexDays()));
        List<Candidate> out = new ArrayList<>();
        int fallbackTotal = cab.estimateCabTotal(ctx.route());
        for (Trip t : offers) {
            if (ctx.userId() != null && t.getOwner().getId().equals(ctx.userId())) {
                continue;
            }
            double srcKm = GeoUtil.haversineKm(t.getSourceLat(), t.getSourceLon(), ctx.from().lat(), ctx.from().lon());
            double dstKm = GeoUtil.haversineKm(t.getDestinationLat(), t.getDestinationLon(), ctx.to().lat(), ctx.to().lon());
            if (srcKm > ctx.radiusKm() || dstKm > DESTINATION_RADIUS_KM) {
                continue;
            }
            LocalTime depTime = t.getDepartureTime() != null ? t.getDepartureTime() : LocalTime.of(6, 0);
            LocalDateTime dep = t.getTravelDate().atTime(depTime);
            if (dep.isBefore(ctx.now())) {
                continue;
            }
            int minutes = Math.max(30, (int) Math.round(ctx.route().durationMinutes() * 1.08));
            LocalDateTime arr = dep.plusMinutes(minutes);
            int perSeat = t.getCostPerSeat() != null ? t.getCostPerSeat()
                    : (int) (Math.round(fallbackTotal / (double) (t.getSeatsAvailable() + 1) / 10.0) * 10);
            String name = t.getOwner().publicName();
            String area = t.getSourceLocality() == null ? t.getSourceCity() : t.getSourceLocality();
            String label = t.getSeatsAvailable() + " seat" + (t.getSeatsAvailable() == 1 ? "" : "s")
                    + " posted by " + name + ": not confirmed, request to join";
            out.add(new Candidate("ride:" + t.getId(), TransportMode.SHARED_CAR, name + "'s car",
                    "Shared car from " + area, dep, arr, perSeat, 0,
                    "Direct, shared with " + name + (t.isWillingToShare() ? " (open to more riders)" : ""),
                    DataSource.USER_TRIP, "SEATS_POSTED", label, 3, t.getSeatsAvailable(), t.getId(), name,
                    List.of(new Candidate.Leg(TransportMode.SHARED_CAR, ctx.from().name(), ctx.to().name(), dep, arr,
                            name + "'s car", "Shared car from " + area)),
                    null));
        }
        return out;
    }
}
