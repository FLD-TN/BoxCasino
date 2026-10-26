package com.dex.boxcasino.game.plinko;

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
 * Kim Tự Tháp / Plinko (spec 7.2), real physics: the ball bounces left or right with even odds on every row of pegs
 * and drops into the hopper under its last column. Hopper k (from the left) is reached with probability
 * C(rows, k) / 2^rows, so the middle is the most likely and the edges the rarest. Each risk level has its own
 * multipliers (edges pay the most).
 */
public final class PlinkoGame extends Game {

    final Map<String, Risk> risks = new LinkedHashMap<>();
    List<Integer> hopperSlots = List.of();
    int startSlot;
    /** Rows of pegs between the start slot and the hoppers (one bounce each). */
    int rows;
    int stepTicks;
    int resetTicks;
    ItemSpec ballItem;
    ItemSpec spotItem;
    ItemSpec hopperItem;

    public PlinkoGame(BoxCasinoPlugin plugin) {
        super(plugin, "plinko");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        hopperSlots = settings.slots("hoppers");
        startSlot = settings.slot("spot", 4);
        if (hopperSlots.size() < 2) return "slots.hoppers needs at least 2 slots";
        rows = hopperSlots.size() - 1;
        int startCol = startSlot % 9;
        int lastRow = hopperSlots.get(0) / 9;
        if (lastRow - startSlot / 9 - 1 != rows) {
            return "slots.hoppers: " + hopperSlots.size() + " hoppers need " + rows + " rows of pegs between the spot and them";
        }
        for (int k = 0; k <= rows; k++) {
            int slot = hopperSlots.get(k);
            int col = startCol - rows + 2 * k;
            if (slot / 9 != lastRow || slot % 9 != col) {
                return "hopper " + (k + 1) + " must be at column " + col + " of row " + lastRow + " (slot " + (lastRow * 9 + col) + ")";
            }
        }
        risks.clear();
        ConfigurationSection rs = yml.getConfigurationSection("risks");
        if (rs == null) return "risks missing";
        for (String key : rs.getKeys(false)) {
            ConfigurationSection r = rs.getConfigurationSection(key);
            if (r == null) continue;
            List<Double> m = r.getDoubleList("multipliers");
            if (m.size() != hopperSlots.size()) return "risks." + key + ".multipliers needs " + hopperSlots.size() + " values";
            double[] mul = m.stream().mapToDouble(Double::doubleValue).toArray();
            for (double d : mul) if (d < 0) return "risks." + key + ": negative multiplier";
            risks.put(key, new Risk(key, mul, r.getInt("slot", -1), ItemSpec.of(r, "inactive", ItemSpec.NONE),
                    ItemSpec.of(r, "active", ItemSpec.NONE)));
        }
        if (risks.isEmpty()) return "no risk level";
        stepTicks = Math.max(1, yml.getInt("step-ticks", 5));
        resetTicks = Math.max(1, yml.getInt("reset-ticks", 20));
        ballItem = settings.item("ball");
        spotItem = settings.item("spot");
        hopperItem = settings.item("hopper");
        return null;
    }

    /** Chance of hopper {@code k}: C(rows, k) / 2^rows. */
    public double chance(int k) {
        double c = 1;
        for (int i = 1; i <= k; i++) c = c * (rows - k + i) / i;
        return c / Math.pow(2, rows);
    }

    public double rtp(Risk risk) {
        double sum = 0;
        for (int k = 0; k < risk.multipliers().length; k++) sum += chance(k) * risk.multipliers()[k];
        return sum;
    }

    /** Highest RTP of the risk levels (configured equal by default). */
    @Override
    public double rtp() {
        double max = 0;
        for (Risk r : risks.values()) max = Math.max(max, rtp(r));
        return max;
    }

    /** The risk level at {@code index} (remembered as a number), the first one when out of range. */
    Risk risk(int index) {
        List<Risk> list = new ArrayList<>(risks.values());
        return list.get(index >= 0 && index < list.size() ? index : 0);
    }

    int indexOf(Risk risk) {
        return new ArrayList<>(risks.values()).indexOf(risk);
    }

    /** One drop: a fair left / right bounce per row of pegs; the hopper is the number of right bounces. */
    Drop drop(RandomGenerator random) {
        int row = startSlot / 9;
        int col = startSlot % 9;
        int rights = 0;
        List<Integer> path = new ArrayList<>();
        path.add(startSlot);
        for (int i = 0; i < rows; i++) {
            boolean right = random.nextBoolean();
            if (right) rights++;
            col += right ? 1 : -1;
            path.add((row + 1 + i) * 9 + col);
        }
        path.add(hopperSlots.get(rights));
        return new Drop(rights, List.copyOf(path));
    }

    @Override
    public void open(Player player, boolean npc) {
        new PlinkoView(plugin, player, this, npc).open();
    }

    /** Every risk level in turn. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        List<Risk> list = new ArrayList<>(risks.values());
        long bet = 1000;
        double paid = 0;
        for (long r = 0; r < rounds; r++) {
            Risk risk = list.get((int) (r % list.size()));
            paid += Payouts.payout(bet, risk.multipliers()[drop(random).hopper()]);
        }
        return paid / (rounds * (double) bet);
    }

    public record Risk(String key, double[] multipliers, int slot, ItemSpec inactive, ItemSpec active) {
    }

    record Drop(int hopper, List<Integer> path) {
    }
}
