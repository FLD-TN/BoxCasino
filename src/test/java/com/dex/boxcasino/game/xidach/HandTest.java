package com.dex.boxcasino.game.xidach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HandTest {

    private static List<Card> hand(int... ranks) {
        return java.util.Arrays.stream(ranks).mapToObj(r -> new Card(r, 0)).toList();
    }

    @Test
    void aceValuesDependOnHandSize() {
        assertEquals(16, Hand.total(hand(1, 5)));      // 2 cards: A = 11
        assertEquals(20, Hand.total(hand(1, 9)));      // 11 + 9
        assertEquals(15, Hand.total(hand(1, 5, 9)));   // 3 cards: A = 10 -> 24 bust, so A = 1 -> 15
        assertEquals(17, Hand.total(hand(1, 3, 4)));   // 3 cards: A = 10
        assertEquals(13, Hand.total(hand(1, 3, 4, 5))); // 4 cards: A = 1
        assertEquals(21, Hand.total(hand(1, 1)));      // 11 + 10 (xì bàn anyway)
    }

    @Test
    void specialHands() {
        assertTrue(Hand.isXiBan(hand(1, 1)));
        assertTrue(Hand.isXiDach(hand(1, 13)));
        assertTrue(Hand.isXiDach(hand(10, 1)));
        assertFalse(Hand.isXiDach(hand(1, 9)));
        assertEquals(Hand.Rank.XI_BAN, Hand.rank(hand(1, 1), 16));
        assertEquals(Hand.Rank.XI_DACH, Hand.rank(hand(12, 1), 16));
        assertEquals(Hand.Rank.NGU_LINH, Hand.rank(hand(2, 3, 2, 4, 5), 16));
        assertEquals(Hand.Rank.BUST, Hand.rank(hand(10, 9, 5), 16));
        assertEquals(Hand.Rank.NON, Hand.rank(hand(10, 5), 16));
        assertEquals(Hand.Rank.NORMAL, Hand.rank(hand(10, 7), 16));
    }

    @Test
    void compareRules() {
        // higher points win
        assertEquals(Hand.Outcome.WIN, Hand.compare(hand(10, 9), hand(10, 7), 16, true));
        assertEquals(Hand.Outcome.DRAW, Hand.compare(hand(10, 8), hand(9, 9), 16, true));
        // bust loses, both bust = draw (configurable)
        assertEquals(Hand.Outcome.LOSE, Hand.compare(hand(10, 9, 5), hand(10, 7), 16, true));
        assertEquals(Hand.Outcome.DRAW, Hand.compare(hand(10, 9, 5), hand(10, 6, 8), 16, true));
        assertEquals(Hand.Outcome.LOSE, Hand.compare(hand(10, 9, 5), hand(10, 6, 8), 16, false));
        // dealer bust, player fine
        assertEquals(Hand.Outcome.WIN, Hand.compare(hand(10, 6), hand(10, 6, 8), 16, true));
        // non (under 16 after doubling) always loses
        assertEquals(Hand.Outcome.LOSE, Hand.compare(hand(10, 5), hand(10, 6, 8), 16, true));
        // ngũ linh beats 21 points; xì dách beats ngũ linh; xì bàn beats xì dách
        assertEquals(Hand.Outcome.WIN, Hand.compare(hand(2, 3, 2, 4, 5), hand(10, 1, 10), 16, true));
        assertEquals(Hand.Outcome.WIN, Hand.compare(hand(1, 10), hand(2, 3, 2, 4, 5), 16, true));
        assertEquals(Hand.Outcome.WIN, Hand.compare(hand(1, 1), hand(1, 10), 16, true));
        assertEquals(Hand.Outcome.DRAW, Hand.compare(hand(1, 13), hand(10, 1), 16, true));
    }

    @Test
    void deckHas52DifferentCards() {
        var deck = Card.shuffledDeck(new java.util.Random(1));
        assertEquals(52, deck.size());
        assertEquals(52, new java.util.HashSet<>(deck).size());
    }
}
