package com.gharpahucho.repository;

import com.gharpahucho.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByIdIn(Collection<UUID> ids);

    /** Bulk delete; all dependent rows are removed by ON DELETE CASCADE foreign keys. */
    @Modifying
    @Query("delete from User u where u.demoAccount = true")
    int deleteDemoAccounts();
}
