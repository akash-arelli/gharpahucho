package com.gharpahucho.repository;

import com.gharpahucho.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
    Optional<UserPreference> findByUserId(UUID userId);

    List<UserPreference> findByUserIdIn(Collection<UUID> userIds);
}
