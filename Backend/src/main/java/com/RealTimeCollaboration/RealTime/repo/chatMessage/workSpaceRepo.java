package com.RealTimeCollaboration.RealTime.repo.chatMessage;

import com.RealTimeCollaboration.RealTime.DTOs.chatMessageDTO.workSpaceActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface workSpaceRepo extends JpaRepository<workSpaceActivityEntity,Long> {
    List<workSpaceActivityEntity> findByRoomCodeOrderByCreatedAtDesc(String roomCode);

    void deleteByRoomCode(String roomCode);
}
