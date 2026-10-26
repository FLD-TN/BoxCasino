package com.dex.boxcasino.game.xidach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** A playing card: rank 1 (A) .. 13 (K), suit 0..3. */
public record Card(int rank, int suit) {

    public boolean isAce() {
        return rank == 1;
    }

    /** 10, J, Q, K. */
    public boolean isTen() {
        return rank >= 10;
    }

    /** Face value without aces: 2-10, J/Q/K = 10. */
    public int value() {
        return Math.min(rank, 10);
    }

    /** One fresh 52-card deck, shuffled. */
    public static List<Card> shuffledDeck(Random random) {
        List<Card> deck = new ArrayList<>(52);
        for (int s = 0; s < 4; s++) for (int r = 1; r <= 13; r++) deck.add(new Card(r, s));
        Collections.shuffle(deck, random);
        return deck;
    }

    @Override
    public String toString() {
        return rank + "/" + suit;
    }
}
