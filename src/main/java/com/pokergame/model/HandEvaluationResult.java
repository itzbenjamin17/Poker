package com.pokergame.model;

import java.util.List;

public record HandEvaluationResult(List<Card> bestHand, HandRank handRank) {

    public HandEvaluationResult {
        if (bestHand == null || handRank == null) {
            throw new IllegalArgumentException("Best hand and rank cannot be null");
        }
        bestHand = List.copyOf(bestHand);
    }
}