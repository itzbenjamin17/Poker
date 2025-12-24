package com.pokergame.dto;

import com.pokergame.model.Card;
import com.pokergame.model.HandRank;

import java.util.List;

public record PublicPlayerState(
        String id,
        String name,
        int chips,
        int currentBet,
        String status,
        boolean isAllIn,
        boolean isCurrentPlayer,
        boolean hasFolded,
        // Showdown-specific fields
        HandRank handRank,
        List<Card> bestHand,
        Boolean isWinner,
        Integer chipsWon

) {
    // For non showdown states
    public PublicPlayerState (String id, String name, int chips, int currentBet, String status,
                              boolean isAllIn, boolean isCurrentPlayer, boolean hasFolded){
        this(id, name, chips, currentBet, status, isAllIn, isCurrentPlayer, hasFolded, null, null, null, null);
    }
}
