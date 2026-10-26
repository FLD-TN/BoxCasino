package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Format;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Server-wide message for big wins (spec 9): profit at least {@code min-profit}, or a high multiplier with at least
 * 10% of that profit. Discord is not wired yet (the config switch is kept off).
 */
public final class BigWinService {

    private final BoxCasinoPlugin plugin;
    private boolean enabled;
    private final Map<Currency, Long> minProfit = new EnumMap<>(Currency.class);
    private double minMultiplier;

    public BigWinService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("broadcast");
        enabled = c == null || c.getBoolean("enabled", true);
        for (Currency cur : Currency.values()) {
            minProfit.put(cur, c == null ? 0 : c.getLong("min-profit." + cur.name(), cur == Currency.MONEY ? 1_000_000 : 1_000));
        }
        minMultiplier = c == null ? 10 : c.getDouble("min-multiplier", 10);
    }

    public void onSettled(Bet bet, long payout) {
        if (!enabled) return;
        long profit = payout - bet.amount();
        long min = minProfit.getOrDefault(bet.currency, Long.MAX_VALUE);
        if (profit <= 0 || min <= 0) return;
        double multiplier = (double) payout / bet.amount();
        boolean big = profit >= min || (multiplier >= minMultiplier && profit >= min / 10);
        if (!big) return;
        Game game = plugin.games().get(bet.game);
        OfflinePlayer who = Bukkit.getOfflinePlayer(bet.player);
        String[] ph = {
                "player", who.getName() == null ? "?" : who.getName(),
                "game", game == null ? bet.game : game.displayName(),
                "payout", plugin.wallet().format(bet.currency, payout),
                "profit", plugin.wallet().format(bet.currency, profit),
                "multiplier", Format.decimal(multiplier)
        };
        for (Player p : Bukkit.getOnlinePlayers()) plugin.messages().send(p, "broadcast.big-win", ph);
    }
}
