package com.dex.boxcasino.game.roulette;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameSettings;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/**
 * One player's Roulette table, one screen. Pick the bet amount, click Đỏ / Đen / Xanh to put that amount on it (one
 * spot per round: another spot moves the bet; click again = new amount, right click = take it off), then spin. Money
 * is taken at the spin; one spin ends the round and takes the spot off.
 */
final class RouletteView extends GameView {

    private final RouletteGame roulette;
    /** Amount on each spot for the next spin, in {@link #currency()}. */
    private final Map<String, Long> stakes = new LinkedHashMap<>();
    private Bet bet;
    private Map<String, Long> running = Map.of();
    private int result = -1;
    /** Wheel index under the pointer cell. */
    private int offset;
    private int totalSteps;
    private int step;
    private BukkitTask task;
    /** Numbers drawn at this table, newest first. */
    private final Deque<Integer> history = new ArrayDeque<>();

    RouletteView(BoxCasinoPlugin plugin, Player player, RouletteGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.roulette = game;
        render();
    }

    @Override
    protected boolean locked() {
        return bet != null;
    }

    @Override
    public boolean busy() {
        return bet != null;
    }

    private long total() {
        long sum = 0;
        for (long v : stakes.values()) sum += v;
        return sum;
    }

    /**
     * Puts the current bet amount on {@code spot}, the only spot of the round: a bet on another spot moves here.
     * False when refused (player told).
     */
    private boolean place(String spot) {
        Currency c = currency();
        GameSettings.Limits limits = gs.limits.get(c);
        long amount = amount();
        long after = amount;
        if (after > limits.max()) {
            plugin.messages().send(player, "games.roulette.max-total", "max", plugin.wallet().format(c, limits.max()));
            return false;
        }
        long cap = roulette.maxGreen.getOrDefault(c, 0L);
        if (spot.equals("green") && cap > 0 && amount > cap) {
            plugin.messages().send(player, "games.roulette.max-green", "max", plugin.wallet().format(c, cap));
            return false;
        }
        long balance = plugin.wallet().balance(player, c);
        if (balance >= 0 && after > balance) {
            plugin.messages().send(player, "games.roulette.no-money", "balance", plugin.wallet().format(c, balance));
            return false;
        }
        if (stakes.size() == 1 && stakes.getOrDefault(spot, 0L) == amount) return false;
        stakes.clear();
        stakes.put(spot, amount);
        return true;
    }

    @Override
    protected void onCurrencyChanged() {
        if (!stakes.isEmpty()) {
            stakes.clear();
            plugin.messages().send(player, "games.roulette.cleared-currency");
            render();
        }
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        if (slot == roulette.spotSlot || slot == roulette.leverSlot) {
            spin();
            return;
        }
        for (var e : roulette.spotSlots.entrySet()) {
            if (e.getValue() != slot) continue;
            String spot = e.getKey();
            boolean ok;
            if (bet != null) ok = false;
            else if (click.isRightClick()) ok = stakes.remove(spot) != null;
            else ok = place(spot);
            if (ok) {
                sound(click.isRightClick() ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_HAT);
                render();
            } else {
                reject();
            }
            return;
        }
    }

