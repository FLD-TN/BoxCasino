package com.dex.boxcasino.game.pot;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.chat.ChatRoom;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameSettings;
import com.dex.boxcasino.util.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * The pot of one currency. WAITING (0-1 player, the lone player may take the money back) → COUNTDOWN (from the
 * min-players-th player, stakes can still grow) → ROLLING (winner drawn, the strip stops on their colour) → RESULT
 * → WAITING. Main thread only.
 */
final class PotTable implements ChatRoom {

    enum Phase { WAITING, COUNTDOWN, ROLLING, RESULT }

    record Entry(Bet bet, int color, String name) {
    }

    private final BoxCasinoPlugin plugin;
    private final PotGame game;
    final Currency currency;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Set<PotView> viewers = new HashSet<>();
    private Phase phase = Phase.WAITING;
    private int secondsLeft;
    /** WAITING: when the lone player's money goes back. */
    private long waitUntil;
    /** Colours of the strip; the pointer stops on index {@code rollSteps + middle}. */
    private List<Integer> tape = List.of();
    private int position;
    private Entry winner;
    private long lastPayout;
    private BukkitTask task;
    private BukkitTask rollTask;
    private long lastAnnounce;

    PotTable(BoxCasinoPlugin plugin, PotGame game, Currency currency) {
        this.plugin = plugin;
        this.game = game;
        this.currency = currency;
    }

    Phase phase() {
        return phase;
    }

    int secondsLeft() {
        return secondsLeft;
    }

    long waitSecondsLeft() {
        return Math.max(0, (waitUntil - System.currentTimeMillis()) / 1000);
    }

    Entry entryOf(UUID player) {
        return entries.get(player);
    }

    Entry entryOfColor(int color) {
        for (Entry e : entries.values()) if (e.color() == color) return e;
        return null;
    }

    Collection<Entry> entries() {
        return entries.values();
    }

    long pot() {
        long sum = 0;
        for (Entry e : entries.values()) sum += e.bet().amount();
        return sum;
    }

    Entry winner() {
        return winner;
    }

    long lastPayout() {
        return lastPayout;
    }

    /** Colour shown in strip cell {@code i}, or -1 for an empty strip. */
    int stripColor(int i) {
        if (tape.isEmpty()) return -1;
        int index = position + i;
        return index < tape.size() ? tape.get(index) : -1;
    }

    boolean open() {
        return phase == Phase.WAITING || phase == Phase.COUNTDOWN;
    }

    void join(PotView view) {
        viewers.add(view);
    }

    void leave(PotView view) {
        viewers.remove(view);
    }

    boolean watching(UUID player) {
        for (PotView v : viewers) if (v.player().getUniqueId().equals(player)) return true;
        return false;
    }

    /** Puts {@code amount} in on a free colour. False when refused (player told). */
    boolean enter(Player player, int color, long amount) {
        if (!open() || entries.containsKey(player.getUniqueId()) || entryOfColor(color) != null) return false;
        Bet bet = plugin.bets().place(player, game.id(), currency, amount, null);
        if (bet == null) return false;
        entries.put(player.getUniqueId(), new Entry(bet, color, player.getName()));
        if (entries.size() == 1) waitUntil = System.currentTimeMillis() + game.waitMinutes * 60_000L;
        if (phase == Phase.WAITING && entries.size() >= game.minPlayers) {
            phase = Phase.COUNTDOWN;
            secondsLeft = game.countdownSeconds;
            long now = System.currentTimeMillis();
            if (now - lastAnnounce >= plugin.access().announceCooldownMillis()) {
                lastAnnounce = now;
                plugin.access().announceInCasino(player, "games.pot.broadcast-start", "currency", plugin.wallet().name(currency),
                        "pot", plugin.wallet().format(currency, pot()), "seconds", String.valueOf(game.countdownSeconds));
            }
        }
        startTask();
        refreshAll();
        return true;
    }

    /** Adds {@code amount} to the player's stake (capped by the bet limit). False when refused. */
    boolean add(Player player, long amount) {
        Entry e = entries.get(player.getUniqueId());
        if (!open() || e == null) return false;
        GameSettings.Limits limits = game.settings().limits.get(currency);
        long room = limits.max() - e.bet().amount();
        if (room <= 0) {
            plugin.messages().send(player, "games.pot.max-reached", "max", plugin.wallet().format(currency, limits.max()));
            return false;
        }
        if (!plugin.bets().raise(player, e.bet(), Math.min(room, amount))) return false;
        refreshAll();
        return true;
    }

    /** The lone player (still waiting for others) takes the money back. */
    boolean withdraw(Player player) {
        Entry e = entries.get(player.getUniqueId());
        if (phase != Phase.WAITING || e == null) return false;
        entries.remove(player.getUniqueId());
        plugin.bets().refund(e.bet());
        if (entries.isEmpty()) stopTask();
        refreshAll();
        return true;
    }

