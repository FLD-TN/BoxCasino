package com.dex.boxcasino.game.xidach;

import java.util.List;

/**
 * Vietnamese xì dách rules (spec 7.1), pure functions.
 * Ace: 2 cards = 11 / 10 / 1, 3 cards = 10 / 1, 4-5 cards = 1. Total = best total ≤ 21, else the smallest.
 */
public final class Hand {

    public enum Rank {
        /** Over 21. */
        BUST,
        /** Under the minimum to stand (only possible after doubling). */
        NON,
        NORMAL,
        /** 5 cards, total ≤ 21. */
        NGU_LINH,
        /** A + 10/J/Q/K with 2 cards. */
        XI_DACH,
        /** A + A with 2 cards. */
        XI_BAN
    }

    public enum Outcome { WIN, DRAW, LOSE }

    private Hand() {
    }

    public static int total(List<Card> cards) {
        int base = 0;
        int aces = 0;
        for (Card c : cards) {
            if (c.isAce()) aces++;
            else base += c.value();
        }
        if (aces == 0) return base;
        int[] options = cards.size() <= 2 ? new int[] {11, 10, 1} : cards.size() == 3 ? new int[] {10, 1} : new int[] {1};
        int best = -1;
        int smallest = Integer.MAX_VALUE;
        // every ace picks its own value; with at most 4 aces this stays tiny
        int combos = (int) Math.pow(options.length, aces);
        for (int k = 0; k < combos; k++) {
            int sum = base;
            int x = k;
            for (int a = 0; a < aces; a++) {
                sum += options[x % options.length];
                x /= options.length;
            }
            if (sum <= 21) best = Math.max(best, sum);
            smallest = Math.min(smallest, sum);
        }
        return best >= 0 ? best : smallest;
    }

    public static boolean isXiBan(List<Card> cards) {
        return cards.size() == 2 && cards.get(0).isAce() && cards.get(1).isAce();
    }

    public static boolean isXiDach(List<Card> cards) {
        return cards.size() == 2 && ((cards.get(0).isAce() && cards.get(1).isTen())
                || (cards.get(1).isAce() && cards.get(0).isTen()));
    }

    /** Rank of a finished hand; {@code minStand} = lowest total a player may stand on (16). */
    public static Rank rank(List<Card> cards, int minStand) {
        if (isXiBan(cards)) return Rank.XI_BAN;
        if (isXiDach(cards)) return Rank.XI_DACH;
        int t = total(cards);
        if (t > 21) return Rank.BUST;
        if (cards.size() >= 5) return Rank.NGU_LINH;
        if (t < minStand) return Rank.NON;
        return Rank.NORMAL;
    }

    /** Player against dealer. */
    public static Outcome compare(List<Card> player, List<Card> dealer, int minStand, boolean bothBustDraw) {
        Rank p = rank(player, minStand);
        Rank d = rank(dealer, 0);
        if (p == Rank.BUST) return d == Rank.BUST && bothBustDraw ? Outcome.DRAW : Outcome.LOSE;
        if (p == Rank.NON) return Outcome.LOSE;
        if (d == Rank.BUST) return Outcome.WIN;
        if (p != d) return p.ordinal() > d.ordinal() ? Outcome.WIN : Outcome.LOSE;
        if (p == Rank.NORMAL) {
            int a = total(player);
            int b = total(dealer);
            return a == b ? Outcome.DRAW : a > b ? Outcome.WIN : Outcome.LOSE;
        }
        return Outcome.DRAW;
    }
}
