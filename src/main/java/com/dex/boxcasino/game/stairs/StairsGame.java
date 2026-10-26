package com.dex.boxcasino.game.stairs;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Leo Thang (spec 7.5): climb rows from the bottom, each row hides {@code tnt[r]} TNT cells placed at round start.
 * Multiplier after row r = RTP / P(surviving rows 1..r).
 */
public final class StairsGame extends Game {

    double rtp;
    int width;
    /** First slot of each row, bottom row first. */
    List<Integer> rowSlots = List.of();
    List<Integer> labelSlots = List.of();
    int[] tnt = new int[0];
    int spotSlot;
    int leverSlot;
    int resetTicks;
    ItemSpec closedItem;
    ItemSpec currentItem;
    ItemSpec clickedItem;
    ItemSpec tntItem;
    ItemSpec passedItem;
    ItemSpec labelItem;
    ItemSpec spotItem;
    ItemSpec leverIdle;
    ItemSpec leverRunning;
    ItemSpec leverEnd;

    public StairsGame(BoxCasinoPlugin plugin) {
        super(plugin, "stairs");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        rtp = yml.getDouble("rtp", 0.95);
        if (rtp <= 0 || rtp > 1) return "rtp must be in (0, 1]";
        width = yml.getInt("width", 6);
        rowSlots = settings.slots("rows");
        labelSlots = settings.slots("labels");
        List<Integer> t = yml.getIntegerList("tnt");
        if (width < 2 || rowSlots.isEmpty() || t.size() != rowSlots.size()) return "slots.rows and tnt need the same length";
        tnt = t.stream().mapToInt(Integer::intValue).toArray();
        for (int n : tnt) if (n < 1 || n >= width) return "tnt per row must be 1.." + (width - 1);
        spotSlot = settings.slot("spot", 43);
        leverSlot = settings.slot("lever", 52);
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 60));
        closedItem = settings.item("closed");
        currentItem = settings.item("current");
        clickedItem = settings.item("clicked");
        tntItem = settings.item("tnt");
        passedItem = settings.item("passed");
        labelItem = settings.item("label");
        spotItem = settings.item("spot");
        leverIdle = settings.item("lever-idle");
        leverRunning = settings.item("lever-running");
        leverEnd = settings.item("lever-end");
        return null;
    }

    public int rows() {
        return rowSlots.size();
    }

    @Override
    public double rtp() {
        return rtp;
    }

    /** Multiplier after clearing {@code cleared} rows (0 = nothing yet), truncated to 2 decimals. */
    public double multiplier(int cleared) {
        if (cleared <= 0) return 0;
        double survive = 1;
        for (int r = 0; r < cleared; r++) survive *= (double) (width - tnt[r]) / width;
        return Payouts.truncate2(rtp / survive);
    }

    @Override
    public void open(Player player, boolean npc) {
        new StairsView(plugin, player, this, npc).open();
    }

    /** Strategy: cash out after 1-3 rows at random. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long bet = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) {
            int target = 1 + random.nextInt(Math.min(3, rows()));
            boolean alive = true;
            for (int r = 0; r < target && alive; r++) alive = random.nextInt(width) >= tnt[r];
            if (alive) paid += Payouts.payout(bet, multiplier(target));
        }
        return paid / (rounds * (double) bet);
    }
}
