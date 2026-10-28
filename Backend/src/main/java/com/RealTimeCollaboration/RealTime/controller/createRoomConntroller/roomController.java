package com.RealTimeCollaboration.RealTime.controller.createRoomConntroller;

import com.RealTimeCollaboration.RealTime.DTOs.JoinRoomDTOs.joinRoomDTO;
import com.RealTimeCollaboration.RealTime.DTOs.createRoomDTos.createRoomRequest;
import com.RealTimeCollaboration.RealTime.model.createRoom.Room;
//import com.RealTimeCollaboration.RealTime.model.createRoom.user;
import com.RealTimeCollaboration.RealTime.redis.roomRedis.createRoomRedis;
import com.RealTimeCollaboration.RealTime.service.activity.activityService;
import com.RealTimeCollaboration.RealTime.service.createRoomService.roomService;
import com.RealTimeCollaboration.RealTime.repo.chatMessage.chatMessageRepo;
import com.RealTimeCollaboration.RealTime.repo.chatMessage.workSpaceRepo;
import com.RealTimeCollaboration.RealTime.repo.file.fileMetaDataRepo;
import com.RealTimeCollaboration.RealTime.model.file.fileMetaData;
import com.RealTimeCollaboration.RealTime.service.file.fileService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class roomController {

    @Autowired
    roomService roomService;


    @Autowired
    activityService activityService;

    @Autowired
    createRoomRedis createRoomRedis;

    @Autowired
    chatMessageRepo chatMessageRepo;

    @Autowired
    workSpaceRepo workSpaceRepo;

    @Autowired
    fileMetaDataRepo fileMetaDataRepo;

    @Autowired
    fileService fileService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;


    @PostMapping("/room")
    public ResponseEntity<?> createRoom(@RequestBody createRoomRequest request)
    {


        Room room = createRoomRedis.create(request.getRoomName());
        System.out.println(room.getRoomCode());


        return ResponseEntity.ok(room);



    }

    @PostMapping("/joinRoom")
    public ResponseEntity<?> joinRoom(@RequestBody joinRoomDTO joinRoomDTO, Authentication authentication)
    {
        String userId = authentication.getName();
        String join = createRoomRedis.joinRoom(joinRoomDTO.getRoomCode(),userId);
        if ("joined".equals(join)) {
            activityService.record(joinRoomDTO.getRoomCode(), userId, "JOIN_ROOM", " ");
            return ResponseEntity.ok(join);
        }

        // Returning members must be able to resume without being treated as an error.
        if ("Already joined".equals(join)) {
            return ResponseEntity.ok("Already joined; workspace resumed");
        }

        if ("room not found".equals(join)) {
            return ResponseEntity.status(404).body(join);
        }

        return ResponseEntity.badRequest().body(join);
    }

    @GetMapping("/my-rooms")
    public ResponseEntity<?> getMyRooms(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required"));
        }
        return ResponseEntity.ok(createRoomRedis.getOwnedRooms(authentication.getName()));
    }

    @DeleteMapping("/room/{roomCode}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomCode, Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required"));
        }

        String code = roomCode == null ? "" : roomCode.trim().toUpperCase();
        if (!createRoomRedis.isRoomOwner(code, authentication.getName())) {
            if (!createRoomRedis.roomExist(code)) {
                return ResponseEntity.status(404).body(Map.of("message", "Room not found"));
            }
            return ResponseEntity.status(403).body(Map.of("message", "Only the room owner can delete this room"));
        }

        // Remove stored files before deleting their metadata so their paths are available.
        List<fileMetaData> fileMetadata = fileMetaDataRepo.findByRoomCode(code);
        fileService.deleteRoomFiles(code, fileMetadata);
        fileMetaDataRepo.deleteByRoomCode(code);
        chatMessageRepo.deleteByRoomCode(code);
        workSpaceRepo.deleteByRoomCode(code);
        createRoomRedis.deleteRoomKeys(code);


        messagingTemplate.convertAndSend(
                "/topic/room-deleted/" + code,
                (Object) Map.of(
                        "type", "ROOM_DELETED",
                        "roomCode", code
                )
        );


        return ResponseEntity.ok(Map.of("message", "Room and associated data permanently deleted", "roomCode", code));
    }

    @GetMapping("/{roomCode}/members")
    public ResponseEntity<?> getMembers(@PathVariable String roomCode)
    {
        return  ResponseEntity.ok(createRoomRedis.getMembers(roomCode));
    }

    @GetMapping("/roomCode/{roomCode}/validated")
    public ResponseEntity<?> validates(@PathVariable String roomCode,Authentication authentication)
    {
        String username = authentication.getName();
        boolean exists = createRoomRedis.roomExist(roomCode);
        if (!exists)
        {
            return ResponseEntity.status(404)
                    .body("Room not found");
        }


        boolean isMember = createRoomRedis.member(username,roomCode);
        if (!isMember)
        {
            return ResponseEntity.status(403)
                    .body("Access denied");
        }


//        return  ResponseEntity.ok(Map.of("exists",true,"isMember",true));

        return ResponseEntity.ok("Valid room");
    }


}
