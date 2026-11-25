package com.gharpahucho.repository;

import com.gharpahucho.entity.RideAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideAlertRepository extends JpaRepository<RideAlert, UUID> {

    List<RideAlert> findByUserIdAndActiveTrueOrderByTravelDateAsc(UUID userId);

    long countByUserIdAndActiveTrue(UUID userId);

    Optional<RideAlert> findByIdAndUserId(UUID id, UUID userId);

    /** Active alerts of other people on the same route. The caller applies each alert's own date window. */
    @Query("""
            select a from RideAlert a
            where a.active = true and a.user.id <> :excludeUser
              and lower(a.sourceCity) = lower(:source) and lower(a.destinationCity) = lower(:destination)
            """)
    List<RideAlert> findOnRoute(@Param("excludeUser") UUID excludeUser, @Param("source") String source,
                                @Param("destination") String destination);
}
