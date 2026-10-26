package com.dex.boxcasino.storage;

import com.dex.boxcasino.economy.Currency;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Rounds and bets (spec 6). Every state change of a bet is written synchronously before the money moves:
 * OPEN (before withdraw) → SETTLING (before payout) → DONE, or REFUNDING → REFUNDED.
 */
public final class Ledger {

    public static final String OPEN = "OPEN";
    public static final String SETTLING = "SETTLING";
    public static final String DONE = "DONE";
    public static final String REFUNDING = "REFUNDING";
    public static final String REFUNDED = "REFUNDED";

    private final Database db;

    public Ledger(Database db) {
        this.db = db;
    }

    public long createRound(String game, String outcome) throws SQLException {
        return db.insert("INSERT INTO rounds (game, created_at, outcome) VALUES (?, ?, ?)",
                game, System.currentTimeMillis(), outcome);
    }

    public void setOutcome(long roundId, String outcome) throws SQLException {
        db.update("UPDATE rounds SET outcome = ? WHERE id = ?", outcome, roundId);
    }

    public long createBet(long roundId, UUID player, Currency currency, long amount) throws SQLException {
        return db.insert("INSERT INTO bets (round_id, uuid, currency, amount, state, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                roundId, player.toString(), currency.name(), amount, OPEN, System.currentTimeMillis());
    }

    /** The withdraw failed: the bet never existed. */
    public void deleteBet(long betId) throws SQLException {
        db.update("DELETE FROM bets WHERE id = ? AND state = ?", betId, OPEN);
    }

    public void deleteRoundIfEmpty(long roundId) throws SQLException {
        db.update("DELETE FROM rounds WHERE id = ? AND NOT EXISTS (SELECT 1 FROM bets WHERE round_id = ?)", roundId, roundId);
    }

    /** Raises the stake of an OPEN bet (doubling); written before the second withdraw. */
    public void setAmount(long betId, long amount) throws SQLException {
        db.update("UPDATE bets SET amount = ? WHERE id = ? AND state = ?", amount, betId, OPEN);
    }

    public void setState(long betId, String state, long payout) throws SQLException {
        db.update("UPDATE bets SET state = ?, payout = ?, settled_at = ? WHERE id = ?",
                state, payout, System.currentTimeMillis(), betId);
    }

    public List<BetRow> unfinished() throws SQLException {
        return db.query("SELECT b.id, b.round_id, r.game, b.uuid, b.currency, b.amount, b.payout, b.state, b.created_at"
                        + " FROM bets b JOIN rounds r ON r.id = b.round_id WHERE b.state IN (?, ?, ?)",
                BetRow::of, OPEN, SETTLING, REFUNDING);
    }

    public BetRow bet(long betId) throws SQLException {
        return db.first("SELECT b.id, b.round_id, r.game, b.uuid, b.currency, b.amount, b.payout, b.state, b.created_at"
                + " FROM bets b JOIN rounds r ON r.id = b.round_id WHERE b.id = ?", BetRow::of, betId);
    }

    public List<BetRow> recent(UUID player, int limit, int offset) throws SQLException {
        return db.query("SELECT b.id, b.round_id, r.game, b.uuid, b.currency, b.amount, b.payout, b.state, b.created_at"
                        + " FROM bets b JOIN rounds r ON r.id = b.round_id WHERE b.uuid = ? ORDER BY b.id DESC LIMIT ? OFFSET ?",
                BetRow::of, player.toString(), limit, offset);
    }

    /** Deletes finished bets (and empty rounds) older than {@code cutoffMillis}. */
    public void purge(long cutoffMillis) throws SQLException {
        db.update("DELETE FROM bets WHERE created_at < ? AND state IN (?, ?)", cutoffMillis, DONE, REFUNDED);
        db.update("DELETE FROM rounds WHERE created_at < ? AND NOT EXISTS (SELECT 1 FROM bets WHERE bets.round_id = rounds.id)",
                cutoffMillis);
    }

    // --- pending payouts (spec 3.5) ---

    public void addPending(UUID player, Currency currency, long amount, String reason) throws SQLException {
        db.update("INSERT INTO pending_payouts (uuid, currency, amount, reason, created_at) VALUES (?, ?, ?, ?, ?)",
                player.toString(), currency.name(), amount, reason, System.currentTimeMillis());
    }

    public List<Pending> pending(UUID player) throws SQLException {
        return db.query("SELECT id, currency, amount, reason FROM pending_payouts WHERE uuid = ? ORDER BY id",
                rs -> new Pending(rs.getLong(1), Currency.valueOf(rs.getString(2)), rs.getLong(3), rs.getString(4)),
                player.toString());
    }

    public void deletePending(long id) throws SQLException {
        db.update("DELETE FROM pending_payouts WHERE id = ?", id);
    }

    public record Pending(long id, Currency currency, long amount, String reason) {
    }

    public record BetRow(long id, long roundId, String game, UUID player, Currency currency, long amount, long payout,
                         String state, long createdAt) {
        static BetRow of(java.sql.ResultSet rs) throws SQLException {
            return new BetRow(rs.getLong(1), rs.getLong(2), rs.getString(3), UUID.fromString(rs.getString(4)),
                    Currency.valueOf(rs.getString(5)), rs.getLong(6), rs.getLong(7), rs.getString(8), rs.getLong(9));
        }
    }
}
