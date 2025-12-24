package com.pokergame.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RoomData(
        String roomId,
        String roomName,
        int maxPlayers,
        int buyIn,
        int smallBlind,
        int bigBlind,
        LocalDateTime createdAt,
        String hostName,
        List<PlayerJoinInfo> players,
        int currentPlayers,
        boolean canStartGame
) {}