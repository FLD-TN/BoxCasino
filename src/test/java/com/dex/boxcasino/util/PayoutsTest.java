package com.dex.boxcasino.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PayoutsTest {

    @Test
    void halfRoundsUp() {
        assertEquals(8, Payouts.payout(3, 2.5));   // 7.5 -> 8
        assertEquals(7, Payouts.payout(749, 0.01)); // 7.49 -> 7
        assertEquals(8, Payouts.payout(751, 0.01)); // 7.51 -> 8
    }

    @Test
    void wholeAndZero() {
        assertEquals(3000, Payouts.payout(1000, 3.0));
        assertEquals(0, Payouts.payout(1000, 0));
        assertEquals(0, Payouts.payout(0, 5));
        assertEquals(400, Payouts.payout(1000, 0.4));
    }

    @Test
    void truncateNeverRoundsUp() {
        assertEquals(1.08, Payouts.truncate2(1.0864), 1e-12);
        assertEquals(1.19, Payouts.truncate2(1.1999), 1e-12);
        assertEquals(2.0, Payouts.truncate2(2.0), 1e-12);
    }

    @Test
    void format() {
        assertEquals("1.250", Format.number(1250));
        assertEquals("1.000.000", Format.number(1_000_000));
        assertEquals("1,19", Format.decimal(1.19));
        assertEquals("95%", Format.percent(0.95));
    }
}
