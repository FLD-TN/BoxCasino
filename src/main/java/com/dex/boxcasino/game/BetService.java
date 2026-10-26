package com.dex.boxcasino.game;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.storage.Ledger;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;

/**
 * The only place where casino money moves (spec 2, 6). Ledger row first, economy call second. Main thread only.
 */
public final class BetService {

    private final BoxCasinoPlugin plugin;
    private final Ledger ledger;
    /** Bet ids held by a live game (admins cannot refund these by hand). */
    private final Set<Long> live = new HashSet<>();

    public BetService(BoxCasinoPlugin plugin, Ledger ledger) {
        this.plugin = plugin;
        this.ledger = ledger;
    }

    /**
     * Takes a stake: ledger OPEN, then withdraw. Tells the player and returns {@code null} when it fails (not enough
     * money, daily loss limit, storage error).
     *
     * @param outcome JSON of the pre-generated result, stored on the round (may be null and set later)
     */
    public Bet place(Player player, String game, Currency currency, long amount, String outcome) {
        if (amount <= 0) return null;
        if (!plugin.access().canBet(player, currency, amount)) return null;
        long roundId;
        long betId;
        try {
            long[] ids = ledger.open(game, outcome, player.getUniqueId(), currency, amount);
            roundId = ids[0];
            betId = ids[1];
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ledger write failed, bet refused", ex);
            plugin.messages().send(player, "bet.storage-error");
            return null;
        }
        if (!plugin.wallet().withdraw(player, currency, amount, reason(game, "bet"))) {
            try {
                ledger.deleteBet(betId);
                ledger.deleteRoundIfEmpty(roundId);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete failed bet #" + betId
                        + " (it will be refunded at next start although nothing was taken!)", ex);
            }
            plugin.messages().send(player, "bet.not-enough", "currency", plugin.wallet().name(currency));
            return null;
        }
        Bet bet = new Bet(roundId, betId, player.getUniqueId(), game, currency, amount);
        live.add(betId);
        plugin.profiles().addDailyNet(bet.player, currency, amount);
        house(game, currency, amount, 0, 0);
        return bet;
    }

    /**
     * Adds {@code extra} to an open bet (xì dách x2): the new stake is written first, then withdrawn; on failure the
     * ledger is put back. Returns false when refused (player told).
     */
    public boolean raise(Player player, Bet bet, long extra) {
        if (!bet.isOpen() || extra <= 0) return false;
        if (!plugin.access().canBet(player, bet.currency, extra)) return false;
        long before = bet.amount;
        try {
            ledger.setAmount(bet.id, before + extra);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ledger write failed, raise refused", ex);
            plugin.messages().send(player, "bet.storage-error");
            return false;
        }
        if (!plugin.wallet().withdraw(player, bet.currency, extra, reason(bet.game, "bet"))) {
            try {
                ledger.setAmount(bet.id, before);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not restore stake of bet #" + bet.id
                        + " after a failed raise (a restart would refund " + (before + extra) + " instead of " + before + ")", ex);
            }
            plugin.messages().send(player, "bet.not-enough", "currency", plugin.wallet().name(bet.currency));
            return false;
        }
        bet.amount = before + extra;
        plugin.profiles().addDailyNet(bet.player, bet.currency, extra);
        house(bet.game, bet.currency, extra, 0, 0);
        return true;
    }

    public void setOutcome(Bet bet, String outcome) {
        try {
            ledger.setOutcome(bet.roundId, outcome);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not store outcome of round #" + bet.roundId, ex);
        }
    }

    /** Ends the bet with {@code payout} (0 = lost). Safe to call twice: the second call does nothing. */
    public void settle(Bet bet, long payout) {
        if (!bet.isOpen()) return;
        bet.state = Bet.State.SETTLED;
        live.remove(bet.id);
        // nothing to pay on a loss: straight to DONE (one write instead of two)
        if (payout > 0) {
            try {
                ledger.setState(bet.id, Ledger.SETTLING, payout);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Ledger SETTLING write failed for bet #" + bet.id + ", paying anyway", ex);
            }
        }
        pay(bet.player, bet.currency, payout, reason(bet.game, "payout"));
        markDone(bet.id, Ledger.DONE, payout);
        plugin.profiles().addDailyNet(bet.player, bet.currency, -payout);
        plugin.profiles().addStats(bet.player, bet.game, bet.currency, bet.amount, payout);
        house(bet.game, bet.currency, 0, payout, 0);
        plugin.bigWins().onSettled(bet, payout);
        plugin.music().onSettled(bet, payout);
        if (payout > bet.amount()) {
            org.bukkit.entity.Player winner = org.bukkit.Bukkit.getPlayer(bet.player);
            if (winner != null) plugin.winEffects().win(winner);
        }
        plugin.jackpot().onSettled(bet, payout);
    }

    /** House totals for money that moves outside a bet (jackpot wins). */
    public void recordHouse(String game, Currency currency, long wagered, long paid) {
        house(game, currency, wagered, paid, 0);
    }

    /** Gives the stake back. Safe to call twice. */
    public void refund(Bet bet) {
        if (!bet.isOpen()) return;
        bet.state = Bet.State.REFUNDED;
        live.remove(bet.id);
        try {
            ledger.setState(bet.id, Ledger.REFUNDING, bet.amount);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ledger REFUNDING write failed for bet #" + bet.id + ", refunding anyway", ex);
        }
        pay(bet.player, bet.currency, bet.amount, reason(bet.game, "refund"));
        markDone(bet.id, Ledger.REFUNDED, bet.amount);
        plugin.profiles().addDailyNet(bet.player, bet.currency, -bet.amount);
        house(bet.game, bet.currency, -bet.amount, 0, 0);
    }

    public boolean isLive(long betId) {
        return live.contains(betId);
    }

    /** Deposit now, or store a pending payout when that is not possible. */
    public void pay(UUID player, Currency currency, long amount, String reason) {
        if (amount <= 0) return;
        if (plugin.wallet().deposit(player, currency, amount, reason)) return;
        try {
            ledger.addPending(player, currency, amount, reason);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "LOST PAYOUT: could not store pending payout " + amount + " "
                    + currency + " for " + player + " (" + reason + ")", ex);
        }
    }

    /** Pays the pending payouts of a player who just joined (and is loaded in BoxCore for gems). */
    public void payPending(Player player) {
        List<Ledger.Pending> list;
        try {
            list = ledger.pending(player.getUniqueId());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Reading pending payouts failed", ex);
            return;
        }
        for (Ledger.Pending p : list) {
            if (!plugin.wallet().deposit(player.getUniqueId(), p.currency(), p.amount(), p.reason())) continue;
            try {
                ledger.deletePending(p.id());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Paid pending payout #" + p.id() + " but could not delete it", ex);
            }
            plugin.messages().send(player, "bet.pending-paid", "amount", plugin.wallet().format(p.currency(), p.amount()));
        }
    }

    /** Startup recovery (spec 6): refund OPEN and REFUNDING bets, pay SETTLING bets. */
    public void recover() {
        List<Ledger.BetRow> rows;
        try {
            rows = ledger.unfinished();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ledger recovery could not read unfinished bets", ex);
            return;
        }
        int refunded = 0;
        int paid = 0;
        for (Ledger.BetRow row : rows) {
            if (Ledger.SETTLING.equals(row.state())) {
                pay(row.player(), row.currency(), row.payout(), reason(row.game(), "payout"));
                markDone(row.id(), Ledger.DONE, row.payout());
                paid++;
            } else {
                pay(row.player(), row.currency(), row.amount(), reason(row.game(), "refund"));
                markDone(row.id(), Ledger.REFUNDED, row.amount());
                plugin.profiles().addDailyNet(row.player(), row.currency(), -row.amount());
                refunded++;
            }
        }
        if (!rows.isEmpty()) {
            plugin.getLogger().warning("Ledger recovery: " + refunded + " unfinished bet(s) refunded, "
                    + paid + " decided payout(s) paid");
        }
    }

    /** Admin refund of a bet that no live game holds. */
    public boolean adminRefund(long betId) throws SQLException {
        if (live.contains(betId)) return false;
        Ledger.BetRow row = ledger.bet(betId);
        if (row == null || !(Ledger.OPEN.equals(row.state()) || Ledger.REFUNDING.equals(row.state()))) return false;
        pay(row.player(), row.currency(), row.amount(), reason(row.game(), "refund"));
        markDone(row.id(), Ledger.REFUNDED, row.amount());
        return true;
    }

    private void markDone(long betId, String state, long payout) {
        try {
            ledger.setState(betId, state, payout);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ledger " + state + " write failed for bet #" + betId
                    + " (it may be paid again at next start)", ex);
        }
    }

    private void house(String game, Currency currency, long wagered, long paid, long tax) {
        plugin.writes().addHouse(plugin.profiles().today(), game, currency, wagered, paid, tax);
    }

    public static String reason(String game, String what) {
        return "boxcasino:" + game + "/" + what;
    }
}
