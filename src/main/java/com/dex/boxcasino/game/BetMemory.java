package com.dex.boxcasino.game;

import com.dex.boxcasino.economy.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Last currency and bet amount of each player (per game and currency), kept while the server runs. */
public final class BetMemory {

    private final Map<UUID, Entry> entries = new HashMap<>();

    public Entry of(UUID player) {
        return entries.computeIfAbsent(player, k -> new Entry());
    }

    public void forget(UUID player) {
        entries.remove(player);
    }

    public static final class Entry {
        public Currency currency = Currency.MONEY;
        public final Map<String, Long> amounts = new HashMap<>();
        /** Other per-game choices kept with the bet (Tên Lửa auto cash-out). */
        public final Map<String, Double> options = new HashMap<>();

        public long amount(String game, Currency c, long fallback) {
            return amounts.getOrDefault(game + ":" + c.name(), fallback);
        }

        public void setAmount(String game, Currency c, long value) {
            amounts.put(game + ":" + c.name(), value);
        }
    }
}
