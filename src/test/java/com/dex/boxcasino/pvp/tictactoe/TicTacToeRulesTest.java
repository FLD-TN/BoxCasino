package com.dex.boxcasino.pvp.tictactoe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TicTacToeRulesTest {

    @Test
    void lines() {
        int[] diagonal = {0, -1, 1, -1, 0, 1, -1, -1, 0};
        assertArrayEquals(new int[] {0, 4, 8}, TicTacToeGame.winLine(diagonal, 0));
        assertNull(TicTacToeGame.winLine(diagonal, 1));

        int[] column = {1, 0, -1, 1, 0, -1, 1, -1, 0};
        assertArrayEquals(new int[] {0, 3, 6}, TicTacToeGame.winLine(column, 1));

        int[] full = {0, 1, 0, 0, 1, 1, 1, 0, 0};
        assertNull(TicTacToeGame.winLine(full, 0));
        assertNull(TicTacToeGame.winLine(full, 1));
    }
}
