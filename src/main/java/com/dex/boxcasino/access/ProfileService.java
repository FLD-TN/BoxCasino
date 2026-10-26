package com.dex.boxcasino.access;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.storage.Profile;
import com.dex.boxcasino.storage.ProfileStore;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Online players' {@link Profile}s: loaded async at join, saved async. Main thread only. */
public final class ProfileService {

    private final BoxCasinoPlugin plugin;
    private final ProfileStore store;
    private final Map<UUID, Profile> profiles = new HashMap<>();

    public ProfileService(BoxCasinoPlugin plugin, ProfileStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public String today() {
        return LocalDate.now(plugin.settings().zone).toString();
    }

    public Profile get(UUID uuid) {
        Profile p = profiles.get(uuid);
        if (p != null && !today().equals(p.day)) {
            p.day = today();
            for (Currency c : Currency.values()) p.dailyNet.put(c, 0L);
        }
        return p;
    }

    /** Join: record the IP hash and load the profile on the DB thread, then publish it on the main thread. */
    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        String day = today();
        plugin.database().async(() -> {
            if (ip != null) store.recordIp(uuid, store.hashIp(ip));
            Profile p = store.load(uuid, day);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (Bukkit.getPlayer(uuid) != null) profiles.put(uuid, p);
            });
        });
    }

    public void unload(UUID uuid) {
        Profile p = profiles.remove(uuid);
        if (p != null) saveLimits(p);
    }

    public void saveLimits(Profile p) {
        long play = p.playSeconds;
        long rest = p.restUntil;
        long excluded = p.excludedUntil;
        p.limitsDirty = false;
        plugin.database().async(() -> store.saveLimits(p.uuid, play, rest, excluded));
    }

    public void saveAllDirty() {
        for (Profile p : profiles.values()) {
            if (p.limitsDirty) saveLimits(p);
        }
    }

    /** Adds to the player's net loss of today (positive = lost money), in memory and on disk. */
    public void addDailyNet(UUID uuid, Currency currency, long delta) {
        if (delta == 0) return;
        String day = today();
        Profile p = get(uuid);
        if (p != null) p.dailyNet.merge(currency, delta, Long::sum);
        plugin.writes().addDailyNet(uuid, day, currency, delta);
    }

    public void addStats(UUID uuid, String game, Currency currency, long bet, long payout) {
        Profile p = get(uuid);
        if (p != null) {
            Profile.Totals t = p.totals.get(currency);
            t.rounds++;
            t.wagered += bet;
            t.paid += payout;
            t.won += Math.max(0, payout - bet);
            t.lost += Math.max(0, bet - payout);
            t.biggestWin = Math.max(t.biggestWin, payout - bet);
        }
        plugin.writes().addStats(uuid, game, currency, bet, payout);
    }

    /** Offline edit of limits (admin commands). */
    public void updateOffline(UUID uuid, java.util.function.Consumer<Profile> change) {
        Profile online = get(uuid);
        if (online != null) {
            change.accept(online);
            saveLimits(online);
            return;
        }
        String day = today();
        plugin.database().async(() -> {
            Profile p = store.load(uuid, day);
            change.accept(p);
            store.saveLimits(uuid, p.playSeconds, p.restUntil, p.excludedUntil);
        });
    }

    public void logError(String what, Exception ex) {
        plugin.getLogger().log(Level.SEVERE, what, ex);
    }
}
