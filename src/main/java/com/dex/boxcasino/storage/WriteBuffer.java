package com.dex.boxcasino.storage;

import com.dex.boxcasino.economy.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stats, house totals, daily loss and jackpot pots are summed in memory and written in one transaction every few
 * seconds: one short DB lock per flush instead of 3-4 queued writes per round fighting the main thread's ledger
 * writes for the connection. The ledger itself stays synchronous. Main thread only (the flush runs on the DB thread
 * with its own copy).
 */
public final class WriteBuffer {

    private record StatKey(UUID uuid, String game, Currency currency) {
    }

    private record DayKey(UUID uuid, String day, Currency currency) {
    }

    private record HouseKey(String day, String game, Currency currency) {
    }

    static final class Stat {
        long rounds, wins, losses, draws, wagered, paid, won, lost, biggestWin;
    }

    static final class House {
        long wagered, paid, tax;
    }

    private final Database db;
    private final ProfileStore store;
    private Map<StatKey, Stat> stats = new HashMap<>();
    private Map<DayKey, Long> daily = new HashMap<>();
    private Map<HouseKey, House> house = new HashMap<>();
    private Map<Currency, Long> pots = new EnumMap<>(Currency.class);

    public WriteBuffer(Database db, ProfileStore store) {
        this.db = db;
        this.store = store;
    }

    public void addStats(UUID uuid, String game, Currency currency, long bet, long payout) {
        Stat s = stats.computeIfAbsent(new StatKey(uuid, game, currency), k -> new Stat());
        s.rounds++;
        if (payout > bet) s.wins++;
        else if (payout < bet) s.losses++;
        else s.draws++;
        s.wagered += bet;
        s.paid += payout;
        s.won += Math.max(0, payout - bet);
        s.lost += Math.max(0, bet - payout);
        s.biggestWin = Math.max(s.biggestWin, Math.max(0, payout - bet));
    }

    public void addDailyNet(UUID uuid, String day, Currency currency, long delta) {
        if (delta != 0) daily.merge(new DayKey(uuid, day, currency), delta, Long::sum);
    }

    public void addHouse(String day, String game, Currency currency, long wagered, long paid, long tax) {
        House h = house.computeIfAbsent(new HouseKey(day, game, currency), k -> new House());
        h.wagered += wagered;
        h.paid += paid;
        h.tax += tax;
    }

    /** Latest value of a jackpot pot (only the last one is written). */
    public void setPot(Currency currency, long pot) {
        pots.put(currency, pot);
    }

    /** Queues everything collected so far as one transaction on the DB thread (keeps order with other DB tasks). */
    public void flush() {
        if (stats.isEmpty() && daily.isEmpty() && house.isEmpty() && pots.isEmpty()) return;
        Map<StatKey, Stat> s = stats;
        Map<DayKey, Long> d = daily;
        Map<HouseKey, House> h = house;
        Map<Currency, Long> p = pots;
        stats = new HashMap<>();
        daily = new HashMap<>();
        house = new HashMap<>();
        pots = new EnumMap<>(Currency.class);
        db.async(() -> db.transaction(() -> {
            for (var e : s.entrySet()) {
                StatKey k = e.getKey();
                Stat v = e.getValue();
                store.addStats(k.uuid(), k.game(), k.currency(), v.rounds, v.wins, v.losses, v.draws, v.wagered, v.paid,
                        v.won, v.lost, v.biggestWin);
            }
            for (var e : d.entrySet()) store.addDailyNet(e.getKey().uuid(), e.getKey().day(), e.getKey().currency(), e.getValue());
            for (var e : h.entrySet()) {
                HouseKey k = e.getKey();
                store.addHouse(k.day(), k.game(), k.currency(), e.getValue().wagered, e.getValue().paid, e.getValue().tax);
            }
            for (var e : p.entrySet()) store.setPot(e.getKey(), e.getValue());
        }));
    }
}
