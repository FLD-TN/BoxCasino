package com.dex.boxcasino.economy;

import java.util.Locale;

public enum Currency {
    MONEY,
    GEM;

    public Currency other() {
        return this == MONEY ? GEM : MONEY;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "tien"/"money" → MONEY, "gem" → GEM, otherwise null. */
    public static Currency parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "tien", "tiền", "money", "$" -> MONEY;
            case "gem", "gems" -> GEM;
            default -> null;
        };
    }
}
