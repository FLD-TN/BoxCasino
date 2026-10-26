package com.dex.boxcasino.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Vietnamese number format: 1.250 / 1.000.000, multipliers ×1,19. */
public final class Format {

    private static final ThreadLocal<DecimalFormat> INT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0", symbols()));
    private static final ThreadLocal<DecimalFormat> DEC = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", symbols()));

    private Format() {
    }

    private static DecimalFormatSymbols symbols() {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return sym;
    }

    public static String number(long n) {
        return INT.get().format(n);
    }

    /** Up to 2 decimals: 1,19 / 3 / 12,88. */
    public static String decimal(double d) {
        return DEC.get().format(d);
    }

    public static String percent(double fraction) {
        return decimal(fraction * 100.0) + "%";
    }

    /** "1h 05m" style duration for limits. */
    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (d > 0) return d + " ngày " + h + " giờ";
        if (h > 0) return h + " giờ " + m + " phút";
        if (m > 0) return m + " phút " + s + " giây";
        return s + " giây";
    }
}
