package com.dex.boxcasino.game.pot;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Góp Hũ (Vegas "Classic"): players put money into one pot; the winner is drawn with a chance equal to their share of
 * the pot and takes it all minus the tax. One pot per currency. Player against player: no jackpot.
 */
public final class PotGame extends Game {

    double taxPercent;
    int minPlayers;
    int countdownSeconds;
    int waitMinutes;
    int resultSeconds;
    int rollSteps;
    final List<ItemSpec> colors = new ArrayList<>();
    List<Integer> spotSlots = List.of();
    List<Integer> headSlots = List.of();
    List<Integer> stripSlots = List.of();
    List<Integer> statusSlots = List.of();
    ItemSpec statusIdle;
    ItemSpec statusCountdown;
    ItemSpec statusRolling;
    ItemSpec statusEnd;
    ItemSpec emptyItem;
    private final Map<Currency, PotTable> tables = new EnumMap<>(Currency.class);

    public PotGame(BoxCasinoPlugin plugin) {
        super(plugin, "pot");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        taxPercent = yml.getDouble("tax-percent", 5);
        if (taxPercent < 0 || taxPercent >= 100) return "tax-percent must be 0..99";
        minPlayers = Math.max(2, yml.getInt("min-players", 2));
        countdownSeconds = Math.max(5, yml.getInt("countdown-seconds", 30));
        waitMinutes = Math.max(1, yml.getInt("wait-minutes", 5));
        resultSeconds = Math.max(2, yml.getInt("result-seconds", 5));
        rollSteps = Math.max(10, yml.getInt("roll-steps", 40));
        colors.clear();
        for (Object o : yml.getList("colors", List.of())) colors.add(ItemSpec.of(o, ItemSpec.NONE));
        spotSlots = settings.slots("spots");
        headSlots = settings.slots("heads");
        stripSlots = settings.slots("strip");
        statusSlots = settings.slots("status");
        if (spotSlots.size() < 2 || colors.size() < spotSlots.size()) return "slots.spots needs 2+ slots and one colour each";
        if (minPlayers > spotSlots.size()) return "min-players is more than the spots";
        if (stripSlots.size() % 2 == 0) return "slots.strip needs an odd number of slots (the middle one is the pointer)";
        statusIdle = settings.item("status-idle");
        statusCountdown = settings.item("status-countdown");
        statusRolling = settings.item("status-rolling");
        statusEnd = settings.item("status-end");
        emptyItem = settings.item("empty");
        for (Currency c : Currency.values()) tables.computeIfAbsent(c, k -> new PotTable(plugin, this, k));
        return null;
    }

    PotTable table(Currency currency) {
        return tables.get(currency);
    }

    /** The pot a player has money in, or null (one pot at a time). */
    PotTable tableOf(UUID player) {
        for (PotTable t : tables.values()) if (t.entryOf(player) != null) return t;
        return null;
    }

    /** The pot as a room for a player in it or watching it, or null. */
    public com.dex.boxcasino.chat.ChatRoom roomOf(UUID player) {
        for (PotTable t : tables.values()) if (t.entryOf(player) != null || t.watching(player)) return t;
        return null;
    }

    /** What the winner receives: the pot minus the tax. */
    long winnerPayout(long pot) {
        return Payouts.payout(pot, 1 - taxPercent / 100.0);
    }

    @Override
    public boolean house() {
        return false;
    }

    @Override
    public double rtp() {
        return 1 - taxPercent / 100.0;
    }

    public double taxPercent() {
        return taxPercent;
    }

    @Override
    public void open(Player player, boolean npc) {
        new PotView(plugin, player, this, npc).open();
    }

    @Override
    public void shutdown() {
        for (PotTable t : tables.values()) t.shutdown();
    }

    /** Fair pot: each stake wins in proportion to its size, the winner pays the tax on the whole pot. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        double wagered = 0;
        double paid = 0;
        for (long r = 0; r < rounds; r++) {
            int n = 2 + random.nextInt(Math.max(1, spotSlots.size() - 1));
            long[] stakes = new long[n];
            long pot = 0;
            for (int i = 0; i < n; i++) {
                stakes[i] = 1000L * (1 + random.nextInt(10));
                pot += stakes[i];
            }
            long pick = random.nextLong(pot);
            wagered += pot;
            for (int i = 0; i < n; i++) {
                pick -= stakes[i];
                if (pick < 0) {
                    paid += winnerPayout(pot);
                    break;
                }
            }
        }
        return paid / wagered;
    }
}
