package com.pokergame.model;

import com.pokergame.enums.Rank;
import com.pokergame.enums.Suit;

/**
 * Represents a playing card with a suit and rank.
 * Cards are immutable once created.
 *
 * <p>Type safety is enforced through the use of Suit and Rank enums,
 * eliminating the need for validation.</p>
 */
public record Card(Rank rank, Suit suit) {
    /**
     * Creates a new Card with the specified rank and suit.
     *
     * @param rank the rank of the card
     * @param suit the suit of the card
     */
    public Card {
    }

    /**
     * Returns the numeric value of this card.
     *
     * @return the card's numeric value (2-14)
     */
    public int getValue() {
        return rank.getValue();
    }

    /**
     * Returns the rank of this card.
     *
     * @return the card's rank
     */
    @Override
    public Rank rank() {
        return rank;
    }

    /**
     * Returns the suit of this card.
     *
     * @return the card's suit
     */
    @Override
    public Suit suit() {
        return suit;
    }

    /**
     * Returns a string representation of this card in "Rank of Suit" format.
     *
     * @return formatted string (e.g., "Ace of Hearts")
     */
    @Override
    public String toString() {
        return rank.getDisplayName() + " of " + suit.getDisplayName();
    }
}