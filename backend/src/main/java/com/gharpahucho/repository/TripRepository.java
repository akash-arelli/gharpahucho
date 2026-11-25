package com.gharpahucho.repository;

import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.TripStatus;
import com.gharpahucho.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("select t from Trip t join fetch t.owner where t.id = :id")
    Optional<Trip> findWithOwner(@Param("id") UUID id);

    Page<Trip> findByOwnerIdAndStatusNotOrderByTravelDateAsc(UUID ownerId, TripStatus status, Pageable pageable);

    List<Trip> findByOwnerIdAndStatusAndTravelDateGreaterThanEqualOrderByTravelDateAsc(
            UUID ownerId, TripStatus status, LocalDate from);

    /** Candidate pool for matching and ride-sharing: other people's discoverable, still-planned trips in a date window. */
    @Query("""
            select t from Trip t join fetch t.owner
            where t.visibility = :visibility and t.status = :status
              and t.owner.id <> :excludeOwner
              and t.travelDate between :from and :to
            """)
    List<Trip> findCandidates(@Param("visibility") Visibility visibility,
                              @Param("status") TripStatus status,
                              @Param("excludeOwner") UUID excludeOwner,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    @Query("""
            select t from Trip t join fetch t.owner
            where t.visibility = :visibility and t.status = :status
              and t.travelDate between :from and :to
              and t.seatsAvailable > 0
            """)
    List<Trip> findRideOffers(@Param("visibility") Visibility visibility,
                              @Param("status") TripStatus status,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    long countByOwnerId(UUID ownerId);

    void deleteByDataOrigin(com.gharpahucho.entity.DataOrigin origin);
}
