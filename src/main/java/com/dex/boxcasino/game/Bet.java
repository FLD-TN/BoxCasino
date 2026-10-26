package com.dex.boxcasino.game;

import com.dex.boxcasino.economy.Currency;
import java.util.UUID;

/** A stake held by the casino, alive until {@link BetService#settle} or {@link BetService#refund}. */
public final class Bet {

    public enum State { OPEN, SETTLED, REFUNDED }

    public final long roundId;
    public final long id;
    public final UUID player;
    public final String game;
    public final Currency currency;
    long amount;
    State state = State.OPEN;

    Bet(long roundId, long id, UUID player, String game, Currency currency, long amount) {
        this.roundId = roundId;
        this.id = id;
        this.player = player;
        this.game = game;
        this.currency = currency;
        this.amount = amount;
    }

    public long amount() {
        return amount;
    }

    public State state() {
        return state;
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }
}
