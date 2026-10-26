package com.dex.boxcasino.chat;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.game.races.RacesGame;
import com.dex.boxcasino.game.xidach.XiDachGame;
import com.dex.boxcasino.gui.CasinoView;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Tables a player belongs to (PvP match, xì dách table, the shared race): /casino tieptuc opens the table again, and
 * closing a table GUI while a round runs sends a clickable way back.
 */
public final class RoomService {

    private final BoxCasinoPlugin plugin;

    public RoomService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public ChatRoom roomOf(UUID player) {
        ChatRoom match = plugin.pvp().matchOf(player);
        if (match != null) return match;
        Game xd = plugin.games().get("xidach");
        if (xd instanceof XiDachGame g) {
            ChatRoom room = g.roomOf(player);
            if (room != null) return room;
        }
        Game races = plugin.games().get("races");
        if (races instanceof RacesGame g) {
            ChatRoom room = g.roomOf(player);
            if (room != null) return room;
        }
        if (plugin.games().get("crash") instanceof com.dex.boxcasino.game.crash.CrashGame g) {
            ChatRoom room = g.roomOf(player);
            if (room != null) return room;
        }
        if (plugin.games().get("pot") instanceof com.dex.boxcasino.game.pot.PotGame g) {
            ChatRoom room = g.roomOf(player);
            if (room != null) return room;
        }
        return null;
    }

    /** Sent when a member closes the table GUI while still in it: a clickable way back. */
    public void offerReturn(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) instanceof CasinoView) return;
            if (roomOf(player.getUniqueId()) == null) return;
            player.sendMessage(plugin.messages().text("prefix").append(plugin.messages().text("chat.left-room"))
                    .append(Component.text(" "))
                    .append(plugin.messages().text("chat.button-return").clickEvent(ClickEvent.runCommand("/casino tieptuc"))));
        }, 2L);
    }
}
