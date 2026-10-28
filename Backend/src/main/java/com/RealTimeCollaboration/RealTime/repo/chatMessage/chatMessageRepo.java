package com.RealTimeCollaboration.RealTime.repo.chatMessage;

import com.RealTimeCollaboration.RealTime.DTOs.chatMessageDTO.charMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface chatMessageRepo extends JpaRepository<charMessageEntity,Long> {
   List<charMessageEntity>findByRoomCode(String roomCode);

   void deleteByRoomCode(String roomCode);

}
