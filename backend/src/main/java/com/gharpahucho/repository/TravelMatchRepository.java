package com.gharpahucho.repository;

import com.gharpahucho.entity.TravelMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TravelMatchRepository extends JpaRepository<TravelMatch, UUID> {
    Optional<TravelMatch> findByTripIdAndCandidateTripId(UUID tripId, UUID candidateTripId);
}
