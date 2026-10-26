package com.dex.boxcasino.storage;

import com.dex.boxcasino.economy.Currency;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Casino data of one online player, cached while online. Main thread only. */
public final class Profile {

    public final UUID uuid;
    public long playSeconds;
    public long restUntil;
    public long excludedUntil;
    /** Day key ("2026-10-23") the {@link #dailyNet} values belong to. */
    public String day;
    public final Map<Currency, Long> dailyNet = new EnumMap<>(Currency.class);
    public final Map<Currency, Totals> totals = new EnumMap<>(Currency.class);
    /** Result of the main-account check of this session; null = not checked yet. */
    public Boolean altAllowed;
    public boolean limitsDirty;

    public Profile(UUID uuid) {
        this.uuid = uuid;
        for (Currency c : Currency.values()) {
            dailyNet.put(c, 0L);
            totals.put(c, new Totals());
        }
    }

    public static final class Totals {
        public long rounds;
        public long wagered;
        public long paid;
        public long won;
        public long lost;
        public long biggestWin;
    }
}
