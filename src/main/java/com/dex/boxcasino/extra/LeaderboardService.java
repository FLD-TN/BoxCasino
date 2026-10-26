package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;

/** Top 10 net winners per currency, recomputed on the DB thread every few minutes (spec 10). */
public final class LeaderboardService {

    public static final int SIZE = 10;

    public record Entry(UUID player, String name, long net) {
    }

    private final BoxCasinoPlugin plugin;
    private volatile Map<Currency, List<Entry>> top = new EnumMap<>(Currency.class);

    public LeaderboardService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
        for (Currency c : Currency.values()) top.put(c, List.of());
    }

    public List<Entry> top(Currency currency) {
        return top.getOrDefault(currency, List.of());
    }

    /** Queries on the DB thread, resolves names on the main thread (cached, never a web lookup). */
    public void refresh() {
        plugin.database().async(() -> {
            Map<Currency, List<UUID>> ids = new EnumMap<>(Currency.class);
            Map<Currency, List<Long>> nets = new EnumMap<>(Currency.class);
            for (Currency c : Currency.values()) {
                List<UUID> u = new ArrayList<>();
                List<Long> n = new ArrayList<>();
                plugin.database().query("SELECT uuid, SUM(paid) - SUM(wagered) AS net FROM stats WHERE currency = ?"
                        + " GROUP BY uuid HAVING net > 0 ORDER BY net DESC LIMIT " + SIZE, rs -> {
                    u.add(UUID.fromString(rs.getString(1)));
                    n.add(rs.getLong(2));
                    return null;
                }, c.name());
                ids.put(c, u);
                nets.put(c, n);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<Currency, List<Entry>> fresh = new EnumMap<>(Currency.class);
                for (Currency c : Currency.values()) {
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < ids.get(c).size(); i++) {
                        UUID id = ids.get(c).get(i);
                        String name = Bukkit.getOfflinePlayer(id).getName();
                        list.add(new Entry(id, name == null ? "?" : name, nets.get(c).get(i)));
                    }
                    fresh.put(c, List.copyOf(list));
                }
                top = fresh;
            });
        });
    }
}
