package com.gharpahucho.repository;

import com.gharpahucho.entity.GroupMember;
import com.gharpahucho.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    @Query("select m from GroupMember m join fetch m.user where m.group.id = :groupId order by m.invitedAt asc")
    List<GroupMember> findByGroup(@Param("groupId") UUID groupId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    @Query("select m from GroupMember m join fetch m.group g join fetch g.owner where m.user.id = :userId and m.status = :status")
    List<GroupMember> findByUserAndStatus(@Param("userId") UUID userId, @Param("status") MemberStatus status);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);
}
