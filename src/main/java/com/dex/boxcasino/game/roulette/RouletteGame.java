package com.dex.boxcasino.game.roulette;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
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
 * Roulette on a European wheel (37 numbers, one zero), with three bets only: red x2, black x2, green (zero) x36.
 * The number is drawn before the wheel turns; the ring of 16 cells shows a window of the wheel that turns (at most
 * one turn) until the drawn number is under the pointer cell. One screen, the Vegas roulette texture.
 */
public final class RouletteGame extends Game {

    /** Number order on a European wheel, clockwise from zero. */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
            20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** The three bets, in panel order. */
    public static final List<String> SPOTS = List.of("red", "black", "green");

    /** Ring slots in running order. */
    List<Integer> ring = List.of();
    /** Ring index of the pointer cell: the number under it wins. */
    int pointer;
    int spotSlot;
    int leverSlot;
    List<Integer> historySlots = List.of();
    final Map<String, Integer> spotSlots = new LinkedHashMap<>();
    int laps;
    double colorPay;
    double greenPay;
    final Map<Currency, Long> maxGreen = new EnumMap<>(Currency.class);
    final ItemSpec[] numberItems = new ItemSpec[37];
    final Map<String, ItemSpec> spotItems = new LinkedHashMap<>();
    ItemSpec spotItem;

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
        spotSlots.clear();
        spotSlots.put("red", settings.slot("red", 16));
        spotSlots.put("black", settings.slot("black", 25));
        spotSlots.put("green", settings.slot("green", 34));
        laps = Math.max(0, yml.getInt("spin-laps", 0));
        colorPay = yml.getDouble("payouts.color", 2);
        greenPay = yml.getDouble("payouts.green", 36);
        if (colorPay <= 0 || greenPay <= 0) return "payouts must be > 0";
        for (Currency c : Currency.values()) {
            maxGreen.put(c, Math.max(0, yml.getLong("max-green." + c.name(), c == Currency.MONEY ? 100_000 : 100)));
        }
        for (int n = 0; n <= 36; n++) {
            ItemSpec vegas = block(n == 0 ? "green" : RED.contains(n) ? "red" : "black");
            numberItems[n] = ItemSpec.of(yml.get("items.numbers." + n),
                    new ItemSpec("boxcasino:roulette_" + n, vegas.material(), vegas.customModelData()));
        }
        spotItems.clear();
        for (String spot : SPOTS) spotItems.put(spot, ItemSpec.of(yml.get("items." + spot), block(spot)));
        spotItem = ItemSpec.of(yml.get("items.spot"), new ItemSpec("iavegasgui:empty_button", Material.STICK, 5600000));
        return null;
    }

    /** The Double Roulette block of the Vegas pack in that colour. */
    private static ItemSpec block(String color) {
        return switch (color) {
            case "green" -> new ItemSpec("iavegasgui:doubleroulette_green_rolling_block", Material.STICK, 5626107);
            case "red" -> new ItemSpec("iavegasgui:doubleroulette_red_rolling_block", Material.STICK, 5626106);
            default -> new ItemSpec("iavegasgui:doubleroulette_black_rolling_block", Material.STICK, 5626105);
        };
    }

    static boolean isRed(int n) {
        return RED.contains(n);
    }

    /** Multiplier of a bet (stake included). */
    double multiplier(String spot) {
        return spot.equals("green") ? greenPay : colorPay;
    }

    /** True when {@code spot} wins on number {@code n}. Zero is green: red and black both lose. */
    static boolean wins(String spot, int n) {
        return switch (spot) {
            case "green" -> n == 0;
            case "red" -> n != 0 && RED.contains(n);
            case "black" -> n != 0 && !RED.contains(n);
            default -> false;
        };
    }

    /** What a round pays back: every winning bet pays stake x multiplier. */
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

    /** The lower RTP of the two bet types (both 36/37 = 97,3% by default). */
    @Override
    public double rtp() {
        return Math.min(colorPay * 18 / 37.0, greenPay / 37.0);
    }

    @Override
    public void open(Player player, boolean npc) {
        new RouletteView(plugin, player, this, npc).open();
    }

    /** One random bet per round. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long bet = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) {
            String spot = SPOTS.get(random.nextInt(SPOTS.size()));
            paid += payout(Map.of(spot, bet), draw(random));
        }
        return paid / (rounds * (double) bet);
    }
}
