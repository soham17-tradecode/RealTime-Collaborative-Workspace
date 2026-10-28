package com.RealTimeCollaboration.RealTime.controller.chatController;

import com.RealTimeCollaboration.RealTime.DTOs.JoinRoomDTOs.joinRoomDTO;
import com.RealTimeCollaboration.RealTime.DTOs.chatMessageDTO.charMessageEntity;
import com.RealTimeCollaboration.RealTime.DTOs.chatMessageDTO.chatMessage;
import com.RealTimeCollaboration.RealTime.redis.roomRedis.createRoomRedis;
import com.RealTimeCollaboration.RealTime.repo.chatMessage.chatMessageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class chatController {

    private final chatMessageRepo chatMessageRepo;


    private final SimpMessagingTemplate messagingTemplate;

    private final createRoomRedis redisTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(chatMessage message, Principal principal)
    {
        charMessageEntity charMessageEntity = new charMessageEntity();
        charMessageEntity.setSender(principal.getName());

        charMessageEntity.setContent(message.getContent());
        charMessageEntity.setRoomCode(message.getRoomCode());
        charMessageEntity.setMessageType("TEXT");


        charMessageEntity.setSendAt(LocalDateTime.now());
        chatMessageRepo.save(charMessageEntity);


        message.setSender(principal.getName());

        charMessageEntity saved =
                chatMessageRepo.save(charMessageEntity);

        messagingTemplate.convertAndSend(
                "/topic/room/" + message.getRoomCode(),
                saved
        );
//        messagingTemplate.convertAndSend("/topic/room/"+message.getRoomCode(),message);

    }

    @PostMapping("/leave")
    public ResponseEntity<?> leaveRoom(@RequestBody joinRoomDTO joinRoomDTO, Authentication authentication)
    {
        String username = authentication.getName();
        redisTemplate.leavesRoom(joinRoomDTO.getRoomCode(),username);
      return ResponseEntity.ok("Left workspace. Room and membership are preserved for resuming.");
    }


    @GetMapping("/roomCode/{roomCode}/messages")
    public List<charMessageEntity> get (@PathVariable String roomCode)
    {
        return chatMessageRepo.findByRoomCode(roomCode);
    }



}
