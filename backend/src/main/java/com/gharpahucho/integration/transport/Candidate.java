package com.gharpahucho.integration.transport;

import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.TransportMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A concrete way of making the journey, as produced by one {@link TransportProvider}. The provider fills in the
 * provenance ({@link #source}) and availability wording; the recommendation engine never changes either.
 */
public record Candidate(
        String id,
        TransportMode mode,
        String operator,
        String serviceName,
        LocalDateTime departure,
        LocalDateTime arrival,
        int costPerPerson,
        int transfers,
        String stops,
        DataSource source,
        String availability,
        String availabilityLabel,
        int comfort,
        Integer seatsLeft,
        UUID hostTripId,
        String hostName,
        List<Leg> legs,
        String festiveNote) {

    public record Leg(
            TransportMode mode,
            String from,
            String to,
            LocalDateTime departure,
            LocalDateTime arrival,
            String operator,
            String serviceName) {
    }

    public int durationMinutes() {
        return (int) java.time.Duration.between(departure, arrival).toMinutes();
    }
}
