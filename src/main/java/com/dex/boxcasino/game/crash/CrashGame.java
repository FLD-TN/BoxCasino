package com.dex.boxcasino.game.crash;

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
 * Tên Lửa (Vegas "Crash"): one flight shared by the whole server. The multiplier grows from x1.00 until the rocket
 * explodes; a player cashes out before that for bet × multiplier. The explosion point is drawn at take-off with
 * {@code P(point >= x) = rtp / x}, so every cash-out strategy returns {@code rtp}.
 */
public final class CrashGame extends Game {

    record Color(double below, ItemSpec look) {
    }

    double rtp;
    double maxMultiplier;
    double growth;
    int stepTicks;
    int bettingSeconds;
    int resultSeconds;
    List<Double> autoOptions = List.of();
    List<Double> progress = List.of();
    final List<Color> colors = new ArrayList<>();
    int statusSlot;
    int buttonSlot;
    int autoSlot;
    List<Integer> progressSlots = List.of();
    List<Integer> historySlots = List.of();
    ItemSpec statusItem;
    ItemSpec buttonIdle;
    ItemSpec buttonActive;
    ItemSpec buttonFlying;
    ItemSpec buttonEnd;
    ItemSpec autoItem;
    ItemSpec emptyItem;
    private CrashTable table;

    public CrashGame(BoxCasinoPlugin plugin) {
        super(plugin, "crash");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        rtp = yml.getDouble("rtp", 0.96);
        if (rtp <= 0 || rtp > 1) return "rtp must be in (0, 1]";
        maxMultiplier = yml.getDouble("max-multiplier", 100);
        if (maxMultiplier < 1.01) return "max-multiplier must be > 1";
        growth = yml.getDouble("growth-percent", 1.5) / 100.0;
        if (growth <= 0) return "growth-percent must be > 0";
        stepTicks = Math.max(1, yml.getInt("step-ticks", 2));
        bettingSeconds = Math.max(5, yml.getInt("betting-seconds", 10));
        resultSeconds = Math.max(2, yml.getInt("result-seconds", 4));
        List<Double> auto = new ArrayList<>();
        auto.add(0.0);
        for (double d : yml.getDoubleList("auto-cashout")) if (d > 1 && !auto.contains(d)) auto.add(d);
        autoOptions = List.copyOf(auto);
        progress = yml.getDoubleList("progress");
        colors.clear();
        for (Map<?, ?> m : yml.getMapList("colors")) {
            double below = m.get("below") instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
            colors.add(new Color(below, ItemSpec.of(m, ItemSpec.NONE)));
        }
        if (colors.isEmpty()) return "colors is empty";
        colors.sort((a, b) -> Double.compare(a.below(), b.below()));
        statusSlot = settings.slot("status", 4);
        buttonSlot = settings.slot("button", 22);
        autoSlot = settings.slot("auto", 49);
        progressSlots = settings.slots("progress");
        historySlots = settings.slots("history");
        statusItem = settings.item("status");
        buttonIdle = settings.item("button-idle");
        buttonActive = settings.item("button-active");
        buttonFlying = settings.item("button-flying");
        buttonEnd = settings.item("button-end");
        autoItem = settings.item("auto");
        emptyItem = settings.item("empty");
        if (table == null) table = new CrashTable(plugin, this);
        return null;
    }

    /** Explosion point, 2 decimals: x1.00 (instant) with probability 1 − rtp, capped at max-multiplier. */
    double drawPoint(RandomGenerator random) {
        double raw = rtp / (1.0 - random.nextDouble());
        if (raw < 1.0) return 1.0;
        return Math.min(maxMultiplier, Math.floor(raw * 100.0) / 100.0);
    }

    /** Multiplier shown after {@code step} steps of flight (2 decimals, rounded down). */
    double multiplierAt(int step) {
        return Math.min(maxMultiplier, Math.floor(Math.pow(1 + growth, step) * 100.0 + 1e-9) / 100.0);
    }

    ItemSpec colorOf(double multiplier) {
        for (Color c : colors) if (multiplier < c.below()) return c.look();
        return colors.get(colors.size() - 1).look();
    }

    CrashTable table() {
        return table;
    }

    /** The shared flight as a room for a bettor or viewer, or null. */
    public com.dex.boxcasino.chat.ChatRoom roomOf(java.util.UUID player) {
        if (table == null) return null;
        return table.entryOf(player) != null || table.watching(player) ? table : null;
    }

    @Override
    public double rtp() {
        return rtp;
    }

    @Override
    public void open(Player player, boolean npc) {
        new CrashView(plugin, player, this, npc).open();
    }

    @Override
    public void shutdown() {
        if (table != null) table.shutdown();
    }

    /** Strategy: a random auto cash-out target among the configured ones (x2 when none). */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        List<Double> targets = autoOptions.size() > 1 ? autoOptions.subList(1, autoOptions.size()) : List.of(2.0);
        long bet = 1000;
        double paid = 0;
        for (long r = 0; r < rounds; r++) {
            double target = targets.get(random.nextInt(targets.size()));
            if (drawPoint(random) >= target) paid += Payouts.payout(bet, target);
        }
        return paid / (rounds * (double) bet);
    }
}
