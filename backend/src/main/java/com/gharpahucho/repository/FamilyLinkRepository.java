package com.gharpahucho.repository;

import com.gharpahucho.entity.FamilyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyLinkRepository extends JpaRepository<FamilyLink, UUID> {

    List<FamilyLink> findByTripIdAndOwnerIdAndRevokedFalseOrderByCreatedAtDesc(UUID tripId, UUID ownerId);

    Optional<FamilyLink> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select f from FamilyLink f join fetch f.trip t join fetch f.owner where f.token = :token")
    Optional<FamilyLink> findByToken(@Param("token") String token);
}
