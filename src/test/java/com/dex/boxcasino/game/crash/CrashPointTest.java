package com.dex.boxcasino.game.crash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxcasino.game.TestGames;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** Explosion points follow P(point >= x) = rtp / x; the flight multiplier only grows and stops at the cap. */
class CrashPointTest {

    @Test
    void pointDistribution() {
        CrashGame crash = TestGames.load(new CrashGame(null), "crash");
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        int n = 1_000_000;
        int instant = 0;
        int two = 0;
        int ten = 0;
        for (int i = 0; i < n; i++) {
            double p = crash.drawPoint(random);
            assertTrue(p >= 1.0 && p <= crash.maxMultiplier);
            if (p <= 1.0) instant++;
            if (p >= 2) two++;
            if (p >= 10) ten++;
        }
        // below x1.01 rounds down to x1.00: 1 - 0.96 / 1.01
        assertEquals(1 - 0.96 / 1.01, instant / (double) n, 0.002);
        assertEquals(0.48, two / (double) n, 0.003);
        assertEquals(0.096, ten / (double) n, 0.002);
    }

    @Test
    void multiplierGrowsToTheCap() {
        CrashGame crash = TestGames.load(new CrashGame(null), "crash");
        double last = 1.0;
        int step = 0;
        while (last < crash.maxMultiplier) {
            step++;
            double m = crash.multiplierAt(step);
            assertTrue(m >= last, "step " + step);
            last = m;
        }
        assertEquals(crash.maxMultiplier, last, 1e-9);
        // x2 after about 5 seconds at the default speed (1.5% per 2 ticks)
        int toTwo = 0;
        while (crash.multiplierAt(toTwo) < 2) toTwo++;
        assertEquals(94, toTwo * crash.stepTicks, 4);
    }
}
