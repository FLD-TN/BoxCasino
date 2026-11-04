package com.dex.boxcasino.game.roulette;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Roulette, European single zero: 37 numbers, each equally likely, drawn before the wheel animation starts. A round
 * holds any number of bets (spots): one number x36, red / black / even / odd / 1-18 / 19-36 x2, dozens x3. Zero loses
 * every outside bet. The ring of 16 cells shows a window of the wheel that turns until the drawn number is under the
 * pointer cell.
 */
public final class RouletteGame extends Game {

    /** Number order on a European wheel, clockwise from zero. */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
            20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** Outside spots in board order. Numbers are the spots "0" ... "36". */
    public static final List<String> OUTSIDE = List.of("red", "black", "even", "odd", "low", "high", "dozen1", "dozen2", "dozen3");

    /** Ring slots in running order. */
    List<Integer> ring = List.of();
    /** Ring index of the pointer cell: the number under it wins. */
    int pointer;
    int spotSlot;
    int leverSlot;
    List<Integer> historySlots = List.of();
    int boardSlot;
    int summarySlot;
    int clearSlot;
    int repeatSlot;
    int laps;
    int resetTicks;
    double straightPay;
    double evenPay;
    double dozenPay;
    final Map<Currency, Long> maxStraight = new EnumMap<>(Currency.class);
    final ItemSpec[] numberItems = new ItemSpec[37];
    final Map<String, ItemSpec> outsideItems = new LinkedHashMap<>();
    ItemSpec spotItem;
    ItemSpec clearItem;
    ItemSpec repeatItem;
    ItemSpec undoItem;
    ItemSpec backItem;
    ItemSpec spinItem;
    String boardTitle;

