package com.pokergame.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a standard 52-card deck for poker games.
 * The deck is automatically shuffled upon creation.
 */
public class Deck {
    private final List<Card> cards;

    /**
     * Creates a new shuffled deck containing all 52 standard playing cards.
     */
    public Deck() {
        this.cards = new ArrayList<>();
        initializeDeck();
        shuffle();
    }

    /**
     * Initializes the deck with all 52 cards.
     */
    private void initializeDeck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
    }

    /**
     * Shuffles the deck randomly.
     */
    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Deals one card from the deck.
     *
     * @return the dealt card
     * @throws IllegalStateException if the deck is empty
     */
    public Card dealCard() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("No more cards in the deck");
        }
        return cards.removeLast();
    }

    /**
     * Deals multiple cards from the deck.
     *
     * @param numberOfCards the number of cards to deal
     * @return a list of dealt cards
     * @throws IllegalArgumentException if numberOfCards is invalid
     * @throws IllegalStateException if there aren't enough cards in the deck
     */
    public List<Card> dealCards(int numberOfCards) {
        if (numberOfCards <= 0) {
            throw new IllegalArgumentException("Number of cards must be positive");
        }
        if (numberOfCards > cards.size()) {
            throw new IllegalStateException(
                    "Not enough cards in deck. Requested: " + numberOfCards +
                            ", Available: " + cards.size()
            );
        }

        List<Card> dealtCards = new ArrayList<>();
        for (int i = 0; i < numberOfCards; i++) {
            dealtCards.add(cards.removeLast());
        }
        return dealtCards;
    }
}