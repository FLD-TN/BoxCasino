package com.dex.boxcasino.game.xidach;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Xì Dách (spec 7.1): tables of up to {@code slots.seats} players against the dealer, Vietnamese rules. Opening the
 * game seats the player at a table with a free seat (a new table when all are full). Every xì dách machine in the
 * world is a table of its own: chair n is seat n, and only the players on its chairs sit there.
 */
public final class XiDachGame extends Game {

    static final String[] RANKS = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};
    static final String[] SUITS = {"buba", "chervi", "pika", "kresti"};

    List<Integer> seatSlots = List.of();
    int dealerSlot;
    int hitSlot;
    int standSlot;
    int doubleSlot;
    int surrenderSlot;
    int timerSlot;
    int bettingSeconds;
    int turnSeconds;
    int dealerStepTicks;
    int resultSeconds;
    int minStand;
    int dealerMinStand;
    boolean bothBustDraw;
    double payXiBan;
    double payXiDach;
    double payNguLinh;
    double payWin;
    double paySurrender;
    String cardPattern;
    int cardCmdBase;
    int cardCmdSuitStep;
    ItemSpec closedCard;
    ItemSpec seatItem;
    ItemSpec seatFreeItem;
    ItemSpec dealerItem;
    ItemSpec victoryItem;
    ItemSpec defeatItem;
    ItemSpec drawItem;
    ItemSpec leftItem;
    ItemSpec buttonItem;
    final List<XiDachTable> tables = new ArrayList<>();
    /** Tables of the machines in the world, by machine id. */
    final java.util.Map<String, XiDachTable> machineTables = new java.util.HashMap<>();

    public XiDachGame(BoxCasinoPlugin plugin) {
        super(plugin, "xidach");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        seatSlots = settings.slots("seats");
        if (seatSlots.isEmpty()) return "slots.seats needs at least 1 slot";
        for (int s : seatSlots) if (s % 9 + 5 >= 9) return "seat " + s + " has no room for 5 cards";
        dealerSlot = settings.slot("dealer", 27);
        if (dealerSlot % 9 + 5 >= 9) return "dealer slot has no room for 5 cards";
        hitSlot = settings.slot("hit", 47);
        standSlot = settings.slot("stand", 48);
        timerSlot = settings.slot("timer", 49);
        doubleSlot = settings.slot("double", 50);
        surrenderSlot = settings.slot("surrender", 51);
        bettingSeconds = Math.max(5, yml.getInt("betting-seconds", 15));
        turnSeconds = Math.max(5, yml.getInt("turn-seconds", 15));
        dealerStepTicks = Math.max(5, yml.getInt("dealer-step-ticks", 20));
        resultSeconds = Math.max(2, yml.getInt("result-seconds", 5));
        minStand = yml.getInt("min-stand", 16);
        dealerMinStand = yml.getInt("dealer-min-stand", 16);
        bothBustDraw = yml.getBoolean("both-bust-draw", true);
        payXiBan = yml.getDouble("payouts.xi-ban", 3);
        payXiDach = yml.getDouble("payouts.xi-dach", 2.5);
        payNguLinh = yml.getDouble("payouts.ngu-linh", 3);
        payWin = yml.getDouble("payouts.win", 2);
        paySurrender = yml.getDouble("payouts.surrender", 0.5);
        cardPattern = yml.getString("cards.item", "iavegasgui:poker_card_{rank}_{suit}");
        cardCmdBase = yml.getInt("cards.cmd-base", 5606102);
        cardCmdSuitStep = yml.getInt("cards.cmd-suit-step", 1000);
        closedCard = settings.item("closed-card");
        seatItem = settings.item("seat");
        seatFreeItem = settings.item("seat-free");
        dealerItem = settings.item("dealer");
        victoryItem = settings.item("victory");
        defeatItem = settings.item("defeat");
        drawItem = settings.item("draw");
        leftItem = settings.item("left");
        buttonItem = settings.item("button");
        return null;
    }

    /** Look of one face-up card (ItemsAdder id from the pattern, fallback STICK + model id). */
    ItemSpec cardItem(Card c) {
        String id = cardPattern.replace("{rank}", RANKS[c.rank() - 1]).replace("{suit}", SUITS[c.suit()]);
        // pack order per suit: 2..10, J, Q, K, A
        int index = c.rank() == 1 ? 12 : c.rank() - 2;
        return new ItemSpec(id, Material.PAPER, cardCmdBase + c.suit() * cardCmdSuitStep + index);
    }

    /** Total multiplier paid to a winning player hand. */
    double winMultiplier(Hand.Rank rank) {
        return switch (rank) {
            case XI_BAN -> payXiBan;
            case XI_DACH -> payXiDach;
            case NGU_LINH -> payNguLinh;
            default -> payWin;
        };
    }

    @Override
    public double rtp() {
        return Double.NaN;
    }

    @Override
    public void open(Player player, boolean npc) {
        for (XiDachTable t : machineTables.values()) {
            if (t.seatOf(player.getUniqueId()) >= 0) {
                new XiDachView(plugin, player, this, t, npc).open();
                return;
            }
        }
        XiDachTable table = null;
        for (XiDachTable t : tables) if (t.seatOf(player.getUniqueId()) >= 0) table = t;
        if (table == null) {
            for (XiDachTable t : tables) {
                if (t.hasFreeSeat()) {
                    table = t;
                    break;
                }
            }
        }
        if (table == null) {
            table = new XiDachTable(plugin, this, plugin.machines().highestNumber(id()) + tables.size() + 1);
            tables.add(table);
        }
        new XiDachView(plugin, player, this, table, npc).open();
    }

    /** The table where the player sits (chat room), or null. */
    public com.dex.boxcasino.chat.ChatRoom roomOf(java.util.UUID player) {
        for (XiDachTable t : tables) if (t.seatOf(player) >= 0) return t;
        for (XiDachTable t : machineTables.values()) if (t.seatOf(player) >= 0) return t;
        return null;
    }

    /** True while the player has a bet in play on any table. */
    public boolean inHand(java.util.UUID player) {
        for (XiDachTable t : tables) if (t.inHand(player)) return true;
        for (XiDachTable t : machineTables.values()) if (t.inHand(player)) return true;
        return false;
    }

    /** Sat down on chair {@code seat} of a machine: its table, that seat. */
    public void openAtMachine(Player player, String machine, int seat) {
        XiDachTable table = machineTables.computeIfAbsent(machine,
                k -> new XiDachTable(plugin, this, Math.max(1, plugin.machines().number(k)), k));
        new XiDachView(plugin, player, this, table, false, seat).open();
    }

    /** Before sitting at a machine: an empty seat at a menu table is given up, a hand in play there refuses. */
    public boolean leaveOtherTables(Player player) {
        for (XiDachTable t : tables) {
            if (t.inHand(player.getUniqueId())) {
                plugin.messages().send(player, "games.xidach.busy-other");
                return false;
            }
        }
        for (XiDachTable t : List.copyOf(tables)) t.standUp(player.getUniqueId());
        return true;
    }

    /** Stood up from a chair of a machine: the seat is freed (after the hand when a bet is in play). */
    public void leftMachine(java.util.UUID player, String machine) {
        XiDachTable t = machineTables.get(machine);
        if (t != null) t.standUp(player);
    }

    @Override
    public void shutdown() {
        for (XiDachTable t : tables) t.shutdown();
        for (XiDachTable t : machineTables.values()) t.shutdown();
        tables.clear();
        machineTables.clear();
    }

    void removeIfEmpty(XiDachTable table) {
        if (table.machine == null && table.isEmpty() && tables.size() > 1) tables.remove(table);
    }

    /**
     * Basic strategy for the measurement: hit below 16 (forced), hit 16 when the dealer shows 7+, never double or
     * surrender. Dealer draws below {@code dealer-min-stand}.
     */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        Random r = new Random(random.nextLong());
        long bet = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) {
            List<Card> deck = Card.shuffledDeck(r);
            List<Card> p = new ArrayList<>(List.of(deck.remove(0), deck.remove(0)));
            List<Card> d = new ArrayList<>(List.of(deck.remove(0), deck.remove(0)));
            if (Hand.isXiBan(d) || Hand.isXiDach(d)) {
                Hand.Outcome o = Hand.compare(p, d, minStand, bothBustDraw);
                paid += o == Hand.Outcome.WIN ? Payouts.payout(bet, winMultiplier(Hand.rank(p, minStand)))
                        : o == Hand.Outcome.DRAW ? bet : 0;
                continue;
            }
            if (!Hand.isXiBan(p) && !Hand.isXiDach(p)) {
                while (p.size() < 5 && Hand.total(p) <= 21
                        && (Hand.total(p) < minStand || (Hand.total(p) == 16 && d.get(0).value() >= 7))) {
                    p.add(deck.remove(0));
                }
            }
            while (d.size() < 5 && Hand.total(d) < dealerMinStand) d.add(deck.remove(0));
            Hand.Outcome o = Hand.compare(p, d, minStand, bothBustDraw);
            paid += o == Hand.Outcome.WIN ? Payouts.payout(bet, winMultiplier(Hand.rank(p, minStand)))
                    : o == Hand.Outcome.DRAW ? bet : 0;
        }
        return paid / (rounds * (double) bet);
    }
}
