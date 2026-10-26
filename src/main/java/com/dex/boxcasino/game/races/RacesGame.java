package com.dex.boxcasino.game.races;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Đua Ngựa (spec 7.3): one race shared by the whole server, winner uniform among the horses. */
public final class RacesGame extends Game {

    double multiplier;
    int bettingSeconds;
    int resultSeconds;
    int raceSteps;
    int stepTicks;
    /** First slot (start column) of each lane; the horse runs to the right. */
    List<Integer> laneSlots = List.of();
    int finishColumn;
    List<Integer> flagSlots = List.of();
    int spotSlot;
    List<Integer> leverSlots = List.of();
    final List<Horse> horses = new ArrayList<>();
    ItemSpec flagA;
    ItemSpec flagB;
    ItemSpec spotItem;
    ItemSpec leverIdle;
    ItemSpec leverChoose;
    ItemSpec leverBetting;
    ItemSpec leverRacing;
    ItemSpec leverEnd;
    List<ItemSpec> countdown = List.of();
    private RaceTable table;

    public RacesGame(BoxCasinoPlugin plugin) {
        super(plugin, "races");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        multiplier = yml.getDouble("multiplier", 3.0);
        bettingSeconds = Math.max(5, yml.getInt("betting-seconds", 30));
        resultSeconds = Math.max(2, yml.getInt("result-seconds", 5));
        raceSteps = Math.max(10, yml.getInt("race-steps", 40));
        stepTicks = Math.max(1, yml.getInt("step-ticks", 5));
        laneSlots = settings.slots("lanes");
        finishColumn = yml.getInt("finish-column", 7);
        flagSlots = settings.slots("flags");
        spotSlot = settings.slot("spot", 49);
        leverSlots = settings.slots("lever");
        horses.clear();
        ConfigurationSection hs = yml.getConfigurationSection("horses");
        if (hs != null) {
            for (String key : hs.getKeys(false)) {
                ConfigurationSection h = hs.getConfigurationSection(key);
                if (h == null) continue;
                horses.add(new Horse(horses.size(), ItemSpec.of(h, "idle", ItemSpec.NONE), ItemSpec.of(h, "choose", ItemSpec.NONE),
                        ItemSpec.of(h, "selected", ItemSpec.NONE), ItemSpec.of(h, "running", ItemSpec.NONE)));
            }
        }
        if (horses.size() < 2 || horses.size() != laneSlots.size()) return "need 2+ horses and one lane slot per horse";
        for (int lane : laneSlots) {
            if (lane % 9 + finishColumn >= 9) return "finish-column " + finishColumn + " leaves lane " + lane;
        }
        flagA = settings.item("flag-a");
        flagB = settings.item("flag-b");
        spotItem = settings.item("spot");
        leverIdle = settings.item("lever-idle");
        leverChoose = settings.item("lever-choose");
        leverBetting = settings.item("lever-betting");
        leverRacing = settings.item("lever-racing");
        leverEnd = settings.item("lever-end");
        List<ItemSpec> cd = new ArrayList<>();
        for (Object o : yml.getList("items.countdown", List.of())) cd.add(ItemSpec.of(o, ItemSpec.NONE));
        countdown = cd;
        if (table == null) table = new RaceTable(plugin, this);
        return null;
    }

    /** The shared race as a chat room for a bettor or viewer, or null. */
    public com.dex.boxcasino.chat.ChatRoom roomOf(java.util.UUID player) {
        if (table == null) return null;
        return table.betOf(player) != null || table.watching(player) ? table : null;
    }

    RaceTable table() {
        return table;
    }

    @Override
    public double rtp() {
        return multiplier / horses.size();
    }

    @Override
    public void open(Player player, boolean npc) {
        RacesView view = new RacesView(plugin, player, this, npc);
        view.open();
    }

    /** Settles (race decided) or refunds (still betting) every bet: plugin disable / reload. */
    @Override
    public void shutdown() {
        if (table != null) table.shutdown();
    }

    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long bet = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) {
            if (random.nextInt(horses.size()) == random.nextInt(horses.size())) paid += Payouts.payout(bet, multiplier);
        }
        return paid / (rounds * (double) bet);
    }

    record Horse(int index, ItemSpec idle, ItemSpec choose, ItemSpec selected, ItemSpec running) {
    }
}
