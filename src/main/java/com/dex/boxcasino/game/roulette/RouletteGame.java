package com.dex.boxcasino.game.roulette;

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
 * Vòng Quay (spec 7.6): a marker runs around a ring of cells and stops on the cell drawn at the start. One win cell
 * (multiplier set by the difficulty), a few bonus cells, the rest pay nothing. Weights are percentages.
 */
public final class RouletteGame extends Game {

    /** Ring slots in running order. */
    List<Integer> ring = List.of();
    int winIndex;
    final List<Bonus> bonuses = new ArrayList<>();
    final Map<String, Difficulty> difficulties = new LinkedHashMap<>();
    int spotSlot;
    int leverSlot;
    int laps;
    int resetTicks;
    ItemSpec cellA;
    ItemSpec cellB;
    ItemSpec labelItem;
    ItemSpec spotItem;

    public RouletteGame(BoxCasinoPlugin plugin) {
        super(plugin, "roulette");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        ring = settings.slots("ring");
        if (ring.size() < 4) return "slots.ring needs at least 4 slots";
        winIndex = yml.getInt("win-index", 0);
        if (winIndex < 0 || winIndex >= ring.size()) return "win-index outside the ring";
        bonuses.clear();
        ConfigurationSection bs = yml.getConfigurationSection("bonus");
        double bonusWeight = 0;
        if (bs != null) {
            for (String key : bs.getKeys(false)) {
                ConfigurationSection b = bs.getConfigurationSection(key);
                if (b == null) continue;
                int index = b.getInt("index", -1);
                if (index < 0 || index >= ring.size() || index == winIndex) return "bonus." + key + ".index invalid";
                Bonus bonus = new Bonus(key, index, b.getDouble("weight"), b.getDouble("multiplier"), b.getInt("label-slot", -1));
                if (bonus.weight() < 0 || bonus.multiplier() < 0) return "bonus." + key + " negative";
                bonuses.add(bonus);
                bonusWeight += bonus.weight();
            }
        }
        difficulties.clear();
        ConfigurationSection ds = yml.getConfigurationSection("difficulties");
        if (ds == null) return "difficulties missing";
        for (String key : ds.getKeys(false)) {
            ConfigurationSection d = ds.getConfigurationSection(key);
            if (d == null) continue;
            Difficulty diff = new Difficulty(key, d.getDouble("weight"), d.getDouble("multiplier"), d.getInt("slot", -1),
                    ItemSpec.of(d, "inactive", ItemSpec.NONE), ItemSpec.of(d, "active", ItemSpec.NONE));
            if (diff.weight() <= 0 || diff.multiplier() <= 0) return "difficulties." + key + " needs weight and multiplier > 0";
            if (diff.weight() + bonusWeight > 100) return "difficulties." + key + ": weights above 100%";
            difficulties.put(key, diff);
        }
        if (difficulties.isEmpty()) return "no difficulty";
        if (ring.size() - 1 - bonuses.size() < 1) return "no empty cell left in the ring";
        spotSlot = settings.slot("spot", 11);
        leverSlot = settings.slot("lever", 20);
        laps = Math.max(1, yml.getInt("laps", 3));
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 40));
        cellA = settings.item("cell-a");
        cellB = settings.item("cell-b");
        labelItem = settings.item("label");
        spotItem = settings.item("spot");
        return null;
    }

    /** RTP of a difficulty. */
    public double rtp(Difficulty d) {
        double sum = d.weight() * d.multiplier();
        for (Bonus b : bonuses) sum += b.weight() * b.multiplier();
        return sum / 100.0;
    }

    /** Highest RTP of all difficulties (they are configured to be equal by default). */
    @Override
    public double rtp() {
        double max = 0;
        for (Difficulty d : difficulties.values()) max = Math.max(max, rtp(d));
        return max;
    }

    /** Ring index the marker stops on, drawn with the difficulty's weights. */
    int draw(Difficulty d, RandomGenerator random) {
        double r = random.nextDouble() * 100.0;
        if (r < d.weight()) return winIndex;
        r -= d.weight();
        for (Bonus b : bonuses) {
            if (r < b.weight()) return b.index();
            r -= b.weight();
        }
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) if (!isSpecial(i)) empty.add(i);
        return empty.get(random.nextInt(empty.size()));
    }

    boolean isSpecial(int index) {
        if (index == winIndex) return true;
        for (Bonus b : bonuses) if (b.index() == index) return true;
        return false;
    }

    /** Multiplier paid when the marker stops on {@code index}. */
    double multiplier(Difficulty d, int index) {
        if (index == winIndex) return d.multiplier();
        for (Bonus b : bonuses) if (b.index() == index) return b.multiplier();
        return 0;
    }

    @Override
    public void open(Player player, boolean npc) {
        new RouletteView(plugin, player, this, npc).open();
    }

    @Override
    public double simulate(long rounds, RandomGenerator random) {
        List<Difficulty> list = new ArrayList<>(difficulties.values());
        long bet = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) {
            Difficulty d = list.get((int) (i % list.size()));
            paid += Payouts.payout(bet, multiplier(d, draw(d, random)));
        }
        return paid / (rounds * (double) bet);
    }

    public record Bonus(String key, int index, double weight, double multiplier, int labelSlot) {
    }

    public record Difficulty(String key, double weight, double multiplier, int slot, ItemSpec inactive, ItemSpec active) {
    }
}
