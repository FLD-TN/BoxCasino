package com.dex.boxcasino.access;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.config.Settings;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.hook.WorldGuardHook;
import com.dex.boxcasino.storage.Profile;
import com.dex.boxcasino.util.Format;
import java.sql.SQLException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Who may use the casino, where, and for how long (spec 4). Main thread only. */
public final class AccessService {

    public static final String BYPASS_LIMITS = "boxcasino.bypass.limits";
    public static final String BYPASS_IP = "boxcasino.bypass.ip";

    private final BoxCasinoPlugin plugin;
    private boolean warnedNoWorldGuard;

    public AccessService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Settings settings() {
        return plugin.settings();
    }

    /**
     * Checks everything needed to open a casino GUI or start a round, telling the player why not.
     *
     * @param casinoOnly the game needs the casino region
     * @param npc opened by the console (NPC); may skip the region check
     */
    /**
     * One game at a time: a player sitting at a machine, in a running 1v1 or with cards on a xì dách table can only
     * open that game ({@code game} null = the casino menu). An open challenge (Xúc Xắc / Oẳn Tù Tì) blocks every
     * other game until it is cancelled. Tells the player and returns true when refused.
     */
    public boolean busyElsewhere(Player player, String game) {
        var seat = plugin.seats().of(player.getUniqueId());
        if (seat != null && !seat.game().equals(game)) {
            msg().send(player, "access.busy-seated");
            return true;
        }
        var match = plugin.pvp().matchOf(player.getUniqueId());
        if (match != null && !match.finished() && !match.game().id().equals(game)) {
            msg().send(player, "access.busy-match");
            return true;
        }
        var challenge = game == null ? null : plugin.pvp().challengeOf(player.getUniqueId());
        if (challenge != null && !challenge.game().id().equals(game)) {
            msg().send(player, "access.busy-challenge", "game", challenge.game().displayName(),
                    "id", String.valueOf(challenge.id()));
            return true;
        }
        if (!"xidach".equals(game) && plugin.games().get("xidach") instanceof com.dex.boxcasino.game.xidach.XiDachGame xd
                && xd.inHand(player.getUniqueId())) {
            msg().send(player, "access.busy-match");
            return true;
        }
        return false;
    }

    public boolean canPlay(Player player, boolean casinoOnly, boolean npc) {
        Profile p = plugin.profiles().get(player.getUniqueId());
        if (p == null || !plugin.wallet().ready()) {
            msg().send(player, "access.not-loaded");
            return false;
        }
        if (casinoOnly && !(npc && settings().npcIgnoresRegion) && !inCasino(player)) {
            msg().send(player, "access.not-in-casino");
            return false;
        }
        long now = System.currentTimeMillis();
        if (p.excludedUntil > now && !player.hasPermission(BYPASS_LIMITS)) {
            msg().send(player, "access.excluded", "time", Format.duration((p.excludedUntil - now) / 1000));
            return false;
        }
        if (!player.hasPermission(BYPASS_IP) && !mainAccount(player, p)) {
            msg().send(player, "access.alt-blocked");
            return false;
        }
        if (settings().playLimitEnabled && p.restUntil > now && !player.hasPermission(BYPASS_LIMITS)) {
            msg().send(player, "access.resting", "time", Format.duration((p.restUntil - now) / 1000));
            return false;
        }
        return true;
    }

    /** Daily profit limit: once today's net profit reaches it, no new bet until midnight. Rounds in play still pay. */
    public boolean canBet(Player player, Currency currency, long amount) {
        if (player.hasPermission(BYPASS_LIMITS)) return true;
        long limit = settings().dailyProfitLimit.getOrDefault(currency, 0L);
        if (limit <= 0) return true;
        Profile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return false;
        if (profitToday(p, currency) >= limit) {
            msg().send(player, "access.daily-profit", "limit", plugin.wallet().format(currency, limit));
            return false;
        }
        return true;
    }

    /** Profit still allowed today, or -1 when the limit is off. */
    public long profitLeft(Player player, Currency currency) {
        long limit = settings().dailyProfitLimit.getOrDefault(currency, 0L);
        if (limit <= 0) return -1;
        Profile p = plugin.profiles().get(player.getUniqueId());
        return p == null ? 0 : Math.max(0, limit - profitToday(p, currency));
    }

    /** {@code dailyNet} counts money lost, so profit is its negative. */
    private static long profitToday(Profile p, Currency currency) {
        return -p.dailyNet.getOrDefault(currency, 0L);
    }

    /**
     * Plain chat line to every other player inside the casino region, seated players included (shared tables
     * starting); the player who caused it does not get it.
     */
    public void announceInCasino(Player source, String path, String... placeholders) {
        if (!plugin.getConfig().getBoolean("broadcast.tables", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(source) && inCasino(p)) msg().send(p, path, placeholders);
        }
    }

    /** Seconds between two announcements of the same table (bet / cancel / bet again must not flood the chat). */
    public long announceCooldownMillis() {
        return Math.max(0, plugin.getConfig().getLong("broadcast.table-cooldown", 30)) * 1000L;
    }

    public boolean inCasino(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            if (!warnedNoWorldGuard) {
                warnedNoWorldGuard = true;
                plugin.getLogger().warning("WorldGuard is not installed: casino region checks are skipped");
            }
            return true;
        }
        return WorldGuardHook.inAnyRegion(player.getLocation(), settings().regions);
    }

    private boolean mainAccount(Player player, Profile p) {
        if (p.altAllowed != null) return p.altAllowed;
        try {
            String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
            if (ip != null) plugin.profileStore().recordIp(player.getUniqueId(), plugin.profileStore().hashIp(ip));
            p.altAllowed = plugin.profileStore().claimMain(player.getUniqueId());
        } catch (SQLException ex) {
            plugin.profiles().logError("Main account check failed for " + player.getName(), ex);
            return false;
        }
        return p.altAllowed;
    }

    /** Forget cached main-account results (after admin alt commands). */
    public void resetAltCache(Player player) {
        Profile p = plugin.profiles().get(player.getUniqueId());
        if (p != null) p.altAllowed = null;
    }

    // --- play-time limit (spec 4.3), called every second ---

    public void tick() {
        if (!settings().playLimitEnabled) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof CasinoView view)) continue;
            if (player.hasPermission(BYPASS_LIMITS)) continue;
            Profile p = plugin.profiles().get(player.getUniqueId());
            if (p == null) continue;
            if (p.restUntil > now) {
                if (!view.busy()) {
                    player.closeInventory();
                    msg().send(player, "access.resting", "time", Format.duration((p.restUntil - now) / 1000));
                }
                continue;
            }
            p.playSeconds++;
            p.limitsDirty = true;
            if (p.playSeconds >= settings().playSeconds) {
                p.playSeconds = 0;
                p.restUntil = now + settings().restSeconds * 1000;
                plugin.profiles().saveLimits(p);
                msg().send(player, "access.limit-reached", "time", Format.duration(settings().restSeconds));
                if (!view.busy()) player.closeInventory();
            }
        }
    }

    /** Seconds of play left before the rest period, or -1 when the limit is off. */
    public long playLeft(Player player) {
        if (!settings().playLimitEnabled) return -1;
        Profile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return 0;
        if (p.restUntil > System.currentTimeMillis()) return 0;
        return Math.max(0, settings().playSeconds - p.playSeconds);
    }

    public long restLeft(Player player) {
        Profile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return 0;
        return Math.max(0, (p.restUntil - System.currentTimeMillis()) / 1000);
    }
}
