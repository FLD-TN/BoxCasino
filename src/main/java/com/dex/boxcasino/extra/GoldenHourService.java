package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Giờ Vàng: for a while every winning round against the house pays {@code percent}% more of its profit. Started by
 * {@code /casino giovang <phút> [%]} (console, CommandTimer, a shop...). While it runs a boss bar with the time left is
 * shown to every online player, anywhere on the server. Kept in memory only: a restart ends it. Main thread only.
 */
public final class GoldenHourService {

    private final BoxCasinoPlugin plugin;
    private long endsAt;
    private double percent;
    private BukkitTask endTask;
    private long totalMillis;
    private BossBar bar;
    private BukkitTask barTask;

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
        totalMillis = minutes * 60_000L;
        endsAt = System.currentTimeMillis() + totalMillis;
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::expire, minutes * 60 * 20);
        showBar();
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
        hideBar();
        for (Player p : Bukkit.getOnlinePlayers()) plugin.messages().send(p, "golden-hour.ended");
    }

    // --- boss bar (config.yml golden-hour.bossbar) ---

    private void showBar() {
        hideBar();
        if (!plugin.getConfig().getBoolean("golden-hour.bossbar.enabled", true)) return;
        bar = BossBar.bossBar(Component.empty(), 1f,
                parse(BossBar.Color.class, plugin.getConfig().getString("golden-hour.bossbar.color", "YELLOW"), BossBar.Color.YELLOW),
                parse(BossBar.Overlay.class, plugin.getConfig().getString("golden-hour.bossbar.style", "PROGRESS"),
                        BossBar.Overlay.PROGRESS));
        updateBar();
        // every second: new time left, and players who joined since then see it too
        barTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBar, 20L, 20L);
    }

    private void updateBar() {
        if (bar == null) return;
        if (!active()) {
            hideBar();
            return;
        }
        float progress = totalMillis <= 0 ? 0f : (float) (endsAt - System.currentTimeMillis()) / totalMillis;
        bar.progress(Math.max(0f, Math.min(1f, progress)));
        bar.name(plugin.messages().text("golden-hour.bossbar", "percent", Format.decimal(percent),
                "time", Format.duration(secondsLeft())));
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
    }

    private void hideBar() {
        if (barTask != null) {
            barTask.cancel();
            barTask = null;
        }
        if (bar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        bar = null;
    }

    /** Plugin disable: the bar must not stay on screens (the golden hour ends with the plugin). */
    public void shutdown() {
        hideBar();
        endsAt = 0;
    }

    private <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("golden-hour.bossbar: '" + name + "' is not a valid " + type.getSimpleName()
                    + ", using " + fallback.name());
            return fallback;
        }
    }

    /** Extra payout of a settled round: {@code percent}% of the profit of a winning house round, 0 otherwise. */
    public long bonus(Bet bet, long payout) {
        if (!active() || payout <= bet.amount()) return 0;
        Game game = plugin.games().get(bet.game);
        if (game == null || !game.house()) return 0;
        return Payouts.payout(payout - bet.amount(), percent / 100.0);
    }
}
