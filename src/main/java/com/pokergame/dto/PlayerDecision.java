package com.pokergame.dto;

import com.pokergame.model.PlayerAction;

public record PlayerDecision(PlayerAction action, int amount, String playerId) {
    public PlayerDecision {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
}