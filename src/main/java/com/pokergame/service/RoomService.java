package com.pokergame.service;

import com.pokergame.dto.PlayerJoinInfo;
import com.pokergame.dto.RoomData;
import com.pokergame.dto.request.CreateRoomRequest;
import com.pokergame.dto.request.JoinRoomRequest;
import com.pokergame.dto.response.ApiResponse;
import com.pokergame.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service class responsible for poker room management.
 * Handles room creation, joining, leaving, and room data retrieval.
 */
@Service
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Room storage
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomHosts = new ConcurrentHashMap<>();

    /**
     * Creates a new poker room with the specified configuration.
     *
     * @param request The room creation request containing room name, host, and game
     *                settings
     * @return The unique room ID for the created room
     * @throws IllegalArgumentException if room name is already taken
     */
    public String createRoom(CreateRoomRequest request) {
        if (isRoomNameTaken(request.getRoomName())) {
            throw new IllegalArgumentException(
                    "Room name '" + request.getRoomName() + "' is already taken. Please choose a different name.");
        }

        String roomId = UUID.randomUUID().toString();

        Room room = new Room(
                roomId,
                request.getRoomName(),
                request.getPlayerName(), // Host name
                request.getMaxPlayers(),
                request.getSmallBlind(),
                request.getBigBlind(),
                request.getBuyIn(),
                request.getPassword());

        // Add the host as the first player
        room.addPlayer(request.getPlayerName());

        rooms.put(roomId, room);
        roomHosts.put(roomId, request.getPlayerName());

        messagingTemplate.convertAndSend("/rooms" + roomId, new ApiResponse<>(true, "ROOM_CREATED", getRoomData(roomId)));

        logger.info("Room created: {} (ID: {}) by host: {}",
                request.getRoomName(), roomId, request.getPlayerName());

        return roomId;
    }

    /**
     * Adds a player to an existing room (not a started game).
     * Validates password for private rooms and checks for room capacity and
     * duplicate names.
     *
     * @param joinRequest the request containing room name, player name, and
     *                    password
     * @throws IllegalArgumentException if room not found, password invalid, room
     *                                  full, or name taken
     */
    public void joinRoom(JoinRoomRequest joinRequest) {
        Room foundRoom = findRoomByName(joinRequest.roomName());
        if (foundRoom == null) {
            throw new IllegalArgumentException("Room not found");
        }

        String roomId = foundRoom.getRoomId();
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Check password if room is private
        if (room.hasPassword() && !room.checkPassword(joinRequest.password())) {
            throw new IllegalArgumentException("Invalid password");
        }

        // Check if room is full
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new IllegalArgumentException("Room is full");
        }

        // Check if player name already exists
        if (room.hasPlayer(joinRequest.playerName())) {
            throw new IllegalArgumentException("Player name already taken");
        }

        room.addPlayer(joinRequest.playerName());

        logger.info("Player {} joined room: {}", joinRequest.playerName(), room.getRoomName());

        messagingTemplate.convertAndSend("/rooms" + roomId, new ApiResponse<>(true, "PLAYER_JOINED", getRoomData(roomId)));
    }


    /**
     * Removes a player from a room. If the host leaves, the entire room is
     * destroyed.
     * If all players leave, the room is also destroyed.
     *
     * @param roomId     the unique identifier of the room
     * @param playerName the name of the player leaving
     * @throws IllegalArgumentException if room not found
     */
    public void leaveRoom(String roomId, String playerName) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Check if the leaving player is the host
        if (isRoomHost(roomId, playerName)) {
            // Host is leaving - destroy the entire Room
            logger.info("Host {} leaving room {}, destroying room", playerName, room.getRoomName());
            messagingTemplate.convertAndSend("/rooms" + roomId, new ApiResponse<>(true, "ROOM_CLOSED", null));
            destroyRoom(roomId);
        } else {
            // Regular player leaving - just remove them from the room
            room.removePlayer(playerName);
            logger.info("Player {} left room: {}", playerName, room.getRoomName());
            messagingTemplate.convertAndSend("/rooms" + roomId, new ApiResponse<>(true, "PLAYER_LEFT", getRoomData(roomId)));

            // If no players left after removal, also destroy the room
            if (room.getPlayers().isEmpty()) {
                logger.info("No players remaining in room {}, destroying room", room.getRoomName());
                messagingTemplate.convertAndSend("/rooms" + roomId, new ApiResponse<>(true, "ROOM_CLOSED", null));
                destroyRoom(roomId);
            }
        }
    }


    /**
     * Retrieves all currently available rooms.
     *
     * @return a list of all Room objects
     */
    public List<Room> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * Retrieves formatted room data for API responses and WebSocket broadcasts.
     * Includes player information with host status indicators.
     *
     * @param roomId the unique identifier of the room
     * @return a RoomData object containing formatted room information
     * @throws IllegalArgumentException if roomId is null or room not found
     */
    public RoomData getRoomData(String roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID cannot be null");
        }
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Convert player names to PlayerInfoDTO objects
        List<PlayerJoinInfo> playerObjects = room.getPlayers().stream()
                .map(playerName -> new PlayerJoinInfo(
                        playerName,
                        isRoomHost(roomId, playerName),
                        "recently" // Placeholder as in original logic
                ))
                .collect(Collectors.toList());

        // Create and return the RoomDataDTO
        return new RoomData(
                roomId,
                room.getRoomName(),
                room.getMaxPlayers(),
                room.getBuyIn(),
                room.getSmallBlind(),
                room.getBigBlind(),
                room.getCreatedAt(),
                room.getHostName(),
                playerObjects,
                playerObjects.size(),
                playerObjects.size() >= 2);
    }

    /**
     * Retrieves a room by its unique identifier.
     *
     * @param roomId the unique identifier of the room
     * @return the Room object if found, null otherwise
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * Finds a room by its name (case-insensitive).
     *
     * @param roomName the name of the room to find
     * @return the Room object if found, null otherwise
     */
    public Room findRoomByName(String roomName) {
        return rooms.values().stream()
                .filter(room -> room.getRoomName().equalsIgnoreCase(roomName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a player is the host of a specific room.
     *
     * @param roomId     the unique identifier of the room
     * @param playerName the name of the player to check
     * @return true if the player is the room host, false otherwise
     */
    public boolean isRoomHost(String roomId, String playerName) {
        String hostName = roomHosts.get(roomId);
        return hostName != null && hostName.equals(playerName);
    }

    /**
     * Completely destroys and removes a room from the system.
     * Removes both the room and its host mapping.
     *
     * @param roomId the unique identifier of the room to destroy
     */
    public void destroyRoom(String roomId) {
        Room room = rooms.remove(roomId);
        roomHosts.remove(roomId);
        if (room != null) {
            logger.info("Room destroyed: {} (ID: {})", room.getRoomName(), roomId);
        }
    }

    /**
     * Checks if a room name is already taken (case-insensitive).
     *
     * @param roomName the name to check for availability
     * @return true if the name is already in use, false otherwise
     */
    private boolean isRoomNameTaken(String roomName) {
        return rooms.values().stream()
                .anyMatch(room -> room.getRoomName().equalsIgnoreCase(roomName));
    }
}
