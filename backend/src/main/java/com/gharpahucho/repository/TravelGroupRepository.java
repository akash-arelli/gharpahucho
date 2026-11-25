package com.gharpahucho.repository;

import com.gharpahucho.entity.MemberStatus;
import com.gharpahucho.entity.TravelGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TravelGroupRepository extends JpaRepository<TravelGroup, UUID> {

    @Query("""
            select g from TravelGroup g join fetch g.owner
            where g.id in (select m.group.id from GroupMember m where m.user.id = :userId and m.status in :statuses)
            order by g.travelDate asc
            """)
    List<TravelGroup> findForUser(@Param("userId") UUID userId, @Param("statuses") List<MemberStatus> statuses);
}
