package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.BetService;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.storage.Database;
import com.dex.boxcasino.util.Payouts;
import java.sql.SQLException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * One pot per currency (spec 8). A share of every losing stake of a house game goes into the pot; every settled house
 * round with a big enough bet has a small chance to win the whole pot on top of its result. Main thread only.
 */
public final class JackpotService {

    private final BoxCasinoPlugin plugin;
    private final Database db;
    private final Map<Currency, Long> pots = new EnumMap<>(Currency.class);
    private boolean enabled;
    private double contributePercent;
    private double chancePercent;
    private final Map<Currency, Long> minBet = new EnumMap<>(Currency.class);

    public JackpotService(BoxCasinoPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("jackpot");
        enabled = c != null && c.getBoolean("enabled", true);
        contributePercent = c == null ? 1 : Math.max(0, c.getDouble("contribute-percent", 1));
        chancePercent = c == null ? 0.05 : Math.max(0, c.getDouble("chance-percent", 0.05));
        for (Currency cur : Currency.values()) {
            minBet.put(cur, c == null ? 0 : c.getLong("min-bet." + cur.name(), cur == Currency.MONEY ? 10_000 : 10));
            pots.put(cur, 0L);
        }
        try {
            db.query("SELECT currency, pot FROM jackpot", rs -> {
                pots.put(Currency.valueOf(rs.getString(1)), rs.getLong(2));
                return null;
            });
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the jackpot pots", ex);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public long pot(Currency currency) {
        return pots.getOrDefault(currency, 0L);
    }

    public double chancePercent() {
        return chancePercent;
    }

    public long minBet(Currency currency) {
        return minBet.getOrDefault(currency, 0L);
    }

    /** Called by {@link BetService#settle} for every finished bet. */
    public void onSettled(Bet bet, long payout) {
        if (!enabled) return;
        Game game = plugin.games().get(bet.game);
        if (game == null || !game.house()) return;
        if (payout < bet.amount()) {
            long add = Payouts.payout(bet.amount() - payout, contributePercent / 100.0);
            if (add > 0) {
                long v = pot(bet.currency) + add;
                pots.put(bet.currency, v);
                plugin.writes().setPot(bet.currency, v);
            }
        }
        long pot = pot(bet.currency);
        if (pot <= 0 || bet.amount() < minBet(bet.currency)) return;
        if (plugin.random().nextDouble() * 100.0 >= chancePercent) return;
        win(bet, game, pot);
    }

    private void win(Bet bet, Game game, long pot) {
        set(bet.currency, 0);
        plugin.bets().pay(bet.player, bet.currency, pot, BetService.reason(bet.game, "jackpot"));
        plugin.bets().recordHouse("jackpot", bet.currency, 0, pot);
        OfflinePlayer winner = Bukkit.getOfflinePlayer(bet.player);
        String name = winner.getName() == null ? "?" : winner.getName();
        String amount = plugin.wallet().format(bet.currency, pot);
        Title title = Title.title(plugin.messages().text("jackpot.title"),
                plugin.messages().text("jackpot.subtitle", "player", name, "amount", amount),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(p, "jackpot.broadcast", "player", name, "amount", amount, "game", game.displayName());
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        Player lucky = Bukkit.getPlayer(bet.player);
        if (lucky != null) plugin.music().result(lucky, com.dex.boxcasino.music.MusicService.Result.JACKPOT);
        plugin.getLogger().info("JACKPOT: " + name + " won " + pot + " " + bet.currency + " in " + bet.game);
    }

    /** Admin set / jackpot won: written right away (contributions are written with the next flush). */
    public void set(Currency currency, long value) {
        long v = Math.max(0, value);
        pots.put(currency, v);
        plugin.writes().setPot(currency, v);
        plugin.writes().flush();
    }
}
