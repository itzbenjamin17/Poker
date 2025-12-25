package com.pokergame.dto.request;

import com.pokergame.enums.PlayerAction;
import jakarta.validation.constraints.*;

/**
 * DTO for player action requests.
 * Player identity is now determined from the JWT token (Principal),
 * not from the request body.
 */
public record PlayerActionRequest(

        @NotNull(message = "Action is required") PlayerAction action,

        // Amount is optional - only needed for BET and RAISE actions
        @Min(value = 1, message = "Bet/raise amount must be at least 1 chip") @Max(value = 10000, message = "Bet/raise amount cannot exceed 10,000 chips") Integer amount

) {
    public PlayerActionRequest {
        // Validate amount is provided for betting actions
        if ((action == PlayerAction.BET || action == PlayerAction.RAISE)) {
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException("BET and RAISE actions require a positive amount");
            }
        }

        // Amount should not be provided for non-betting actions
        if ((action == PlayerAction.FOLD || action == PlayerAction.CHECK ||
                action == PlayerAction.CALL || action == PlayerAction.ALL_IN)) {
            if (amount != null && amount != 0) {
                throw new IllegalArgumentException(
                        action + " action should not include an amount (server calculates this)");
            }
        }
    }
}
