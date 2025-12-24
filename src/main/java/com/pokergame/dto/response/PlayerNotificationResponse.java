package com.pokergame.dto.response;

/**
 * Response DTO for player-specific notifications during gameplay
 */
public record PlayerNotificationResponse(
        String type,
        String message,
        String playerName,
        String gameId
) {}
