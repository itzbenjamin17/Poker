package com.pokergame.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for joining an existing poker room
 */
public record JoinRoomRequest(
        @NotBlank(message = "Room name is required")
        String roomName,

        @NotBlank(message = "Player name is required")
        @Size(min = 1, max = 50, message = "Player name must be between 1 and 50 characters")
        String playerName,

        @Size(max = 50, message = "Password cannot exceed 50 characters")
        String password // Optional - null means no password required

) {
}
