package com.dex.boxcasino.game.slots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxcasino.game.TestGames;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** The screen of every spin shows exactly the drawn prize: the shape in its symbol, nothing that pays as much. */
class SlotsScreenTest {

    @Test
    void screenMatchesDrawnPrize() {
        SlotsGame slots = TestGames.load(new SlotsGame(null), "slots");
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200_000; i++) {
            SlotsGame.Spin spin = slots.spin(random);
            double best = 0;
            String bestKey = null;
            for (SlotsGame.Pattern p : slots.patterns) {
                for (boolean[] m : p.masks()) {
                    int s = SlotsGame.filledBy(spin.grid(), m);
                    if (s < 0) continue;
                    double pay = p.multiplier() * slots.symbols.get(s).bonus();
                    if (pay > best) {
                        best = pay;
                        bestKey = p.key();
                    }
                }
            }
            if (spin.pattern() == null) {
                assertEquals(null, bestKey, "losing spin shows a shape");
            } else {
                assertEquals(spin.multiplier(), best, 1e-9, "screen pays something else than drawn " + spin.pattern().key());
                assertEquals(spin.symbol().index(), SlotsGame.filledBy(spin.grid(), spin.mask()));
                seen.add(spin.pattern().key());
            }
        }
        assertTrue(seen.size() >= 9, "rare shapes drawn: " + seen);
    }

    @Test
    void spreadNeverFormsAShape() {
        SlotsGame slots = TestGames.load(new SlotsGame(null), "slots");
        for (int offset = 0; offset < 7; offset++) {
            int[] g = new int[25];
            for (int i = 0; i < 25; i++) g[i] = SlotsGame.spread(i / 5, i % 5, offset);
            for (SlotsGame.Pattern p : slots.patterns) {
                for (boolean[] m : p.masks()) assertEquals(-1, SlotsGame.filledBy(g, m), p.key());
            }
        }
    }
}
