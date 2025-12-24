package com.pokergame.dto;

import com.pokergame.model.Card;

import java.util.List;

public record PrivatePlayerState(
        String playerId,
        List<Card> holeCards
) {
}
