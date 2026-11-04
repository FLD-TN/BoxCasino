package com.dex.boxcasino.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigFilesBlockTest {

    private static final List<String> FILE = List.of(
            "prefix: 'x'",          // 0
            "games:",               // 1
            "  stairs:",            // 2
            "    name: 'a'",        // 3
            "  roulette:",          // 4
            "    name: 'b'",        // 5
            "    difficulty:",      // 6
            "      easy: 'c'",      // 7
            "",                     // 8
            "  races:",             // 9
            "    name: 'd'",        // 10
            "pvp:",                 // 11
            "  roulette:");         // 12

    @Test
    void findsOnlyTheBlockUnderItsParent() {
        assertArrayEquals(new int[] {4, 8}, ConfigFiles.block(FILE, "games", "roulette"));
        assertArrayEquals(new int[] {9, 11}, ConfigFiles.block(FILE, "games", "races"));
        assertNull(ConfigFiles.block(FILE, "games", "crash"));
        assertArrayEquals(new int[] {12, 13}, ConfigFiles.block(FILE, "pvp", "roulette"));
    }

    @Test
    void jarMessagesHaveTheNewRouletteBlock() throws Exception {
        try (var in = ConfigFilesBlockTest.class.getClassLoader().getResourceAsStream("messages.yml")) {
            List<String> lines = List.of(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\r?\n", -1));
            int[] b = ConfigFiles.block(lines, "games", "roulette");
            for (int i = b[0]; i < b[1]; i++) {
                if (lines.get(i).startsWith("    difficulty:")) throw new AssertionError("old marker left in the jar block");
            }
        }
    }
}
