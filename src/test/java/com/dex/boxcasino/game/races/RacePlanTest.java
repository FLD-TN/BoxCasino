package com.dex.boxcasino.game.races;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The animation must always show the drawn winner crossing first. */
class RacePlanTest {

    @Test
    void drawnWinnerAlwaysFinishesFirstAndAlone() {
        Random random = new Random(42);
        int steps = 40;
        int finish = 7;
        for (int i = 0; i < 5000; i++) {
            int win = random.nextInt(4);
            int[][] plan = RaceTable.plan(win, 4, steps, finish, random);
            for (int h = 0; h < 4; h++) {
                assertEquals(0, plan[h][0]);
                for (int s = 1; s <= steps; s++) {
                    int d = plan[h][s] - plan[h][s - 1];
                    assertTrue(d == 0 || d == 1, "one column per step at most");
                }
                if (h == win) {
                    assertEquals(finish, plan[h][steps]);
                    assertTrue(plan[h][steps - 1] < finish, "winner crosses on the last step");
                } else {
                    for (int s = 0; s <= steps; s++) assertTrue(plan[h][s] < finish, "a loser never reaches the finish");
                }
            }
        }
    }
}
