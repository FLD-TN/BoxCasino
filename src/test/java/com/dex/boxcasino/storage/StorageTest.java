package com.dex.boxcasino.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxcasino.economy.Currency;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {

    @TempDir
    Path dir;
    private Database db;
    private Ledger ledger;
    private ProfileStore store;

    @BeforeEach
    void open() throws Exception {
        db = new Database(Logger.getLogger("test"));
        db.open(new File(dir.toFile(), "data.db"));
        ledger = new Ledger(db);
        store = new ProfileStore(db);
        store.init();
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void unfinishedBetsAreFoundForRecovery() throws Exception {
        UUID p = UUID.randomUUID();
        long r1 = ledger.createRound("mines", "{}");
        long open = ledger.createBet(r1, p, Currency.MONEY, 1000);
        long r2 = ledger.createRound("plinko", "{}");
        long settling = ledger.createBet(r2, p, Currency.GEM, 5);
        ledger.setState(settling, Ledger.SETTLING, 15);
        long r3 = ledger.createRound("plinko", "{}");
        long done = ledger.createBet(r3, p, Currency.GEM, 5);
        ledger.setState(done, Ledger.DONE, 2);

        List<Ledger.BetRow> rows = ledger.unfinished();
        assertEquals(2, rows.size());
        Ledger.BetRow s = rows.stream().filter(b -> b.id() == settling).findFirst().orElseThrow();
        assertEquals(15, s.payout());
        assertEquals("plinko", s.game());
        assertTrue(rows.stream().anyMatch(b -> b.id() == open && b.state().equals(Ledger.OPEN)));
    }

    @Test
    void failedWithdrawLeavesNoBet() throws Exception {
        long r = ledger.createRound("mines", null);
        long b = ledger.createBet(r, UUID.randomUUID(), Currency.MONEY, 1000);
        ledger.deleteBet(b);
        ledger.deleteRoundIfEmpty(r);
        assertTrue(ledger.unfinished().isEmpty());
    }

    @Test
    void pendingPayouts() throws Exception {
        UUID p = UUID.randomUUID();
        ledger.addPending(p, Currency.GEM, 42, "boxcasino:mines/payout");
        List<Ledger.Pending> list = ledger.pending(p);
        assertEquals(1, list.size());
        assertEquals(42, list.get(0).amount());
        ledger.deletePending(list.get(0).id());
        assertTrue(ledger.pending(p).isEmpty());
    }

    @Test
    void oneCasinoAccountPerIp() throws Exception {
        UUID main = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        String ip = store.hashIp("1.2.3.4");
        store.recordIp(main, ip);
        store.recordIp(alt, ip);

        assertTrue(store.claimMain(main));
        assertFalse(store.claimMain(alt));
        assertTrue(store.claimMain(main), "the main account keeps access");

        store.allowAlt(alt);
        assertTrue(store.claimMain(alt), "admin exception");

        store.resetAlt(alt);
        assertFalse(store.claimMain(alt), "exception removed again");
        store.resetAlt(main);
        assertTrue(store.claimMain(alt), "IP freed: next account becomes main");
        assertFalse(store.claimMain(main));
    }

    @Test
    void altOnSecondIpIsStillBlocked() throws Exception {
        UUID main = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        store.recordIp(main, store.hashIp("1.1.1.1"));
        store.recordIp(alt, store.hashIp("1.1.1.1"));
        assertTrue(store.claimMain(main));
        store.recordIp(alt, store.hashIp("9.9.9.9"));
        assertFalse(store.claimMain(alt), "a uuid seen on the main's IP stays blocked on any IP");
    }

    @Test
    void statsAndDailyLoss() throws Exception {
        UUID p = UUID.randomUUID();
        store.addStats(p, "mines", Currency.MONEY, 1000, 2500);
        store.addStats(p, "mines", Currency.MONEY, 1000, 0);
        store.addDailyNet(p, "2026-10-23", Currency.MONEY, 1000);
        store.addDailyNet(p, "2026-10-23", Currency.MONEY, -300);
        Profile profile = store.load(p, "2026-10-23");
        Profile.Totals t = profile.totals.get(Currency.MONEY);
        assertEquals(2, t.rounds);
        assertEquals(2000, t.wagered);
        assertEquals(2500, t.paid);
        assertEquals(1500, t.won);
        assertEquals(1000, t.lost);
        assertEquals(1500, t.biggestWin);
        assertEquals(700, profile.dailyNet.get(Currency.MONEY));
    }
}