    public RouletteGame(BoxCasinoPlugin plugin) {
        super(plugin, "roulette");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        ring = settings.slots("ring");
        if (ring.size() < 4) return "slots.ring needs at least 4 slots";
        pointer = yml.getInt("win-index", 0);
        if (pointer < 0 || pointer >= ring.size()) return "win-index outside the ring";
        spotSlot = settings.slot("spot", 11);
        leverSlot = settings.slot("lever", 20);
        historySlots = settings.slots("history");
        boardSlot = settings.slot("board", 16);
        summarySlot = settings.slot("summary", 25);
        clearSlot = settings.slot("clear", 34);
        repeatSlot = settings.slot("repeat", 43);
        // "laps" of the old Vòng Quay was 3 rounds of a 16-cell ring; one turn of the 37-number wheel is as long
        laps = Math.max(0, yml.getInt("spin-laps", 1));
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 40));
        straightPay = yml.getDouble("payouts.straight", 36);
        evenPay = yml.getDouble("payouts.even", 2);
        dozenPay = yml.getDouble("payouts.dozen", 3);
        if (straightPay <= 0 || evenPay <= 0 || dozenPay <= 0) return "payouts must be > 0";
        for (Currency c : Currency.values()) {
            maxStraight.put(c, Math.max(0, yml.getLong("max-straight." + c.name(), c == Currency.MONEY ? 100_000 : 100)));
        }
        for (int n = 0; n <= 36; n++) {
            ItemSpec vegas = n == 0 ? new ItemSpec("iavegasgui:doubleroulette_green_rolling_block", Material.STICK, 5626107)
                    : RED.contains(n) ? new ItemSpec("iavegasgui:doubleroulette_red_rolling_block", Material.STICK, 5626106)
                    : new ItemSpec("iavegasgui:doubleroulette_black_rolling_block", Material.STICK, 5626105);
            ItemSpec fallback = new ItemSpec("boxcasino:roulette_" + n, vegas.material(), vegas.customModelData());
            numberItems[n] = ItemSpec.of(yml.get("items.numbers." + n), fallback);
        }
        Map<String, ItemSpec> outsideDefaults = Map.of(
                "red", new ItemSpec("iavegasgui:doubleroulette_red_rolling_block", Material.STICK, 5626106),
                "black", new ItemSpec("iavegasgui:doubleroulette_black_rolling_block", Material.STICK, 5626105),
                "even", new ItemSpec("", Material.LIGHT_BLUE_DYE, 0),
                "odd", new ItemSpec("", Material.ORANGE_DYE, 0),
                "low", new ItemSpec("", Material.LIME_DYE, 0),
                "high", new ItemSpec("", Material.YELLOW_DYE, 0),
                "dozen1", new ItemSpec("", Material.PINK_DYE, 0),
                "dozen2", new ItemSpec("", Material.MAGENTA_DYE, 0),
                "dozen3", new ItemSpec("", Material.PURPLE_DYE, 0));
        outsideItems.clear();
        for (String spot : OUTSIDE) outsideItems.put(spot, ItemSpec.of(yml.get("items.board." + spot), outsideDefaults.get(spot)));
        spotItem = item(yml, "spot", new ItemSpec("iavegasgui:empty_button", Material.STICK, 5600000));
        clearItem = item(yml, "clear", new ItemSpec("", Material.BARRIER, 0));
        repeatItem = item(yml, "repeat", new ItemSpec("", Material.CLOCK, 0));
        undoItem = item(yml, "undo", new ItemSpec("", Material.ARROW, 0));
        backItem = item(yml, "back", new ItemSpec("", Material.OAK_DOOR, 0));
        spinItem = item(yml, "spin", new ItemSpec("", Material.LIME_DYE, 0));
        boardTitle = yml.getString("board.title", "");
        return null;
    }

    private static ItemSpec item(YamlConfiguration yml, String key, ItemSpec fallback) {
        return ItemSpec.of(yml.get("items." + key), fallback);
    }

    static boolean isRed(int n) {
        return RED.contains(n);
    }

    static boolean isNumber(String spot) {
        return !OUTSIDE.contains(spot);
    }

    /** Multiplier of a spot (stake included): x36 / x2 / x3. */
    double multiplier(String spot) {
        if (isNumber(spot)) return straightPay;
        return spot.startsWith("dozen") ? dozenPay : evenPay;
    }

    /** True when {@code spot} wins on number {@code n}. Zero loses every outside spot. */
    static boolean wins(String spot, int n) {
        if (isNumber(spot)) return Integer.parseInt(spot) == n;
        if (n == 0) return false;
        return switch (spot) {
            case "red" -> RED.contains(n);
            case "black" -> !RED.contains(n);
            case "even" -> n % 2 == 0;
            case "odd" -> n % 2 == 1;
            case "low" -> n <= 18;
            case "high" -> n >= 19;
            case "dozen1" -> n <= 12;
            case "dozen2" -> n >= 13 && n <= 24;
            case "dozen3" -> n >= 25;
            default -> false;
        };
    }

    /** What a round pays back: every winning spot pays stake x multiplier. */
    public long payout(Map<String, Long> stakes, int n) {
        long sum = 0;
        for (Map.Entry<String, Long> e : stakes.entrySet()) {
            if (wins(e.getKey(), n)) sum += Payouts.payout(e.getValue(), multiplier(e.getKey()));
        }
        return sum;
    }

    int draw(RandomGenerator random) {
        return random.nextInt(37);
    }

    static int wheelIndex(int n) {
        for (int i = 0; i < WHEEL.length; i++) if (WHEEL[i] == n) return i;
        return 0;
    }

    /** The lowest RTP among the bet types (all 36/37 = 97,3% by default). */
    @Override
    public double rtp() {
        return Math.min(straightPay / 37.0, Math.min(evenPay * 18 / 37.0, dozenPay * 12 / 37.0));
    }

    @Override
    public void open(Player player, boolean npc) {
        new RouletteView(plugin, player, this, npc).open();
    }

    /** One random spot per round, every spot type equally often. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        List<String> spots = new ArrayList<>(OUTSIDE);
        for (int n = 0; n <= 36; n++) spots.add(String.valueOf(n));
        long bet = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) {
            String spot = spots.get(random.nextInt(spots.size()));
            paid += payout(Map.of(spot, bet), draw(random));
        }
        return paid / (rounds * (double) bet);
    }
}
