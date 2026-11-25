package com.gharpahucho.repository;

import com.gharpahucho.entity.InterestStatus;
import com.gharpahucho.entity.TravelInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TravelInterestRepository extends JpaRepository<TravelInterest, UUID> {

    @Query("""
            select i from TravelInterest i
              join fetch i.fromTrip join fetch i.toTrip join fetch i.fromUser join fetch i.toUser
            where i.toUser.id = :userId order by i.createdAt desc
            """)
    List<TravelInterest> findIncoming(@Param("userId") UUID userId);

    @Query("""
            select i from TravelInterest i
              join fetch i.fromTrip join fetch i.toTrip join fetch i.fromUser join fetch i.toUser
            where i.fromUser.id = :userId order by i.createdAt desc
            """)
    List<TravelInterest> findOutgoing(@Param("userId") UUID userId);

    @Query("""
            select i from TravelInterest i
              join fetch i.fromTrip join fetch i.toTrip join fetch i.fromUser join fetch i.toUser
            where i.id = :id
            """)
    Optional<TravelInterest> findDetailed(@Param("id") UUID id);

    Optional<TravelInterest> findByFromTripIdAndToTripId(UUID fromTripId, UUID toTripId);

    long countByToUserIdAndStatus(UUID toUserId, InterestStatus status);
}
