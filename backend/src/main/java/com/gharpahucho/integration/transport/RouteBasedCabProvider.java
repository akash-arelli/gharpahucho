package com.gharpahucho.integration.transport;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.maps.RouteInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Private cab option derived from a real routing calculation: distance and duration come from OSRM/Google Routes
 * ({@link DataSource#ROUTE_CALCULATION}); the fare is an ESTIMATE from a configurable per-km rate and is labelled so.
 * A cab is on demand, so there is no inventory to be "available" or "sold out".
 */
@Component
public class RouteBasedCabProvider implements TransportProvider {

    public static final int SEATS_PER_CAB = 4;

    private final GharPahuchoProperties.Maps maps;

    public RouteBasedCabProvider(GharPahuchoProperties props) {
        this.maps = props.getMaps();
    }

    @Override
    public String id() {
        return "route-cab";
    }

    @Override
    public DataSource dataSource() {
        return DataSource.ROUTE_CALCULATION;
    }

    /** Total fare estimate (INR) for one cab for the whole route. */
    public int estimateCabTotal(RouteInfo route) {
        return (int) (Math.round((maps.getCabBaseFare() + maps.getCabRatePerKm() * route.distanceKm()) / 10.0) * 10);
    }

    @Override
    public List<Candidate> search(SearchContext ctx) {
        RouteInfo route = ctx.route();
        int cabs = Math.max(1, (int) Math.ceil(ctx.travellers() / (double) SEATS_PER_CAB));
        int total = estimateCabTotal(route) * cabs;
        int perPerson = (int) Math.ceil(total / (double) ctx.travellers());
        int duration = Math.max(30, route.durationMinutes());

        LocalDateTime departure;
        LocalDateTime deadline = ctx.deadline();
        if (deadline != null) {
            departure = deadline.minusMinutes(duration + 30L);
        } else {
            departure = ctx.travelDate().atTime(LocalTime.of(6, 0));
        }
        if (departure.isBefore(ctx.now().plusMinutes(30))) {
            departure = ctx.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }
        LocalDateTime arrival = departure.plusMinutes(duration);

        String measured = route.measured() ? "route calculated by " + route.engine() : "straight-line estimate (routing unavailable)";
        String serviceName = cabs > 1 ? cabs + " cabs, door to door" : "Private cab, door to door";
        return List.of(new Candidate(
                "cab:" + ctx.from().name() + ":" + ctx.to().name() + ":" + ctx.travelDate(),
                TransportMode.CAB, "Any cab operator", serviceName, departure, arrival, perPerson, 0,
                "Direct, " + Math.round(route.distanceKm()) + " km (" + measured + ")",
                DataSource.ROUTE_CALCULATION, "ON_DEMAND",
                "On demand: you choose the departure time; fare is an estimate",
                4, null, null, null,
                List.of(new Candidate.Leg(TransportMode.CAB, ctx.from().name(), ctx.to().name(), departure, arrival, "Any cab operator", serviceName)),
                null));
    }
}
