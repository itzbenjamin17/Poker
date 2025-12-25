package com.pokergame.dto.response;

/**
 * Response DTO containing JWT token and room/game identifiers.
 * Returned when a player creates or joins a room.
 */
public class TokenResponse {
    private String token;
    private String roomId;
    private String playerName;

    public TokenResponse() {
    }

    public TokenResponse(String token, String roomId, String playerName) {
        this.token = token;
        this.roomId = roomId;
        this.playerName = playerName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}
