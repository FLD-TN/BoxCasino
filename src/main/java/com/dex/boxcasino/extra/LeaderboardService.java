package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Top 10 net winners and top 10 net losers per currency, recomputed on the DB thread every few minutes (spec 10).
 * {@link Entry#net()} is the amount won or lost, always positive.
 */
public final class LeaderboardService {

    public static final int SIZE = 10;

    public enum Board {
        /** Most money won overall (received − bet > 0). */
        WINNERS,
        /** Most money lost overall (bet − received > 0). */
        LOSERS
    }

    public record Entry(UUID player, String name, long net) {
    }

    private record Key(Board board, Currency currency) {
    }

    private final BoxCasinoPlugin plugin;
    private volatile Map<Key, List<Entry>> top = Map.of();

    public LeaderboardService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Entry> top(Currency currency) {
        return top(Board.WINNERS, currency);
    }

    public List<Entry> top(Board board, Currency currency) {
        return top.getOrDefault(new Key(board, currency), List.of());
    }

    /** Queries on the DB thread, resolves names on the main thread (cached, never a web lookup). */
    public void refresh() {
        plugin.database().async(() -> {
            Map<Key, List<UUID>> ids = new HashMap<>();
            Map<Key, List<Long>> nets = new HashMap<>();
            for (Board board : Board.values()) {
                String sql = board == Board.WINNERS
                        ? "SELECT uuid, SUM(paid) - SUM(wagered) AS net FROM stats WHERE currency = ?"
                                + " GROUP BY uuid HAVING net > 0 ORDER BY net DESC LIMIT " + SIZE
                        : "SELECT uuid, SUM(wagered) - SUM(paid) AS net FROM stats WHERE currency = ?"
                                + " GROUP BY uuid HAVING net > 0 ORDER BY net DESC LIMIT " + SIZE;
                for (Currency c : Currency.values()) {
                    List<UUID> u = new ArrayList<>();
                    List<Long> n = new ArrayList<>();
                    plugin.database().query(sql, rs -> {
                        u.add(UUID.fromString(rs.getString(1)));
                        n.add(rs.getLong(2));
                        return null;
                    }, c.name());
                    ids.put(new Key(board, c), u);
                    nets.put(new Key(board, c), n);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<Key, List<Entry>> fresh = new HashMap<>();
                for (Key k : ids.keySet()) {
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < ids.get(k).size(); i++) {
                        UUID id = ids.get(k).get(i);
                        String name = Bukkit.getOfflinePlayer(id).getName();
                        list.add(new Entry(id, name == null ? "?" : name, nets.get(k).get(i)));
                    }
                    fresh.put(k, List.copyOf(list));
                }
                top = Map.copyOf(fresh);
            });
        });
    }
}
