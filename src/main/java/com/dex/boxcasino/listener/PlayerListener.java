package com.dex.boxcasino.listener;

import com.dex.boxcasino.BoxCasinoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads profiles, records IP hashes and pays pending payouts (spec 3.5, 4.2). */
public final class PlayerListener implements Listener {

    /** Gems may not be loaded right after join: try again later. */
    private static final long[] PENDING_DELAYS = {100L, 600L};

    private final BoxCasinoPlugin plugin;

    public PlayerListener(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        plugin.profiles().load(player);
        for (long delay : PENDING_DELAYS) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.bets().payPending(player);
            }, delay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.pvp().quit(e.getPlayer().getUniqueId());
        plugin.profiles().unload(e.getPlayer().getUniqueId());
    }
}
