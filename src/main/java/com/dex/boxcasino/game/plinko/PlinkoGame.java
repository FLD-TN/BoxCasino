package com.dex.boxcasino.game.plinko;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Kim Tự Tháp / Plinko (spec 7.2). The hopper is drawn by weight when the ball is dropped; the ball then follows a
 * random zig-zag path through the pegs that ends in that hopper (rows of 9 slots, one column step per row).
 */
public final class PlinkoGame extends Game {

    double[] weights = new double[0];
    double[] multipliers = new double[0];
    List<Integer> hopperSlots = List.of();
    int startSlot;
    int stepTicks;
    int resetTicks;
    ItemSpec ballItem;
    ItemSpec spotItem;
    ItemSpec hopperItem;
    private double totalWeight;

    public PlinkoGame(BoxCasinoPlugin plugin) {
        super(plugin, "plinko");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        List<Double> w = yml.getDoubleList("hoppers.weights");
        List<Double> m = yml.getDoubleList("hoppers.multipliers");
        hopperSlots = settings.slots("hoppers");
        if (w.size() != 5 || m.size() != 5 || hopperSlots.size() != 5) {
            return "hoppers.weights, hoppers.multipliers and slots.hoppers need exactly 5 values (texture has 5 hoppers)";
        }
        weights = w.stream().mapToDouble(Double::doubleValue).toArray();
        multipliers = m.stream().mapToDouble(Double::doubleValue).toArray();
        totalWeight = 0;
        for (double d : weights) {
            if (d < 0) return "negative weight";
            totalWeight += d;
        }
        for (double d : multipliers) if (d < 0) return "negative multiplier";
        if (totalWeight <= 0) return "weights sum to 0";
        startSlot = settings.slot("spot", 4);
        stepTicks = Math.max(1, yml.getInt("step-ticks", 5));
        resetTicks = Math.max(1, yml.getInt("reset-ticks", 20));
        for (int slot : hopperSlots) {
            int steps = slot / 9 - startSlot / 9 - 1;
            int diff = slot % 9 - startSlot % 9;
            if (steps < 1 || Math.abs(diff) > steps || (diff + steps) % 2 != 0) {
                return "hopper slot " + slot + " cannot be reached from spot slot " + startSlot;
            }
        }
        ballItem = settings.item("ball");
        spotItem = settings.item("spot");
        hopperItem = settings.item("hopper");
        return null;
    }

    @Override
    public double rtp() {
        double sum = 0;
        for (int i = 0; i < weights.length; i++) sum += weights[i] / totalWeight * multipliers[i];
        return sum;
    }

    public double chance(int hopper) {
        return weights[hopper] / totalWeight;
    }

    int draw(RandomGenerator random) {
        double r = random.nextDouble() * totalWeight;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return i;
        }
        return weights.length - 1;
    }

    /**
     * Slots the ball visits, from the start slot down to the hopper. Rows 1-4 step one column left or right (the pegs
     * sit on the other columns), the last row drops straight into hopper column {@code 2 × hopper}.
     */
    List<Integer> path(int hopper, RandomGenerator random) {
        int startRow = startSlot / 9;
        int col = startSlot % 9;
        int target = hopperSlots.get(hopper) % 9;
        int lastRow = hopperSlots.get(hopper) / 9;
        int steps = lastRow - startRow - 1;
        int rights = (target - col + steps) / 2;
        List<Boolean> moves = new ArrayList<>();
        for (int i = 0; i < steps; i++) moves.add(i < rights);
        java.util.Collections.shuffle(moves, new java.util.Random(random.nextLong()));
        List<Integer> out = new ArrayList<>();
        out.add(startSlot);
        for (int i = 0; i < steps; i++) {
            col += moves.get(i) ? 1 : -1;
            out.add((startRow + 1 + i) * 9 + col);
        }
        out.add(hopperSlots.get(hopper));
        return out;
    }

    @Override
    public void open(Player player, boolean npc) {
        new PlinkoView(plugin, player, this, npc).open();
    }

    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long bet = 1000;
        double paid = 0;
        for (long r = 0; r < rounds; r++) paid += Payouts.payout(bet, multipliers[draw(random)]);
        return paid / (rounds * (double) bet);
    }
}
