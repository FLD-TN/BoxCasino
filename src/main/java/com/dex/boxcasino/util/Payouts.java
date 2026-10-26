package com.dex.boxcasino.util;

/** Payout arithmetic shared by every game (spec 3.4). */
public final class Payouts {

    private Payouts() {
    }

    /** {@code bet × multiplier}, fractional part ≥ 0.5 rounds up, otherwise down. Never negative. */
    public static long payout(long bet, double multiplier) {
        if (bet <= 0 || multiplier <= 0) return 0;
        return (long) Math.floor(bet * multiplier + 0.5);
    }

    /** Multiplier truncated (not rounded) to 2 decimals, so the shown value is never above the real one. */
    public static double truncate2(double multiplier) {
        return Math.floor(multiplier * 100.0 + 1e-9) / 100.0;
    }
}
