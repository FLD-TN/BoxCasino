package com.dex.boxcasino.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxcasino.game.chest.ChestGame;
import com.dex.boxcasino.game.crash.CrashGame;
import com.dex.boxcasino.game.lottery.LotteryGame;
import com.dex.boxcasino.game.mines.MinesGame;
import com.dex.boxcasino.game.slots.SlotsGame;
import com.dex.boxcasino.game.pot.PotGame;
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
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** RTP of the shipped game configs (spec 16.6). */
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
    void stairsMultipliersAndRtp() {
        StairsGame stairs = load(new StairsGame(null), "stairs");
        double[] expected = {1.14, 1.36, 1.64, 2.46, 3.69, 7.38};
        for (int r = 0; r < expected.length; r++) assertEquals(expected[r], stairs.multiplier(r + 1), 1e-9, "row " + (r + 1));
        double measured = stairs.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        assertTrue(measured <= 0.955 && measured >= 0.93, "measured " + measured);
    }

    @Test
    void rouletteIsEuropeanSingleZero() {
        RouletteGame roulette = load(new RouletteGame(null), "roulette");
        assertEquals(36 / 37.0, roulette.rtp(), 1e-9);
        double measured = roulette.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(36 / 37.0, measured, 0.015);
        // every number once on the wheel, 18 red
        assertEquals(37, java.util.Arrays.stream(RouletteGame.WHEEL).distinct().count());
        assertEquals(18, RouletteGame.RED.size());
        // exact RTP of every spot over the 37 numbers
        java.util.List<String> spots = new java.util.ArrayList<>(RouletteGame.OUTSIDE);
        for (int n = 0; n <= 36; n++) spots.add(String.valueOf(n));
        for (String spot : spots) {
            long paid = 0;
            for (int n = 0; n <= 36; n++) paid += roulette.payout(java.util.Map.of(spot, 1000L), n);
            assertEquals(36_000, paid, spot);
        }
        // zero loses every outside spot
        for (String spot : RouletteGame.OUTSIDE) assertEquals(0, roulette.payout(java.util.Map.of(spot, 1000L), 0), spot);
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

    @Test
    void lotteryDefaultRtpIs95() {
        LotteryGame lottery = load(new LotteryGame(null), "lottery");
        assertEquals(0.95, lottery.rtp(), 0.002);
        assertEquals(lottery.rtp(), lottery.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom")), 0.01);
    }

    @Test
    void slotsDefaultRtpIs95() {
        SlotsGame slots = load(new SlotsGame(null), "slots");
        assertEquals(0.948, slots.rtp(), 0.001);
        assertEquals(slots.rtp(), slots.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom")), 0.02);
    }

    @Test
    void chestDifficultiesAreAbout95() {
        ChestGame chest = load(new ChestGame(null), "chest");
        for (var d : chest.difficulties().values()) assertEquals(0.95, chest.rtp(d), 0.01, d.key());
        // every difficulty in turn: the mean of the three
        double expected = chest.difficulties().values().stream().mapToDouble(chest::rtp).average().orElseThrow();
        assertEquals(expected, chest.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom")), 0.01);
    }

    @Test
    void crashPaysRtpForAnyTarget() {
        CrashGame crash = load(new CrashGame(null), "crash");
        assertEquals(0.96, crash.rtp(), 1e-9);
        assertEquals(0.96, crash.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom")), 0.01);
    }

    @Test
    void potReturnsAllButTheTax() {
        PotGame pot = load(new PotGame(null), "pot");
        assertEquals(0.95, pot.rtp(), 1e-9);
        assertEquals(0.95, pot.simulate(ROUNDS, RandomGenerator.of("L64X128MixRandom")), 0.001);
    }
}
