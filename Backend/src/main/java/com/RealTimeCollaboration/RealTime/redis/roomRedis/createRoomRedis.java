package com.RealTimeCollaboration.RealTime.redis.roomRedis;

import com.RealTimeCollaboration.RealTime.model.createRoom.Room;
import com.RealTimeCollaboration.RealTime.model.registerUser;
import com.RealTimeCollaboration.RealTime.repo.userRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class createRoomRedis {

    private static final int MAX_MEMBERS = 4;
    private static final String ROOM_PREFIX = "room:";
    private static final String MEMBERS_SUFFIX = ":members";

    private final StringRedisTemplate template;
    private final userRepo userRepo;

    public Room create(String roomName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        registerUser owner = userRepo.findByName(username);

        String roomId;
        do {
            roomId = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase();
        } while (Boolean.TRUE.equals(template.hasKey(roomKey(roomId)))
                || Boolean.TRUE.equals(template.hasKey(legacyRoomKey(roomId)))
                || Boolean.TRUE.equals(template.hasKey(membersKey(roomId))));

        Room room = new Room();
        room.setRoomCode(roomId);
        room.setOwner(owner);
        room.setRoomName(roomName);
        room.setCreatedAt(LocalDateTime.now());

        writeRoomMetadata(roomId, roomName, username,
                owner != null && owner.getId() != null ? owner.getId().toString() : null);

        // Creator is registered as a member immediately. Dashboard may call
        // /joinRoom afterwards; that request is safely idempotent.
        template.opsForSet().add(membersKey(roomId), username);
        return room;
    }

    /** Returns only rooms whose stored owner matches the authenticated username. */
    public List<Map<String, String>> getOwnedRooms(String username) {
        List<Map<String, String>> rooms = new ArrayList<>();
        Set<String> keys = template.keys(ROOM_PREFIX + "*");
        if (keys == null) return rooms;

        for (String key : keys) {
            if (key.endsWith(MEMBERS_SUFFIX) || key.startsWith("room: ")) continue;
            Object roomId = template.opsForHash().get(key, "roomId");
            if (roomId == null) continue; // Not a room metadata hash.
            Object ownerName = template.opsForHash().get(key, "ownerName");
            if (ownerName == null || !username.equals(ownerName.toString())) continue;

            String code = roomId.toString();
            Map<String, String> room = new HashMap<>();
            room.put("roomCode", code);
            Object roomName = template.opsForHash().get(key, "roomName");
            room.put("roomName", roomName == null ? "Untitled room" : roomName.toString());
            rooms.add(room);
        }

        rooms.sort((a, b) -> a.get("roomName").compareToIgnoreCase(b.get("roomName")));
        return rooms;
    }

    public boolean isRoomOwner(String roomCode, String username) {
        String code = normalize(roomCode);
        Object ownerName = template.opsForHash().get(roomKey(code), "ownerName");
        if (ownerName == null && Boolean.TRUE.equals(template.hasKey(legacyRoomKey(code)))) {
            ownerName = template.opsForHash().get(legacyRoomKey(code), "ownerName");
        }
        // Do not reconstruct metadata from the members set here: membership alone
        // is not proof of ownership and must never authorize permanent deletion.
        return ownerName != null && username.equals(ownerName.toString());
    }

    /** Deletes room metadata, membership, room-specific Redis workspace state and presence. */
    public void deleteRoomKeys(String roomCode) {
        String code = normalize(roomCode);
        template.delete(roomKey(code));
        template.delete(legacyRoomKey(code));
        template.delete(membersKey(code));
        Set<String> workspaceKeys = template.keys("workspace:" + code + ":*");
        if (workspaceKeys != null && !workspaceKeys.isEmpty()) template.delete(workspaceKeys);
        Set<String> presenceKeys = template.keys("presence:" + code + ":*");
        if (presenceKeys != null && !presenceKeys.isEmpty()) template.delete(presenceKeys);
    }

    public String joinRoom(String roomCode, String userID) {
        String code = normalize(roomCode);
        if (!roomExist(code)) {
            return "room not found";
        }

        String memberKey = membersKey(code);
        if (Boolean.TRUE.equals(template.opsForSet().isMember(memberKey, userID))) {
            return "Already joined";
        }

        Long count = template.opsForSet().size(memberKey);
        if (count != null && count >= MAX_MEMBERS) {
            return "room is full";
        }

        template.opsForSet().add(memberKey, userID);
        return "joined";
    }

    /**
     * Leave is a presence/session action only. Keep room metadata and membership
     * so a registered member can re-enter later.
     */
    public void leavesRoom(String roomCode, String userId) {
        // Intentionally do not remove membership or room metadata.
    }

    public Set<String> getMembers(String roomCode) {
        Set<String> members = template.opsForSet().members(membersKey(normalize(roomCode)));
        return members == null ? Set.of() : members;
    }

    /**
     * Supports current key room:CODE and legacy key "room: CODE".
     * If metadata is missing but a members set survived, reconstruct minimal
     * metadata so existing members can resume. If both keys are gone, Redis
     * has no evidence of the room and it cannot be recovered by this method.
     */
    public boolean roomExist(String roomCode) {
        String code = normalize(roomCode);
        if (Boolean.TRUE.equals(template.hasKey(roomKey(code)))) {
            return true;
        }

        if (Boolean.TRUE.equals(template.hasKey(legacyRoomKey(code)))) {
            migrateLegacyMetadata(code);
            return true;
        }

        if (Boolean.TRUE.equals(template.hasKey(membersKey(code)))) {
            Set<String> members = template.opsForSet().members(membersKey(code));
            String ownerName = (members == null || members.isEmpty()) ? "unknown" : members.iterator().next();
            writeRoomMetadata(code, "Resumed workspace", ownerName, null);
            return true;
        }

        return false;
    }

    public boolean member(String username, String roomCode) {
        return Boolean.TRUE.equals(
                template.opsForSet().isMember(membersKey(normalize(roomCode)), username)
        );
    }

    private void migrateLegacyMetadata(String code) {
        String legacyKey = legacyRoomKey(code);
        String canonicalKey = roomKey(code);
        if (Boolean.TRUE.equals(template.hasKey(canonicalKey))) return;

        Object roomName = template.opsForHash().get(legacyKey, "roomName");
        Object ownerId = template.opsForHash().get(legacyKey, "ownerId");
        Object ownerName = template.opsForHash().get(legacyKey, "ownerName");

        writeRoomMetadata(
                code,
                roomName == null ? "Resumed workspace" : roomName.toString(),
                ownerName == null ? "unknown" : ownerName.toString(),
                ownerId == null ? null : ownerId.toString()
        );
    }

    private void writeRoomMetadata(String code, String roomName, String ownerName, String ownerId) {
        String key = roomKey(code);
        template.opsForHash().put(key, "roomId", code);
        template.opsForHash().put(key, "roomName", roomName == null ? "Resumed workspace" : roomName);
        template.opsForHash().put(key, "ownerName", ownerName == null ? "unknown" : ownerName);
        if (ownerId != null) {
            template.opsForHash().put(key, "ownerId", ownerId);
        }
        // No TTL: room metadata persists until explicit deletion/retention policy.
    }

    private String normalize(String roomCode) {
        return roomCode == null ? "" : roomCode.trim().toUpperCase();
    }

    private String roomKey(String roomCode) {
        return ROOM_PREFIX + roomCode;
    }

    private String legacyRoomKey(String roomCode) {
        return "room: " + roomCode;
    }

    private String membersKey(String roomCode) {
        return ROOM_PREFIX + roomCode + MEMBERS_SUFFIX;
    }
}
