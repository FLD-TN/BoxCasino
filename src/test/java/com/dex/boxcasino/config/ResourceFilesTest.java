package com.dex.boxcasino.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every shipped file parses and carries its config-version, so old server copies get upgraded (ConfigFiles). */
class ResourceFilesTest {

    private static final List<String> FILES = List.of("config.yml", "messages.yml", "games/mines.yml", "games/plinko.yml",
            "games/stairs.yml", "games/roulette.yml", "games/races.yml", "games/xidach.yml", "games/dice.yml",
            "games/rps.yml", "games/tictactoe.yml", "games/lottery.yml", "games/slots.yml", "games/chest.yml", "games/crash.yml", "games/pot.yml");

    @Test
    void allFilesParseWithVersion() throws Exception {
        for (String f : FILES) {
            try (InputStream in = getClass().getResourceAsStream("/" + f)) {
                assertNotNull(in, f);
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                int expected = f.equals("config.yml") ? 2 : f.equals("messages.yml") ? 9
                        : Set.of("games/lottery.yml", "games/plinko.yml", "games/slots.yml", "games/xidach.yml",
                                "games/tictactoe.yml").contains(f) ? 5 : 4;
                assertEquals(expected, yml.getInt("config-version"), f);
                assertFalse(yml.getKeys(false).isEmpty(), f);
            }
        }
    }

    @Test
    void menuGamesAreOneSlotApart() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            var games = yml.getConfigurationSection("menu.games");
            assertNotNull(games);
            assertEquals(14, games.getKeys(false).size());
            for (String id : games.getKeys(false)) {
                int slot = games.getInt(id + ".slot");
                for (String other : games.getKeys(false)) {
                    if (other.equals(id)) continue;
                    int o = games.getInt(other + ".slot");
                    boolean neighbours = o / 9 == slot / 9 && Math.abs(o - slot) == 1;
                    assertFalse(neighbours, id + " and " + other + " touch");
                }
            }
        }
    }
}