    private void spin() {
        if (bet != null) {
            reject();
            return;
        }
        if (stakes.isEmpty()) {
            plugin.messages().send(player, "games.roulette.no-stakes");
            reject();
            return;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!mayStart()) {
            reject();
            return;
        }
        int drawn = roulette.draw(plugin.random());
        StringBuilder json = new StringBuilder("{\"number\":").append(drawn).append(",\"stakes\":{");
        boolean first = true;
        for (var e : stakes.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        json.append("}}");
        Bet placed = plugin.bets().place(player, game.id(), currency(), total(), json.toString());
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        result = drawn;
        running = Map.copyOf(stakes);
        int len = RouletteGame.WHEEL.length;
        // straight to the drawn number (plus spin-laps extra turns), never a too short spin
        totalSteps = roulette.laps * len + Math.floorMod(RouletteGame.wheelIndex(drawn) - offset, len);
        if (totalSteps < 12) totalSteps += len;
        step = 0;
        render();
        sound(Sound.UI_BUTTON_CLICK);
        plugin.dealers().actFor(player.getUniqueId());
        scheduleStep();
    }

    /** Fast at first, slower towards the end (1 → 5 ticks per step). */
    private void scheduleStep() {
        double t = step / (double) Math.max(1, totalSteps);
        long delay = 1 + (long) (4.0 * t * t);
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            offset = (offset + 1) % RouletteGame.WHEEL.length;
            step++;
            renderRing();
            sound(Sound.BLOCK_NOTE_BLOCK_HAT);
            if (step % 12 == 0) plugin.dealers().actFor(player.getUniqueId());
            if (step >= totalSteps) {
                task = null;
                finish(true);
            } else {
                scheduleStep();
            }
        }, delay);
    }

    private void finish(boolean visible) {
        offset = RouletteGame.wheelIndex(result);
        long payout = roulette.payout(running, result);
        plugin.bets().settle(bet, payout);
        Messages m = plugin.messages();
        String[] ph = {"number", String.valueOf(result), "color", colorName(result),
                "payout", plugin.wallet().format(bet.currency, payout), "bet", plugin.wallet().format(bet.currency, bet.amount())};
        boolean profit = payout > bet.amount();
        String key = profit ? "games.roulette.won" : payout > 0 ? "games.roulette.back" : "games.roulette.lost";
        m.send(player, key, ph);
        history.addFirst(result);
        while (history.size() > Math.max(1, roulette.historySlots.size())) history.removeLast();
        bet = null;
        running = Map.of();
        // one spin ends the round: the spot is taken off, the next round starts empty
        stakes.clear();
        if (!visible) return;
        sound(profit ? Sound.ENTITY_PLAYER_LEVELUP : payout > 0 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        player.showTitle(net.kyori.adventure.title.Title.title(m.text("games.roulette.title", ph),
                m.text(key + "-subtitle", ph), net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                        java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(300))));
        render();
    }

    private String colorName(int n) {
        return plugin.messages().raw(n == 0 ? "games.roulette.color-green" : RouletteGame.isRed(n)
                ? "games.roulette.color-red" : "games.roulette.color-black");
    }

    /** ESC while the wheel turns: the GUI comes back (watch it to the end); standing up from the chair skips it. */
    @Override
    protected boolean holdOnEsc() {
        return bet != null;
    }

    @Override
    protected void onClose() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bet != null) finish(false);
    }

    @Override
    protected void redraw() {
        render();
    }

    private void render() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        renderPanel();
        renderRing();
        Map<String, Long> shown = bet != null ? running : stakes;
        Currency c = bet != null ? bet.currency : currency();
        String bet1 = plugin.wallet().format(currency(), amount());
        // the three bets
        for (String spot : RouletteGame.SPOTS) {
            long stake = shown.getOrDefault(spot, 0L);
            String mul = Format.decimal(roulette.multiplier(spot));
            List<Component> lore = new ArrayList<>(m.lines("games.roulette.bet-spot." + spot + ".lore", "multiplier", mul));
            if (stake > 0) {
                lore.addAll(m.lines("games.roulette.bet-spot.staked", "amount", plugin.wallet().format(c, stake),
                        "payout", plugin.wallet().format(c, Payouts.payout(stake, roulette.multiplier(spot)))));
            }
            if (bet == null) lore.addAll(m.lines(stake > 0 ? "games.roulette.bet-spot.hint-staked" : "games.roulette.bet-spot.hint",
                    "bet", bet1));
            set(roulette.spotSlots.get(spot), roulette.spotItems.get(spot),
                    m.line(stake > 0 ? "games.roulette.bet-spot.name-staked" : "games.roulette.bet-spot.name",
                            "spot", m.raw("games.roulette.spot." + spot), "multiplier", mul,
                            "amount", plugin.wallet().format(c, stake)), lore, stake > 0);
        }
        // center: last number, spin button, history
        if (bet == null && result >= 0) {
            set(roulette.spotSlot, roulette.numberItems[result], m.line("games.roulette.last.name", "number",
                    String.valueOf(result), "color", colorName(result)), List.of(), true);
        } else {
            set(roulette.spotSlot, roulette.spotItem, m.line(bet != null ? "games.roulette.spinning.name"
                    : "games.roulette.last-none.name"), List.of());
        }
        String totalText = plugin.wallet().format(c, bet != null ? bet.amount() : total());
        if (bet != null) {
            set(roulette.leverSlot, roulette.spotItem, m.line("games.roulette.spinning.name"), List.of());
        } else if (stakes.isEmpty()) {
            set(roulette.leverSlot, roulette.spotItem, m.line("games.roulette.lever-empty.name"),
                    m.lines("games.roulette.lever-empty.lore"));
        } else {
            set(roulette.leverSlot, roulette.spotItem, m.line("games.roulette.lever.name", "total", totalText),
                    m.lines("games.roulette.lever.lore", "total", totalText));
        }
        List<Integer> h = new ArrayList<>(history);
        for (int i = 0; i < roulette.historySlots.size(); i++) {
            int slot = roulette.historySlots.get(i);
            if (i < h.size()) {
                int n = h.get(i);
                set(slot, roulette.numberItems[n], m.line("games.roulette.history.name", "number", String.valueOf(n),
                        "color", colorName(n), "index", String.valueOf(i + 1)), List.of());
            } else {
                set(slot, roulette.spotItem, m.line("games.roulette.history-empty.name"), List.of());
            }
        }
    }

    /** The 16 ring cells: a window of the wheel, the pointer cell shows the wheel number at {@link #offset}. */
    private void renderRing() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        int size = roulette.ring.size();
        int len = RouletteGame.WHEEL.length;
        for (int i = 0; i < size; i++) {
            int n = RouletteGame.WHEEL[Math.floorMod(offset + i - roulette.pointer, len)];
            boolean atPointer = i == roulette.pointer;
            String key = atPointer ? (bet != null ? "games.roulette.ring-pointer-running.name" : "games.roulette.ring-pointer.name")
                    : "games.roulette.ring.name";
            set(roulette.ring.get(i), roulette.numberItems[n], m.line(key, "number", String.valueOf(n), "color", colorName(n)),
                    List.of(), atPointer);
        }
    }
}
