package com.pokergame.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    private String roomName;

    @NotBlank(message = "Player name is required")
    private String playerName;

    @NotNull
    @Min(value = 2, message = "Minimum 2 players required")
    @Max(value = 10, message = "Maximum 10 players allowed")
    private Integer maxPlayers;

    @NotNull
    @Min(value = 1, message = "Small blind must be at least 1")
    private Integer smallBlind;

    @NotNull
    @Min(value = 2, message = "Big blind must be at least 2")
    private Integer bigBlind;

    @NotNull
    @Min(value = 20, message = "Buy-in must be at least 20")
    private Integer buyIn;

    private String password; // Optional - can be null/empty for public rooms

    // Default constructor
    public CreateRoomRequest() {
    }

    // Constructor
    public CreateRoomRequest(String roomName, String playerName, Integer maxPlayers,
                             Integer smallBlind, Integer bigBlind, Integer buyIn, String password) {
        this.roomName = roomName;
        this.playerName = playerName;
        this.maxPlayers = maxPlayers;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.buyIn = buyIn;
        this.password = password;
    }

    // Getters and Setters
    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public Integer getSmallBlind() {
        return smallBlind;
    }

    public void setSmallBlind(Integer smallBlind) {
        this.smallBlind = smallBlind;
    }

    public Integer getBigBlind() {
        return bigBlind;
    }

    public void setBigBlind(Integer bigBlind) {
        this.bigBlind = bigBlind;
    }

    public Integer getBuyIn() {
        return buyIn;
    }

    public void setBuyIn(Integer buyIn) {
        this.buyIn = buyIn;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Validation method
    public boolean hasPassword() {
        return password != null && !password.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "CreateRoomRequest{" +
                "roomName='" + roomName + '\'' +
                ", playerName='" + playerName + '\'' +
                ", maxPlayers=" + maxPlayers +
                ", smallBlind=" + smallBlind +
                ", bigBlind=" + bigBlind +
                ", buyIn=" + buyIn +
                ", hasPassword=" + hasPassword() +
                '}';
    }
}