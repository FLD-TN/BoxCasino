package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Giờ Vàng: for a while every winning round against the house pays {@code percent}% more of its profit. Started by
 * {@code /casino giovang <phút> [%]} (console, CommandTimer, a shop...). Kept in memory only: a restart ends it.
 * Main thread only.
 */
public final class GoldenHourService {

    private final BoxCasinoPlugin plugin;
    private long endsAt;
    private double percent;
    private BukkitTask endTask;

    public GoldenHourService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean active() {
        return System.currentTimeMillis() < endsAt;
    }

    public double percent() {
        return percent;
    }

    public long secondsLeft() {
        return Math.max(0, (endsAt - System.currentTimeMillis()) / 1000);
    }

    /** Starts (or replaces) the golden hour and tells the whole server. */
    public void start(long minutes, double percent) {
        if (endTask != null) endTask.cancel();
        this.percent = percent;
        endsAt = System.currentTimeMillis() + minutes * 60_000L;
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::expire, minutes * 60 * 20);
        String[] ph = {"minutes", String.valueOf(minutes), "percent", Format.decimal(percent)};
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(p, "golden-hour.started", ph);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        }
    }

    /** Stopped by command: ends now and tells the server. False when it was not running. */
    public boolean stop() {
        if (!active()) return false;
        expire();
        return true;
    }

    private void expire() {
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        endsAt = 0;
        for (Player p : Bukkit.getOnlinePlayers()) plugin.messages().send(p, "golden-hour.ended");
    }

    /** Extra payout of a settled round: {@code percent}% of the profit of a winning house round, 0 otherwise. */
    public long bonus(Bet bet, long payout) {
        if (!active() || payout <= bet.amount()) return 0;
        Game game = plugin.games().get(bet.game);
        if (game == null || !game.house()) return 0;
        return Payouts.payout(payout - bet.amount(), percent / 100.0);
    }
}
