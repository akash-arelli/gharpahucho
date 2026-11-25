package com.gharpahucho.repository;

import com.gharpahucho.entity.GroupMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, UUID> {

    @Query("select m from GroupMessage m join fetch m.sender where m.group.id = :groupId order by m.createdAt asc")
    List<GroupMessage> findByGroup(@Param("groupId") UUID groupId);
}
