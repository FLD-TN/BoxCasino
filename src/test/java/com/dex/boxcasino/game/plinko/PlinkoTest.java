package com.dex.boxcasino.game.plinko;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxcasino.game.TestGames;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** Plinko risk levels return 95% and the ball really bounces 50-50 on every peg row. */
class PlinkoTest {

    @Test
    void plinkoEveryRiskIs95() {
        PlinkoGame plinko = TestGames.load(new PlinkoGame(null), "plinko");
        assertEquals(3, plinko.risks.size());
        for (var r : plinko.risks.values()) assertEquals(0.95, plinko.rtp(r), 1e-9, r.key());
        double measured = plinko.simulate(2_000_000, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(0.95, measured, 0.006);
    }

    @Test
    void plinkoDropsFollowFairBounces() {
        PlinkoGame plinko = TestGames.load(new PlinkoGame(null), "plinko");
        double[] expected = {1 / 16.0, 4 / 16.0, 6 / 16.0, 4 / 16.0, 1 / 16.0};
        for (int k = 0; k < 5; k++) assertEquals(expected[k], plinko.chance(k), 1e-12);
        Set<Integer> pegs = Set.of(13, 21, 23, 29, 31, 33, 37, 39, 41, 43, 46, 48, 50, 52);
        List<Integer> hoppers = List.of(45, 47, 49, 51, 53);
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        int[] counts = new int[5];
        int drops = 400_000;
        for (int i = 0; i < drops; i++) {
            PlinkoGame.Drop drop = plinko.drop(random);
            List<Integer> slots = drop.path();
            counts[drop.hopper()]++;
            assertEquals(4, slots.get(0));
            assertEquals(hoppers.get(drop.hopper()), slots.get(slots.size() - 1));
            for (int s = 1; s < slots.size(); s++) {
                assertEquals(slots.get(s - 1) / 9 + 1, slots.get(s) / 9, "one row per step");
                assertTrue(Math.abs(slots.get(s) % 9 - slots.get(s - 1) % 9) <= 1, "one column per step");
                assertTrue(!pegs.contains(slots.get(s)), "ball hits a peg at " + slots.get(s));
            }
        }
        for (int k = 0; k < 5; k++) assertEquals(expected[k], counts[k] / (double) drops, 0.005, "hopper " + (k + 1));
    }
}
