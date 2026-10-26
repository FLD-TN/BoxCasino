package com.dex.boxcasino.game.mines;

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

/** Dò Mìn (spec 7.4): real mines placed at round start, fair multipliers scaled by the configured RTP. */
public final class MinesGame extends Game {

    double rtp;
    List<Integer> cells = List.of();
    final Map<String, Difficulty> difficulties = new LinkedHashMap<>();
    int spotSlot;
    List<Integer> leverSlots = List.of();
    int resetTicks;
    ItemSpec closedItem;
    ItemSpec mineItem;
    ItemSpec spotItem;
    ItemSpec leverIdle;
    ItemSpec leverRunning;
    ItemSpec leverEnd;

    public MinesGame(BoxCasinoPlugin plugin) {
        super(plugin, "mines");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        rtp = yml.getDouble("rtp", 0.97);
        if (rtp <= 0 || rtp > 1) return "rtp must be in (0, 1]";
        cells = settings.slots("cells");
        if (cells.size() < 2) return "slots.cells needs at least 2 slots";
        spotSlot = settings.slot("spot", 49);
        leverSlots = settings.slots("lever");
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 60));
        closedItem = settings.item("closed");
        mineItem = settings.item("mine");
        spotItem = settings.item("spot");
        leverIdle = settings.item("lever-idle");
        leverRunning = settings.item("lever-running");
        leverEnd = settings.item("lever-end");
        difficulties.clear();
        ConfigurationSection ds = yml.getConfigurationSection("difficulties");
        if (ds == null) return "difficulties missing";
        for (String key : ds.getKeys(false)) {
            ConfigurationSection d = ds.getConfigurationSection(key);
            if (d == null) continue;
            int mines = d.getInt("mines");
            if (mines < 1 || mines >= cells.size()) return "difficulties." + key + ".mines must be 1.." + (cells.size() - 1);
            difficulties.put(key, new Difficulty(key, mines, d.getInt("slot", -1), ItemSpec.of(d, "inactive", ItemSpec.NONE),
                    ItemSpec.of(d, "active", ItemSpec.NONE), ItemSpec.of(d, "opened", closedItem)));
        }
        if (difficulties.isEmpty()) return "no difficulty";
        return null;
    }

    @Override
    public double rtp() {
        return rtp;
    }

    /** Multiplier after {@code opened} safe cells with {@code mines} mines (truncated to 2 decimals). */
    public double multiplier(int mines, int opened) {
        int n = cells.size();
        double m = rtp;
        for (int i = 0; i < opened; i++) m *= (double) (n - i) / (n - mines - i);
        return opened == 0 ? 0 : Payouts.truncate2(m);
    }

    @Override
    public void open(Player player, boolean npc) {
        new MinesView(plugin, player, this, npc).open();
    }

    /**
     * Strategy: every difficulty in turn, open 1-3 cells at random, then cash out. The expected return is the same for
     * any number of cells; few cells keep the variance low enough for a stable measurement.
     */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        int n = cells.size();
        List<Difficulty> list = new ArrayList<>(difficulties.values());
        long bet = 1000;
        double wagered = 0;
        double paid = 0;
        for (long r = 0; r < rounds; r++) {
            Difficulty d = list.get((int) (r % list.size()));
            int target = 1 + random.nextInt(Math.min(3, n - d.mines()));
            boolean alive = true;
            for (int i = 0; i < target; i++) {
                // probability that the next cell is safe given i safe cells already opened
                if (random.nextInt(n - i) < d.mines()) {
                    alive = false;
                    break;
                }
            }
            wagered += bet;
            if (alive) paid += Payouts.payout(bet, multiplier(d.mines(), target));
        }
        return paid / wagered;
    }

    public record Difficulty(String key, int mines, int slot, ItemSpec inactive, ItemSpec active, ItemSpec opened) {
    }
}
