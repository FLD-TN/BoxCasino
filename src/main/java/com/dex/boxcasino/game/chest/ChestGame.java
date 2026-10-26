package com.dex.boxcasino.game.chest;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Rương Hải Tặc (Vegas "Dead Man's Chest"): {@code opens} openings of the chests, each one gold (+ bet × value), a
 * multiplier (× all gold) or a trap (everything lost, round over). Paid: bet × sum of gold × product of multipliers.
 * Every opening is drawn when the bet is placed; which chest is clicked does not matter.
 */
public final class ChestGame extends Game {

    enum Kind { GOLD, MULTIPLIER, TRAP }

    record Open(Kind kind, double value) {
    }

    public record Difficulty(String key, double trap, double gold, double multiplier, double goldMin, double goldMax,
                      double multiplierMin, double multiplierMax, int slot, ItemSpec inactive, ItemSpec active) {
        double total() {
            return trap + gold + multiplier;
        }
    }

    int opens;
    final Map<String, Difficulty> difficulties = new LinkedHashMap<>();
    List<Integer> chestSlots = List.of();
    List<Integer> resultSlots = List.of();
    int spotSlot;
    List<Integer> leverSlots = List.of();
    int resetTicks;
    ItemSpec chestClosed;
    ItemSpec chestOpened;
    ItemSpec goldItem;
    ItemSpec multiplierItem;
    ItemSpec trapItem;
    ItemSpec emptyResult;
    ItemSpec spotItem;
    ItemSpec leverIdle;
    ItemSpec leverRunning;
    ItemSpec leverEnd;

    public ChestGame(BoxCasinoPlugin plugin) {
        super(plugin, "chest");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        opens = yml.getInt("opens", 3);
        if (opens < 1 || opens > 9) return "opens must be 1..9";
        chestSlots = settings.slots("chests");
        resultSlots = settings.slots("results");
        if (chestSlots.isEmpty()) return "slots.chests is empty";
        if (resultSlots.size() < opens) return "slots.results needs one slot per opening (" + opens + ")";
        difficulties.clear();
        ConfigurationSection ds = yml.getConfigurationSection("difficulties");
        if (ds == null) return "difficulties missing";
        for (String key : ds.getKeys(false)) {
            ConfigurationSection d = ds.getConfigurationSection(key);
            if (d == null) continue;
            Difficulty diff = new Difficulty(key, d.getDouble("trap"), d.getDouble("gold"), d.getDouble("multiplier"),
                    d.getDouble("gold-min"), d.getDouble("gold-max"), d.getDouble("multiplier-min"), d.getDouble("multiplier-max"),
                    d.getInt("slot", -1), ItemSpec.of(d, "inactive", ItemSpec.NONE), ItemSpec.of(d, "active", ItemSpec.NONE));
            if (diff.trap() < 0 || diff.gold() < 0 || diff.multiplier() < 0 || diff.total() <= 0) {
                return "difficulties." + key + ": trap / gold / multiplier must be >= 0 and not all 0";
            }
            if (diff.goldMin() < 0 || diff.goldMax() < diff.goldMin() || diff.multiplierMin() < 0
                    || diff.multiplierMax() < diff.multiplierMin()) {
                return "difficulties." + key + ": min must be <= max and not negative";
            }
            difficulties.put(key, diff);
        }
        if (difficulties.isEmpty()) return "no difficulty";
        spotSlot = settings.slot("spot", 49);
        leverSlots = settings.slots("lever");
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 60));
        chestClosed = settings.item("chest-closed");
        chestOpened = settings.item("chest-opened");
        goldItem = settings.item("gold");
        multiplierItem = settings.item("multiplier");
        trapItem = settings.item("trap");
        emptyResult = settings.item("result-empty");
        spotItem = settings.item("spot");
        leverIdle = settings.item("lever-idle");
        leverRunning = settings.item("lever-running");
        leverEnd = settings.item("lever-end");
        return null;
    }

    public Map<String, Difficulty> difficulties() {
        return difficulties;
    }

    /** All openings of a round, drawn at once; stops early at a trap. */
    List<Open> draw(Difficulty d, RandomGenerator random) {
        List<Open> out = new ArrayList<>();
        for (int i = 0; i < opens; i++) {
            double r = random.nextDouble() * d.total();
            if (r < d.trap()) {
                out.add(new Open(Kind.TRAP, 0));
                break;
            }
            if (r < d.trap() + d.gold()) out.add(new Open(Kind.GOLD, round2(d.goldMin() + random.nextDouble() * (d.goldMax() - d.goldMin()))));
            else out.add(new Open(Kind.MULTIPLIER, round2(d.multiplierMin() + random.nextDouble() * (d.multiplierMax() - d.multiplierMin()))));
        }
        return out;
    }

    /** Bet multiplier of the openings revealed so far: 0 after a trap or without gold. */
    static double total(List<Open> revealed) {
        double gold = 0;
        double mul = 1;
        for (Open o : revealed) {
            switch (o.kind()) {
                case TRAP -> {
                    return 0;
                }
                case GOLD -> gold += o.value();
                case MULTIPLIER -> mul *= o.value();
            }
        }
        return gold * mul;
    }

    static double gold(List<Open> revealed) {
        double gold = 0;
        for (Open o : revealed) if (o.kind() == Kind.GOLD) gold += o.value();
        return gold;
    }

    static double multiplier(List<Open> revealed) {
        double mul = 1;
        for (Open o : revealed) if (o.kind() == Kind.MULTIPLIER) mul *= o.value();
        return mul;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Exact return: sum over k gold openings of C(n, k) × pGold^k × pMul^(n−k) × k × mean gold × mean mul^(n−k). */
    public double rtp(Difficulty d) {
        double pg = d.gold() / d.total();
        double pm = d.multiplier() / d.total();
        double g = (d.goldMin() + d.goldMax()) / 2;
        double m = (d.multiplierMin() + d.multiplierMax()) / 2;
        double sum = 0;
        for (int k = 0; k <= opens; k++) {
            sum += binomial(opens, k) * Math.pow(pg, k) * Math.pow(pm, opens - k) * k * g * Math.pow(m, opens - k);
        }
        return sum;
    }

    private static double binomial(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }

    /** The best difficulty (menu shows one number; each difficulty shows its own in the GUI). */
    @Override
    public double rtp() {
        double max = 0;
        for (Difficulty d : difficulties.values()) max = Math.max(max, rtp(d));
        return max;
    }

    @Override
    public void open(Player player, boolean npc) {
        new ChestView(plugin, player, this, npc).open();
    }

    /** Every difficulty in turn, all openings. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        List<Difficulty> list = new ArrayList<>(difficulties.values());
        long bet = 1000;
        double paid = 0;
        for (long r = 0; r < rounds; r++) {
            paid += Payouts.payout(bet, total(draw(list.get((int) (r % list.size())), random)));
        }
        return paid / (rounds * (double) bet);
    }
}
