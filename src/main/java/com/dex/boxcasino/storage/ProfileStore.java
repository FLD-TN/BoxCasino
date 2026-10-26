package com.dex.boxcasino.storage;

import com.dex.boxcasino.economy.Currency;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Limits, daily loss, stats, house totals and the one-account-per-IP data. */
public final class ProfileStore {

    private final Database db;
    private String salt;

    public ProfileStore(Database db) {
        this.db = db;
    }

    public void init() throws SQLException {
        salt = db.salt();
    }

    /** SHA-256(salt + ip). The raw IP is never stored. */
    public String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + ip).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // --- profile ---

    public Profile load(UUID uuid, String day) throws SQLException {
        Profile p = new Profile(uuid);
        p.day = day;
        String id = uuid.toString();
        db.query("SELECT play_seconds, rest_until, excluded_until FROM limits WHERE uuid = ?", rs -> {
            p.playSeconds = rs.getLong(1);
            p.restUntil = rs.getLong(2);
            p.excludedUntil = rs.getLong(3);
            return null;
        }, id);
        db.query("SELECT currency, net FROM daily_loss WHERE uuid = ? AND day = ?", rs -> {
            p.dailyNet.put(Currency.valueOf(rs.getString(1)), rs.getLong(2));
            return null;
        }, id, day);
        db.query("SELECT currency, SUM(rounds), SUM(wagered), SUM(paid), SUM(won), SUM(lost), MAX(biggest_win)"
                + " FROM stats WHERE uuid = ? GROUP BY currency", rs -> {
            Profile.Totals t = p.totals.get(Currency.valueOf(rs.getString(1)));
            t.rounds = rs.getLong(2);
            t.wagered = rs.getLong(3);
            t.paid = rs.getLong(4);
            t.won = rs.getLong(5);
            t.lost = rs.getLong(6);
            t.biggestWin = rs.getLong(7);
            return null;
        }, id);
        return p;
    }

    public void saveLimits(UUID uuid, long playSeconds, long restUntil, long excludedUntil) throws SQLException {
        db.update("INSERT INTO limits (uuid, play_seconds, rest_until, excluded_until) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET play_seconds = excluded.play_seconds,"
                        + " rest_until = excluded.rest_until, excluded_until = excluded.excluded_until",
                uuid.toString(), playSeconds, restUntil, excludedUntil);
    }

    public void addDailyNet(UUID uuid, String day, Currency currency, long delta) throws SQLException {
        db.update("INSERT INTO daily_loss (uuid, day, currency, net) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT(uuid, day, currency) DO UPDATE SET net = net + excluded.net",
                uuid.toString(), day, currency.name(), delta);
    }

    public void purgeDailyBefore(String day) throws SQLException {
        db.update("DELETE FROM daily_loss WHERE day < ?", day);
    }

    public void addStats(UUID uuid, String game, Currency currency, long bet, long payout) throws SQLException {
        int win = payout > bet ? 1 : 0;
        int loss = payout < bet ? 1 : 0;
        int draw = payout == bet ? 1 : 0;
        long won = Math.max(0, payout - bet);
        long lost = Math.max(0, bet - payout);
        db.update("INSERT INTO stats (uuid, game, currency, rounds, wins, losses, draws, wagered, paid, won, lost, biggest_win)"
                        + " VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(uuid, game, currency) DO UPDATE SET rounds = rounds + 1, wins = wins + excluded.wins,"
                        + " losses = losses + excluded.losses, draws = draws + excluded.draws,"
                        + " wagered = wagered + excluded.wagered, paid = paid + excluded.paid, won = won + excluded.won,"
                        + " lost = lost + excluded.lost, biggest_win = MAX(biggest_win, excluded.biggest_win)",
                uuid.toString(), game, currency.name(), win, loss, draw, bet, payout, won, lost, won);
    }

    public void addHouse(String day, String game, Currency currency, long wagered, long paid, long tax) throws SQLException {
        db.update("INSERT INTO house_daily (day, game, currency, wagered, paid, tax) VALUES (?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(day, game, currency) DO UPDATE SET wagered = wagered + excluded.wagered,"
                        + " paid = paid + excluded.paid, tax = tax + excluded.tax",
                day, game, currency.name(), wagered, paid, tax);
    }

    public List<HouseRow> house(String fromDay, String game) throws SQLException {
        String sql = "SELECT game, currency, SUM(wagered), SUM(paid), SUM(tax) FROM house_daily WHERE day >= ?"
                + (game == null ? "" : " AND game = ?") + " GROUP BY game, currency ORDER BY game, currency";
        Database.RowMapper<HouseRow> m = rs -> new HouseRow(rs.getString(1), Currency.valueOf(rs.getString(2)),
                rs.getLong(3), rs.getLong(4), rs.getLong(5));
        return game == null ? db.query(sql, m, fromDay) : db.query(sql, m, fromDay, game);
    }

    public List<StatRow> stats(UUID uuid) throws SQLException {
        return db.query("SELECT game, currency, rounds, wins, losses, draws, wagered, paid, biggest_win FROM stats"
                        + " WHERE uuid = ? ORDER BY game, currency",
                rs -> new StatRow(rs.getString(1), Currency.valueOf(rs.getString(2)), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9)),
                uuid.toString());
    }

    // --- one casino account per IP (spec 4.2) ---

    public void recordIp(UUID uuid, String ipHash) throws SQLException {
        db.update("INSERT INTO player_ips (uuid, ip_hash, last_seen) VALUES (?, ?, ?)"
                        + " ON CONFLICT(uuid, ip_hash) DO UPDATE SET last_seen = excluded.last_seen",
                uuid.toString(), ipHash, System.currentTimeMillis());
    }

    /**
     * True when {@code uuid} may use the casino: every IP it was seen on has no main account or has it as main
     * (or it is an allowed exception). Claims the free IPs for it. Synchronous; called on first casino use.
     */
    public synchronized boolean claimMain(UUID uuid) throws SQLException {
        String id = uuid.toString();
        if (db.first("SELECT 1 FROM alt_allowed WHERE uuid = ?", rs -> 1, id) != null) return true;
        List<String> hashes = db.query("SELECT ip_hash FROM player_ips WHERE uuid = ?", rs -> rs.getString(1), id);
        for (String h : hashes) {
            String main = db.first("SELECT main_uuid FROM ip_main WHERE ip_hash = ?", rs -> rs.getString(1), h);
            if (main != null && !main.equals(id)) return false;
        }
        for (String h : hashes) db.update("INSERT OR IGNORE INTO ip_main (ip_hash, main_uuid) VALUES (?, ?)", h, id);
        return true;
    }

    public void allowAlt(UUID uuid) throws SQLException {
        db.update("INSERT OR IGNORE INTO alt_allowed (uuid) VALUES (?)", uuid.toString());
    }

    /** Frees every IP this player owns as main account and removes an exception. */
    public int resetAlt(UUID uuid) throws SQLException {
        db.update("DELETE FROM alt_allowed WHERE uuid = ?", uuid.toString());
        return db.update("DELETE FROM ip_main WHERE main_uuid = ?", uuid.toString());
    }

    /** Main accounts of the IPs this player was seen on (other than itself), and whether it is an exception. */
    public AltInfo altInfo(UUID uuid) throws SQLException {
        String id = uuid.toString();
        boolean allowed = db.first("SELECT 1 FROM alt_allowed WHERE uuid = ?", rs -> 1, id) != null;
        int ips = db.query("SELECT ip_hash FROM player_ips WHERE uuid = ?", rs -> 1, id).size();
        List<String> mains = db.query("SELECT DISTINCT m.main_uuid FROM player_ips p JOIN ip_main m ON m.ip_hash = p.ip_hash"
                + " WHERE p.uuid = ?", rs -> rs.getString(1), id);
        return new AltInfo(ips, allowed, mains);
    }

    public record AltInfo(int ipCount, boolean allowed, List<String> mainAccounts) {
    }

    public record HouseRow(String game, Currency currency, long wagered, long paid, long tax) {
    }

    public record StatRow(String game, Currency currency, long rounds, long wins, long losses, long draws, long wagered,
                          long paid, long biggestWin) {
    }
}
