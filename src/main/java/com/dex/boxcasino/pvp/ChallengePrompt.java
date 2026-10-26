package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The amount of a challenge created from the lobby is typed in chat: the next chat line of the player is taken (and
 * not shown to anyone). "huy" cancels; the question expires after 60 seconds.
 */
public final class ChallengePrompt implements Listener {

    private record Ask(PvpGame game, Currency currency, long until) {
    }

    private final BoxCasinoPlugin plugin;
    private final Map<UUID, Ask> asks = new ConcurrentHashMap<>();

    public ChallengePrompt(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    void ask(Player player, PvpGame game, Currency currency) {
        asks.put(player.getUniqueId(), new Ask(game, currency, System.currentTimeMillis() + 60_000L));
        var limits = game.settings().limits.get(currency);
        plugin.messages().send(player, "pvp.lobby.prompt", "game", game.displayName(), "currency", plugin.wallet().name(currency),
                "min", plugin.wallet().format(currency, limits.min()), "max", plugin.wallet().format(currency, limits.max()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        Ask ask = asks.get(player.getUniqueId());
        if (ask == null) return;
        if (ask.until() < System.currentTimeMillis()) {
            asks.remove(player.getUniqueId());
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim().toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTask(plugin, () -> answer(player, ask, text));
    }

    private void answer(Player player, Ask ask, String text) {
        if (text.equals("huy") || text.equals("huỷ") || text.equals("hủy") || text.equals("cancel")) {
            asks.remove(player.getUniqueId());
            plugin.messages().send(player, "pvp.lobby.prompt-cancelled");
            return;
        }
        long amount = PvpService.parseAmount(text);
        if (amount <= 0) {
            plugin.messages().send(player, "pvp.lobby.prompt-invalid");
            return;
        }
        asks.remove(player.getUniqueId());
        if (plugin.access().busyElsewhere(player, ask.game().id())) return;
        if (plugin.pvp().create(player, ask.game(), null, ask.currency(), amount, false)) ask.game().open(player, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        asks.remove(e.getPlayer().getUniqueId());
    }
}
