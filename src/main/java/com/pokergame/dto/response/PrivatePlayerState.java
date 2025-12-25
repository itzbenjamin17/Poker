package com.pokergame.dto.response;

import com.pokergame.model.Card;

import java.util.List;

public record PrivatePlayerState(
        String playerId,
        List<Card> holeCards
) {
}
