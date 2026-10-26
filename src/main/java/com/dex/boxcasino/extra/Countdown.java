package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Chat countdown of the last seconds (config.yml {@code countdown}): before a shared round starts (cards dealt, race,
 * take-off, pot roll, dice) and before a player's own turn runs out. One line per second, with a tick sound.
 */
public final class Countdown {

    private Countdown() {
    }

    /** {@code secondsLeft} of a countdown before a round starts: told to {@code players} in the last seconds. */
    public static void start(BoxCasinoPlugin plugin, Collection<UUID> players, int secondsLeft, String path, String... ph) {
        tell(plugin, players, secondsLeft, plugin.getConfig().getInt("countdown.start-seconds", 5), path, ph);
    }

    /** {@code secondsLeft} of a player's turn: told in the last seconds (only to players who have not moved). */
    public static void turn(BoxCasinoPlugin plugin, Collection<UUID> players, int secondsLeft, String path, String... ph) {
        tell(plugin, players, secondsLeft, plugin.getConfig().getInt("countdown.turn-seconds", 3), path, ph);
    }

    private static void tell(BoxCasinoPlugin plugin, Collection<UUID> players, int secondsLeft, int from, String path,
                             String... ph) {
        if (secondsLeft <= 0 || secondsLeft > from || players.isEmpty()) return;
        String[] all = Arrays.copyOf(ph, ph.length + 2);
        all[ph.length] = "seconds";
        all[ph.length + 1] = String.valueOf(secondsLeft);
        boolean sound = plugin.getConfig().getBoolean("countdown.sound", true);
        // higher pitch as the end comes closer
        float pitch = 1.0f + 0.15f * Math.max(0, from - secondsLeft);
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.messages().send(p, path, all);
            if (sound) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, Math.min(2f, pitch));
        }
    }
}
