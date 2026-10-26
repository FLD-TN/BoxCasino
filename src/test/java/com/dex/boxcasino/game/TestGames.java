package com.dex.boxcasino.game;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads a game from its shipped {@code games/<name>.yml} without a running plugin (for tests in the game packages). */
public final class TestGames {

    private TestGames() {
    }

    public static YamlConfiguration yml(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                TestGames.class.getResourceAsStream("/games/" + name + ".yml"), StandardCharsets.UTF_8));
    }

    public static <G extends Game> G load(G game, String name) {
        YamlConfiguration yml = yml(name);
        game.settings = new GameSettings(yml);
        assertNull(game.loadGame(yml));
        return game;
    }
}
