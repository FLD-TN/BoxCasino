package com.dex.boxcasino.game.lottery;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Vé Số: a ticket of hidden cells, each one drawn by weight from the prize table when the ticket is bought. The player
 * scratches {@code picks} cells and is paid the bet times the highest multiplier found. The cells are independent, so
 * which cells are scratched never changes the odds.
 */
public final class LotteryGame extends Game {

    record Prize(double multiplier, double weight, ItemSpec look) {
    }

    final List<Prize> prizes = new ArrayList<>();
    List<Integer> cells = List.of();
    int picks;
    int spotSlot;
    List<Integer> leverSlots = List.of();
    int revealTicks;
    int resetTicks;
    ItemSpec spotItem;
    ItemSpec closedItem;
    ItemSpec leverIdle;
    ItemSpec leverRunning;
    ItemSpec leverEnd;
    private double totalWeight;

    public LotteryGame(BoxCasinoPlugin plugin) {
        super(plugin, "lottery");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        prizes.clear();
        totalWeight = 0;
        for (Map<?, ?> m : yml.getMapList("prizes")) {
            double mul = m.get("multiplier") instanceof Number n ? n.doubleValue() : -1;
            double w = m.get("weight") instanceof Number n ? n.doubleValue() : -1;
            if (mul < 0 || w < 0) return "prizes need multiplier >= 0 and weight >= 0";
            prizes.add(new Prize(mul, w, ItemSpec.of(m, ItemSpec.NONE)));
            totalWeight += w;
        }
        if (prizes.size() < 2 || totalWeight <= 0) return "prizes need at least 2 entries with weight";
        prizes.sort((a, b) -> Double.compare(a.multiplier(), b.multiplier()));
        cells = settings.slots("cells");
        picks = yml.getInt("picks", 5);
        if (picks < 1 || picks > cells.size()) return "picks must be 1.." + cells.size() + " (slots.cells)";
        spotSlot = settings.slot("spot", 49);
        leverSlots = settings.slots("lever");
        revealTicks = Math.max(1, yml.getInt("reveal-ticks", 6));
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 60));
        spotItem = settings.item("spot");
        closedItem = settings.item("closed");
        leverIdle = settings.item("lever-idle");
        leverRunning = settings.item("lever-running");
        leverEnd = settings.item("lever-end");
        return null;
    }

    double chance(Prize p) {
        return p.weight() / totalWeight;
    }

    /** One cell of a new ticket. */
    Prize draw(RandomGenerator random) {
        double r = random.nextDouble() * totalWeight;
        for (Prize p : prizes) {
            r -= p.weight();
            if (r < 0) return p;
        }
        return prizes.get(prizes.size() - 1);
    }

    /** Expected highest multiplier of {@code picks} independent cells: sum of m × (F(m)^k − F(m−)^k). */
    @Override
    public double rtp() {
        double below = 0;
        double sum = 0;
        for (Prize p : prizes) {
            double upTo = below + chance(p);
            sum += p.multiplier() * (Math.pow(upTo, picks) - Math.pow(below, picks));
            below = upTo;
        }
        return sum;
    }

    @Override
    public void open(Player player, boolean npc) {
        new LotteryView(plugin, player, this, npc).open();
    }

    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long bet = 1000;
        double paid = 0;
        for (long r = 0; r < rounds; r++) {
            double best = 0;
            for (int i = 0; i < picks; i++) best = Math.max(best, draw(random).multiplier());
            paid += Payouts.payout(bet, best);
        }
        return paid / (rounds * (double) bet);
    }
}
