package com.dex.boxcasino.game.crash;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.chat.ChatRoom;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.extra.Countdown;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
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
 * The one shared flight. IDLE (nobody bet) → BETTING (countdown from the first bet) → FLYING (explosion point drawn at
 * take-off, multiplier grows each step) → CRASHED (result shown) → IDLE. Main thread only.
 */
final class CrashTable implements ChatRoom {

    enum Phase { IDLE, BETTING, FLYING, CRASHED }

    static final class Entry {
        final Bet bet;
        final String name;
        /** Multiplier the player cashed out at, 0 = still flying (or lost). */
        double cashedAt;

        Entry(Bet bet, String name) {
            this.bet = bet;
            this.name = name;
        }
    }

    private final BoxCasinoPlugin plugin;
    private final CrashGame game;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Set<CrashView> viewers = new HashSet<>();
    private final Deque<Double> history = new ArrayDeque<>();
    private Phase phase = Phase.IDLE;
    private int secondsLeft;
    private double point;
    private int step;
    private double multiplier = 1.0;
    private BukkitTask task;
    private int ticks;
    private long lastAnnounce;

    CrashTable(BoxCasinoPlugin plugin, CrashGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    Phase phase() {
        return phase;
    }

    int secondsLeft() {
        return secondsLeft;
    }

    double multiplier() {
        return multiplier;
    }

    double point() {
        return point;
    }

    List<Double> history() {
        return List.copyOf(history);
    }

    Entry entryOf(UUID player) {
        return entries.get(player);
    }

    Collection<Entry> entries() {
        return entries.values();
    }

    boolean acceptsBets() {
        return phase == Phase.IDLE || phase == Phase.BETTING;
    }

    void join(CrashView view) {
        viewers.add(view);
    }

    void leave(CrashView view) {
        viewers.remove(view);
    }

    boolean watching(UUID player) {
        for (CrashView v : viewers) if (v.player().getUniqueId().equals(player)) return true;
        return false;
    }

    /** Takes the stake; the first bet starts the countdown. False when refused (player told). */
    boolean place(Player player, Currency currency, long amount) {
        if (!acceptsBets() || entries.containsKey(player.getUniqueId())) return false;
        Bet bet = plugin.bets().place(player, game.id(), currency, amount, null);
        if (bet == null) return false;
        entries.put(player.getUniqueId(), new Entry(bet, player.getName()));
        if (phase == Phase.IDLE) {
            phase = Phase.BETTING;
            secondsLeft = game.bettingSeconds;
            startTask();
            long now = System.currentTimeMillis();
            if (now - lastAnnounce >= plugin.access().announceCooldownMillis()) {
                lastAnnounce = now;
                plugin.access().announceInCasino(player, "games.crash.broadcast-start",
                        "player", player.getName(), "seconds", String.valueOf(game.bettingSeconds));
            }
        }
        refreshAll();
        return true;
    }

    /** Right click during the countdown: stake back. */
    boolean cancel(Player player) {
        if (phase != Phase.BETTING) return false;
        Entry e = entries.remove(player.getUniqueId());
        if (e == null) return false;
        plugin.bets().refund(e.bet);
        if (entries.isEmpty()) {
            phase = Phase.IDLE;
            stopTask();
        }
        refreshAll();
        return true;
    }

    /** Cash out at the current multiplier. False when not flying or already out. */
    boolean cashOut(UUID player) {
        Entry e = entries.get(player);
        if (phase != Phase.FLYING || e == null || e.cashedAt > 0 || !e.bet.isOpen()) return false;
        pay(e, multiplier);
        refreshAll();
        return true;
    }

    private void pay(Entry e, double at) {
        e.cashedAt = at;
        long payout = Payouts.payout(e.bet.amount(), at);
        plugin.bets().settle(e.bet, payout);
        Player p = Bukkit.getPlayer(e.bet.player);
        if (p != null) {
            String payoutText = plugin.wallet().format(e.bet.currency, payout);
            plugin.messages().send(p, "games.crash.cashed", "multiplier", Format.decimal(at), "payout", payoutText);
            title(p, "games.crash.title-cashed", "games.crash.subtitle-cashed", "payout", payoutText,
                    "multiplier", Format.decimal(at));
            if (plugin.gameSounds()) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    private static final net.kyori.adventure.title.Title.Times TITLE_TIMES = net.kyori.adventure.title.Title.Times.times(
            java.time.Duration.ZERO, java.time.Duration.ofMillis(1200), java.time.Duration.ofMillis(300));

    private void title(Player p, String titleKey, String subtitleKey, String... placeholders) {
        p.showTitle(net.kyori.adventure.title.Title.title(plugin.messages().text(titleKey, placeholders),
                plugin.messages().text(subtitleKey, placeholders), TITLE_TIMES));
    }

    /** Big text for everyone looking at the table. */
    private void titleViewers(String titleKey, String subtitleKey, String... placeholders) {
        for (CrashView v : new ArrayList<>(viewers)) title(v.player(), titleKey, subtitleKey, placeholders);
    }

    /** Live multiplier (and what the viewer would get now) above the hotbar, every step of the flight. */
    private void actionBars() {
        String mul = Format.decimal(multiplier);
        for (CrashView v : new ArrayList<>(viewers)) {
            Player p = v.player();
            Entry e = entries.get(p.getUniqueId());
            net.kyori.adventure.text.Component text;
            if (e == null) {
                text = plugin.messages().text("games.crash.actionbar-watch", "multiplier", mul);
            } else if (e.cashedAt > 0) {
                text = plugin.messages().text("games.crash.actionbar-out", "multiplier", mul, "at", Format.decimal(e.cashedAt),
                        "payout", plugin.wallet().format(e.bet.currency, Payouts.payout(e.bet.amount(), e.cashedAt)));
            } else {
                text = plugin.messages().text("games.crash.actionbar-in", "multiplier", mul,
                        "payout", plugin.wallet().format(e.bet.currency, Payouts.payout(e.bet.amount(), multiplier)));
            }
            p.sendActionBar(text);
        }
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
                    takeOff();
                } else {
                    Countdown.start(plugin, entries.keySet(), secondsLeft, "games.crash.countdown");
                    if (secondsLeft <= 3) {
                        titleViewers("games.crash.title-countdown", "games.crash.subtitle-countdown",
                                "seconds", String.valueOf(secondsLeft));
                    }
                    refreshStatus();
                }
            }
            case FLYING -> {
                if (ticks % game.stepTicks != 0) return;
                step++;
                multiplier = game.multiplierAt(step);
                flightStep();
            }
            case CRASHED -> {
                if (ticks % 20 != 0) return;
                secondsLeft--;
                if (secondsLeft <= 0) {
                    phase = Phase.IDLE;
                    entries.clear();
                    multiplier = 1.0;
                    stopTask();
                    refreshAll();
                }
            }
            default -> stopTask();
        }
    }

