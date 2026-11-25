package com.gharpahucho.repository;

import com.gharpahucho.entity.SavedTrip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedTripRepository extends JpaRepository<SavedTrip, UUID> {
    List<SavedTrip> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<SavedTrip> findByIdAndUserId(UUID id, UUID userId);
}
