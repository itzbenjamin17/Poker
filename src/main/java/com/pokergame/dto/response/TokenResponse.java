package com.pokergame.dto.response;

/**
 * Response DTO containing JWT token and room/game identifiers.
 * Returned when a player creates or joins a room.
 */
public record TokenResponse(String token, String roomId, String playerName) {
}