    private void takeOff() {
        point = game.drawPoint(plugin.random());
        step = 0;
        ticks = 0;
        multiplier = 1.0;
        phase = Phase.FLYING;
        String outcome = "{\"point\":" + point + "}";
        for (Entry e : entries.values()) plugin.bets().setOutcome(e.bet, outcome);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);
        if (point <= 1.0) {
            explode();
            return;
        }
        titleViewers("games.crash.title-launch", "games.crash.subtitle-launch");
        // closed the table before take-off: out at once (x1.00), like ESC during the flight
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            if (!watching(me.getKey())) pay(me.getValue(), 1.0);
        }
        refreshAll();
    }

    private void flightStep() {
        if (multiplier >= game.maxMultiplier && point >= game.maxMultiplier) {
            // top of the scale: everyone still in is paid the cap
            for (Entry e : entries.values()) if (e.cashedAt == 0) pay(e, game.maxMultiplier);
            multiplier = game.maxMultiplier;
            explode();
            return;
        }
        if (multiplier >= point) {
            multiplier = point;
            explode();
            return;
        }
        // a tick that climbs in pitch with the multiplier (x1 low -> x10 and up high)
        if (step % 4 == 0 && plugin.gameSounds()) {
            float pitch = (float) Math.min(2.0, 0.6 + 1.4 * Math.log10(Math.max(1.0, multiplier)));
            for (CrashView v : viewers) v.player().playSound(v.player().getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
        }
        actionBars();
        refreshFlight();
    }

    private void explode() {
        multiplier = point;
        for (Entry e : entries.values()) {
            if (e.cashedAt > 0 || !e.bet.isOpen()) continue;
            plugin.bets().settle(e.bet, 0);
            Player p = Bukkit.getPlayer(e.bet.player);
            if (p != null) plugin.messages().send(p, "games.crash.lost", "multiplier", Format.decimal(point),
                    "bet", plugin.wallet().format(e.bet.currency, e.bet.amount()));
        }
        history.addFirst(point);
        while (history.size() > Math.max(1, game.historySlots.size())) history.removeLast();
        sound(Sound.ENTITY_GENERIC_EXPLODE);
        String mul = Format.decimal(point);
        for (CrashView v : new ArrayList<>(viewers)) {
            Player p = v.player();
            Entry e = entries.get(p.getUniqueId());
            if (e == null) {
                title(p, "games.crash.title-crashed", "games.crash.subtitle-crashed", "multiplier", mul);
            } else if (e.cashedAt > 0) {
                title(p, "games.crash.title-crashed", "games.crash.subtitle-crashed-out", "multiplier", mul,
                        "at", Format.decimal(e.cashedAt));
            } else {
                title(p, "games.crash.title-crashed", "games.crash.subtitle-crashed-lost", "multiplier", mul,
                        "bet", plugin.wallet().format(e.bet.currency, e.bet.amount()));
            }
        }
        phase = Phase.CRASHED;
        secondsLeft = game.resultSeconds;
        ticks = 0;
        refreshAll();
    }

    /** Plugin disable / reload: still betting = refund; flying = everyone still in cashes out now. */
    void shutdown() {
        stopTask();
        if (phase == Phase.FLYING) {
            for (Entry e : entries.values()) if (e.cashedAt == 0 && e.bet.isOpen()) pay(e, multiplier);
        } else {
            for (Entry e : entries.values()) plugin.bets().refund(e.bet);
        }
        entries.clear();
        phase = Phase.IDLE;
        multiplier = 1.0;
        viewers.clear();
    }

    private void sound(Sound sound) {
        if (!plugin.gameSounds()) return;
        for (CrashView v : viewers) v.player().playSound(v.player().getLocation(), sound, 0.6f, 1f);
    }

    @Override
    public Collection<UUID> members() {
        Set<UUID> out = new HashSet<>(entries.keySet());
        for (CrashView v : viewers) out.add(v.player().getUniqueId());
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
        for (CrashView v : new ArrayList<>(viewers)) v.refresh();
    }

    private void refreshStatus() {
        for (CrashView v : new ArrayList<>(viewers)) v.refreshStatus();
    }

    private void refreshFlight() {
        for (CrashView v : new ArrayList<>(viewers)) v.refreshFlight();
    }
}
