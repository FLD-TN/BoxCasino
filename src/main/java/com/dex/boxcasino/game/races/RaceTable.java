package com.dex.boxcasino.game.races;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.chat.ChatRoom;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.extra.Countdown;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * The one shared race. IDLE (nobody bet) → BETTING (countdown from the first bet) → RACING (winner drawn at the
 * start, animation planned so that horse crosses first) → RESULT → IDLE. Main thread only.
 */
final class RaceTable implements ChatRoom {

    private long lastAnnounce;

    enum Phase { IDLE, BETTING, RACING, RESULT }

    record Entry(Bet bet, int horse, String name) {
    }

    private final BoxCasinoPlugin plugin;
    private final RacesGame game;
    private final Map<UUID, Entry> bets = new LinkedHashMap<>();
    private final Set<RacesView> viewers = new HashSet<>();
    private Phase phase = Phase.IDLE;
    private int secondsLeft;
    private int step;
    private int winner = -1;
    /** positions[horse][step], column 0..finishColumn. */
    private int[][] plan = new int[0][0];
    private BukkitTask task;
    private int ticks;

    RaceTable(BoxCasinoPlugin plugin, RacesGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    Phase phase() {
        return phase;
    }

    int secondsLeft() {
        return secondsLeft;
    }

    int winner() {
        return winner;
    }

    /** Column of a horse right now. */
    int position(int horse) {
        if (phase == Phase.IDLE || phase == Phase.BETTING || plan.length == 0) return 0;
        return plan[horse][Math.min(step, game.raceSteps)];
    }

    Entry betOf(UUID player) {
        return bets.get(player);
    }

    int bettors(int horse) {
        int n = 0;
        for (Entry e : bets.values()) if (e.horse() == horse) n++;
        return n;
    }

    boolean acceptsBets() {
        return phase == Phase.IDLE || phase == Phase.BETTING;
    }

    void join(RacesView view) {
        viewers.add(view);
    }

    void leave(RacesView view) {
        viewers.remove(view);
    }

    /** Takes the stake; starts the countdown on the first bet. Returns false when refused (already told). */
    boolean place(Player player, int horse, Currency currency, long amount) {
        if (!acceptsBets() || bets.containsKey(player.getUniqueId())) return false;
        Bet bet = plugin.bets().place(player, game.id(), currency, amount, null);
        if (bet == null) return false;
        bets.put(player.getUniqueId(), new Entry(bet, horse, player.getName()));
        if (phase == Phase.IDLE) {
            phase = Phase.BETTING;
            secondsLeft = game.bettingSeconds;
            startTask();
            long now = System.currentTimeMillis();
            if (now - lastAnnounce >= plugin.access().announceCooldownMillis()) {
                lastAnnounce = now;
                plugin.access().announceInCasino(player, "games.races.broadcast-start",
                        "player", player.getName(), "seconds", String.valueOf(game.bettingSeconds));
            }
        }
        refreshAll();
        return true;
    }

    boolean cancel(Player player) {
        if (phase != Phase.BETTING) return false;
        Entry e = bets.remove(player.getUniqueId());
        if (e == null) return false;
        plugin.bets().refund(e.bet());
        if (bets.isEmpty()) {
            phase = Phase.IDLE;
            stopTask();
        }
        refreshAll();
        return true;
    }

    private void startTask() {
        if (task != null) return;
        ticks = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        ticks++;
        switch (phase) {
            case BETTING -> {
                if (ticks % 20 != 0) return;
                secondsLeft--;
                if (secondsLeft <= 0) {
                    startRace();
                } else {
                    Countdown.start(plugin, bets.keySet(), secondsLeft, "games.races.countdown");
                    refreshLever();
                }
            }
            case RACING -> {
                if (ticks % game.stepTicks != 0) return;
                step++;
                refreshAll();
                if (step >= game.raceSteps) finishRace();
            }
            case RESULT -> {
                if (ticks % 20 != 0) return;
                secondsLeft--;
                if (secondsLeft <= 0) {
                    phase = Phase.IDLE;
                    winner = -1;
                    plan = new int[0][0];
                    stopTask();
                    refreshAll();
                }
            }
            default -> stopTask();
        }
    }

    private void startRace() {
        winner = plugin.random().nextInt(game.horses.size());
        plan = plan(winner, game.horses.size(), game.raceSteps, game.finishColumn, plugin.random());
        step = 0;
        ticks = 0;
        phase = Phase.RACING;
        String outcome = "{\"winner\":" + (winner + 1) + "}";
        for (Entry e : bets.values()) plugin.bets().setOutcome(e.bet(), outcome.replace("}", ",\"horse\":" + (e.horse() + 1) + "}"));
        refreshAll();
    }

    private void finishRace() {
        settleAll(true);
        phase = Phase.RESULT;
        secondsLeft = game.resultSeconds;
        ticks = 0;
        refreshAll();
    }

    private void settleAll(boolean announce) {
        for (Map.Entry<UUID, Entry> me : bets.entrySet()) {
            Entry e = me.getValue();
            boolean won = e.horse() == winner;
            long payout = won ? Payouts.payout(e.bet().amount(), game.multiplier) : 0;
            plugin.bets().settle(e.bet(), payout);
            Player p = Bukkit.getPlayer(me.getKey());
            if (p != null && announce) {
                plugin.messages().send(p, won ? "games.races.won" : "games.races.lost",
                        "horse", String.valueOf(e.horse() + 1), "winner", String.valueOf(winner + 1),
                        "payout", plugin.wallet().format(e.bet().currency, payout),
                        "bet", plugin.wallet().format(e.bet().currency, e.bet().amount()));
            }
        }
        bets.clear();
    }

    /** Plugin disable / reload: a decided race is paid, bets still in the betting phase are refunded. */
    void shutdown() {
        stopTask();
        if (phase == Phase.RACING) {
            settleAll(true);
        } else {
            for (Entry e : bets.values()) plugin.bets().refund(e.bet());
            bets.clear();
        }
        phase = Phase.IDLE;
        winner = -1;
        plan = new int[0][0];
        viewers.clear();
    }

    /**
     * Each horse makes as many one-column moves as its final column, at random steps. The winner ends on the finish
     * column with its last move on the very last step; the others stop 1-3 columns before it.
     */
    static int[][] plan(int win, int n, int steps, int finish, Random random) {
        int[][] out = new int[n][steps + 1];
        for (int h = 0; h < n; h++) {
            int target = h == win ? finish : finish - 1 - random.nextInt(Math.min(3, finish));
            List<Integer> all = new ArrayList<>();
            for (int s = 1; s <= (h == win ? steps - 1 : steps); s++) all.add(s);
            Collections.shuffle(all, random);
            int moves = h == win ? target - 1 : target;
            Set<Integer> moveSteps = new HashSet<>(all.subList(0, Math.min(moves, all.size())));
            if (h == win) moveSteps.add(steps);
            int pos = 0;
            for (int s = 1; s <= steps; s++) {
                if (moveSteps.contains(s)) pos++;
                out[h][s] = pos;
            }
        }
        return out;
    }

    boolean watching(UUID player) {
        for (RacesView v : viewers) if (v.player().getUniqueId().equals(player)) return true;
        return false;
    }

    @Override
    public java.util.Collection<UUID> members() {
        Set<UUID> out = new HashSet<>(bets.keySet());
        for (RacesView v : viewers) out.add(v.player().getUniqueId());
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
        for (RacesView v : List.copyOf(viewers)) v.refresh();
    }

    private void refreshLever() {
        for (RacesView v : List.copyOf(viewers)) v.refreshLever();
    }
}
