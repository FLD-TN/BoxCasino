package com.dex.boxcasino.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxcasino.game.mines.MinesGame;
import com.dex.boxcasino.game.plinko.PlinkoGame;
import com.dex.boxcasino.game.races.RacesGame;
import com.dex.boxcasino.game.roulette.RouletteGame;
import com.dex.boxcasino.game.stairs.StairsGame;
import com.dex.boxcasino.game.xidach.XiDachGame;
import com.dex.boxcasino.pvp.PvpGame;
import com.dex.boxcasino.pvp.dice.DiceGame;
import com.dex.boxcasino.pvp.rps.RpsGame;
import com.dex.boxcasino.pvp.tictactoe.TicTacToeGame;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** RTP of the shipped game configs (spec 16.6) and the Plinko ball paths. */
class GameMathTest {

    private static final long ROUNDS = 2_000_000;

    private static YamlConfiguration yml(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                GameMathTest.class.getResourceAsStream("/games/" + name + ".yml"), StandardCharsets.UTF_8));
    }

    private static <G extends Game> G load(G game, String name) {
        YamlConfiguration yml = yml(name);
        game.settings = new GameSettings(yml);
        assertNull(game.loadGame(yml));
        return game;
    }

    @Test
    void plinkoDefaultRtpIs95() {
        PlinkoGame plinko = load(new PlinkoGame(null), "plinko");
        assertEquals(0.95, plinko.rtp(), 1e-9);
        double measured = plinko.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(0.95, measured, 0.005);
    }

    @Test
    void plinkoPathsAvoidPegsAndEndInHopper() throws Exception {
        PlinkoGame plinko = load(new PlinkoGame(null), "plinko");
        Set<Integer> pegs = Set.of(13, 21, 23, 29, 31, 33, 37, 39, 41, 43, 46, 48, 50, 52);
        List<Integer> hoppers = List.of(45, 47, 49, 51, 53);
        var path = PlinkoGame.class.getDeclaredMethod("path", int.class, RandomGenerator.class);
        path.setAccessible(true);
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        for (int h = 0; h < 5; h++) {
            for (int i = 0; i < 200; i++) {
                @SuppressWarnings("unchecked")
                List<Integer> slots = (List<Integer>) path.invoke(plinko, h, random);
                assertEquals(4, slots.get(0));
                assertEquals(hoppers.get(h), slots.get(slots.size() - 1));
                for (int s = 1; s < slots.size(); s++) {
                    assertEquals(slots.get(s - 1) / 9 + 1, slots.get(s) / 9, "one row per step");
                    assertTrue(Math.abs(slots.get(s) % 9 - slots.get(s - 1) % 9) <= 1, "one column per step");
                    assertTrue(!pegs.contains(slots.get(s)), "ball hits a peg at " + slots.get(s));
                }
            }
        }
    }

    @Test
    void stairsMultipliersAndRtp() {
        StairsGame stairs = load(new StairsGame(null), "stairs");
        double[] expected = {1.14, 1.36, 1.64, 2.46, 3.69, 7.38};
        for (int r = 0; r < expected.length; r++) assertEquals(expected[r], stairs.multiplier(r + 1), 1e-9, "row " + (r + 1));
        double measured = stairs.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        assertTrue(measured <= 0.955 && measured >= 0.93, "measured " + measured);
    }

    @Test
    void rouletteEveryDifficultyIs95() {
        RouletteGame roulette = load(new RouletteGame(null), "roulette");
        assertEquals(0.95, roulette.rtp(), 1e-9);
        double measured = roulette.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(0.95, measured, 0.006);
    }

    @Test
    void racesRtpIs75() {
        RacesGame races = load(new RacesGame(null), "races");
        assertEquals(0.75, races.rtp(), 1e-9);
        assertEquals(0.75, races.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom")), 0.005);
    }

    @Test
    void pvpConfigsLoadAndTaxIsFivePercent() {
        for (PvpGame game : List.of(new DiceGame(null), new RpsGame(null), new TicTacToeGame(null))) {
            load(game, game.id());
            assertEquals(0.95, game.rtp(), 1e-9);
            assertEquals(1900, game.winnerPayout(1000));
            assertEquals(19, game.winnerPayout(10)); // 20 x 0.95
            assertEquals(2, game.winnerPayout(1));   // 2 x 0.95 = 1.9 -> 2
        }
    }

    @Test
    void xiDachRtpIsBelow100() {
        XiDachGame xd = load(new XiDachGame(null), "xidach");
        double measured = xd.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        System.out.println("Xi dach measured RTP (basic strategy): " + measured);
        assertTrue(measured < 1.0 && measured > 0.8, "measured " + measured);
    }

    @Test
    void minesMultipliers() {
        MinesGame mines = load(new MinesGame(null), "mines");
        // 0.97 x 28/25 = 1.0864 -> 1.08
        assertEquals(1.08, mines.multiplier(3, 1), 1e-9);
        // 0.97 x 28/14 = 1.94
        assertEquals(1.94, mines.multiplier(14, 1), 1e-9);
        assertEquals(0, mines.multiplier(3, 0), 1e-9);
        assertTrue(mines.multiplier(14, 14) > 1_000_000);
    }

    @Test
    void minesRtpCloseTo97() {
        MinesGame mines = load(new MinesGame(null), "mines");
        double measured = mines.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        // truncation to 2 decimals only lowers the payout slightly
        assertTrue(measured <= 0.975 && measured >= 0.94, "measured " + measured);
    }
}
