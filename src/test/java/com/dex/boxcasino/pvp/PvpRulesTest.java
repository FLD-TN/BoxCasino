package com.dex.boxcasino.pvp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PvpRulesTest {

    @Test
    void amounts() {
        assertEquals(10_000, PvpService.parseAmount("10000"));
        assertEquals(10_000, PvpService.parseAmount("10k"));
        assertEquals(2_000_000, PvpService.parseAmount("2M"));
        assertEquals(1_250, PvpService.parseAmount("1.250"));
        assertEquals(-1, PvpService.parseAmount("0"));
        assertEquals(-1, PvpService.parseAmount("-5"));
        assertEquals(-1, PvpService.parseAmount("abc"));
    }
}
