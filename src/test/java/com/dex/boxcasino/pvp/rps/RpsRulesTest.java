package com.dex.boxcasino.pvp.rps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RpsRulesTest {

    private static final int ROCK = 0;
    private static final int PAPER = 1;
    private static final int SCISSORS = 2;

    @Test
    void classicRules() {
        assertEquals(1, RpsGame.compare(ROCK, SCISSORS));
        assertEquals(1, RpsGame.compare(PAPER, ROCK));
        assertEquals(1, RpsGame.compare(SCISSORS, PAPER));
        assertEquals(-1, RpsGame.compare(SCISSORS, ROCK));
        assertEquals(-1, RpsGame.compare(ROCK, PAPER));
        assertEquals(-1, RpsGame.compare(PAPER, SCISSORS));
        for (int f = 0; f < 3; f++) assertEquals(0, RpsGame.compare(f, f));
    }
}