    private void startTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void second() {
        switch (phase) {
            case WAITING -> {
                if (entries.isEmpty()) {
                    stopTask();
                } else if (System.currentTimeMillis() >= waitUntil) {
                    for (Entry e : entries.values()) {
                        plugin.bets().refund(e.bet());
                        Player p = Bukkit.getPlayer(e.bet().player);
                        if (p != null) plugin.messages().send(p, "games.pot.timeout", "minutes", String.valueOf(game.waitMinutes));
                    }
                    entries.clear();
                    stopTask();
                    refreshAll();
                } else {
                    refreshStatus();
                }
            }
            case COUNTDOWN -> {
                secondsLeft--;
                if (secondsLeft <= 0) roll();
                else refreshStatus();
            }
            case RESULT -> {
                secondsLeft--;
                if (secondsLeft <= 0) reset();
            }
            default -> {
            }
        }
    }

    /** Draws the winner by stake, builds the strip that stops on their colour, then animates it. */
    private void roll() {
        long pot = pot();
        long pick = plugin.random().nextLong(pot);
        for (Entry e : entries.values()) {
            pick -= e.bet().amount();
            if (pick < 0) {
                winner = e;
                break;
            }
        }
        String outcome = "{\"winner\":\"" + winner.name() + "\",\"pot\":" + pot + "}";
        for (Entry e : entries.values()) plugin.bets().setOutcome(e.bet(), outcome);
        int visible = game.stripSlots.size();
        int middle = visible / 2;
        List<Integer> t = new ArrayList<>();
        for (int i = 0; i < game.rollSteps + visible; i++) t.add(randomColorByStake(pot));
        t.set(game.rollSteps + middle, winner.color());
        tape = t;
        position = 0;
        phase = Phase.ROLLING;
        refreshAll();
        scheduleStep();
    }

    private int randomColorByStake(long pot) {
        long pick = plugin.random().nextLong(pot);
        for (Entry e : entries.values()) {
            pick -= e.bet().amount();
            if (pick < 0) return e.color();
        }
        return winner.color();
    }

    /** Fast at first, slower towards the end (1 → 6 ticks per step). */
    private void scheduleStep() {
        int steps = game.rollSteps;
        long delay = 1 + (long) (5.0 * position / steps * position / steps);
        rollTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            position++;
            sound(Sound.BLOCK_NOTE_BLOCK_HAT);
            if (position >= steps) {
                rollTask = null;
                settle(true);
            } else {
                refreshStrip();
                scheduleStep();
            }
        }, delay);
    }

    private void settle(boolean announce) {
        long pot = pot();
        long payout = game.winnerPayout(pot);
        lastPayout = payout;
        for (Entry e : entries.values()) plugin.bets().settle(e.bet(), e == winner ? payout : 0);
        if (announce) {
            String potText = plugin.wallet().format(currency, pot);
            String prize = plugin.wallet().format(currency, payout);
            for (Entry e : entries.values()) {
                Player p = Bukkit.getPlayer(e.bet().player);
                if (p == null) continue;
                if (e == winner) {
                    plugin.messages().send(p, "games.pot.won", "pot", potText, "payout", prize,
                            "chance", Format.percent(e.bet().amount() / (double) pot));
                } else {
                    plugin.messages().send(p, "games.pot.lost", "player", winner.name(), "payout", prize,
                            "bet", plugin.wallet().format(currency, e.bet().amount()));
                }
            }
            sound(Sound.ENTITY_PLAYER_LEVELUP);
        }
        phase = Phase.RESULT;
        secondsLeft = game.resultSeconds;
        position = game.rollSteps;
        refreshAll();
    }

    private void reset() {
        entries.clear();
        tape = List.of();
        position = 0;
        winner = null;
        phase = Phase.WAITING;
        stopTask();
        refreshAll();
    }

    /** Plugin disable / reload: a drawn winner is paid, otherwise every stake goes back. */
    void shutdown() {
        stopTask();
        if (rollTask != null) {
            rollTask.cancel();
            rollTask = null;
        }
        if (phase == Phase.ROLLING) {
            settle(true);
        } else if (phase != Phase.RESULT) {
            for (Entry e : entries.values()) plugin.bets().refund(e.bet());
        }
        entries.clear();
        tape = List.of();
        winner = null;
        phase = Phase.WAITING;
        viewers.clear();
    }

    private void sound(Sound sound) {
        if (!plugin.gameSounds()) return;
        for (PotView v : viewers) v.player().playSound(v.player().getLocation(), sound, 0.6f, 1f);
    }

    @Override
    public Collection<UUID> members() {
        Set<UUID> out = new HashSet<>(entries.keySet());
        for (PotView v : viewers) out.add(v.player().getUniqueId());
        return out;
    }

    @Override
    public void refreshViews() {
        refreshAll();
    }

    @Override
    public void reopen(Player player) {
        game.open(player, false);
    }

    private void refreshAll() {
        for (PotView v : new ArrayList<>(viewers)) v.refresh();
    }

    private void refreshStatus() {
        for (PotView v : new ArrayList<>(viewers)) v.refreshStatus();
    }

    private void refreshStrip() {
        for (PotView v : new ArrayList<>(viewers)) v.refreshStrip();
    }
}
