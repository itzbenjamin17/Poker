package com.pokergame.model;

import com.pokergame.enums.HandRank;
import com.pokergame.exception.BadRequestException;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record HandEvaluationResult(List<Card> bestHand, HandRank handRank) {
    private static final Logger logger = LoggerFactory.getLogger(HandEvaluationResult.class);

    public HandEvaluationResult {
        if (bestHand == null || handRank == null) {
            logger.error("Null bestHand or handRank: bestHand={}, handRank={}", bestHand, handRank);
            throw new BadRequestException("An error occured evaluating hand. Please try again.");
        }
        bestHand = List.copyOf(bestHand);
    }
}